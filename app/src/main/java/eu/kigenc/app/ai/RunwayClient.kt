package eu.kigenc.app.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Runway (KI-Videos, runwayml.com) – macht aus einem Foto einen kurzen KI-Videoclip (Bild-zu-Video, Gen-4 Turbo).
 * Eigener API-Schlüssel aus dem Entwicklerportal dev.runwayml.com, Abrechnung pro Sekunde über Runway-Guthaben.
 */
object RunwayClient {
    private const val BASE = "https://api.dev.runwayml.com/v1"
    private const val VERSION = "2024-11-06"
    const val MODEL = "gen4_turbo"
    /** Gen-4 Turbo: 5 Credits pro Sekunde, 1 Credit = 1 Cent. */
    const val CREDITS_PER_SECOND = 5

    /** Seitenverhältnis für Gen-4 Turbo passend zum Videoformat. */
    fun ratioFor(width: Int, height: Int): String = when {
        width > height -> "1280:720"
        height > width -> "720:1280"
        else -> "960:960"
    }

    private fun open(path: String, method: String, key: String): HttpURLConnection =
        (URL(BASE + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 60_000
            setRequestProperty("Authorization", "Bearer $key")
            setRequestProperty("X-Runway-Version", VERSION)
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("User-Agent", "KI-GenC-Android")
        }

    private fun HttpURLConnection.json(): JSONObject {
        val code = responseCode
        val body = (if (code in 200..299) inputStream else errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) {
            val detail = runCatching { JSONObject(body).let { it.optString("error").ifBlank { it.optString("message") } } }.getOrNull()
                .orEmpty().ifBlank { body.take(200) }
            throw MistralException(
                code,
                when (code) {
                    401 -> "Runway-Schlüssel ungültig – bitte unter Optionen → Bildgenerator prüfen."
                    429 -> "Runway: zu viele Anfragen gleichzeitig – bitte kurz warten."
                    else -> "Runway-Fehler $code: $detail".trim()
                },
            )
        }
        return JSONObject(body)
    }

    /**
     * Erzeugt einen Clip aus [imageDataUrl] (JPEG/PNG als Data-URL, höchstens ca. 3 MB) und speichert ihn als MP4.
     * [onStatus] meldet den Zwischenstand (z. B. „In der Warteschlange“, „Wird erzeugt“).
     */
    suspend fun imageToVideo(
        key: String,
        imageDataUrl: String,
        prompt: String,
        ratio: String,
        seconds: Int,
        outDir: File,
        onStatus: (String) -> Unit = {},
    ): File = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("model", MODEL)
            .put("promptImage", imageDataUrl)
            .put("ratio", ratio)
            .put("duration", seconds)
        if (prompt.isNotBlank()) body.put("promptText", prompt.take(900))
        val start = open("/image_to_video", "POST", key)
        val taskId = try {
            start.doOutput = true
            start.outputStream.use { it.write(body.toString().toByteArray()) }
            start.json().optString("id").ifBlank { throw MistralException(0, "Runway hat keine Auftragsnummer geliefert.") }
        } finally {
            start.disconnect()
        }
        onStatus("In der Warteschlange …")
        // Abfragen, bis der Clip fertig ist (höchstens ca. 6 Minuten)
        val deadline = System.currentTimeMillis() + 6 * 60_000
        var videoUrl: String? = null
        while (videoUrl == null) {
            ensureActive()
            if (System.currentTimeMillis() > deadline) throw MistralException(0, "Runway braucht ungewöhnlich lange – bitte später erneut versuchen.")
            delay(5_000)
            val c = open("/tasks/$taskId", "GET", key)
            val task = try { c.json() } finally { c.disconnect() }
            when (task.optString("status")) {
                "SUCCEEDED" -> videoUrl = task.optJSONArray("output")?.optString(0)?.takeIf { it.isNotBlank() }
                    ?: throw MistralException(0, "Runway hat kein Video geliefert.")
                "FAILED", "CANCELLED" -> {
                    val code = task.optString("failureCode")
                    val reason = task.optString("failure").ifBlank { "unbekannter Grund" }
                    throw MistralException(
                        0,
                        if (code.contains("SAFETY", true) || code.contains("MODERATION", true))
                            "Runway hat das Bild oder die Beschreibung abgelehnt (Inhaltsprüfung). Bitte ein anderes Foto oder eine andere Beschreibung wählen."
                        else "Runway konnte den Clip nicht erzeugen: $reason"
                    )
                }
                "RUNNING" -> onStatus("Wird erzeugt …" + task.optDouble("progress", -1.0).let { if (it >= 0) " ${(it * 100).toInt()} %" else "" })
                else -> onStatus("In der Warteschlange …")
            }
        }
        // Ergebnis sofort herunterladen – Runway-Links laufen nach 24–48 Stunden ab
        onStatus("Wird heruntergeladen …")
        val out = File(outDir.apply { mkdirs() }, "runway_${System.currentTimeMillis()}.mp4")
        val d = URL(videoUrl).openConnection() as HttpURLConnection
        try {
            d.connectTimeout = 20_000
            d.readTimeout = 120_000
            if (d.responseCode !in 200..299) throw MistralException(d.responseCode, "Runway-Video konnte nicht geladen werden (${d.responseCode}).")
            d.inputStream.use { input -> out.outputStream().use { input.copyTo(it) } }
        } finally {
            d.disconnect()
        }
        out
    }
}
