package eu.kigenc.app.ai

import eu.kigenc.app.KiApp
import eu.kigenc.app.data.AppSettings
import eu.kigenc.app.data.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Gemeinsame Bildgenerierung (Studio, Foto-Editor).
 * 1. Versuch: direkt über die Conversations-API mit dem eingebauten Werkzeug (kein Agent nötig).
 * 2. Versuch: über einen eigenen Bild-Agenten.
 */
object ImageGenerator {

    private suspend fun agentId(app: KiApp, s: AppSettings, forceNew: Boolean): String {
        if (!forceNew) s.imageAgentId?.let { return it }
        val id = app.mistral.createImageAgent(s.apiKey, AppSettings.IMAGE_AGENT_MODEL)
        app.settings.setImageAgentId(id)
        return id
    }

    private suspend fun viaAgent(app: KiApp, s: AppSettings, prompt: String): List<MistralClient.GeneratedImage> =
        try {
            app.mistral.generateImages(s.apiKey, agentId(app, s, forceNew = false), prompt)
        } catch (e: MistralException) {
            if (e.code == 404 || e.code == 422) app.mistral.generateImages(s.apiKey, agentId(app, s, forceNew = true), prompt)
            else throw e
        }

    suspend fun generate(app: KiApp, s: AppSettings, prompt: String): List<File> {
        val images = try {
            app.mistral.generateImagesDirect(s.apiKey, AppSettings.IMAGE_AGENT_MODEL, prompt)
        } catch (direct: MistralException) {
            if (direct.code == 401 || direct.code == 429) throw explain(direct)
            try {
                viaAgent(app, s, prompt)
            } catch (agent: MistralException) {
                throw explain(if (agent.code == 0 && direct.code != 0) direct else agent)
            }
        }
        return withContext(Dispatchers.IO) { images.map { ImageUtils.saveGenerated(app, it.bytes) } }
    }

    private fun explain(e: MistralException): MistralException = when (e.code) {
        402, 403 -> MistralException(
            e.code,
            "Bildgenerierung ist für dein Mistral-Konto nicht freigeschaltet (${e.code}). Im kostenlosen Experiment-Tarif " +
                "sind Agenten/Werkzeuge wie die Bildgenerierung oft nicht enthalten – dafür wird der Scale-Tarif " +
                "(Bezahlung nach Verbrauch) benötigt. Details: ${e.message}"
        )
        else -> e
    }
}
