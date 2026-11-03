package eu.kigenc.app.ai

import eu.kigenc.app.KiApp
import eu.kigenc.app.data.AppSettings
import eu.kigenc.app.data.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Gemeinsame Bildgenerierung (Studio, Foto-Editor): kümmert sich um den Mistral-Bild-Agenten. */
object ImageGenerator {

    private suspend fun agentId(app: KiApp, s: AppSettings, forceNew: Boolean): String {
        if (!forceNew) s.imageAgentId?.let { return it }
        val id = app.mistral.createImageAgent(s.apiKey, AppSettings.IMAGE_AGENT_MODEL)
        app.settings.setImageAgentId(id)
        return id
    }

    suspend fun generate(app: KiApp, s: AppSettings, prompt: String): List<File> {
        val images = try {
            app.mistral.generateImages(s.apiKey, agentId(app, s, forceNew = false), prompt)
        } catch (e: MistralException) {
            when (e.code) {
                404, 422 -> app.mistral.generateImages(s.apiKey, agentId(app, s, forceNew = true), prompt)
                // Kein Bild erhalten (nur Text) → einmal mit eindeutiger Anweisung wiederholen.
                0 -> app.mistral.generateImages(
                    s.apiKey, agentId(app, s, forceNew = false),
                    "Nutze jetzt das Bildgenerierungs-Werkzeug und erzeuge genau ein Bild: $prompt"
                )
                else -> throw e
            }
        }
        return withContext(Dispatchers.IO) { images.map { ImageUtils.saveGenerated(app, it.bytes) } }
    }
}
