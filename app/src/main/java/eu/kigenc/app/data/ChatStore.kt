package eu.kigenc.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/** Speichert Unterhaltungen lokal als JSON-Dateien im privaten App-Speicher. */
class ChatStore(private val context: Context) {

    private fun dir(mode: ChatMode): File =
        File(context.filesDir, "chats/${mode.name.lowercase()}").apply { mkdirs() }

    suspend fun list(mode: ChatMode): List<Conversation> = withContext(Dispatchers.IO) {
        dir(mode).listFiles { f -> f.extension == "json" }
            .orEmpty()
            .mapNotNull { f -> runCatching { Conversation.fromJson(JSONObject(f.readText())) }.getOrNull() }
            .sortedByDescending { it.updatedAt }
    }

    suspend fun save(conversation: Conversation) = withContext(Dispatchers.IO) {
        val target = File(dir(conversation.mode), "${conversation.id}.json")
        val tmp = File(target.parentFile, "${conversation.id}.tmp")
        tmp.writeText(conversation.toJson().toString())
        if (!tmp.renameTo(target)) {
            target.writeText(tmp.readText())
            tmp.delete()
        }
    }

    suspend fun delete(conversation: Conversation) = withContext(Dispatchers.IO) {
        conversation.messages.mapNotNull { it.imagePath }.forEach { File(it).delete() }
        File(dir(conversation.mode), "${conversation.id}.json").delete()
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        File(context.filesDir, "chats").deleteRecursively()
        File(context.filesDir, "images").deleteRecursively()
        File(context.filesDir, "generated").deleteRecursively()
    }
}
