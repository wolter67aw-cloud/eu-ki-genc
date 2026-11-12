package eu.kigenc.app.ui.start

import androidx.lifecycle.viewModelScope
import eu.kigenc.app.KiApp
import eu.kigenc.app.ai.ApiMessage
import eu.kigenc.app.ai.ImageGenerator
import eu.kigenc.app.ai.MistralClient
import eu.kigenc.app.data.ImageUtils
import eu.kigenc.app.data.RecentStart
import eu.kigenc.app.data.RecentStarts
import eu.kigenc.app.ui.tools.AiToolViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

class StartViewModel(app: KiApp) : AiToolViewModel(app) {
    /** Freitext: „Was möchtest du machen?“ */
    val wish = MutableStateFlow("")
    val defining = MutableStateFlow(false)
    val note = MutableStateFlow("")

    val category = MutableStateFlow<StartCategory?>(null)
    val options = MutableStateFlow<Map<String, String>>(emptyMap())
    val content = MutableStateFlow("")
    val extras = MutableStateFlow<Map<String, String>>(emptyMap())
    /** Vom Nutzer bearbeiteter Auftrag (null = automatisch aus den Feldern). */
    val customPrompt = MutableStateFlow<String?>(null)

    val image = MutableStateFlow<File?>(null)
    val sources = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val status = MutableStateFlow("")

    /** Zuletzt ausgeführte Aufträge (Schnellwahl auf der Startseite). */
    private val recentStore = RecentStarts(app)
    val recent = MutableStateFlow(recentStore.list())

    /** Übernimmt einen früheren Auftrag: Kategorie, Auswahl und Felder werden wiederhergestellt. */
    fun useRecent(r: RecentStart) {
        val cat = categoryById(r.categoryId) ?: return
        choose(cat)
        options.value = options.value + r.options.filter { (k, v) -> cat.groups.any { g -> g.key == k && v in g.options } }
        extras.value = r.extras.filter { (k, v) -> v.isNotBlank() && cat.extras.any { it.key == k } }
        content.value = r.content
        customPrompt.value = r.customPrompt
        note.value = ""
    }

    fun removeRecent(r: RecentStart) { recent.value = recentStore.remove(r) }

    fun clearRecent() { recent.value = recentStore.clear() }

    fun togglePinRecent(r: RecentStart) { recent.value = recentStore.togglePin(r) }

    fun renameRecent(r: RecentStart, name: String) { recent.value = recentStore.rename(r, name) }

    private fun rememberRun(cat: StartCategory) {
        if (cat.mode == StartMode.TOOL) return
        runCatching {
            recent.value = recentStore.add(
                RecentStart(cat.id, options.value, content.value, extras.value.filterValues { it.isNotBlank() }, customPrompt.value)
            )
        }
    }

    fun choose(cat: StartCategory?) {
        category.value = cat
        options.value = cat?.groups?.mapNotNull { g -> g.default?.let { g.key to it } }?.toMap().orEmpty()
        extras.value = emptyMap()
        customPrompt.value = null
        output.value = ""
        image.value = null
        sources.value = emptyList()
        if (cat != null && content.value.isBlank()) content.value = wish.value
    }

    fun setOption(key: String, value: String) { options.value = options.value + (key to value); customPrompt.value = null }
    fun setExtra(key: String, value: String) { extras.value = extras.value + (key to value); customPrompt.value = null }
    fun setContent(value: String) { content.value = value; customPrompt.value = null }

    fun reset() {
        choose(null)
        wish.value = ""
        content.value = ""
        note.value = ""
    }

    /** Baut aus Kategorie, Auswahl und Feldern einen präzisen Auftrag. */
    fun builtPrompt(): String {
        customPrompt.value?.let { return it }
        val cat = category.value ?: return wish.value
        val o = options.value
        val sb = StringBuilder()
        when (cat.id) {
            "write" -> sb.append("Schreibe: ${o["type"] ?: "Text"}")
            "translate" -> sb.append("Übersetze nach ${o["target"] ?: "Englisch"} (Stil: ${o["style"] ?: "natürlich"})")
            "analyze" -> sb.append("Analysiere (${o["what"] ?: "Text"}) mit Fokus: ${o["focus"] ?: "Zusammenfassung"}")
            "research" -> sb.append("Recherchiere (${o["depth"] ?: "gründlich"})")
            "plan" -> sb.append("Plane: ${o["type"] ?: "Projekt"}")
            "learn" -> sb.append("Lernhilfe als ${o["form"] ?: "Erklärung"}")
            "code" -> sb.append("${o["task"] ?: "Neu schreiben"} – Programmiersprache ${o["lang"] ?: "Python"}")
            "image" -> sb.append("Bild")
            else -> sb.append(cat.title)
        }
        sb.append("\n\n${cat.contentLabel.ifBlank { "Inhalt" }}: ${content.value.trim()}")
        cat.groups.forEach { g ->
            val v = o[g.key]
            if (v != null && g.key !in setOf("type", "target", "focus", "what", "form", "task", "depth")) sb.append("\n${g.label}: $v")
        }
        cat.extras.forEach { f -> extras.value[f.key]?.takeIf { it.isNotBlank() }?.let { sb.append("\n${f.label}: $it") } }
        return sb.toString()
    }

    /** Die KI liest den Wunsch und füllt Kategorie und Auswahl passend aus. */
    fun define() {
        val text = wish.value.trim()
        if (text.isBlank() || defining.value) return
        viewModelScope.launch {
            val s = settingsOrNull() ?: return@launch
            defining.value = true
            note.value = ""
            try {
                val catalog = START_CATEGORIES.joinToString("\n") { c ->
                    "- ${c.id} (${c.title})" + c.groups.joinToString("") { g -> "; ${g.key}: ${g.options.joinToString("|")}" } +
                        c.extras.joinToString("") { f -> "; Feld ${f.key}: ${f.label}" }
                }
                val prompt = "Ein Nutzer beschreibt, was er mit einer KI-App machen möchte. Ordne den Wunsch der passendsten Kategorie zu " +
                    "und fülle die Auswahl so genau wie möglich aus (nur Werte aus der Liste, sonst weglassen).\n\nKategorien:\n$catalog\n\n" +
                    "Antworte nur als JSON: {\"category\":\"id\",\"options\":{\"key\":\"Wert\"},\"content\":\"der eigentliche Inhalt bzw. das Thema, präzisiert\"," +
                    "\"fields\":{\"feldkey\":\"Wert\"},\"note\":\"ein Satz auf Deutsch: was du verstanden hast und was der Nutzer ggf. noch ergänzen sollte\"}\n\n" +
                    "Wunsch: $text"
                val json = app.mistral.complete(s.apiKey, MistralClient.FALLBACK_MODEL, listOf(ApiMessage("user", prompt)), json = true, temperature = 0.1)
                val o = JSONObject(json)
                val cat = categoryById(o.optString("category")) ?: categoryById("write")!!
                choose(cat)
                val opts = o.optJSONObject("options")
                cat.groups.forEach { g ->
                    val v = opts?.optString(g.key).orEmpty()
                    val match = g.options.firstOrNull { it.equals(v, ignoreCase = true) }
                    if (match != null) setOption(g.key, match)
                }
                content.value = o.optString("content").ifBlank { text }
                val fields = o.optJSONObject("fields")
                cat.extras.forEach { f -> fields?.optString(f.key)?.takeIf { it.isNotBlank() }?.let { setExtra(f.key, it) } }
                note.value = o.optString("note")
            } catch (e: Exception) {
                eu.kigenc.app.data.ErrorLog.log("Startseite", e)
                choose(categoryById("write"))
                note.value = "Die KI konnte den Wunsch nicht automatisch zuordnen – wähle die Auswahl bitte selbst."
            } finally {
                defining.value = false
            }
        }
    }

    fun run() {
        val cat = category.value ?: return
        if (content.value.isBlank() && customPrompt.value.isNullOrBlank()) {
            message.value = "Bitte zuerst „${cat.contentLabel}“ ausfüllen."
            return
        }
        val prompt = builtPrompt()
        rememberRun(cat)
        image.value = null
        sources.value = emptyList()
        when (cat.mode) {
            StartMode.TEXT, StartMode.CODE -> stream(
                "Du bist KI-GenC, ein präziser Assistent. ${cat.instruction} Halte dich genau an alle Vorgaben " +
                    "(Art, Ton, Länge, Sprache, Format, Zielgruppe). Antworte in Markdown.",
                ApiMessage("user", prompt),
                temperature = if (cat.id == "write") 0.7 else 0.3,
                model = { if (cat.mode == StartMode.CODE) it.codeModel else it.chatModel },
                saveAs = "Start: ${cat.title}",
            )
            StartMode.RESEARCH -> launchAi { s ->
                output.value = ""
                status.value = "Recherchiere im Web …"
                try {
                    val r = app.mistral.webSearch(s.apiKey, s.chatModel, prompt)
                    output.value = r.text
                    sources.value = r.sources
                    saveResult("Start: Recherche", content.value, r.text + r.sources.joinToString("") { "\n- [${it.first}](${it.second})" })
                } finally {
                    status.value = ""
                }
            }
            StartMode.IMAGE -> launchAi { s ->
                output.value = ""
                val (w, h) = when (options.value["format"]) {
                    "Hochformat" -> 768 to 1344
                    "Querformat" -> 1344 to 768
                    else -> 1024 to 1024
                }
                status.value = "Bild wird erzeugt … ca. 10–40 Sekunden"
                try {
                    val desc = "${content.value.trim()}, Stil: ${options.value["style"] ?: ""}, Stimmung: ${options.value["mood"] ?: ""}"
                    val files = ImageGenerator.generate(app, s, customPrompt.value ?: desc, w, h)
                    image.value = files.firstOrNull() ?: error("Es kam kein Bild zurück.")
                    output.value = "_Erzeugt mit ${ImageGenerator.lastProvider}_"
                } finally {
                    status.value = ""
                }
            }
            StartMode.TOOL -> Unit
        }
    }

    fun saveImage() {
        val f = image.value ?: return
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { ImageUtils.saveToGallery(app, f) }
            message.value = if (ok) "In der Galerie gespeichert (Bilder/KI-GenC)." else "Speichern fehlgeschlagen."
        }
    }
}
