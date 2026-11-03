package eu.kigenc.app.ai

import eu.kigenc.app.data.ModelInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

class MistralException(val code: Int, message: String, val retryAfterSec: Int? = null) : IOException(message)

/** Eine Nachricht im Format der Mistral-Chat-API (Text plus optional ein oder mehrere Bilder). */
data class ApiMessage(
    val role: String,
    val text: String,
    val imageDataUrl: String? = null,
    val extraImages: List<String> = emptyList(),
) {
    fun toJson(): JSONObject {
        val o = JSONObject().put("role", role)
        val images = listOfNotNull(imageDataUrl) + extraImages
        if (images.isEmpty()) {
            o.put("content", text)
        } else {
            val parts = JSONArray()
            if (text.isNotBlank()) parts.put(JSONObject().put("type", "text").put("text", text))
            images.forEach { parts.put(JSONObject().put("type", "image_url").put("image_url", it)) }
            o.put("content", parts)
        }
        return o
    }
}

/** Ergebnis einer Web-Recherche: Antworttext und verwendete Quellen (Titel, URL). */
data class SearchResult(val text: String, val sources: List<Pair<String, String>>)

/**
 * Schlanker Client für die Mistral-AI-API (Server in der EU).
 * Nutzt nur Bordmittel (HttpURLConnection + org.json) – keine zusätzlichen Abhängigkeiten.
 */
class MistralClient {

    companion object {
        private const val BASE = "https://api.mistral.ai/v1"
        private const val MAX_ATTEMPTS = 3
        const val FALLBACK_MODEL = "mistral-small-latest"
        const val TRANSCRIBE_MODEL = "voxtral-mini-latest"
    }

    private fun open(path: String, method: String, apiKey: String, readTimeoutMs: Int = 120_000): HttpURLConnection {
        val c = URL(BASE + path).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 20_000
        c.readTimeout = readTimeoutMs
        c.setRequestProperty("Authorization", "Bearer $apiKey")
        c.setRequestProperty("Accept", "application/json")
        c.setRequestProperty("User-Agent", "EU-KI-GenC-Android")
        return c
    }

    private fun HttpURLConnection.sendJson(body: JSONObject) {
        doOutput = true
        setRequestProperty("Content-Type", "application/json; charset=utf-8")
        outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
    }

    private fun HttpURLConnection.ensureOk() {
        val code = responseCode
        if (code in 200..299) return
        val raw = runCatching { errorStream?.bufferedReader()?.use { it.readText() } }.getOrNull().orEmpty()
        val detail = runCatching {
            val o = JSONObject(raw)
            o.optString("message").ifBlank { o.optJSONObject("error")?.optString("message").orEmpty() }
                .ifBlank { o.optString("detail") }
        }.getOrNull().orEmpty()
        val msg = when (code) {
            401 -> "API-Schlüssel ungültig oder abgelaufen (401). Bitte in den Einstellungen prüfen."
            403 -> "Zugriff verweigert (403). Ist das Modell für dein Konto freigeschaltet?"
            404 -> "Nicht gefunden (404). ${detail.ifBlank { "Modell oder Ressource existiert nicht." }}"
            422 -> "Ungültige Anfrage (422). $detail"
            429 -> "Mistral-Limit erreicht (429)${if (detail.isNotBlank()) ": $detail" else "."} " +
                "Im kostenlosen Experiment-Tarif sind nur wenige Anfragen pro Sekunde/Minute erlaubt – " +
                "kurz warten und erneut senden. Deine Limits siehst du auf admin.mistral.ai unter „Limits“."
            in 500..599 -> "Mistral-Server nicht erreichbar ($code). Bitte später erneut versuchen."
            else -> "Fehler $code. $detail"
        }
        val retryAfter = getHeaderField("Retry-After")?.trim()?.toDoubleOrNull()?.toInt()
        throw MistralException(code, msg.trim(), retryAfter)
    }

    private fun HttpURLConnection.readJson(): JSONObject {
        ensureOk()
        return JSONObject(inputStream.bufferedReader().use { it.readText() })
    }

    /**
     * Streamt die Antwort Stück für Stück (Server-Sent Events).
     * Bei Limit-Fehlern (429) wird automatisch kurz gewartet und erneut versucht;
     * hilft das nicht, wird auf das sparsamere Modell [FALLBACK_MODEL] ausgewichen.
     */
    fun streamChat(
        apiKey: String,
        model: String,
        messages: List<ApiMessage>,
        temperature: Double = 0.7,
    ): Flow<String> = flow {
        val candidates = if (model == FALLBACK_MODEL) listOf(model) else listOf(model, FALLBACK_MODEL)
        var lastError: MistralException? = null
        for ((index, m) in candidates.withIndex()) {
            val body = JSONObject()
                .put("model", m)
                .put("stream", true)
                .put("temperature", temperature)
                .put("messages", JSONArray().apply { messages.forEach { put(it.toJson()) } })
            for (attempt in 0 until MAX_ATTEMPTS) {
                val c = open("/chat/completions", "POST", apiKey)
                try {
                    c.setRequestProperty("Accept", "text/event-stream")
                    c.sendJson(body)
                    c.ensureOk()
                    if (index > 0) emit("_Hinweis: $model war gerade ausgelastet, Antwort von $m._\n\n")
                    readStream(c)
                    return@flow
                } catch (e: MistralException) {
                    if (e.code != 429) throw e
                    lastError = e
                    if (attempt < MAX_ATTEMPTS - 1) {
                        val waitSec = (e.retryAfterSec ?: (1 shl (attempt + 1))).coerceIn(1, 10)
                        delay(waitSec * 1000L)
                    }
                } finally {
                    c.disconnect()
                }
            }
        }
        throw lastError ?: MistralException(429, "Mistral-Limit erreicht (429).")
    }.flowOn(Dispatchers.IO)

    private suspend fun FlowCollector<String>.readStream(c: HttpURLConnection) {
        c.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
            while (true) {
                currentCoroutineContext().ensureActive()
                val line = reader.readLine() ?: break
                if (!line.startsWith("data:")) continue
                val data = line.substring(5).trim()
                if (data == "[DONE]") break
                if (data.isEmpty()) continue
                val json = runCatching { JSONObject(data) }.getOrNull() ?: continue
                val choices = json.optJSONArray("choices") ?: continue
                if (choices.length() == 0) continue
                val delta = choices.getJSONObject(0).optJSONObject("delta") ?: continue
                when (val content = delta.opt("content")) {
                    is String -> if (content.isNotEmpty()) emit(content)
                    is JSONArray -> for (i in 0 until content.length()) {
                        // Reasoning-Modelle liefern Chunks; nur den eigentlichen Text anzeigen.
                        val chunk = content.optJSONObject(i) ?: continue
                        if (chunk.optString("type") == "text") {
                            val t = chunk.optString("text")
                            if (t.isNotEmpty()) emit(t)
                        }
                    }
                }
            }
        }
    }

    private fun contentText(content: Any?): String = when (content) {
        is String -> content
        is JSONArray -> buildString {
            for (i in 0 until content.length()) {
                val chunk = content.optJSONObject(i) ?: continue
                if (chunk.optString("type") == "text") append(chunk.optString("text"))
            }
        }
        else -> ""
    }

    /** Einfache (nicht gestreamte) Antwort – optional als JSON-Objekt. */
    suspend fun complete(
        apiKey: String,
        model: String,
        messages: List<ApiMessage>,
        json: Boolean = false,
        temperature: Double = 0.3,
    ): String = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("model", model)
            .put("temperature", temperature)
            .put("messages", JSONArray().apply { messages.forEach { put(it.toJson()) } })
        if (json) body.put("response_format", JSONObject().put("type", "json_object"))
        var last: MistralException? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            val c = open("/chat/completions", "POST", apiKey)
            try {
                c.sendJson(body)
                val o = c.readJson()
                val msg = o.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
                return@withContext contentText(msg.opt("content"))
            } catch (e: MistralException) {
                if (e.code != 429) throw e
                last = e
                if (attempt < MAX_ATTEMPTS - 1) delay(((e.retryAfterSec ?: (2 shl attempt)).coerceIn(1, 10)) * 1000L)
            } finally {
                c.disconnect()
            }
        }
        throw last ?: MistralException(429, "Mistral-Limit erreicht (429).")
    }

    /** Recherche im Internet über das eingebaute Web-Search-Werkzeug von Mistral. */
    suspend fun webSearch(apiKey: String, model: String, question: String): SearchResult =
        withContext(Dispatchers.IO) {
            val prompt = question.trim() + "\n\n(Recherchiere aktuell im Web. Antworte auf Deutsch, gut gegliedert " +
                "in Markdown, und stütze dich auf die gefundenen Quellen.)"
            val body = JSONObject()
                .put("model", model)
                .put("inputs", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
                .put("tools", JSONArray().put(JSONObject().put("type", "web_search")))
                .put("store", false)
            val c = open("/conversations", "POST", apiKey, readTimeoutMs = 180_000)
            val response = try {
                c.sendJson(body)
                c.readJson()
            } finally {
                c.disconnect()
            }
            val text = StringBuilder()
            val sources = LinkedHashMap<String, String>()
            val outputs = response.optJSONArray("outputs") ?: JSONArray()
            for (i in 0 until outputs.length()) {
                val entry = outputs.optJSONObject(i) ?: continue
                if (entry.optString("type") != "message.output") continue
                when (val content = entry.opt("content")) {
                    is String -> text.append(content)
                    is JSONArray -> for (j in 0 until content.length()) {
                        val chunk = content.optJSONObject(j) ?: continue
                        when (chunk.optString("type")) {
                            "text" -> text.append(chunk.optString("text"))
                            "tool_reference" -> {
                                val url = chunk.optString("url")
                                if (url.startsWith("http")) sources[url] = chunk.optString("title").ifBlank { url }
                            }
                        }
                    }
                }
            }
            SearchResult(text.toString().trim(), sources.map { (url, title) -> title to url })
        }

    /** Wandelt eine Audiodatei mit Voxtral in Text um. */
    suspend fun transcribe(
        apiKey: String,
        bytes: ByteArray,
        fileName: String,
        mime: String,
        language: String?,
    ): String = withContext(Dispatchers.IO) {
        val boundary = "----EuKiGenC" + System.currentTimeMillis()
        val c = open("/audio/transcriptions", "POST", apiKey, readTimeoutMs = 300_000)
        try {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            c.outputStream.use { out ->
                fun field(name: String, value: String) {
                    out.write("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n".toByteArray())
                }
                field("model", TRANSCRIBE_MODEL)
                if (!language.isNullOrBlank()) field("language", language)
                out.write(
                    ("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"$fileName\"\r\n" +
                        "Content-Type: $mime\r\n\r\n").toByteArray()
                )
                out.write(bytes)
                out.write("\r\n--$boundary--\r\n".toByteArray())
            }
            c.readJson().optString("text").trim()
        } finally {
            c.disconnect()
        }
    }

    /** Fragt die aktuell verfügbaren Chat-Modelle ab – neue Mistral-Modelle erscheinen so automatisch. */
    suspend fun listModels(apiKey: String): List<ModelInfo> = withContext(Dispatchers.IO) {
        val c = open("/models", "GET", apiKey, readTimeoutMs = 30_000)
        try {
            val data = c.readJson().optJSONArray("data") ?: JSONArray()
            buildList {
                for (i in 0 until data.length()) {
                    val m = data.getJSONObject(i)
                    val caps = m.optJSONObject("capabilities")
                    if (caps != null && !caps.optBoolean("completion_chat", true)) continue
                    if (!m.isNull("deprecation") && m.optString("deprecation").isNotBlank()) continue
                    val id = m.getString("id")
                    if (id.contains("embed") || id.contains("moderation") || id.contains("ocr")) continue
                    add(
                        ModelInfo(
                            id = id,
                            vision = caps?.optBoolean("vision", false) ?: false,
                            description = m.optString("description"),
                        )
                    )
                }
            }.distinctBy { it.id }.sortedWith(compareBy({ !it.id.endsWith("-latest") }, { it.id }))
        } finally {
            c.disconnect()
        }
    }

    /** Legt einen Agenten mit dem eingebauten Bildgenerierungs-Werkzeug an und liefert dessen ID. */
    suspend fun createImageAgent(apiKey: String, model: String): String = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("model", model)
            .put("name", "KI-GenC Bildgenerator")
            .put("description", "Erzeugt Bilder für die App KI-GenC.")
            .put(
                "instructions",
                "Du bist ein Bildgenerator. Nutze für jede Anfrage das Werkzeug zur Bildgenerierung " +
                    "und erzeuge genau ein Bild. Antworte danach mit höchstens einem kurzen Satz auf Deutsch."
            )
            .put("tools", JSONArray().put(JSONObject().put("type", "image_generation")))
        val c = open("/agents", "POST", apiKey)
        try {
            c.sendJson(body)
            c.readJson().getString("id")
        } finally {
            c.disconnect()
        }
    }

    data class GeneratedImage(val bytes: ByteArray, val caption: String)

    /** Erzeugt Bilder über die Conversations-API und lädt sie herunter. */
    suspend fun generateImages(apiKey: String, agentId: String, prompt: String): List<GeneratedImage> =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("agent_id", agentId)
                .put("inputs", prompt)
                .put("store", false)
            val c = open("/conversations", "POST", apiKey, readTimeoutMs = 180_000)
            val response = try {
                c.sendJson(body)
                c.readJson()
            } finally {
                c.disconnect()
            }

            val outputs = response.optJSONArray("outputs") ?: JSONArray()
            val fileIds = mutableListOf<String>()
            val caption = StringBuilder()
            for (i in 0 until outputs.length()) {
                val entry = outputs.optJSONObject(i) ?: continue
                if (entry.optString("type") != "message.output") continue
                when (val content = entry.opt("content")) {
                    is String -> caption.append(content)
                    is JSONArray -> for (j in 0 until content.length()) {
                        val chunk = content.optJSONObject(j) ?: continue
                        when (chunk.optString("type")) {
                            "tool_file" -> chunk.optString("file_id").takeIf { it.isNotBlank() }?.let(fileIds::add)
                            "text" -> caption.append(chunk.optString("text"))
                        }
                    }
                }
            }
            if (fileIds.isEmpty()) {
                throw MistralException(
                    0,
                    caption.toString().ifBlank { "Es wurde kein Bild erzeugt. Bitte Beschreibung anpassen." }
                )
            }
            fileIds.map { id -> GeneratedImage(downloadFile(apiKey, id), caption.toString().trim()) }
        }

    private fun downloadFile(apiKey: String, fileId: String): ByteArray {
        val c = open("/files/$fileId/content", "GET", apiKey, readTimeoutMs = 120_000)
        c.setRequestProperty("Accept", "*/*")
        return try {
            c.ensureOk()
            c.inputStream.use { it.readBytes() }
        } finally {
            c.disconnect()
        }
    }
}

/** Wandelt technische Fehler in verständliche deutsche Meldungen um. */
fun Throwable.friendlyMessage(): String = when (this) {
    is MistralException -> message ?: "Unbekannter Fehler."
    is UnknownHostException -> "Keine Internetverbindung."
    is SocketTimeoutException -> "Zeitüberschreitung – der Server hat zu lange gebraucht."
    is IOException -> "Netzwerkfehler: ${message ?: javaClass.simpleName}"
    else -> message ?: javaClass.simpleName
}
