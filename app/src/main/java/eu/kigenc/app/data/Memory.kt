package eu.kigenc.app.data

import android.content.Context
import eu.kigenc.app.ai.ApiMessage
import eu.kigenc.app.ai.MistralClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Ein gemerkter Fakt über den Nutzer. */
data class MemoryFact(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("text", text).put("createdAt", createdAt)

    companion object {
        fun fromJson(o: JSONObject) = MemoryFact(
            id = o.optString("id", UUID.randomUUID().toString()),
            text = o.optString("text", ""),
            createdAt = o.optLong("createdAt", 0L),
        )
    }
}

/**
 * Gedächtnis wie bei Claude: Fakten über den Nutzer (Name, Beruf, Vorlieben …), die in jedem Chat
 * mitgegeben werden. Liegt nur auf dem Gerät (filesDir/memory.json) und ist unter Optionen einsehbar.
 */
class MemoryStore(context: Context) {
    private val file = File(context.filesDir, "memory.json")
    private val _facts = MutableStateFlow(load())
    val facts: StateFlow<List<MemoryFact>> = _facts.asStateFlow()

    private fun load(): List<MemoryFact> = runCatching {
        if (!file.exists()) return emptyList()
        val arr = JSONArray(file.readText())
        List(arr.length()) { MemoryFact.fromJson(arr.getJSONObject(it)) }.filter { it.text.isNotBlank() }
    }.getOrDefault(emptyList())

    /** Systemtext für die KI (leer, wenn nichts gemerkt). */
    fun systemText(): String {
        val list = _facts.value
        if (list.isEmpty()) return ""
        return "\n\nGedächtnis – was du über den Nutzer weißt (nutze es unaufdringlich, wenn es passt; " +
            "erwähne es nicht ständig):\n" + list.joinToString("\n") { "- ${it.text}" }
    }

    suspend fun add(text: String): Boolean {
        val t = text.trim().trimEnd('.').take(MAX_FACT_LENGTH)
        if (t.length < 3) return false
        val current = _facts.value
        if (current.any { it.text.equals(t, ignoreCase = true) }) return false
        write((current + MemoryFact(text = t)).takeLast(MAX_FACTS))
        return true
    }

    suspend fun update(id: String, text: String) {
        val t = text.trim().take(MAX_FACT_LENGTH)
        if (t.isBlank()) return delete(id)
        write(_facts.value.map { if (it.id == id) it.copy(text = t) else it })
    }

    suspend fun delete(id: String) = write(_facts.value.filterNot { it.id == id })

    suspend fun clear() = write(emptyList())

    private suspend fun write(list: List<MemoryFact>) {
        _facts.value = list
        withContext(Dispatchers.IO) {
            synchronized(this@MemoryStore) {
                val tmp = File(file.parentFile, "memory.tmp")
                tmp.writeText(JSONArray().apply { list.forEach { put(it.toJson()) } }.toString())
                if (!tmp.renameTo(file)) {
                    file.writeText(tmp.readText())
                    tmp.delete()
                }
            }
        }
    }

    /**
     * Erkennt „Merk dir …“ / „Merke dir …“ / „Bitte merken: …“ am Anfang einer Nachricht und liefert den Fakt.
     */
    fun explicitFact(message: String): String? {
        val m = EXPLICIT.find(message.trim()) ?: return null
        return m.groupValues[1].trim().takeIf { it.length >= 3 }
    }

    /**
     * Lässt ein sparsames Modell aus der letzten Nachricht dauerhafte Fakten über den Nutzer herausziehen
     * und speichert neue. Fehler werden still ignoriert (Gedächtnis ist eine Zusatzfunktion).
     */
    suspend fun learnFrom(mistral: MistralClient, apiKey: String, userMessage: String, explicit: String? = null): Int {
        if (explicit == null && userMessage.length < 15) return 0
        val known = _facts.value.joinToString("\n") { "- ${it.text}" }.ifBlank { "(noch nichts)" }
        val prompt = if (explicit != null) {
            "Der Nutzer einer KI-App möchte ausdrücklich, dass du dir Folgendes über ihn merkst:\n\"\"\"${explicit.take(1000)}\"\"\"\n" +
                "Bekannte Fakten:\n$known\n\nFormuliere es als 1–2 kurze Fakten in dritter Person auf Deutsch " +
                "(z. B. „Heißt Andy“, „Mag keine Emojis in Antworten“). Antwort als JSON: {\"facts\": [\"…\"]}"
        } else "Du pflegst ein Gedächtnis über den Nutzer einer KI-App. Bekannte Fakten:\n$known\n\n" +
            "Neue Nachricht des Nutzers:\n\"\"\"${userMessage.take(3000)}\"\"\"\n\n" +
            "Gib NUR dauerhafte, persönliche Fakten zurück, die der Nutzer über SICH SELBST klar gesagt hat und die " +
            "noch nicht bekannt sind (z. B. Name, Wohnort, Beruf, Familie, Haustiere, Vorlieben, Ziele, wie er Antworten " +
            "haben möchte). KEINE Fragen, Aufgaben, Vermutungen, vorübergehenden Dinge, Gesundheits-, Finanz- oder andere " +
            "sensible Angaben. Kurz, in dritter Person auf Deutsch (z. B. „Heißt Andy“, „Hat einen Hund namens Bello“). " +
            "Antwort als JSON: {\"facts\": [\"…\"]} – meist ist die Liste leer."
        val raw = runCatching {
            mistral.complete(apiKey, MistralClient.FALLBACK_MODEL, listOf(ApiMessage("user", prompt)), json = true, temperature = 0.0)
        }.getOrNull()
        val arr = raw?.let { runCatching { JSONObject(it).optJSONArray("facts") }.getOrNull() }
        if (arr == null) return if (explicit != null && add(explicit)) 1 else 0
        var added = 0
        for (i in 0 until minOf(arr.length(), 3)) {
            if (add(arr.optString(i))) added++
        }
        // Ausdrücklicher Wunsch, aber nichts Neues erkannt → wörtlich merken (sofern nicht schon bekannt)
        if (explicit != null && added == 0 && arr.length() == 0 && add(explicit)) added++
        return added
    }

    companion object {
        const val MAX_FACTS = 60
        const val MAX_FACT_LENGTH = 200
        private val EXPLICIT = Regex(
            "^(?:bitte\\s+)?(?:merk(?:e)?\\s+(?:dir|es\\s+dir)|bitte\\s+merken|notier(?:e)?\\s+dir)\\s*[:,]?\\s*(?:dass\\s+)?(.+)$",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
    }
}
