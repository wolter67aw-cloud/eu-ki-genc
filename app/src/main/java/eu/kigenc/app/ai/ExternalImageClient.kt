package eu.kigenc.app.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Alternative Bildgeneratoren, falls Mistral (z. B. im Gratis-Tarif) keine Bilder erzeugt. */
object ExternalImageClient {

    private fun get(url: String, headers: Map<String, String> = emptyMap(), timeoutMs: Int = 120_000): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 20_000
        c.readTimeout = timeoutMs
        c.setRequestProperty("User-Agent", "KI-GenC-Android")
        headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
        return c
    }

    private fun errorText(c: HttpURLConnection): String =
        runCatching { c.errorStream?.bufferedReader()?.use { it.readText() } }.getOrNull().orEmpty().take(200)

    /** Pollinations.ai – kostenlos und ohne Schlüssel (mit Ratenbegrenzung). */
    suspend fun pollinations(prompt: String, width: Int = 1024, height: Int = 1024): ByteArray = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(prompt.take(900), "UTF-8").replace("+", "%20")
        val seed = (1..999_999).random()
        val url = "https://image.pollinations.ai/prompt/$encoded?width=$width&height=$height&model=flux&nologo=true&seed=$seed"
        var lastError: Exception? = null
        repeat(2) { attempt ->
            val c = get(url)
            try {
                val code = c.responseCode
                if (code == 429) {
                    lastError = MistralException(429, "Pollinations: zu viele Anfragen – bitte ca. 15 Sekunden warten.")
                    if (attempt == 0) delay(16_000)
                    return@repeat
                }
                if (code !in 200..299) throw MistralException(code, "Pollinations-Fehler $code ${errorText(c)}".trim())
                val type = c.contentType.orEmpty()
                if (!type.startsWith("image/")) throw MistralException(0, "Pollinations hat kein Bild geliefert ($type).")
                return@withContext c.inputStream.use { it.readBytes() }
            } catch (e: IOException) {
                lastError = e
            } finally {
                c.disconnect()
            }
        }
        throw lastError ?: MistralException(0, "Pollinations hat kein Bild geliefert.")
    }

    /** Black Forest Labs (FLUX) – Bild aus Text erzeugen. Eigener API-Schlüssel, Abrechnung pro Bild. */
    suspend fun blackForestLabs(apiKey: String, prompt: String, width: Int = 1024, height: Int = 1024): ByteArray =
        bflJob(apiKey, "flux-2-pro", JSONObject().put("prompt", prompt).put("width", width).put("height", height))

    /**
     * Black Forest Labs FLUX.1 Kontext – bearbeitet das echte Foto (Bild-zu-Bild).
     * Dadurch bleiben Gesichter und Personen erhalten, statt neu gemalt zu werden.
     */
    suspend fun blackForestLabsEdit(
        apiKey: String, prompt: String, imageBase64: String, aspectRatio: String, extra: List<String> = emptyList(),
    ): ByteArray {
        val body = JSONObject()
            .put("prompt", prompt)
            .put("input_image", imageBase64)
            .put("aspect_ratio", aspectRatio)
            .put("output_format", "jpeg")
            .put("safety_tolerance", 2)
        // Zusätzliche Referenzbilder (input_image_2 … input_image_4)
        extra.take(3).forEachIndexed { i, img -> body.put("input_image_${i + 2}", img) }
        return bflJob(apiKey, "flux-kontext-pro", body)
    }

    private suspend fun bflJob(apiKey: String, endpoint: String, body: JSONObject): ByteArray =
        withContext(Dispatchers.IO) {
            val start = URL("https://api.bfl.ai/v1/$endpoint").openConnection() as HttpURLConnection
            val job = try {
                start.requestMethod = "POST"
                start.connectTimeout = 20_000
                start.readTimeout = 60_000
                start.doOutput = true
                start.setRequestProperty("x-key", apiKey)
                start.setRequestProperty("Content-Type", "application/json")
                start.setRequestProperty("Accept", "application/json")
                start.outputStream.use { it.write(body.toString().toByteArray()) }
                val code = start.responseCode
                if (code == 401 || code == 403) throw MistralException(code, "Black-Forest-Labs-Schlüssel ungültig ($code).")
                if (code == 402) throw MistralException(code, "Kein Guthaben bei Black Forest Labs (402). Bitte auf bfl.ai aufladen.")
                if (code !in 200..299) throw MistralException(code, "Black-Forest-Labs-Fehler $code ${errorText(start)}".trim())
                JSONObject(start.inputStream.bufferedReader().use { it.readText() })
            } finally {
                start.disconnect()
            }
            val pollingUrl = job.optString("polling_url").ifBlank { "https://api.bfl.ai/v1/get_result?id=${job.optString("id")}" }
            repeat(120) {
                delay(1500)
                val c = get(pollingUrl, mapOf("x-key" to apiKey, "Accept" to "application/json"), 30_000)
                val result = try {
                    if (c.responseCode !in 200..299) null else JSONObject(c.inputStream.bufferedReader().use { it.readText() })
                } finally {
                    c.disconnect()
                }
                when (result?.optString("status")) {
                    "Ready" -> {
                        val sample = result.optJSONObject("result")?.optString("sample").orEmpty()
                        if (sample.isBlank()) throw MistralException(0, "Black Forest Labs: kein Bild im Ergebnis.")
                        val img = get(sample)
                        return@withContext try {
                            img.inputStream.use { it.readBytes() }
                        } finally {
                            img.disconnect()
                        }
                    }
                    "Error", "Failed" -> throw MistralException(0, "Black Forest Labs: Bearbeitung fehlgeschlagen.")
                    "Content Moderated", "Request Moderated" ->
                        throw MistralException(0, "Black Forest Labs hat die Anfrage aus Inhaltsgründen abgelehnt.")
                }
            }
            throw MistralException(0, "Black Forest Labs: Zeitüberschreitung.")
        }
}
