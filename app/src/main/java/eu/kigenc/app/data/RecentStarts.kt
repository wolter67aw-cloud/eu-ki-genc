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
)

/**
 * Speichert die letzten Aufträge der Startseite (höchstens [MAX]) lokal in eigenen SharedPreferences.
 * Eigenes, neues Speicherformat – bestehende Daten (Chats, Ideen, Ergebnisse, Einstellungen) bleiben unberührt.
 */
class RecentStarts(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("recent_starts", Context.MODE_PRIVATE)

    fun list(): List<RecentStart> = runCatching {
        val arr = JSONArray(prefs.getString(KEY, "[]"))
        (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(::fromJson) }
    }.getOrElse { emptyList() }

    /** Fügt einen Auftrag vorne ein; gleiche Aufträge werden zusammengefasst. */
    fun add(entry: RecentStart): List<RecentStart> {
        val list = (listOf(entry) + list().filterNot { it.sameAs(entry) }).take(MAX)
        save(list)
        return list
    }

    fun remove(entry: RecentStart): List<RecentStart> {
        val list = list().filterNot { it.sameAs(entry) }
        save(list)
        return list
    }

    fun clear() = prefs.edit().remove(KEY).apply()

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
        )
    }

    private fun JSONObject?.toMap(): Map<String, String> {
        val o = this ?: return emptyMap()
        return o.keys().asSequence().associateWith { o.optString(it) }
    }

    companion object {
        const val MAX = 6
        private const val KEY = "items"
    }
}
