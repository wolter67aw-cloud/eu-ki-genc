package eu.kigenc.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class AttachmentKind(val label: String) { PHOTO("Foto"), VIDEO("Video"), FILE("Datei") }

/** Ein Anhang für Studio-Anfragen: Text und/oder Bilder (als Data-URL für Vision-Modelle). */
data class Attachment(
    val id: String = UUID.randomUUID().toString(),
    val kind: AttachmentKind,
    val name: String,
    val text: String? = null,
    val images: List<String> = emptyList(),
    val thumbnail: Bitmap? = null,
    val note: String = "",
)

object Attachments {
    private const val MAX_TEXT = 40_000
    private const val MAX_PDF_PAGES = 5

    fun displayName(context: Context, uri: Uri): String =
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment ?: "Datei"

    private fun thumb(bmp: Bitmap): Bitmap {
        val s = min(1f, 160f / max(bmp.width, bmp.height))
        return Bitmap.createScaledBitmap(bmp, max(1, (bmp.width * s).roundToInt()), max(1, (bmp.height * s).roundToInt()), true)
    }

    fun photo(context: Context, uri: Uri): Attachment {
        val path = ImageUtils.importImage(context, uri)
        val bmp = BitmapFactory.decodeFile(path) ?: error("Bild konnte nicht gelesen werden.")
        return Attachment(
            kind = AttachmentKind.PHOTO,
            name = displayName(context, uri),
            images = listOf(ImageUtils.bitmapToDataUrl(bmp, 1280, 82)),
            thumbnail = thumb(bmp),
        )
    }

    fun video(context: Context, uri: Uri): Attachment {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, uri)
            val durMs = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val frames = (0 until 6).mapNotNull { i ->
                r.getFrameAtTime((durMs * (i + 0.5) / 6).toLong() * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }
            if (frames.isEmpty()) error("Aus dem Video konnten keine Bilder gelesen werden.")
            return Attachment(
                kind = AttachmentKind.VIDEO,
                name = displayName(context, uri),
                images = frames.map { ImageUtils.bitmapToDataUrl(it, 768, 70) },
                thumbnail = thumb(frames.first()),
                note = "Video, ca. ${durMs / 1000} s – ${frames.size} gleichmäßig verteilte Standbilder in zeitlicher Reihenfolge",
            )
        } finally {
            r.release()
        }
    }

    /** Liest Text-, PDF-, Word-, Bild- oder Videodateien. */
    fun file(context: Context, uri: Uri): Attachment {
        val name = displayName(context, uri)
        val lower = name.lowercase()
        val mime = context.contentResolver.getType(uri).orEmpty()
        return when {
            mime.startsWith("image/") -> photo(context, uri)
            mime.startsWith("video/") -> video(context, uri)
            mime == "application/pdf" || lower.endsWith(".pdf") -> pdf(context, uri, name)
            lower.endsWith(".docx") || mime == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ->
                textAttachment(name, docxText(context, uri))
            lower.endsWith(".xlsx") || lower.endsWith(".xls") || lower.endsWith(".doc") || lower.endsWith(".pptx") ->
                error("Dieses Office-Format wird nicht unterstützt. Bitte als PDF, DOCX, TXT oder CSV speichern.")
            else -> {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Datei leer")
                if (bytes.take(4096).any { it == 0.toByte() }) error("Dateityp wird nicht unterstützt (keine Textdatei).")
                textAttachment(name, String(bytes, Charsets.UTF_8).removePrefix("﻿"))
            }
        }
    }

    private fun textAttachment(name: String, text: String): Attachment {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) error("In der Datei wurde kein Text gefunden.")
        val cut = trimmed.length > MAX_TEXT
        return Attachment(
            kind = AttachmentKind.FILE,
            name = name,
            text = if (cut) trimmed.take(MAX_TEXT) else trimmed,
            note = if (cut) "gekürzt auf die ersten $MAX_TEXT Zeichen" else "${trimmed.length} Zeichen",
        )
    }

    private fun pdf(context: Context, uri: Uri, name: String): Attachment {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: error("PDF konnte nicht geöffnet werden.")
        pfd.use {
            PdfRenderer(it).use { renderer ->
                val count = renderer.pageCount
                val pages = (0 until min(count, MAX_PDF_PAGES)).map { index ->
                    renderer.openPage(index).use { page ->
                        val width = 1200
                        val height = (width.toFloat() * page.height / page.width).roundToInt().coerceAtMost(2400)
                        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bmp
                    }
                }
                return Attachment(
                    kind = AttachmentKind.FILE,
                    name = name,
                    images = pages.map { p -> ImageUtils.bitmapToDataUrl(p, 1200, 80) },
                    thumbnail = pages.firstOrNull()?.let(::thumb),
                    note = if (count > MAX_PDF_PAGES) "PDF: Seiten 1–$MAX_PDF_PAGES von $count" else "PDF: $count Seite(n)",
                )
            }
        }
    }

    private fun docxText(context: Context, uri: Uri): String {
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.name == "word/document.xml") {
                        val xml = zip.readBytes().toString(Charsets.UTF_8)
                        return xml
                            .replace(Regex("</w:p>"), "\n")
                            .replace(Regex("<w:tab/>"), "\t")
                            .replace(Regex("<[^>]+>"), "")
                            .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                            .replace("&apos;", "'").replace("&amp;", "&")
                    }
                }
            }
        }
        error("Word-Datei konnte nicht gelesen werden.")
    }

    /** Baut den Nutzertext inklusive Anhängen zusammen. */
    fun composePrompt(userText: String, attachments: List<Attachment>): String = buildString {
        append(userText.trim())
        attachments.forEachIndexed { i, a ->
            append("\n\n--- Anhang ${i + 1}: ${a.kind.label} „${a.name}“")
            if (a.note.isNotBlank()) append(" (${a.note})")
            append(" ---\n")
            if (a.text != null) append(a.text)
            else append("(siehe beigefügte Bilder)")
        }
    }
}
