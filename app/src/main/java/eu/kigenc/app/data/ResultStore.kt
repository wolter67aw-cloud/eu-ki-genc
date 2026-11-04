package eu.kigenc.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Ein gespeichertes Ergebnis eines Werkzeugs (Recherche, Transkript, Analyse …). */
data class SavedResult(
    val id: String = UUID.randomUUID().toString(),
    val tool: String,
    val title: String,
    val text: String,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Speichert Werkzeug-Ergebnisse dauerhaft (max. 200), damit sie beim Verlassen nicht verloren gehen. */
object ResultStore {
    private const val MAX = 200

    private fun file(context: Context) = File(File(context.filesDir, "results").apply { mkdirs() }, "ergebnisse.json")

    suspend fun list(context: Context): List<SavedResult> = withContext(Dispatchers.IO) {
        runCatching {
            val arr = JSONArray(file(context).readText())
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                SavedResult(o.getString("id"), o.optString("tool"), o.optString("title"), o.optString("text"), o.optLong("createdAt"))
            }
        }.getOrDefault(emptyList())
    }

    private fun write(context: Context, list: List<SavedResult>) {
        val arr = JSONArray()
        list.take(MAX).forEach {
            arr.put(JSONObject().put("id", it.id).put("tool", it.tool).put("title", it.title).put("text", it.text).put("createdAt", it.createdAt))
        }
        file(context).writeText(arr.toString())
    }

    suspend fun add(context: Context, tool: String, title: String, text: String) = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext
        val t = title.lineSequence().firstOrNull().orEmpty().trim().let { if (it.length > 70) it.take(67) + "…" else it }.ifBlank { tool }
        write(context, listOf(SavedResult(tool = tool, title = t, text = text)) + list(context))
    }

    suspend fun delete(context: Context, id: String) = withContext(Dispatchers.IO) {
        write(context, list(context).filterNot { it.id == id })
    }
}
