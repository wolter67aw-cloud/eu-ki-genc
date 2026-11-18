package eu.kigenc.app.data

import android.content.Context
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Wandelt KI-Antworten (Markdown) in Dateien um: Word (.docx), PDF und Excel (.xlsx, aus Markdown-Tabellen).
 * Ohne zusätzliche Bibliotheken – DOCX/XLSX sind ZIP-Dateien mit XML, PDF entsteht mit Android-Bordmitteln.
 */
object DocExport {

    // ---------- Markdown in Blöcke zerlegen ----------

    sealed interface Block
    data class Heading(val level: Int, val text: String) : Block
    data class Para(val text: String) : Block
    data class Item(val text: String, val marker: String) : Block
    data class Code(val text: String) : Block
    data class Table(val rows: List<List<String>>) : Block
    data object Rule : Block

    fun parse(md: String): List<Block> {
        val out = mutableListOf<Block>()
        val lines = md.replace("\r\n", "\n").split("\n")
        var i = 0
        val para = StringBuilder()
        fun flush() {
            if (para.isNotBlank()) out += Para(para.toString().trim())
            para.clear()
        }
        while (i < lines.size) {
            val raw = lines[i]
            val line = raw.trimEnd()
            val t = line.trim()
            when {
                t.startsWith("```") -> {
                    flush()
                    val code = StringBuilder()
                    i++
                    while (i < lines.size && !lines[i].trim().startsWith("```")) { code.append(lines[i]).append('\n'); i++ }
                    out += Code(code.toString().trimEnd())
                }
                t.isEmpty() -> flush()
                Regex("^#{1,6}\\s").containsMatchIn(t) -> {
                    flush()
                    val level = t.takeWhile { it == '#' }.length
                    out += Heading(level, t.drop(level).trim())
                }
                t.matches(Regex("^(-{3,}|\\*{3,}|_{3,})$")) -> { flush(); out += Rule }
                t.startsWith("|") && i + 1 < lines.size && lines[i + 1].trim().matches(Regex("^\\|?\\s*:?-{2,}.*")) -> {
                    flush()
                    val rows = mutableListOf<List<String>>()
                    rows += cells(t)
                    i += 2
                    while (i < lines.size && lines[i].trim().startsWith("|")) { rows += cells(lines[i].trim()); i++ }
                    out += Table(rows)
                    continue
                }
                Regex("^[-*+]\\s+").containsMatchIn(t) -> { flush(); out += Item(t.replaceFirst(Regex("^[-*+]\\s+"), ""), "•") }
                Regex("^\\d+[.)]\\s+").containsMatchIn(t) -> {
                    flush()
                    val num = t.takeWhile { it.isDigit() }
                    out += Item(t.replaceFirst(Regex("^\\d+[.)]\\s+"), ""), "$num.")
                }
                else -> para.append(if (para.isEmpty()) t else " $t")
            }
            i++
        }
        flush()
        return out
    }

    private fun cells(row: String): List<String> =
        row.trim().removePrefix("|").removeSuffix("|").split("|").map { it.trim() }

    /** Inline-Markdown in Abschnitte: (Text, fett, kursiv, Code). */
    private data class Run(val text: String, val bold: Boolean = false, val italic: Boolean = false, val code: Boolean = false)

    private fun runs(text: String): List<Run> {
        val res = mutableListOf<Run>()
        val re = Regex("\\*\\*(.+?)\\*\\*|__(.+?)__|`([^`]+)`|\\*([^*]+)\\*|\\[([^\\]]+)]\\(([^)]+)\\)")
        var pos = 0
        for (m in re.findAll(text)) {
            if (m.range.first > pos) res += Run(text.substring(pos, m.range.first))
            val g = m.groupValues
            res += when {
                g[1].isNotEmpty() -> Run(g[1], bold = true)
                g[2].isNotEmpty() -> Run(g[2], bold = true)
                g[3].isNotEmpty() -> Run(g[3], code = true)
                g[4].isNotEmpty() -> Run(g[4], italic = true)
                else -> Run("${g[5]} (${g[6]})")
            }
            pos = m.range.last + 1
        }
        if (pos < text.length) res += Run(text.substring(pos))
        return res
    }

    fun plain(text: String) = runs(text).joinToString("") { it.text }

    fun hasTable(md: String) = parse(md).any { it is Table }

    /** Dateiname aus der ersten Zeile, ohne Sonderzeichen. */
    fun fileName(md: String, ext: String): String {
        val first = md.lineSequence().map { plain(it.trim().trimStart('#').trim()) }.firstOrNull { it.isNotBlank() }.orEmpty()
        val base = first.replace(Regex("[^\\p{L}\\p{N} _-]"), "").trim().take(40).ifBlank { "KI-GenC" }
        val stamp = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.GERMANY).format(Date())
        return "${base}_$stamp.$ext"
    }

    private fun shareDir(context: Context) = File(context.cacheDir, "share").apply { mkdirs() }

    // ---------- Word (.docx) ----------

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun wRuns(text: String, forceBold: Boolean = false, size: Int? = null): String = runs(text).joinToString("") { r ->
        val props = buildString {
            if (r.code) append("<w:rFonts w:ascii=\"Consolas\" w:hAnsi=\"Consolas\"/>")
            if (r.bold || forceBold) append("<w:b/>")
            if (r.italic) append("<w:i/>")
            if (size != null) append("<w:sz w:val=\"$size\"/>")
        }
        "<w:r>${if (props.isNotEmpty()) "<w:rPr>$props</w:rPr>" else ""}<w:t xml:space=\"preserve\">${esc(r.text)}</w:t></w:r>"
    }

    private fun wPara(inner: String, spacingAfter: Int = 120, indent: Int = 0) =
        "<w:p><w:pPr><w:spacing w:after=\"$spacingAfter\"/>${if (indent > 0) "<w:ind w:left=\"$indent\" w:hanging=\"280\"/>" else ""}</w:pPr>$inner</w:p>"

    fun docx(context: Context, md: String): File {
        val body = StringBuilder()
        for (b in parse(md)) {
            body.append(
                when (b) {
                    is Heading -> wPara(wRuns(b.text, forceBold = true, size = when (b.level) { 1 -> 36; 2 -> 30; 3 -> 26; else -> 24 }), 160)
                    is Para -> wPara(wRuns(b.text))
                    is Item -> wPara("<w:r><w:t xml:space=\"preserve\">${esc(b.marker)}\t</w:t></w:r>" + wRuns(b.text), 60, 560)
                    is Code -> b.text.split("\n").joinToString("") { line ->
                        wPara("<w:r><w:rPr><w:rFonts w:ascii=\"Consolas\" w:hAnsi=\"Consolas\"/><w:sz w:val=\"20\"/></w:rPr><w:t xml:space=\"preserve\">${esc(line)}</w:t></w:r>", 0)
                    }
                    is Rule -> "<w:p><w:pPr><w:pBdr><w:bottom w:val=\"single\" w:sz=\"6\" w:space=\"1\" w:color=\"999999\"/></w:pBdr></w:pPr></w:p>"
                    is Table -> {
                        val border = "<w:top w:val=\"single\" w:sz=\"4\" w:color=\"999999\"/><w:left w:val=\"single\" w:sz=\"4\" w:color=\"999999\"/>" +
                            "<w:bottom w:val=\"single\" w:sz=\"4\" w:color=\"999999\"/><w:right w:val=\"single\" w:sz=\"4\" w:color=\"999999\"/>" +
                            "<w:insideH w:val=\"single\" w:sz=\"4\" w:color=\"999999\"/><w:insideV w:val=\"single\" w:sz=\"4\" w:color=\"999999\"/>"
                        val cols = b.rows.maxOf { it.size }.coerceAtLeast(1)
                        "<w:tbl><w:tblPr><w:tblW w:w=\"0\" w:type=\"auto\"/><w:tblBorders>$border</w:tblBorders></w:tblPr>" +
                            "<w:tblGrid>" + "<w:gridCol w:w=\"${9638 / cols}\"/>".repeat(cols) + "</w:tblGrid>" +
                            b.rows.mapIndexed { ri, row ->
                                "<w:tr>" + row.joinToString("") { c -> "<w:tc><w:tcPr><w:tcW w:w=\"0\" w:type=\"auto\"/></w:tcPr>${wPara(wRuns(c, forceBold = ri == 0), 40)}</w:tc>" } + "</w:tr>"
                            }.joinToString("") + "</w:tbl>" + wPara("", 60)
                    }
                }
            )
        }
        val document = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>$body" +
            "<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/><w:pgMar w:top=\"1134\" w:right=\"1134\" w:bottom=\"1134\" w:left=\"1134\"/></w:sectPr></w:body></w:document>"
        val files = linkedMapOf(
            "[Content_Types].xml" to "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
                "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
                "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
                "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/></Types>",
            "_rels/.rels" to "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/></Relationships>",
            "word/document.xml" to document,
        )
        return File(shareDir(context), fileName(md, "docx")).also { it.writeBytes(zip(files)) }
    }

    // ---------- Excel (.xlsx) aus Markdown-Tabellen ----------

    fun xlsx(context: Context, md: String): File {
        val tables = parse(md).filterIsInstance<Table>()
        require(tables.isNotEmpty()) { "Die Antwort enthält keine Tabelle." }
        fun col(n: Int): String { var x = n + 1; var s = ""; while (x > 0) { val r = (x - 1) % 26; s = "${'A' + r}$s"; x = (x - 1) / 26 }; return s }
        fun sheet(t: Table): String {
            val rows = t.rows.mapIndexed { ri, row ->
                "<row r=\"${ri + 1}\">" + row.mapIndexed { ci, raw ->
                    val v = plain(raw)
                    val ref = "${col(ci)}${ri + 1}"
                    // Zahl im deutschen Format (1.234,5) erkennen
                    val num = v.replace("€", "").replace("%", "").trim().let { n ->
                        if (Regex("^-?[\\d.]*,\\d+$").matches(n) || Regex("^-?\\d{1,3}(\\.\\d{3})+$").matches(n)) n.replace(".", "").replace(",", ".") else n
                    }.toDoubleOrNull()
                    val style = if (ri == 0) " s=\"1\"" else ""
                    if (num != null && ri > 0) "<c r=\"$ref\"><v>$num</v></c>"
                    else "<c r=\"$ref\" t=\"inlineStr\"$style><is><t xml:space=\"preserve\">${esc(v)}</t></is></c>"
                }.joinToString("") + "</row>"
            }.joinToString("")
            return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>$rows</sheetData></worksheet>"
        }
        val files = linkedMapOf<String, String>()
        files["[Content_Types].xml"] = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
            "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
            "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
            "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
            "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>" +
            tables.indices.joinToString("") { "<Override PartName=\"/xl/worksheets/sheet${it + 1}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" } +
            "</Types>"
        files["_rels/.rels"] = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>"
        files["xl/workbook.xml"] = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>" +
            tables.indices.joinToString("") { "<sheet name=\"Tabelle${it + 1}\" sheetId=\"${it + 1}\" r:id=\"rId${it + 1}\"/>" } +
            "</sheets></workbook>"
        files["xl/_rels/workbook.xml.rels"] = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            tables.indices.joinToString("") { "<Relationship Id=\"rId${it + 1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet${it + 1}.xml\"/>" } +
            "<Relationship Id=\"rId${tables.size + 1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/></Relationships>"
        files["xl/styles.xml"] = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
            "<fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font><font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>" +
            "<fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill></fills>" +
            "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>" +
            "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
            "<cellXfs count=\"2\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/><xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/></cellXfs>" +
            "</styleSheet>"
        tables.forEachIndexed { i, t -> files["xl/worksheets/sheet${i + 1}.xml"] = sheet(t) }
        return File(shareDir(context), fileName(md, "xlsx")).also { it.writeBytes(zip(files)) }
    }

    private fun zip(files: Map<String, String>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            files.forEach { (name, content) ->
                z.putNextEntry(ZipEntry(name))
                z.write(content.toByteArray(Charsets.UTF_8))
                z.closeEntry()
            }
        }
        return bos.toByteArray()
    }

    // ---------- PDF ----------

    fun pdf(context: Context, md: String): File {
        val text = SpannableStringBuilder()
        fun appendRuns(s: String, sizePx: Int? = null, bold: Boolean = false) {
            val start = text.length
            for (r in runs(s)) {
                val st = text.length
                text.append(r.text)
                if (r.bold) text.setSpan(StyleSpan(Typeface.BOLD), st, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (r.italic) text.setSpan(StyleSpan(Typeface.ITALIC), st, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (r.code) text.setSpan(TypefaceSpan("monospace"), st, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (bold) text.setSpan(StyleSpan(Typeface.BOLD), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (sizePx != null) text.setSpan(AbsoluteSizeSpan(sizePx), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        for (b in parse(md)) {
            when (b) {
                is Heading -> { appendRuns(b.text, when (b.level) { 1 -> 20; 2 -> 17; else -> 14 }, bold = true); text.append("\n\n") }
                is Para -> { appendRuns(b.text); text.append("\n\n") }
                is Item -> { text.append("   ${b.marker} "); appendRuns(b.text); text.append("\n") }
                is Code -> {
                    val st = text.length
                    text.append(b.text).append("\n\n")
                    text.setSpan(TypefaceSpan("monospace"), st, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    text.setSpan(AbsoluteSizeSpan(9), st, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                is Rule -> text.append("────────────────────────────\n\n")
                is Table -> {
                    b.rows.forEachIndexed { ri, row ->
                        appendRuns(row.joinToString("   |   ") { plain(it) }, bold = ri == 0)
                        text.append("\n")
                    }
                    text.append("\n")
                }
            }
        }
        // A4 in Punkten (72 dpi), 2 cm Rand
        val pageW = 595; val pageH = 842; val margin = 56
        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply { textSize = 11f; color = android.graphics.Color.BLACK }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, pageW - 2 * margin)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(2f, 1f)
            .build()
        val doc = PdfDocument()
        val contentH = pageH - 2 * margin
        var line = 0
        var pageNo = 1
        while (line < layout.lineCount) {
            val top = layout.getLineTop(line)
            var last = line
            while (last + 1 < layout.lineCount && layout.getLineBottom(last + 1) - top <= contentH) last++
            val bottom = layout.getLineBottom(last)
            val page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create())
            val c = page.canvas
            c.save()
            c.translate(margin.toFloat(), (margin - top).toFloat())
            c.clipRect(0, top, pageW - 2 * margin, bottom)
            layout.draw(c)
            c.restore()
            val foot = TextPaint(paint).apply { textSize = 8f; color = android.graphics.Color.GRAY }
            c.drawText("KI-GenC · Seite $pageNo", margin.toFloat(), (pageH - 24).toFloat(), foot)
            doc.finishPage(page)
            line = last + 1
            pageNo++
        }
        if (pageNo == 1) doc.finishPage(doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, 1).create()))
        val file = File(shareDir(context), fileName(md, "pdf"))
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }
}
