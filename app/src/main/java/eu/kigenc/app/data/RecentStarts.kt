package eu.kigenc.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Ein zuletzt ausgeführter Auftrag der Startseite (zum schnellen Wiederverwenden). */
data class RecentStart(
    val categoryId: String,
    val options: Map<String, String>,
    val content: String,
    val extras: Map<String, String>,
    val customPrompt: String?,
    val time: Long = System.currentTimeMillis(),
    /** Angeheftet: bleibt oben stehen und wird weder verdrängt noch von „Leeren“ entfernt. */
    val pinned: Boolean = false,
    /** Eigener Name (z. B. für Favoriten); leer = erste Zeile des Inhalts wird angezeigt. */
    val name: String? = null,
)

/**
 * Speichert die letzten Aufträge der Startseite (höchstens [MAX]) lokal in eigenen SharedPreferences.
 * Eigenes, neues Speicherformat – bestehende Daten (Chats, Ideen, Ergebnisse, Einstellungen) bleiben unberührt.
 * Angeheftete Aufträge (Favoriten) stehen vorne und zählen nicht zur Obergrenze [MAX].
 */
class RecentStarts(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("recent_starts", Context.MODE_PRIVATE)

    fun list(): List<RecentStart> = runCatching {
        val arr = JSONArray(prefs.getString(KEY, "[]"))
        (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(::fromJson) }
    }.getOrElse { emptyList() }

    /** Fügt einen Auftrag vorne ein; gleiche Aufträge werden zusammengefasst. Angeheftete bleiben angeheftet. */
    fun add(entry: RecentStart): List<RecentStart> {
        val old = list()
        if (old.any { it.pinned && it.sameAs(entry) }) return old
        val (pinned, others) = old.partition { it.pinned }
        val keptName = entry.name ?: others.firstOrNull { it.sameAs(entry) }?.name
        val list = pinned + (listOf(entry.copy(name = keptName)) + others.filterNot { it.sameAs(entry) }).take(MAX)
        save(list)
        return list
    }

    /** Heftet einen Auftrag an bzw. löst ihn wieder (dann wird er wieder normal einsortiert). */
    fun togglePin(entry: RecentStart): List<RecentStart> {
        val old = list()
        val current = old.firstOrNull { it.sameAs(entry) } ?: return old
        val rest = old.filterNot { it.sameAs(entry) }
        val (pinned, others) = rest.partition { it.pinned }
        val list = if (current.pinned) {
            pinned + (listOf(current.copy(pinned = false)) + others).sortedByDescending { it.time }.take(MAX)
        } else {
            pinned + current.copy(pinned = true) + others
        }
        save(list)
        return list
    }

    /** Gibt einem Auftrag einen eigenen Namen; ein leerer Name stellt die automatische Anzeige wieder her. */
    fun rename(entry: RecentStart, name: String): List<RecentStart> {
        val clean = name.trim().take(MAX_NAME).ifBlank { null }
        val list = list().map { if (it.sameAs(entry)) it.copy(name = clean) else it }
        save(list)
        return list
    }

    fun remove(entry: RecentStart): List<RecentStart> {
        val list = list().filterNot { it.sameAs(entry) }
        save(list)
        return list
    }

    /** Entfernt alle nicht angehefteten Aufträge. */
    fun clear(): List<RecentStart> {
        val list = list().filter { it.pinned }
        save(list)
        return list
    }

    private fun save(list: List<RecentStart>) {
        val arr = JSONArray()
        list.forEach { arr.put(toJson(it)) }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private fun RecentStart.sameAs(o: RecentStart) =
        categoryId == o.categoryId && options == o.options && content.trim() == o.content.trim() &&
            extras == o.extras && customPrompt == o.customPrompt

    private fun toJson(r: RecentStart) = JSONObject().apply {
        put("cat", r.categoryId)
        put("options", JSONObject(r.options))
        put("content", r.content)
        put("extras", JSONObject(r.extras))
        r.customPrompt?.let { put("prompt", it) }
        put("time", r.time)
        if (r.pinned) put("pinned", true)
        r.name?.let { put("name", it) }
    }

    private fun fromJson(o: JSONObject): RecentStart? {
        val cat = o.optString("cat").ifBlank { return null }
        return RecentStart(
            categoryId = cat,
            options = o.optJSONObject("options").toMap(),
            content = o.optString("content"),
            extras = o.optJSONObject("extras").toMap(),
            customPrompt = if (o.has("prompt")) o.optString("prompt") else null,
            time = o.optLong("time", 0L),
            pinned = o.optBoolean("pinned", false),
            name = o.optString("name").trim().ifBlank { null },
        )
    }

    private fun JSONObject?.toMap(): Map<String, String> {
        val o = this ?: return emptyMap()
        return o.keys().asSequence().associateWith { o.optString(it) }
    }

    companion object {
        const val MAX = 6
        const val MAX_NAME = 60
        private const val KEY = "items"
    }
}
