@file:OptIn(ExperimentalMaterial3Api::class)

package eu.kigenc.app.ui.components

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.File

/**
 * Live-Vorschau wie Claudes Artefakte: HTML-Seiten, Mini-Apps, SVG-Grafiken und Mermaid-Diagramme
 * aus einer KI-Antwort direkt in der App ansehen und ausprobieren.
 */
object LivePreview {
    data class Artifact(val title: String, val html: String)

    private val BLOCK = Regex("```([\\w+#.-]*)[^\\n]*\\n([\\s\\S]*?)```")

    /** Sucht in der Antwort nach etwas Darstellbarem (null = nichts). */
    fun find(md: String): Artifact? {
        val blocks = BLOCK.findAll(md).map { it.groupValues[1].lowercase() to it.groupValues[2] }.toList()
        if (blocks.isEmpty()) return null
        val css = blocks.filter { it.first == "css" }.joinToString("\n") { it.second }
        val js = blocks.filter { it.first in setOf("js", "javascript") }.joinToString("\n") { it.second }
        blocks.firstOrNull { (lang, code) -> lang in setOf("html", "htm", "xhtml") || code.trimStart().startsWith("<!DOCTYPE", true) || code.contains("<html", true) }
            ?.let { (_, code) -> return Artifact("Webseite", inject(code, css, js)) }
        blocks.firstOrNull { (lang, code) -> lang == "svg" || (lang in setOf("xml", "") && code.trimStart().startsWith("<svg")) }
            ?.let { (_, code) -> return Artifact("Grafik", page("<div style=\"display:flex;justify-content:center\">$code</div>", "svg{max-width:100%;height:auto}")) }
        blocks.firstOrNull { it.first == "mermaid" }
            ?.let { (_, code) -> return Artifact("Diagramm", mermaid(code)) }
        return null
    }

    private fun page(body: String, style: String = "", head: String = "") =
        "<!DOCTYPE html><html lang=\"de\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
            "<style>body{font-family:sans-serif;margin:12px}$style</style>$head</head><body>$body</body></html>"

    /** Fügt [text] vor (oder nach) dem ersten Treffer von [re] ein; ohne Treffer bleibt der Text unverändert. */
    private fun insertAt(h: String, re: Regex, text: String, after: Boolean = false): String {
        val m = re.find(h) ?: return h
        val at = if (after) m.range.last + 1 else m.range.first
        return h.substring(0, at) + text + h.substring(at)
    }

    private fun inject(html: String, css: String, js: String): String {
        var h = html
        if (!h.contains("<html", true)) h = page(h)
        if (!h.contains("name=\"viewport\"", true)) {
            h = insertAt(h, Regex("<head[^>]*>", RegexOption.IGNORE_CASE), "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">", after = true)
        }
        if (css.isNotBlank()) h = insertAt(h, Regex("</head>", RegexOption.IGNORE_CASE), "<style>\n$css\n</style>")
        if (js.isNotBlank()) {
            val tag = "<script>\n$js\n</script>"
            h = if (h.contains("</body>", true)) insertAt(h, Regex("</body>", RegexOption.IGNORE_CASE), tag) else h + tag
        }
        return h
    }

    private fun mermaid(code: String): String {
        val esc = code.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        return page(
            "<pre class=\"mermaid\">$esc</pre><p id=\"err\" style=\"color:#b00\"></p>",
            head = "<script src=\"https://cdn.jsdelivr.net/npm/mermaid@11.4.1/dist/mermaid.min.js\"></script>" +
                "<script>window.addEventListener('load',function(){try{mermaid.initialize({startOnLoad:false,theme:'default'});" +
                "mermaid.run().catch(function(e){document.getElementById('err').textContent='Diagramm fehlerhaft: '+e});}" +
                "catch(e){document.getElementById('err').textContent='Diagramm-Bibliothek nicht geladen (Internet?)';}});</script>"
        )
    }
}

/** Knopf „Vorschau“ – erscheint nur, wenn die Antwort etwas Darstellbares enthält. */
@Composable
fun LivePreviewButton(text: String, iconSize: Int = 20) {
    val artifact = remember(text) { LivePreview.find(text) } ?: return
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(Icons.Outlined.PlayCircle, "Live-Vorschau (${artifact.title})", Modifier.size(iconSize.dp))
    }
    if (open) LivePreviewDialog(artifact) { open = false }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LivePreviewDialog(artifact: LivePreview.Artifact, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var reload by remember { mutableIntStateOf(0) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Vorschau: ${artifact.title}") },
                    navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Schließen") } },
                    actions = {
                        IconButton(onClick = { reload++ }) { Icon(Icons.Outlined.Refresh, "Neu laden") }
                        IconButton(onClick = {
                            val f = File(File(context.cacheDir, "share").apply { mkdirs() }, "vorschau.html").apply { writeText(artifact.html) }
                            context.shareFile(f, "text/html", "HTML-Datei teilen")
                        }) { Icon(Icons.Outlined.Share, "Als HTML-Datei teilen") }
                    }
                )
            }
        ) { inner ->
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        // Abgeschottet: kein Zugriff auf Dateien oder die App, nur die Seite selbst
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                // Links öffnen im Browser statt in der Vorschau
                                if (request.isForMainFrame && request.url.scheme in setOf("http", "https", "mailto", "tel")) {
                                    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(request.url.toString())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                                    return true
                                }
                                return request.url.scheme !in setOf("about", "data", "blob")
                            }
                        }
                    }
                },
                update = { wv ->
                    wv.tag.let { if (it != reload) { wv.tag = reload; wv.loadDataWithBaseURL("https://vorschau.kigenc.invalid/", artifact.html, "text/html", "utf-8", null) } }
                },
                onRelease = { it.destroy() },
                modifier = Modifier.padding(inner).fillMaxSize()
            )
        }
    }
}
