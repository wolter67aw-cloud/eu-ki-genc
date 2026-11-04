package eu.kigenc.app.ai

import eu.kigenc.app.KiApp
import eu.kigenc.app.data.AppSettings
import eu.kigenc.app.data.ImageUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/**
 * Gemeinsame Bildgenerierung (Studio, Foto-Editor) mit wählbarem Anbieter:
 * Mistral (Agenten/Werkzeug), Pollinations (kostenlos) oder Black Forest Labs (FLUX, eigener Schlüssel).
 * Im Modus „Automatisch“ wird bei einem Mistral-Fehler auf Pollinations ausgewichen.
 */
object ImageGenerator {

    /** Name des zuletzt erfolgreich genutzten Anbieters (für die Anzeige). */
    @Volatile
    var lastProvider: String = ""
        private set

    private suspend fun agentId(app: KiApp, s: AppSettings, forceNew: Boolean): String {
        if (!forceNew) s.imageAgentId?.let { return it }
        val id = app.mistral.createImageAgent(s.apiKey, AppSettings.IMAGE_AGENT_MODEL)
        app.settings.setImageAgentId(id)
        return id
    }

    private suspend fun mistral(app: KiApp, s: AppSettings, prompt: String): List<ByteArray> {
        if (!s.hasKey) throw MistralException(401, "Kein Mistral-API-Schlüssel eingetragen.")
        val images = try {
            app.mistral.generateImagesDirect(s.apiKey, AppSettings.IMAGE_AGENT_MODEL, prompt)
        } catch (direct: MistralException) {
            if (direct.code == 401 || direct.code == 429) throw explain(direct)
            try {
                try {
                    app.mistral.generateImages(s.apiKey, agentId(app, s, forceNew = false), prompt)
                } catch (e: MistralException) {
                    if (e.code == 404 || e.code == 422) app.mistral.generateImages(s.apiKey, agentId(app, s, forceNew = true), prompt)
                    else throw e
                }
            } catch (agent: MistralException) {
                throw explain(if (agent.code == 0 && direct.code != 0) direct else agent)
            }
        }
        return images.map { it.bytes }
    }

    /** true, wenn gesichtstreue Bearbeitung (FLUX Kontext) möglich ist. */
    fun canEditFaithfully(s: AppSettings) = s.bflKey.isNotBlank()

    /**
     * Bearbeitet ein echtes Foto mit FLUX Kontext. Gesicht, Identität und Proportionen bleiben erhalten.
     * [imageBase64] ist das JPEG als Base64 (ohne „data:“-Präfix).
     */
    suspend fun editFaithfully(
        app: KiApp, s: AppSettings, imageBase64: String, width: Int, height: Int, instruction: String,
        extraImagesBase64: List<String> = emptyList(),
    ): File {
        if (!canEditFaithfully(s)) throw MistralException(0, "Für gesichtstreue Bearbeitung bitte unter „Optionen → Bildgenerator“ einen Black-Forest-Labs-Schlüssel eintragen.")
        val wish = instruction.trim().ifBlank { "Improve the photo quality: better lighting, sharper details, natural colors." }
        val prompt = "Edit this photo as requested: \"$wish\". " +
            "Keep the exact same person(s): identical face, facial features, identity, age, skin tone, eye shape, " +
            "hairstyle, expression, body proportions and pose. Do not distort, beautify or replace any face. " +
            "Keep the original framing and aspect ratio. Only apply the requested change."
        val bytes = ExternalImageClient.blackForestLabsEdit(s.bflKey, prompt, imageBase64, aspectRatio(width, height), extraImagesBase64)
        lastProvider = "FLUX Kontext (gesichtstreu)"
        return withContext(Dispatchers.IO) { ImageUtils.saveGenerated(app, bytes) }
    }

    private fun aspectRatio(w: Int, h: Int): String {
        if (w <= 0 || h <= 0) return "1:1"
        var r = w.toDouble() / h
        r = r.coerceIn(0.25, 4.0)
        // auf kleine ganze Zahlen runden (z. B. 3:4, 16:9)
        val candidates = listOf(1 to 1, 4 to 3, 3 to 4, 3 to 2, 2 to 3, 16 to 9, 9 to 16, 5 to 4, 4 to 5, 21 to 9, 9 to 21)
        val best = candidates.minBy { (a, b) -> kotlin.math.abs(a.toDouble() / b - r) }
        return "${best.first}:${best.second}"
    }

    /** Bildgröße für Text-zu-Bild passend zum Seitenverhältnis (Vielfache von 64, ca. 1 Megapixel). */
    fun sizeFor(width: Int, height: Int): Pair<Int, Int> {
        if (width <= 0 || height <= 0) return 1024 to 1024
        val r = (width.toDouble() / height).coerceIn(0.25, 4.0)
        val h = kotlin.math.sqrt(1024.0 * 1024.0 / r)
        val w = h * r
        fun round64(v: Double) = ((v / 64).roundToInt() * 64).coerceIn(256, 2048)
        return round64(w) to round64(h)
    }

    suspend fun generate(app: KiApp, s: AppSettings, prompt: String, width: Int = 1024, height: Int = 1024): List<File> {
        val bytes: List<ByteArray> = when (s.imageProvider) {
            AppSettings.IMAGE_MISTRAL -> mistral(app, s, prompt).also { lastProvider = "Mistral" }
            AppSettings.IMAGE_POLLINATIONS -> listOf(ExternalImageClient.pollinations(prompt, width, height)).also { lastProvider = "Pollinations" }
            AppSettings.IMAGE_BFL -> {
                if (s.bflKey.isBlank()) throw MistralException(0, "Bitte unter „Optionen → Bildgenerator“ deinen Black-Forest-Labs-Schlüssel eintragen.")
                listOf(ExternalImageClient.blackForestLabs(s.bflKey, prompt, width, height)).also { lastProvider = "Black Forest Labs" }
            }
            else -> try {
                mistral(app, s, prompt).also { lastProvider = "Mistral" }
            } catch (e: CancellationException) {
                throw e
            } catch (mistralError: Exception) {
                try {
                    listOf(ExternalImageClient.pollinations(prompt, width, height)).also { lastProvider = "Pollinations (Ausweich)" }
                } catch (e: CancellationException) {
                    throw e
                } catch (fallbackError: Exception) {
                    throw MistralException(
                        0,
                        "Kein Bildgenerator hat geantwortet. Mistral: ${mistralError.friendlyMessage()} – " +
                            "Pollinations: ${fallbackError.friendlyMessage()}"
                    )
                }
            }
        }
        if (bytes.isEmpty()) throw MistralException(0, "Es wurde kein Bild erzeugt.")
        return withContext(Dispatchers.IO) { bytes.map { ImageUtils.saveGenerated(app, it) } }
    }

    private fun explain(e: MistralException): MistralException = when (e.code) {
        402, 403 -> MistralException(
            e.code,
            "Bildgenerierung ist für dein Mistral-Konto nicht freigeschaltet (${e.code}) – im Gratis-Tarif oft nicht enthalten. " +
                "Tipp: unter „Optionen → Bildgenerator“ „Pollinations (kostenlos)“ wählen."
        )
        else -> e
    }
}
