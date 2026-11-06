package eu.kigenc.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

enum class IdeaKind(val label: String, val emoji: String) {
    NOTE("Notiz", "📝"),
    MOMENT("Momentaufnahme", "📸"),
    MEMO("Sprachmemo", "🎙️"),
    SKETCH("Skizze", "✏️"),
    CAPSULE("Zeitkapsel", "⏳"),
    CROSS("Ideen-Kreuzung", "🔀"),
}

/** Ein Eintrag der Ideen-Sammlung. Bild- und Tondateien liegen im Ordner „ideas“ (nur Dateiname gespeichert). */
data class Idea(
    val id: String = UUID.randomUUID().toString(),
    val kind: IdeaKind,
    val title: String,
    val text: String,
    val tags: List<String> = emptyList(),
    val mood: String = "",
    val imageFile: String? = null,
    val audioFile: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    /** Nur Zeitkapsel: Inhalt erst ab diesem Zeitpunkt sichtbar. */
    val openAt: Long? = null,
) {
    val locked: Boolean get() = openAt != null && openAt > System.currentTimeMillis()

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("kind", kind.name).put("title", title).put("text", text)
        .put("tags", JSONArray(tags)).put("mood", mood)
        .put("imageFile", imageFile ?: JSONObject.NULL).put("audioFile", audioFile ?: JSONObject.NULL)
        .put("createdAt", createdAt).put("openAt", openAt ?: JSONObject.NULL)

    /** Alles, was für die Suche zählt (verschlossene Zeitkapseln nur mit Titel). */
    fun searchable(): String = if (locked) "$title ${kind.label}"
    else "$title $text ${tags.joinToString(" ")} $mood ${kind.label}"

    companion object {
        fun fromJson(o: JSONObject): Idea {
            val tags = o.optJSONArray("tags")
            return Idea(
                id = o.getString("id"),
                kind = runCatching { IdeaKind.valueOf(o.optString("kind")) }.getOrDefault(IdeaKind.NOTE),
                title = o.optString("title"),
                text = o.optString("text"),
                tags = if (tags == null) emptyList() else List(tags.length()) { tags.getString(it) },
                mood = o.optString("mood"),
                imageFile = o.optString("imageFile").takeIf { it.isNotBlank() && it != "null" },
                audioFile = o.optString("audioFile").takeIf { it.isNotBlank() && it != "null" },
                createdAt = o.optLong("createdAt"),
                openAt = if (o.isNull("openAt")) null else o.optLong("openAt").takeIf { it > 0 },
            )
        }
    }
}

object IdeaStore {
    private val lock = Mutex()

    fun dir(context: Context) = File(context.filesDir, "ideas").apply { mkdirs() }
    private fun file(context: Context) = File(dir(context), "ideen.json")
    fun media(context: Context, name: String?) = name?.let { File(dir(context), it) }?.takeIf { it.exists() }

    suspend fun list(context: Context): List<Idea> = withContext(Dispatchers.IO) {
        runCatching {
            val arr = JSONArray(file(context).readText())
            List(arr.length()) { Idea.fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList()).sortedByDescending { it.createdAt }
    }

    private fun write(context: Context, list: List<Idea>) {
        file(context).writeText(JSONArray().apply { list.forEach { put(it.toJson()) } }.toString())
    }

    suspend fun upsert(context: Context, idea: Idea) = withContext(Dispatchers.IO) {
        lock.withLock {
            val all = list(context).filterNot { it.id == idea.id }
            write(context, listOf(idea) + all)
        }
    }

    suspend fun delete(context: Context, idea: Idea) = withContext(Dispatchers.IO) {
        lock.withLock {
            media(context, idea.imageFile)?.delete()
            media(context, idea.audioFile)?.delete()
            write(context, list(context).filterNot { it.id == idea.id })
        }
    }

    /** Neuer Dateiname im Ideen-Ordner. */
    fun newFile(context: Context, ext: String): File = File(dir(context), "${UUID.randomUUID()}.$ext")
}
