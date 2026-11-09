package eu.kigenc.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Kleiner, abhängigkeitsfreier Markdown-Parser für KI-Antworten (auch während des Streamings). */
object Markdown {

    sealed interface Block {
        data class Code(val language: String, val code: String) : Block
        data class Heading(val level: Int, val text: String) : Block
        data class ListItem(val marker: String, val text: String, val indent: Int) : Block
        data class Quote(val text: String) : Block
        data class Paragraph(val text: String) : Block
        data class Table(val rows: List<List<String>>) : Block
        data object Rule : Block
    }

    private val orderedRegex = Regex("^(\\s*)(\\d+)[.)]\\s+(.*)$")
    private val bulletRegex = Regex("^(\\s*)[-*+•]\\s+(.*)$")
    private val headingRegex = Regex("^(#{1,6})\\s+(.*)$")

    fun parse(src: String): List<Block> {
        val blocks = mutableListOf<Block>()
        val lines = src.replace("\r\n", "\n").split('\n')
        val paragraph = StringBuilder()
        fun flush() {
            if (paragraph.isNotBlank()) blocks += Block.Paragraph(paragraph.toString().trim())
            paragraph.clear()
        }

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()
            when {
                trimmed.startsWith("```") -> {
                    flush()
                    val lang = trimmed.removePrefix("```").trim()
                    val code = StringBuilder()
                    i++
                    while (i < lines.size && !lines[i].trim().startsWith("```")) {
                        code.append(lines[i]).append('\n')
                        i++
                    }
                    blocks += Block.Code(lang, code.toString().trimEnd('\n'))
                }
                trimmed.isEmpty() -> flush()
                headingRegex.matches(trimmed) -> {
                    flush()
                    val m = headingRegex.find(trimmed)!!
                    blocks += Block.Heading(m.groupValues[1].length, m.groupValues[2])
                }
                trimmed == "---" || trimmed == "***" || trimmed == "___" -> {
                    flush()
                    blocks += Block.Rule
                }
                trimmed.startsWith("|") && trimmed.count { it == '|' } >= 2 -> {
                    flush()
                    val rows = mutableListOf<List<String>>()
                    while (i < lines.size && lines[i].trim().startsWith("|")) {
                        val cells = lines[i].trim().trim('|').split('|').map { it.trim() }
                        if (!cells.all { it.matches(Regex("^:?-{2,}:?$")) }) rows += cells
                        i++
                    }
                    i--
                    blocks += Block.Table(rows)
                }
                trimmed.startsWith(">") -> {
                    flush()
                    blocks += Block.Quote(trimmed.removePrefix(">").trim())
                }
                orderedRegex.matches(line) -> {
                    flush()
                    val m = orderedRegex.find(line)!!
                    blocks += Block.ListItem("${m.groupValues[2]}.", m.groupValues[3], m.groupValues[1].length / 2)
                }
                bulletRegex.matches(line) -> {
                    flush()
                    val m = bulletRegex.find(line)!!
                    blocks += Block.ListItem("•", m.groupValues[2], m.groupValues[1].length / 2)
                }
                else -> {
                    if (paragraph.isNotEmpty()) paragraph.append('\n')
                    paragraph.append(line)
                }
            }
            i++
        }
        flush()
        return blocks
    }

    /** Entfernt Markdown-Zeichen, z. B. fürs Vorlesen. */
    fun toPlainText(src: String): String = parse(src).joinToString("\n") { b ->
        when (b) {
            is Block.Code -> "(Codebeispiel)"
            is Block.Heading -> stripInline(b.text)
            is Block.ListItem -> stripInline(b.text)
            is Block.Quote -> stripInline(b.text)
            is Block.Paragraph -> stripInline(b.text)
            is Block.Table -> b.rows.joinToString(". ") { it.joinToString(", ") { c -> stripInline(c) } }
            Block.Rule -> ""
        }
    }

    private fun stripInline(s: String) = s
        .replace(Regex("\\[([^\\]]+)]\\([^)]*\\)"), "$1")
        .replace(Regex("[*_`~#]"), "")

    fun inlineText(text: String, codeBackground: Color, linkColor: Color): AnnotatedString = buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '`') {
                val end = text.indexOf('`', i + 1)
                if (end > i) {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground, fontSize = 14.sp)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                    continue
                }
            }
            if (text.startsWith("**", i) || text.startsWith("__", i)) {
                val marker = text.substring(i, i + 2)
                val end = text.indexOf(marker, i + 2)
                if (end > i + 2) {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                        append(inlineText(text.substring(i + 2, end), codeBackground, linkColor))
                    }
                    i = end + 2
                    continue
                }
            }
            if (text.startsWith("~~", i)) {
                val end = text.indexOf("~~", i + 2)
                if (end > i + 2) {
                    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                    continue
                }
            }
            if ((c == '*' || c == '_') && i + 1 < text.length && !text[i + 1].isWhitespace() &&
                (c == '*' || i == 0 || !text[i - 1].isLetterOrDigit())
            ) {
                val end = text.indexOf(c, i + 1)
                if (end > i + 1 && !text[end - 1].isWhitespace()) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(inlineText(text.substring(i + 1, end), codeBackground, linkColor))
                    }
                    i = end + 1
                    continue
                }
            }
            if (c == '[') {
                val mid = text.indexOf("](", i)
                val end = if (mid > 0) text.indexOf(')', mid + 2) else -1
                if (mid > i && end > mid) {
                    val label = text.substring(i + 1, mid)
                    val url = text.substring(mid + 2, end)
                    if (url.startsWith("http")) {
                        withLink(
                            LinkAnnotation.Url(
                                url,
                                TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
                            )
                        ) { append(label) }
                        i = end + 1
                        continue
                    }
                }
            }
            append(c)
            i++
        }
    }
}

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    onCopyCode: (String) -> Unit = {},
) {
    val blocks = Markdown.parse(markdown)
    val codeBg = MaterialTheme.colorScheme.surfaceContainerHighest
    val link = MaterialTheme.colorScheme.primary
    val body = MaterialTheme.typography.bodyLarge.copy(color = color)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (block in blocks) {
            when (block) {
                is Markdown.Block.Paragraph ->
                    Text(Markdown.inlineText(block.text, codeBg, link), style = body)

                is Markdown.Block.Heading -> Text(
                    Markdown.inlineText(block.text, codeBg, link),
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge
                        2 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    }.copy(color = color, fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(top = 4.dp)
                )

                is Markdown.Block.ListItem -> Row(Modifier.padding(start = (block.indent * 16).dp)) {
                    Text(
                        block.marker,
                        style = body,
                        modifier = Modifier.width(if (block.marker == "•") 16.dp else 26.dp)
                    )
                    Text(Markdown.inlineText(block.text, codeBg, link), style = body)
                }

                is Markdown.Block.Quote -> Row(Modifier.height(IntrinsicSize.Min)) {
                    Box(
                        Modifier
                            .width(3.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(2.dp))
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        Markdown.inlineText(block.text, codeBg, link),
                        style = body.copy(fontStyle = FontStyle.Italic)
                    )
                }

                is Markdown.Block.Code -> CodeBlock(block.language, block.code, onCopyCode)

                is Markdown.Block.Table -> Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Column(
                        Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(10.dp)
                    ) {
                        val widths = IntArray(block.rows.maxOfOrNull { it.size } ?: 0)
                        block.rows.forEach { r -> r.forEachIndexed { idx, cell -> widths[idx] = maxOf(widths[idx], cell.length) } }
                        block.rows.forEachIndexed { rowIdx, r ->
                            Text(
                                r.mapIndexed { idx, cell -> cell.padEnd(widths[idx]) }.joinToString("  │  "),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                fontWeight = if (rowIdx == 0) FontWeight.Bold else FontWeight.Normal,
                                color = color,
                                softWrap = false,
                            )
                        }
                    }
                }

                Markdown.Block.Rule -> HorizontalDivider()
            }
        }
    }
}

@Composable
fun CodeBlock(language: String, code: String, onCopy: (String) -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        // Code immer im dunklen Editor-Stil – so bleibt die Syntax-Färbung in jedem Design gut lesbar
        color = Color(0xFF111622),
        contentColor = Color(0xFFE6EAF5),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    language.ifBlank { "Code" },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { onCopy(code) }) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = "Code kopieren", modifier = Modifier.size(18.dp))
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            val highlighted = remember(code, language) { SyntaxHighlighter.highlight(code, language) }
            Text(
                highlighted,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                softWrap = false,
                color = Color(0xFFE6EAF5),
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            )
        }
    }
}


/** Einfache Syntax-Hervorhebung für Codeblöcke (Schlüsselwörter, Texte, Kommentare, Zahlen). */
object SyntaxHighlighter {
    private val keywordColor = Color(0xFFC792EA)
    private val stringColor = Color(0xFFC3E88D)
    private val commentColor = Color(0xFF7F8AA8)
    private val numberColor = Color(0xFFF78C6C)
    private val typeColor = Color(0xFF82AAFF)

    private val keywords = setOf(
        // Kotlin/Java/C#/Swift
        "fun", "val", "var", "class", "object", "interface", "data", "sealed", "enum", "when", "if", "else", "for", "while",
        "do", "return", "break", "continue", "import", "package", "private", "public", "protected", "internal", "override",
        "open", "abstract", "suspend", "inline", "null", "true", "false", "this", "super", "new", "static", "final", "void",
        "try", "catch", "finally", "throw", "throws", "is", "in", "as", "let", "const", "struct", "func", "switch", "case",
        "default", "extends", "implements", "async", "await", "yield", "lambda", "def", "pass", "None", "True", "False",
        "and", "or", "not", "from", "with", "elif", "except", "raise", "global", "nonlocal", "function", "typeof",
        "instanceof", "export", "using", "namespace", "public", "int", "long", "double", "float", "bool", "boolean", "char",
        "string", "String", "echo", "then", "fi", "esac", "done", "SELECT", "FROM", "WHERE", "INSERT", "UPDATE", "DELETE",
        "JOIN", "ON", "GROUP", "BY", "ORDER", "AS", "AND", "OR", "NOT", "INTO", "VALUES", "CREATE", "TABLE", "select",
        "from", "where", "join", "group", "order", "by",
    )

    private val hashComments = setOf("python", "py", "bash", "sh", "shell", "zsh", "ruby", "rb", "yaml", "yml", "r", "toml", "dockerfile", "perl")
    private val sqlComments = setOf("sql")

    fun highlight(code: String, language: String): AnnotatedString {
        if (code.length > 20_000) return AnnotatedString(code)
        val lang = language.lowercase().trim()
        val hash = lang in hashComments
        val dashDash = lang in sqlComments
        return buildAnnotatedString {
            var i = 0
            val n = code.length
            while (i < n) {
                val c = code[i]
                // Kommentare
                if ((!hash && c == '/' && i + 1 < n && code[i + 1] == '/') || (hash && c == '#') ||
                    (dashDash && c == '-' && i + 1 < n && code[i + 1] == '-')
                ) {
                    val end = code.indexOf('\n', i).let { if (it < 0) n else it }
                    withStyle(SpanStyle(color = commentColor, fontStyle = FontStyle.Italic)) { append(code, i, end) }
                    i = end
                    continue
                }
                if (!hash && c == '/' && i + 1 < n && code[i + 1] == '*') {
                    val end = code.indexOf("*/", i + 2).let { if (it < 0) n else it + 2 }
                    withStyle(SpanStyle(color = commentColor, fontStyle = FontStyle.Italic)) { append(code, i, end) }
                    i = end
                    continue
                }
                // Zeichenketten
                if (c == '"' || c == '\'' || c == '`') {
                    var j = i + 1
                    while (j < n && code[j] != c && code[j] != '\n') {
                        if (code[j] == '\\') j++
                        j++
                    }
                    val end = minOf(n, j + 1)
                    withStyle(SpanStyle(color = stringColor)) { append(code, i, end) }
                    i = end
                    continue
                }
                // Zahlen
                if (c.isDigit() && (i == 0 || !code[i - 1].isLetterOrDigit())) {
                    var j = i
                    while (j < n && (code[j].isLetterOrDigit() || code[j] == '.' || code[j] == '_')) j++
                    withStyle(SpanStyle(color = numberColor)) { append(code, i, j) }
                    i = j
                    continue
                }
                // Wörter
                if (c.isLetter() || c == '_') {
                    var j = i
                    while (j < n && (code[j].isLetterOrDigit() || code[j] == '_')) j++
                    val word = code.substring(i, j)
                    when {
                        word in keywords -> withStyle(SpanStyle(color = keywordColor, fontWeight = FontWeight.SemiBold)) { append(word) }
                        word.first().isUpperCase() && word.length > 1 -> withStyle(SpanStyle(color = typeColor)) { append(word) }
                        else -> append(word)
                    }
                    i = j
                    continue
                }
                append(c)
                i++
            }
        }
    }
}
