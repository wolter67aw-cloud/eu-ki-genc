package eu.kigenc.app.ai

import eu.kigenc.app.KiApp
import eu.kigenc.app.data.AppSettings
import eu.kigenc.app.data.ImageUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

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

    suspend fun generate(app: KiApp, s: AppSettings, prompt: String): List<File> {
        val bytes: List<ByteArray> = when (s.imageProvider) {
            AppSettings.IMAGE_MISTRAL -> mistral(app, s, prompt).also { lastProvider = "Mistral" }
            AppSettings.IMAGE_POLLINATIONS -> listOf(ExternalImageClient.pollinations(prompt)).also { lastProvider = "Pollinations" }
            AppSettings.IMAGE_BFL -> {
                if (s.bflKey.isBlank()) throw MistralException(0, "Bitte unter „Optionen → Bildgenerator“ deinen Black-Forest-Labs-Schlüssel eintragen.")
                listOf(ExternalImageClient.blackForestLabs(s.bflKey, prompt)).also { lastProvider = "Black Forest Labs" }
            }
            else -> try {
                mistral(app, s, prompt).also { lastProvider = "Mistral" }
            } catch (e: CancellationException) {
                throw e
            } catch (mistralError: Exception) {
                try {
                    listOf(ExternalImageClient.pollinations(prompt)).also { lastProvider = "Pollinations (Ausweich)" }
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
