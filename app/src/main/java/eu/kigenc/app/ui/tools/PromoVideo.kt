@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package eu.kigenc.app.ui.tools

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer
import android.media.Image as MediaImage
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import eu.kigenc.app.KiApp
import eu.kigenc.app.ai.ApiMessage
import eu.kigenc.app.ai.MistralClient
import eu.kigenc.app.ai.MistralException
import eu.kigenc.app.data.Attachments
import eu.kigenc.app.data.ErrorLog
import eu.kigenc.app.data.ImageUtils
import eu.kigenc.app.data.report
import eu.kigenc.app.ui.components.shareFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------------------------
// Werbevideo: kurze Clips aus eigenen Fotos, Videos und Dateien – mit KI-Werbetexten,
// Einblendungen, Abschluss-Karte und Musik. Alles wird direkt auf dem Gerät gerendert.
// ---------------------------------------------------------------------------------------------

enum class PromoFormat(val label: String, val w: Int, val h: Int) {
    PORTRAIT("Hochformat 9:16", 720, 1280),
    SQUARE("Quadrat 1:1", 720, 720),
    LANDSCAPE("Querformat 16:9", 1280, 720),
}

enum class PromoSceneKind { PHOTO, CLIP, PDF_PAGE }

data class PromoScene(
    val id: String = UUID.randomUUID().toString(),
    val kind: PromoSceneKind,
    val uri: Uri,
    val label: String,
    val pageIndex: Int = 0,
    /** Länge des Videoclips (nur Clips). */
    val clipMs: Long = 0,
    val caption: String = "",
    val thumb: ImageBitmap? = null,
)

/** Inhalt einer hinzugefügten Datei – nur als Information für die KI-Werbetexte. */
data class PromoDoc(val name: String, val text: String?, val images: List<String>)

/** Farben der Abschluss-Karte (oben, unten). */
val PROMO_COLORS: List<Triple<String, Int, Int>> = listOf(
    Triple("Nachtblau", 0xFF06103A.toInt(), 0xFF3B7BF2.toInt()),
    Triple("Violett", 0xFF150B3E.toInt(), 0xFF8B5CF6.toInt()),
    Triple("Rot", 0xFF4A0A0A.toInt(), 0xFFE53935.toInt()),
    Triple("Grün", 0xFF06281A.toInt(), 0xFF2E9E5B.toInt()),
    Triple("Gold", 0xFF2B1D00.toInt(), 0xFFD4A017.toInt()),
    Triple("Schwarz", 0xFF000000.toInt(), 0xFF3A3A3A.toInt()),
)

data class PromoEndCard(val name: String, val cta: String, val contact: String, val colorIndex: Int)

data class PromoSpec(
    val format: PromoFormat,
    val scenes: List<PromoScene>,
    val photoMs: Long,
    val clipMs: Long,
    val slogan: String,
    val endCard: PromoEndCard?,
)

// ---------------------------------------------------------------------------------------------
// Rendern
// ---------------------------------------------------------------------------------------------

object PromoRenderer {
    const val FPS = 24
    private const val FADE_MS = 500L
    private const val END_MS = 3000L
    const val MAX_SCENES = 20

    private sealed class Part(val durationMs: Long) {
        class Scene(val scene: PromoScene, d: Long) : Part(d)
        class End(val card: PromoEndCard) : Part(END_MS)
    }

    /** Darstellung eines Abschnitts (Bild, Clip oder Abschluss-Karte). */
    private interface Source {
        fun draw(canvas: Canvas, localMs: Long, alpha: Int)
        fun release() {}
    }

    fun durationOf(scene: PromoScene, photoMs: Long, clipMs: Long): Long = when (scene.kind) {
        PromoSceneKind.CLIP -> min(clipMs, scene.clipMs.takeIf { it > 0 } ?: clipMs).coerceAtLeast(1000L)
        else -> photoMs
    }

    /** Gesamtlänge in Millisekunden (für die Anzeige vor dem Erstellen). */
    fun totalMs(spec: PromoSpec): Long {
        val d = spec.scenes.map { durationOf(it, spec.photoMs, spec.clipMs) } + listOfNotNull(spec.endCard?.let { END_MS })
        if (d.isEmpty()) return 0
        return d.sum() - FADE_MS * (d.size - 1)
    }

    /**
     * Videogröße: volle Größe, im schonenden Modus (oder wenn der Encoder des Geräts sie nicht kann) 80 %
     * (576×1024, 576×576, 1024×576). Liefert Breite, Höhe und den passenden Encoder-Namen.
     */
    private fun chooseSize(format: PromoFormat, safe: Boolean): Triple<Int, Int, String?> {
        val full = format.w to format.h
        val reduced = (format.w * 4 / 5) to (format.h * 4 / 5)
        val candidates = if (safe) listOf(reduced) else listOf(full, reduced)
        for ((w, h) in candidates) {
            val name = runCatching {
                MediaCodecList(MediaCodecList.REGULAR_CODECS)
                    .findEncoderForFormat(MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h))
            }.getOrNull()
            if (name != null) return Triple(w, h, name)
        }
        // Keine Auskunft vom Gerät → kleinere Größe mit Standard-Encoder versuchen
        return Triple(reduced.first, reduced.second, null)
    }

    suspend fun render(context: Context, spec: PromoSpec, safe: Boolean, onProgress: (Float) -> Unit): File = withContext(Dispatchers.Default) {
        val (w, h, codecName) = chooseSize(spec.format, safe)
        val parts: List<Part> = spec.scenes.map { Part.Scene(it, durationOf(it, spec.photoMs, spec.clipMs)) } +
            listOfNotNull(spec.endCard?.let { Part.End(it) })
        require(parts.isNotEmpty()) { "Keine Bilder oder Clips gewählt." }
        val starts = LongArray(parts.size)
        var t = 0L
        parts.forEachIndexed { i, p ->
            starts[i] = t
            t += p.durationMs - if (i < parts.lastIndex) FADE_MS else 0L
        }
        val totalMs = t
        val totalFrames = max(1, (totalMs * FPS / 1000L).toInt())
        val out = File(File(context.filesDir, "videos").apply { mkdirs() }, "werbevideo_${System.currentTimeMillis()}.mp4")
        val encoder = FrameEncoder(out, w, h, FPS, codecName, if (safe) 3_000_000 else null)
        val frame = try {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        } catch (e: Throwable) {
            encoder.abort(); out.delete(); throw e
        }
        val canvas = Canvas(frame)
        val sources = arrayOfNulls<Source>(parts.size)
        fun source(i: Int): Source = sources[i] ?: createSource(context, parts[i], i, w, h, safe).also { sources[i] = it }
        val textPaints = CaptionPainter(w, h)
        try {
            for (f in 0 until totalFrames) {
                ensureActive()
                val tMs = f * 1000L / FPS
                var i = 0
                while (i < parts.lastIndex && starts[i + 1] <= tMs) i++
                // Frühere Abschnitte freigeben
                for (k in 0 until i - 1) sources[k]?.let { it.release(); sources[k] = null }
                canvas.drawColor(Color.BLACK)
                val fading = i > 0 && tMs < starts[i] + FADE_MS
                if (fading) {
                    val p = (tMs - starts[i]).toFloat() / FADE_MS
                    drawPart(canvas, source(i - 1), parts[i - 1], tMs - starts[i - 1], 255, 1f - p, i - 1 == 0, spec, textPaints)
                    drawPart(canvas, source(i), parts[i], tMs - starts[i], (255 * p).roundToInt(), p, i == 0, spec, textPaints)
                } else {
                    if (i > 0) sources[i - 1]?.let { it.release(); sources[i - 1] = null }
                    drawPart(canvas, source(i), parts[i], tMs - starts[i], 255, 1f, i == 0, spec, textPaints)
                }
                encoder.writeFrame(frame)
                onProgress((f + 1).toFloat() / totalFrames)
            }
            encoder.finish()
        } catch (e: Throwable) {
            encoder.abort()
            out.delete()
            throw e
        } finally {
            sources.forEach { it?.release() }
            frame.recycle()
        }
        out
    }

    private fun drawPart(
        canvas: Canvas, src: Source, part: Part, localMs: Long, alpha: Int, textAlpha: Float,
        first: Boolean, spec: PromoSpec, text: CaptionPainter,
    ) {
        src.draw(canvas, localMs, alpha)
        if (part is Part.Scene) {
            // Einblendungen erscheinen weich (0,4 s)
            val fadeIn = (localMs / 400f).coerceIn(0f, 1f)
            val a = (fadeIn * textAlpha).coerceIn(0f, 1f)
            if (first && spec.slogan.isNotBlank()) text.slogan(canvas, spec.slogan.trim(), a, localMs)
            if (part.scene.caption.isNotBlank()) text.caption(canvas, part.scene.caption.trim(), a)
        }
    }

    private fun createSource(context: Context, part: Part, index: Int, w: Int, h: Int, safe: Boolean): Source = when (part) {
        is Part.End -> EndSource(part.card, w, h)
        is Part.Scene -> when (part.scene.kind) {
            // Schonender Modus: Clips mit halb so vielen Einzelbildern (12 statt 24 pro Sekunde)
            PromoSceneKind.CLIP -> ClipSource(context, part.scene.uri, w, h, stepMs = if (safe) 2000L / FPS else 0L)
            PromoSceneKind.PHOTO -> StillSource(prepareStill(decodeImage(context, part.scene.uri, if (safe) 1280 else 1600), w, h, forceFit = false), index, part.durationMs)
            PromoSceneKind.PDF_PAGE -> StillSource(prepareStill(renderPdfPage(context, part.scene.uri, part.scene.pageIndex, max(w, h)), w, h, forceFit = true), index, part.durationMs)
        }
    }

    /** Foto mit langsamem Zoom („Ken-Burns-Effekt“), Richtung je Szene abwechselnd. */
    private class StillSource(private val base: Bitmap, index: Int, private val durationMs: Long) : Source {
        private val matrix = Matrix()
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        private val zoomIn = index % 2 == 0
        private val fx = listOf(0.5f, 0.3f, 0.7f, 0.5f)[index % 4]
        private val fy = listOf(0.4f, 0.5f, 0.5f, 0.6f)[index % 4]

        override fun draw(canvas: Canvas, localMs: Long, alpha: Int) {
            val p = (localMs.toFloat() / durationMs).coerceIn(0f, 1f)
            val s = if (zoomIn) 1f + 0.10f * p else 1.10f - 0.10f * p
            matrix.reset()
            matrix.postScale(s, s, base.width * fx, base.height * fy)
            paint.alpha = alpha
            canvas.drawBitmap(base, matrix, paint)
        }

        override fun release() = base.recycle()
    }

    /** Videoclip: Bild für Bild aus dem Video gelesen (ohne Originalton). */
    private class ClipSource(context: Context, uri: Uri, private val w: Int, private val h: Int, private val stepMs: Long) : Source {
        private val retriever = MediaMetadataRetriever()
        private var last: Bitmap? = null
        private var lastMs = -1L
        private val durationMs: Long
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        private val dst = RectF()

        init {
            retriever.setDataSource(context, uri)
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        }

        override fun draw(canvas: Canvas, localMs: Long, alpha: Int) {
            var at = if (durationMs > 0) min(localMs, durationMs - 40).coerceAtLeast(0) else localMs
            if (stepMs > 0) at = at / stepMs * stepMs
            if (at != lastMs) {
                val bmp = scaledFrame(retriever, at * 1000, MediaMetadataRetriever.OPTION_CLOSEST, max(w, h))
                if (bmp != null) {
                    last?.recycle()
                    last = bmp
                }
                lastMs = at
            }
            val b = last ?: return
            val scale = max(w.toFloat() / b.width, h.toFloat() / b.height)
            val dw = b.width * scale
            val dh = b.height * scale
            dst.set((w - dw) / 2f, (h - dh) / 2f, (w + dw) / 2f, (h + dh) / 2f)
            paint.alpha = alpha
            canvas.drawBitmap(b, null, dst, paint)
        }

        override fun release() {
            runCatching { retriever.release() }
            last?.recycle()
            last = null
        }
    }

    /** Abschluss-Karte mit Name, Aufruf zum Handeln und Kontakt. */
    private class EndSource(private val card: PromoEndCard, private val w: Int, private val h: Int) : Source {
        private val colors = PROMO_COLORS.getOrElse(card.colorIndex) { PROMO_COLORS[0] }
        private val bg = Paint().apply {
            shader = LinearGradient(0f, 0f, w * 0.3f, h.toFloat(), colors.second, colors.third, Shader.TileMode.CLAMP)
        }
        private val minSide = min(w, h).toFloat()
        private val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textSize = minSide * 0.09f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(10f, 0f, 3f, 0x80000000.toInt())
        }
        private val ctaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.second; textSize = minSide * 0.055f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        private val contactPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textSize = minSide * 0.045f
        }
        private val nameLayout = card.name.takeIf { it.isNotBlank() }?.let { layout(it.trim(), namePaint, (w * 0.84f).toInt(), 3) }
        private val contactLayout = card.contact.takeIf { it.isNotBlank() }?.let { layout(it.trim(), contactPaint, (w * 0.84f).toInt(), 4) }
        private val ctaLayout = card.cta.takeIf { it.isNotBlank() }?.let { layout(it.trim(), ctaPaint, (w * 0.7f).toInt(), 2) }

        override fun draw(canvas: Canvas, localMs: Long, alpha: Int) {
            bg.alpha = alpha
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bg)
            // Text gleitet leicht herein
            val p = (localMs / 500f).coerceIn(0f, 1f)
            val scale = 0.92f + 0.08f * p
            val textAlpha = (alpha * p).roundToInt()
            val gap = minSide * 0.05f
            val pillPadH = minSide * 0.05f
            val pillPadV = minSide * 0.025f
            val heights = listOfNotNull(
                nameLayout?.height?.toFloat(),
                ctaLayout?.let { it.height + 2 * pillPadV },
                contactLayout?.height?.toFloat(),
            )
            val total = heights.sum() + gap * (heights.size - 1).coerceAtLeast(0)
            canvas.save()
            canvas.scale(scale, scale, w / 2f, h / 2f)
            var y = (h - total) / 2f
            nameLayout?.let {
                namePaint.alpha = textAlpha
                drawCentered(canvas, it, y)
                y += it.height + gap
            }
            ctaLayout?.let {
                val lw = (0 until it.lineCount).maxOf { l -> it.getLineWidth(l) }
                val rect = RectF(w / 2f - lw / 2f - pillPadH, y, w / 2f + lw / 2f + pillPadH, y + it.height + 2 * pillPadV)
                pillPaint.alpha = textAlpha
                canvas.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, pillPaint)
                ctaPaint.alpha = textAlpha
                drawCentered(canvas, it, y + pillPadV)
                y += rect.height() + gap
            }
            contactLayout?.let {
                contactPaint.alpha = textAlpha
                drawCentered(canvas, it, y)
            }
            canvas.restore()
        }

        private fun drawCentered(canvas: Canvas, layout: StaticLayout, top: Float) {
            canvas.save()
            canvas.translate((w - layout.width) / 2f, top)
            layout.draw(canvas)
            canvas.restore()
        }
    }

    /** Zeichnet Slogan (groß, Mitte) und Einblendungen (unten, auf dunklem Band). */
    private class CaptionPainter(private val w: Int, private val h: Int) {
        private val minSide = min(w, h).toFloat()
        private val captionPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textSize = minSide * 0.055f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        private val sloganPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textSize = minSide * 0.085f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(14f, 0f, 4f, Color.BLACK)
        }
        private val band = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
        private val cache = HashMap<String, StaticLayout>()

        fun caption(canvas: Canvas, text: String, alpha: Float) {
            val l = cache.getOrPut("c:$text") { layout(text, captionPaint, (w * 0.82f).toInt(), 3) }
            val padH = minSide * 0.04f
            val padV = minSide * 0.025f
            val lw = (0 until l.lineCount).maxOf { l.getLineWidth(it) }
            // Im Hochformat höher, damit Bedienelemente von Reels/TikTok nichts verdecken
            val bottom = h - h * (if (h > w) 0.16f else 0.07f)
            val top = bottom - l.height - 2 * padV
            val rect = RectF(w / 2f - lw / 2f - padH, top, w / 2f + lw / 2f + padH, bottom)
            band.alpha = (150 * alpha).roundToInt()
            canvas.drawRoundRect(rect, minSide * 0.025f, minSide * 0.025f, band)
            captionPaint.alpha = (255 * alpha).roundToInt()
            canvas.save()
            canvas.translate((w - l.width) / 2f, top + padV)
            l.draw(canvas)
            canvas.restore()
        }

        fun slogan(canvas: Canvas, text: String, alpha: Float, localMs: Long) {
            val l = cache.getOrPut("s:$text") { layout(text, sloganPaint, (w * 0.86f).toInt(), 4) }
            val rise = (1f - (localMs / 600f).coerceIn(0f, 1f)) * minSide * 0.03f
            sloganPaint.alpha = (255 * alpha).roundToInt()
            canvas.save()
            canvas.translate((w - l.width) / 2f, h * 0.38f - l.height / 2f + rise)
            l.draw(canvas)
            canvas.restore()
        }
    }

    private fun layout(text: String, paint: TextPaint, width: Int, maxLines: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setIncludePad(false)
            .build()

    /**
     * Bringt ein Bild auf Videogröße: füllend zugeschnitten – passt das Seitenverhältnis schlecht (oder bei
     * PDF-Seiten), vollständig auf einem unscharfen, abgedunkelten Hintergrund aus demselben Bild.
     */
    private fun prepareStill(src: Bitmap?, w: Int, h: Int, forceFit: Boolean): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.BLACK)
        if (src == null) return out
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        val srcRatio = src.width.toFloat() / src.height
        val dstRatio = w.toFloat() / h
        val mismatch = max(srcRatio / dstRatio, dstRatio / srcRatio)
        val cover = max(w.toFloat() / src.width, h.toFloat() / src.height)
        fun rect(scale: Float): RectF {
            val dw = src.width * scale
            val dh = src.height * scale
            return RectF((w - dw) / 2f, (h - dh) / 2f, (w + dw) / 2f, (h + dh) / 2f)
        }
        if (!forceFit && mismatch < 1.45f) {
            canvas.drawBitmap(src, null, rect(cover), paint)
        } else {
            // Unscharfer Hintergrund: stark verkleinert und wieder vergrößert
            val small = Bitmap.createScaledBitmap(src, max(1, src.width / 28), max(1, src.height / 28), true)
            canvas.drawBitmap(small, null, rect(cover), paint)
            small.recycle()
            canvas.drawColor(0x99000000.toInt())
            val fit = min(w.toFloat() / src.width, h.toFloat() / src.height) * 0.94f
            canvas.drawBitmap(src, null, rect(fit), paint)
        }
        src.recycle()
        return out
    }

    /**
     * Einzelbild eines Videos, höchstens [box] Pixel lang – auch auf Android 8.0 (dort wird das volle Bild
     * sofort verkleinert, damit 4K-Videos nicht den Speicher füllen). Fehler (auch Speichermangel) → null.
     */
    fun scaledFrame(r: MediaMetadataRetriever, timeUs: Long, option: Int, box: Int): Bitmap? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            r.getScaledFrameAtTime(timeUs, option, box, box)
        } else {
            r.getFrameAtTime(timeUs, option)?.let { full ->
                val s = min(1f, box.toFloat() / max(full.width, full.height))
                if (s >= 1f) full else Bitmap.createScaledBitmap(full, max(1, (full.width * s).roundToInt()), max(1, (full.height * s).roundToInt()), true)
                    .also { if (it !== full) full.recycle() }
            }
        }
    } catch (e: Throwable) {
        null
    }

    fun decodeImage(context: Context, uri: Uri, maxSide: Int = 1600): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val s = min(1f, maxSide.toFloat() / max(info.size.width, info.size.height))
                if (s < 1f) decoder.setTargetSize((info.size.width * s).roundToInt(), (info.size.height * s).roundToInt())
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            }
        }
    }.getOrNull()

    fun renderPdfPage(context: Context, uri: Uri, index: Int, longSide: Int): Bitmap? = runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            PdfRenderer(pfd).use { r ->
                r.openPage(index).use { page ->
                    val s = longSide.toFloat() / max(page.width, page.height)
                    val bmp = Bitmap.createBitmap(max(1, (page.width * s).roundToInt()), max(1, (page.height * s).roundToInt()), Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bmp
                }
            }
        }
    }.getOrNull()

    fun pdfPageCount(context: Context, uri: Uri): Int = runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd -> PdfRenderer(pfd).use { it.pageCount } } ?: 0
    }.getOrDefault(0)
}

/** H.264-Encoder für einzelne Bilder (YUV über getInputImage – wie bei der Diashow). */
private class FrameEncoder(out: File, private val w: Int, private val h: Int, private val fps: Int, codecName: String?, bitRate: Int?) {
    private val codec = codecName?.let { runCatching { MediaCodec.createByCodecName(it) }.getOrNull() }
        ?: MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
    private val muxer = try {
        MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    } catch (e: Exception) {
        codec.release(); throw e
    }
    private val info = MediaCodec.BufferInfo()
    private var track = -1
    private var started = false
    private var frame = 0L
    private val px = IntArray(w * h)
    private val y = ByteArray(w * h)
    private val u = ByteArray(w * h / 4)
    private val v = ByteArray(w * h / 4)

    init {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate ?: if (w == h) 4_000_000 else 6_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
        } catch (e: Exception) {
            codec.release()
            muxer.release()
            throw IllegalStateException("Der Video-Encoder dieses Geräts unterstützt das Format ${w}×$h nicht.", e)
        }
    }

    private fun drain(endOfStream: Boolean) {
        var idle = 0
        while (true) {
            val index = codec.dequeueOutputBuffer(info, 10_000)
            when {
                index == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!endOfStream || ++idle > 500) return
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    track = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    started = true
                }
                index >= 0 -> {
                    val buf = codec.getOutputBuffer(index)
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                    if (buf != null && info.size > 0 && started) {
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        muxer.writeSampleData(track, buf, info)
                    }
                    codec.releaseOutputBuffer(index, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }

    private fun nextInput(): Int {
        while (true) {
            val i = codec.dequeueInputBuffer(10_000)
            if (i >= 0) return i
            drain(false)
        }
    }

    fun writeFrame(bmp: Bitmap) {
        toYuv(bmp)
        val idx = nextInput()
        val image = codec.getInputImage(idx) ?: error("Der Video-Encoder dieses Geräts wird nicht unterstützt.")
        writePlane(image.planes[0], y, w, h)
        writePlane(image.planes[1], u, w / 2, h / 2)
        writePlane(image.planes[2], v, w / 2, h / 2)
        codec.queueInputBuffer(idx, 0, w * h * 3 / 2, frame * 1_000_000L / fps, 0)
        frame++
        drain(false)
    }

    fun finish() {
        val idx = nextInput()
        codec.queueInputBuffer(idx, 0, 0, frame * 1_000_000L / fps, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        drain(true)
        close()
    }

    fun abort() = close()

    private var closed = false
    private fun close() {
        if (closed) return
        closed = true
        runCatching { codec.stop() }
        runCatching { codec.release() }
        if (started) runCatching { muxer.stop() }
        runCatching { muxer.release() }
    }

    private fun toYuv(bmp: Bitmap) {
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        for (j in 0 until h) {
            val row = j * w
            val even = j % 2 == 0
            for (i in 0 until w) {
                val c = px[row + i]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                y[row + i] = (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16).coerceIn(0, 255).toByte()
                if (even && i % 2 == 0) {
                    val k = (j / 2) * (w / 2) + i / 2
                    u[k] = (((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128).coerceIn(0, 255).toByte()
                    v[k] = (((112 * r - 94 * g - 18 * b + 128) shr 8) + 128).coerceIn(0, 255).toByte()
                }
            }
        }
    }

    private fun writePlane(plane: MediaImage.Plane, data: ByteArray, pw: Int, ph: Int) {
        val buf = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        for (row in 0 until ph) {
            if (pixelStride == 1) {
                buf.position(row * rowStride)
                buf.put(data, row * pw, pw)
            } else {
                var pos = row * rowStride
                val base = row * pw
                for (col in 0 until pw) {
                    buf.put(pos, data[base + col])
                    pos += pixelStride
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------------------------

class PromoVideoViewModel(app: KiApp) : AiToolViewModel(app) {
    val scenes = MutableStateFlow<List<PromoScene>>(emptyList())
    val docs = MutableStateFlow<List<PromoDoc>>(emptyList())
    val format = MutableStateFlow(PromoFormat.PORTRAIT)
    val description = MutableStateFlow("")
    val slogan = MutableStateFlow("")
    val endCardOn = MutableStateFlow(true)
    val endName = MutableStateFlow("")
    val endCta = MutableStateFlow("")
    val endContact = MutableStateFlow("")
    val endColor = MutableStateFlow(0)
    val photoSeconds = MutableStateFlow(2.5f)
    val clipSeconds = MutableStateFlow(4f)
    val music = MutableStateFlow<Uri?>(null)
    val musicName = MutableStateFlow<String?>(null)
    val loading = MutableStateFlow(false)
    val writing = MutableStateFlow(false)
    val progress = MutableStateFlow<Float?>(null)
    val result = MutableStateFlow<File?>(null)
    /** Schonender Modus: kleinere Auflösung, Clips mit weniger Einzelbildern (für ältere/knappe Geräte). */
    val safeMode = MutableStateFlow(false)

    /** Merker „Erstellen läuft“ – bleibt nur liegen, wenn Android die App währenddessen beendet hat. */
    private val runningMarker = File(app.filesDir, "werbevideo_laeuft.txt")

    init {
        if (runningMarker.exists()) {
            val details = runCatching { runningMarker.readText() }.getOrDefault("")
            ErrorLog.log("Werbevideo", message = "App wurde beim Erstellen beendet ($details)")
            runningMarker.delete()
            safeMode.value = true
            message.value = "Beim letzten Versuch wurde die App beim Erstellen beendet. Der schonende Modus ist jetzt an – " +
                "bitte die App während des Erstellens geöffnet lassen."
        }
    }

    /**
     * Vorschaubild als EIGENES Bitmap (neu gezeichnet). Bitmap.createScaledBitmap kann das Ausgangsbild selbst
     * zurückgeben – das wird danach freigegeben (recycle), und die Oberfläche stürzte beim Zeichnen ab
     * („Canvas: trying to use a recycled bitmap“).
     */
    private fun thumb(bmp: Bitmap?): ImageBitmap? = bmp?.let {
        if (it.isRecycled || it.width <= 0 || it.height <= 0) return@let null
        val s = min(1f, 200f / max(it.width, it.height))
        val tw = max(1, (it.width * s).roundToInt())
        val th = max(1, (it.height * s).roundToInt())
        val out = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(it, null, android.graphics.Rect(0, 0, tw, th), Paint(Paint.FILTER_BITMAP_FLAG))
        out.prepareToDraw()
        out.asImageBitmap()
    }

    private fun room(): Int = PromoRenderer.MAX_SCENES - scenes.value.size

    /** Fotos und Videos aus der Galerie. */
    fun addMedia(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            loading.value = true
            val added = withContext(Dispatchers.IO) {
                uris.take(max(0, room())).mapNotNull { uri ->
                    runCatching {
                        val mime = app.contentResolver.getType(uri).orEmpty()
                        if (mime.startsWith("video/")) clipScene(uri) else photoScene(uri)
                    }.getOrNull()
                }
            }
            scenes.value = scenes.value + added
            if (uris.size > added.size) message.value = "Höchstens ${PromoRenderer.MAX_SCENES} Szenen – ${uris.size - added.size} nicht übernommen."
            result.value = null
            loading.value = false
        }
    }

    private fun photoScene(uri: Uri): PromoScene {
        val bmp = PromoRenderer.decodeImage(app, uri, 400) ?: error("Bild nicht lesbar")
        return PromoScene(kind = PromoSceneKind.PHOTO, uri = uri, label = "Foto", thumb = thumb(bmp)).also { bmp.recycle() }
    }

    private fun clipScene(uri: Uri): PromoScene {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(app, uri)
            val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val frame = PromoRenderer.scaledFrame(r, min(500_000L, dur * 500), MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 400)
            val secs = (dur / 1000).toInt()
            return PromoScene(
                kind = PromoSceneKind.CLIP, uri = uri, clipMs = dur,
                label = "Clip %d:%02d".format(secs / 60, secs % 60), thumb = thumb(frame),
            ).also { frame?.recycle() }
        } finally {
            runCatching { r.release() }
        }
    }

    /** PDF, Word, Text oder Bilder: Inhalt für die KI; PDF-Seiten und Bilder zusätzlich als Szenen. */
    fun addFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            loading.value = true
            val newScenes = mutableListOf<PromoScene>()
            val newDocs = mutableListOf<PromoDoc>()
            val errors = mutableListOf<String>()
            withContext(Dispatchers.IO) {
                for (uri in uris) {
                    val name = Attachments.displayName(app, uri)
                    val mime = app.contentResolver.getType(uri).orEmpty()
                    try {
                        when {
                            mime.startsWith("image/") -> if (scenes.value.size + newScenes.size < PromoRenderer.MAX_SCENES) newScenes += photoScene(uri)
                            mime.startsWith("video/") -> if (scenes.value.size + newScenes.size < PromoRenderer.MAX_SCENES) newScenes += clipScene(uri)
                            else -> {
                                val a = Attachments.file(app, uri)
                                newDocs += PromoDoc(name, a.text, a.images)
                                if (mime == "application/pdf" || name.lowercase().endsWith(".pdf")) {
                                    val count = min(PromoRenderer.pdfPageCount(app, uri), 5)
                                    for (p in 0 until count) {
                                        if (scenes.value.size + newScenes.size >= PromoRenderer.MAX_SCENES) break
                                        val bmp = PromoRenderer.renderPdfPage(app, uri, p, 400)
                                        newScenes += PromoScene(
                                            kind = PromoSceneKind.PDF_PAGE, uri = uri, pageIndex = p,
                                            label = "PDF S. ${p + 1}", thumb = thumb(bmp),
                                        )
                                        bmp?.recycle()
                                    }
                                }
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        errors += "$name: ${e.message ?: "nicht lesbar"}"
                    }
                }
            }
            scenes.value = scenes.value + newScenes
            docs.value = docs.value + newDocs
            if (errors.isNotEmpty()) message.value = errors.joinToString("\n")
            result.value = null
            loading.value = false
        }
    }

    fun removeDoc(doc: PromoDoc) { docs.value = docs.value - doc }

    fun removeScene(id: String) {
        scenes.value = scenes.value.filterNot { it.id == id }
        result.value = null
    }

    fun moveScene(index: Int, delta: Int) {
        val list = scenes.value.toMutableList()
        val target = index + delta
        if (index !in list.indices || target !in list.indices) return
        list.add(target, list.removeAt(index))
        scenes.value = list
    }

    fun setCaption(id: String, text: String) {
        scenes.value = scenes.value.map { if (it.id == id) it.copy(caption = text.take(120)) else it }
    }

    fun setMusic(uri: Uri?) {
        music.value = uri
        musicName.value = uri?.let { Attachments.displayName(app, it) }
    }

    fun spec(): PromoSpec = PromoSpec(
        format = format.value,
        scenes = scenes.value,
        photoMs = (photoSeconds.value * 1000).roundToInt().toLong(),
        clipMs = (clipSeconds.value * 1000).roundToInt().toLong(),
        slogan = slogan.value,
        endCard = if (endCardOn.value && (endName.value.isNotBlank() || endCta.value.isNotBlank() || endContact.value.isNotBlank()))
            PromoEndCard(endName.value, endCta.value, endContact.value, endColor.value) else null,
    )

    /** KI schreibt Slogan, Einblendungen je Szene, Aufruf zum Handeln (und ggf. Name/Kontakt aus den Unterlagen). */
    fun writeTexts() {
        val list = scenes.value
        if (list.isEmpty() && docs.value.isEmpty() && description.value.isBlank()) {
            message.value = "Füge zuerst Fotos, Videos oder Dateien hinzu oder beschreibe, was beworben werden soll."
            return
        }
        if (writing.value) return
        viewModelScope.launch {
            val s = settingsOrNull() ?: return@launch
            writing.value = true
            try {
                val images = withContext(Dispatchers.IO) { sceneImages(list) }
                val docImages = docs.value.flatMap { it.images }.take(max(0, 8 - images.size))
                val docText = docs.value.filter { !it.text.isNullOrBlank() }
                    .joinToString("\n\n") { "### ${it.name}\n${it.text}" }.take(12_000)
                val prompt = buildString {
                    appendLine("Erstelle Texte für ein kurzes Werbevideo (${format.value.label}, ${list.size} Szenen).")
                    appendLine("Was beworben wird: ${description.value.trim().ifBlank { "(nicht angegeben – aus Bildern und Unterlagen ableiten)" }}")
                    if (images.isNotEmpty()) appendLine("Die ersten ${images.size} Bilder zeigen die Szenen 1–${images.size} in Reihenfolge.")
                    if (docImages.isNotEmpty()) appendLine("Weitere ${docImages.size} Bilder sind Seiten aus den Unterlagen.")
                    if (docText.isNotBlank()) appendLine("\nUnterlagen:\n$docText")
                    appendLine()
                    appendLine("Antworte nur als JSON-Objekt mit den Feldern:")
                    appendLine("\"slogan\": kurzer, einprägsamer Werbeslogan (max. 8 Wörter),")
                    appendLine("\"captions\": Liste mit genau ${list.size} kurzen Einblendungen (je max. 6 Wörter, passend zur jeweiligen Szene, ohne Emojis),")
                    appendLine("\"cta\": Aufruf zum Handeln (max. 4 Wörter, z. B. „Jetzt bestellen“),")
                    appendLine("\"name\": Firmen- oder Produktname, nur wenn erkennbar, sonst leer,")
                    appendLine("\"contact\": Webseite/Telefon/Adresse NUR wenn wörtlich in den Unterlagen oder Bildern, sonst leer – nichts erfinden.")
                    append("Alle Texte auf Deutsch, werbewirksam, ehrlich (keine falschen Versprechen oder Preise).")
                }
                val all = images + docImages
                var raw = ""
                var last: Exception? = null
                val models = if (all.isEmpty()) listOf(s.chatModel, MistralClient.FALLBACK_MODEL) else listOf(s.chatModel, MistralClient.FALLBACK_MODEL, "mistral-medium-latest")
                for (m in models.distinct()) {
                    try {
                        raw = app.mistral.complete(
                            s.apiKey, m,
                            listOf(
                                ApiMessage("system", "Du bist ein erfahrener Werbetexter für Social-Media-Videos."),
                                ApiMessage("user", prompt, extraImages = all),
                            ),
                            json = true, temperature = 0.7,
                        )
                        if (raw.isNotBlank()) break
                    } catch (e: MistralException) {
                        if (e.code == 401 || e.code == 429) throw e
                        last = e
                    }
                }
                if (raw.isBlank()) throw last ?: IllegalStateException("Die KI hat keine Texte geliefert.")
                val o = JSONObject(raw.substringAfter('{', "").let { "{$it" }.substringBeforeLast('}') + "}")
                o.optString("slogan").takeIf { it.isNotBlank() }?.let { slogan.value = it.trim() }
                val caps = o.optJSONArray("captions")
                if (caps != null) {
                    scenes.value = scenes.value.mapIndexed { i, sc ->
                        val c = caps.optString(i).trim()
                        if (i < caps.length() && c.isNotBlank() && i < list.size) sc.copy(caption = c.take(120)) else sc
                    }
                }
                o.optString("cta").takeIf { it.isNotBlank() }?.let { endCta.value = it.trim() }
                if (endName.value.isBlank()) o.optString("name").takeIf { it.isNotBlank() }?.let { endName.value = it.trim() }
                if (endContact.value.isBlank()) o.optString("contact").takeIf { it.isNotBlank() }?.let { endContact.value = it.trim() }
                message.value = "Werbetexte eingesetzt – du kannst alles noch ändern."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message.value = e.report("Werbevideo-Texte")
            } finally {
                writing.value = false
            }
        }
    }

    /** Bis zu 6 Szenenbilder (klein) für die KI. */
    private fun sceneImages(list: List<PromoScene>): List<String> = list.take(6).mapNotNull { sc ->
        runCatching {
            val bmp = when (sc.kind) {
                PromoSceneKind.PHOTO -> PromoRenderer.decodeImage(app, sc.uri, 768)
                PromoSceneKind.PDF_PAGE -> PromoRenderer.renderPdfPage(app, sc.uri, sc.pageIndex, 1024)
                PromoSceneKind.CLIP -> MediaMetadataRetriever().let { r ->
                    try {
                        r.setDataSource(app, sc.uri)
                        PromoRenderer.scaledFrame(r, min(1_000_000L, sc.clipMs * 500), MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 768)
                    } finally {
                        runCatching { r.release() }
                    }
                }
            } ?: return@runCatching null
            ImageUtils.bitmapToDataUrl(bmp, 768, 72).also { bmp.recycle() }
        }.getOrNull()
    }

    fun create() {
        val spec = spec()
        if (spec.scenes.isEmpty() || progress.value != null) return
        val safe = safeMode.value
        viewModelScope.launch {
            progress.value = 0f
            runCatching {
                runningMarker.writeText(
                    "${spec.format.label}, ${spec.scenes.size} Szenen, davon ${spec.scenes.count { it.kind == PromoSceneKind.CLIP }} Clips" +
                        (if (safe) ", schonend" else "") + (if (music.value != null) ", mit Musik" else "")
                )
            }
            try {
                val video = PromoRenderer.render(app, spec, safe) { progress.value = it * 0.92f }
                val audio = music.value
                result.value = if (audio != null) {
                    runCatching { AudioMixer.addMusic(app, video, audio) }.getOrElse { e ->
                        if (e is CancellationException) throw e
                        ErrorLog.log("Werbevideo-Musik", e)
                        message.value = "Musik konnte nicht eingefügt werden (${e.message ?: "Format nicht unterstützt"}) – Video ohne Ton erstellt."
                        video
                    }.also { if (it != video) video.delete() }
                } else video
                progress.value = 1f
            } catch (e: CancellationException) {
                throw e
            } catch (e: OutOfMemoryError) {
                ErrorLog.log("Werbevideo", e)
                safeMode.value = true
                message.value = "Zu wenig Arbeitsspeicher. Der schonende Modus ist jetzt an – bitte erneut erstellen " +
                    "(oder weniger bzw. kürzere Clips verwenden)."
            } catch (e: Throwable) {
                ErrorLog.log("Werbevideo", e)
                message.value = "Werbevideo konnte nicht erstellt werden: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                progress.value = null
                runningMarker.delete()
            }
        }
    }

    fun saveToGallery() {
        val file = result.value ?: return
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { ImageUtils.saveVideoToGallery(app, file) }
            message.value = if (ok) "In der Galerie gespeichert (Filme/KI-GenC)." else "Speichern fehlgeschlagen."
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Oberfläche
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PromoVideoScreen(vm: PromoVideoViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scenes by vm.scenes.collectAsStateWithLifecycle()
    val docs by vm.docs.collectAsStateWithLifecycle()
    val format by vm.format.collectAsStateWithLifecycle()
    val description by vm.description.collectAsStateWithLifecycle()
    val slogan by vm.slogan.collectAsStateWithLifecycle()
    val endOn by vm.endCardOn.collectAsStateWithLifecycle()
    val endName by vm.endName.collectAsStateWithLifecycle()
    val endCta by vm.endCta.collectAsStateWithLifecycle()
    val endContact by vm.endContact.collectAsStateWithLifecycle()
    val endColor by vm.endColor.collectAsStateWithLifecycle()
    val photoSeconds by vm.photoSeconds.collectAsStateWithLifecycle()
    val clipSeconds by vm.clipSeconds.collectAsStateWithLifecycle()
    val musicName by vm.musicName.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val writing by vm.writing.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val result by vm.result.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val safeMode by vm.safeMode.collectAsStateWithLifecycle()

    // Während des Erstellens Bildschirm anlassen – sonst kann Android die App im Hintergrund beenden.
    val view = androidx.compose.ui.platform.LocalView.current
    val rendering = progress != null
    androidx.compose.runtime.DisposableEffect(rendering) {
        view.keepScreenOn = rendering
        onDispose { view.keepScreenOn = false }
    }

    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(PromoRenderer.MAX_SCENES)) { vm.addMedia(it) }
    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.addFiles(it) }
    val pickMusic = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.setMusic(uri) }
    val busy = progress != null

    ToolScaffold("Werbevideo", onBack, modifier, message, vm::clearMessage) { inner ->
        LazyColumn(contentPadding = toolPadding(inner), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
            item {
                Text(
                    "Kurze Werbevideos für Reels, TikTok, WhatsApp-Status oder YouTube aus deinen Fotos, Videoclips und Dateien. " +
                        "Die KI schreibt auf Wunsch Slogan und Einblendungen. Das Video entsteht direkt auf deinem Handy.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // 1. Inhalte
            item { PromoHeading("1. Fotos, Videos & Dateien") }
            item {
                Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                        enabled = !busy && !loading, modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Outlined.PhotoLibrary, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text("Fotos & Videos")
                    }
                    OutlinedButton(
                        onClick = {
                            pickFiles.launch(
                                arrayOf(
                                    "application/pdf", "text/*", "image/*", "video/*",
                                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                )
                            )
                        },
                        enabled = !busy && !loading, modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Outlined.Description, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text("Dateien")
                    }
                }
                Text(
                    "Dateien: PDF (Seiten werden zu Bildern), Word oder Text (z. B. Flyer, Speisekarte, Angebot) – die KI nutzt den Inhalt für die Werbetexte.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                }
            }
            if (loading) item { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.size(8.dp)); Text("Lese Dateien …") } }
            if (docs.isNotEmpty()) {
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        docs.forEach { d ->
                            InputChip(
                                selected = false, onClick = { vm.removeDoc(d) },
                                label = { Text(d.name, maxLines = 1) },
                                leadingIcon = { Icon(Icons.Outlined.Description, null, Modifier.size(16.dp)) },
                                trailingIcon = { Icon(Icons.Filled.Close, "Datei „${d.name}“ entfernen", Modifier.size(16.dp)) },
                            )
                        }
                    }
                }
            }
            itemsIndexed(scenes, key = { _, s -> s.id }) { index, scene ->
                SceneRow(
                    index = index, scene = scene, count = scenes.size, enabled = !busy,
                    onCaption = { vm.setCaption(scene.id, it) },
                    onMove = { vm.moveScene(index, it) },
                    onRemove = { vm.removeScene(scene.id) },
                )
            }

            // 2. Format
            item { PromoHeading("2. Format") }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PromoFormat.entries.forEach { f ->
                        FilterChip(selected = f == format, onClick = { vm.format.value = f }, label = { Text(f.label) }, enabled = !busy)
                    }
                }
            }

            // 3. Texte
            item { PromoHeading("3. Werbetexte") }
            item {
                OutlinedTextField(
                    value = description, onValueChange = { vm.description.value = it.take(500) },
                    label = { Text("Was bewirbst du? (z. B. „Bäckerei Sonnenschein, Angebot der Woche“)") },
                    modifier = Modifier.fillMaxWidth(), minLines = 2,
                )
            }
            item {
                Button(onClick = vm::writeTexts, enabled = !writing && !busy, modifier = Modifier.fillMaxWidth()) {
                    if (writing) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.size(8.dp)); Text("KI schreibt …")
                    } else {
                        Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(18.dp)); Spacer(Modifier.size(8.dp)); Text("Werbetexte mit KI schreiben")
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = slogan, onValueChange = { vm.slogan.value = it.take(80) },
                    label = { Text("Slogan (groß auf der ersten Szene)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // 4. Abschluss-Karte
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { PromoHeading("4. Abschluss-Karte") }
                    Switch(checked = endOn, onCheckedChange = { vm.endCardOn.value = it }, modifier = Modifier.semantics { contentDescription = "Abschluss-Karte zeigen" })
                }
            }
            if (endOn) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(endName, { vm.endName.value = it.take(60) }, label = { Text("Name / Firma") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(endCta, { vm.endCta.value = it.take(40) }, label = { Text("Aufruf (z. B. „Jetzt bestellen“)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(endContact, { vm.endContact.value = it.take(160) }, label = { Text("Kontakt (Webseite, Telefon, Adresse)") }, modifier = Modifier.fillMaxWidth(), maxLines = 3)
                        Text("Farbe", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PROMO_COLORS.forEachIndexed { i, (name, top, bottom) ->
                                Box(
                                    Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(androidx.compose.ui.graphics.Color(top), androidx.compose.ui.graphics.Color(bottom))))
                                        .border(if (i == endColor) 3.dp else 1.dp, if (i == endColor) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                                        .clickable { vm.endColor.value = i }
                                        .semantics { contentDescription = "Farbe $name" + if (i == endColor) ", ausgewählt" else "" }
                                )
                            }
                        }
                    }
                }
            }

            // 5. Länge & Musik
            item { PromoHeading("5. Länge & Musik") }
            item {
                Column {
                    SecondsSlider("Sekunden pro Foto/Seite", photoSeconds, 1.5f..5f) { vm.photoSeconds.value = it }
                    if (scenes.any { it.kind == PromoSceneKind.CLIP }) {
                        SecondsSlider("Höchstens Sekunden pro Clip", clipSeconds, 2f..10f) { vm.clipSeconds.value = it }
                        Text(
                            "Von jedem Clip wird der Anfang verwendet (ohne Originalton).",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickMusic.launch(arrayOf("audio/*")) }, enabled = !busy) {
                        Icon(Icons.Outlined.MusicNote, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp))
                        Text(if (musicName == null) "Hintergrundmusik wählen" else "Musik ändern")
                    }
                    if (musicName != null) {
                        Text(musicName ?: "", style = MaterialTheme.typography.labelSmall, maxLines = 1, modifier = Modifier.weight(1f))
                        IconButton(onClick = { vm.setMusic(null) }) { Icon(Icons.Filled.Close, "Musik entfernen") }
                    }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Schonender Modus", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "Für ältere Handys oder viele Clips: etwas kleinere Auflösung, Clips mit weniger Einzelbildern.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = safeMode, onCheckedChange = { vm.safeMode.value = it }, enabled = !busy,
                        modifier = Modifier.semantics { contentDescription = "Schonender Modus" },
                    )
                }
            }

            // Erstellen
            item {
                Column {
                val total = PromoRenderer.totalMs(vm.spec())
                Button(onClick = vm::create, enabled = !busy && scenes.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        when {
                            progress != null -> "Erstelle Werbevideo … ${((progress ?: 0f) * 100).toInt()} %"
                            scenes.isEmpty() -> "Erst Fotos, Videos oder Dateien hinzufügen"
                            else -> "Werbevideo erstellen (ca. ${"%.0f".format(total / 1000f)} s)"
                        }
                    )
                }
                progress?.let {
                    LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                    Text(
                        "Bitte die App geöffnet lassen – Clips brauchen etwas länger.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                }
            }
            result?.let { file ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Werbevideo bereit (${file.length() / 1024} KB)", style = MaterialTheme.typography.titleMedium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "video/mp4")
                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                                    runCatching { context.startActivity(intent) }
                                }) {
                                    Icon(Icons.Outlined.PlayCircle, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text("Abspielen")
                                }
                                OutlinedButton(onClick = vm::saveToGallery) { Text("Speichern") }
                                OutlinedButton(onClick = { context.shareFile(file, "video/mp4", "Werbevideo teilen") }) { Text("Teilen") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PromoHeading(text: String) {
    Text(
        text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp).semantics { heading() },
    )
}

@Composable
private fun SecondsSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text("%.1f s".format(value), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = value, onValueChange = { onChange((it * 2).roundToInt() / 2f) }, valueRange = range,
            modifier = Modifier.semantics { contentDescription = label },
        )
    }
}

@Composable
private fun SceneRow(
    index: Int, scene: PromoScene, count: Int, enabled: Boolean,
    onCaption: (String) -> Unit, onMove: (Int) -> Unit, onRemove: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                val thumb = scene.thumb
                if (thumb != null) {
                    Image(thumb, null, contentScale = ContentScale.Crop, modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)))
                } else {
                    Box(Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
                }
            }
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text("${index + 1}. ${scene.label}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = scene.caption, onValueChange = onCaption,
                    placeholder = { Text("Einblendung (optional)") },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    maxLines = 2, enabled = enabled, modifier = Modifier.fillMaxWidth(),
                )
            }
            Column {
                IconButton(onClick = { onMove(-1) }, enabled = enabled && index > 0, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.KeyboardArrowUp, "Szene ${index + 1} nach oben")
                }
                IconButton(onClick = onRemove, enabled = enabled, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Close, "Szene ${index + 1} entfernen")
                }
                IconButton(onClick = { onMove(1) }, enabled = enabled && index < count - 1, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.KeyboardArrowDown, "Szene ${index + 1} nach unten")
                }
            }
        }
    }
}
