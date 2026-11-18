package eu.kigenc.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class Role { USER, ASSISTANT }

enum class ChatMode { CHAT, CODE }

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: Role,
    val text: String,
    val imagePath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val isError: Boolean = false,
    /** Mehrere Bilder in einer Nachricht (optional; [imagePath] bleibt das erste – für ältere Versionen lesbar). */
    val imagePaths: List<String> = emptyList(),
) {
    /** Alle Bilder dieser Nachricht (neues Feld oder – bei älteren Chats – das eine Bild). */
    val allImages: List<String> get() = imagePaths.ifEmpty { listOfNotNull(imagePath) }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("role", role.name)
        .put("text", text)
        .put("imagePath", imagePath ?: JSONObject.NULL)
        .put("createdAt", createdAt)
        .put("isError", isError)
        .apply { if (imagePaths.size > 1) put("imagePaths", JSONArray().apply { imagePaths.forEach { put(it) } }) }

    companion object {
        fun fromJson(o: JSONObject) = ChatMessage(
            id = o.optString("id", UUID.randomUUID().toString()),
            role = runCatching { Role.valueOf(o.getString("role")) }.getOrDefault(Role.USER),
            text = o.optString("text", ""),
            imagePath = if (o.isNull("imagePath")) null else o.optString("imagePath"),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            isError = o.optBoolean("isError", false),
            imagePaths = o.optJSONArray("imagePaths")?.let { arr -> List(arr.length()) { arr.optString(it) }.filter { it.isNotBlank() } }.orEmpty(),
        )
    }
}

data class Conversation(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val mode: ChatMode,
    val model: String,
    val messages: List<ChatMessage> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis(),
    /** Im Verlauf angeheftet (oben in der Gruppe „Angeheftet“). Optionales Feld, ältere Dateien = false. */
    val pinned: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("mode", mode.name)
        .put("model", model)
        .put("updatedAt", updatedAt)
        .put("pinned", pinned)
        .put("messages", JSONArray().apply { messages.forEach { put(it.toJson()) } })

    companion object {
        fun fromJson(o: JSONObject): Conversation {
            val arr = o.optJSONArray("messages") ?: JSONArray()
            return Conversation(
                id = o.getString("id"),
                title = o.optString("title", "Unterhaltung"),
                mode = runCatching { ChatMode.valueOf(o.getString("mode")) }.getOrDefault(ChatMode.CHAT),
                model = o.optString("model", ""),
                updatedAt = o.optLong("updatedAt", 0L),
                pinned = o.optBoolean("pinned", false),
                messages = List(arr.length()) { ChatMessage.fromJson(arr.getJSONObject(it)) },
            )
        }
    }
}

/** Ein Modell, wie es die Mistral-API unter /v1/models meldet. */
data class ModelInfo(
    val id: String,
    val vision: Boolean,
    val description: String = "",
)
