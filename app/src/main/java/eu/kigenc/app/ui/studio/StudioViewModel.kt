package eu.kigenc.app.ui.studio

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Spellcheck
import androidx.compose.material.icons.outlined.Summarize
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.kigenc.app.KiApp
import eu.kigenc.app.ai.ApiMessage
import eu.kigenc.app.ai.MistralException
import eu.kigenc.app.ai.friendlyMessage
import eu.kigenc.app.data.AppSettings
import eu.kigenc.app.data.ImageUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class TextTemplate(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val hint: String,
    val system: String,
)

val TEXT_TEMPLATES = listOf(
    TextTemplate(
        "free", "Freier Text", Icons.Outlined.Create,
        "Worüber soll ich schreiben? (z. B. eine Kurzgeschichte über einen Roboter in Brüssel)",
        "Du bist ein vielseitiger, kreativer Autor. Schreibe hochwertige, gut strukturierte Texte auf Deutsch, " +
            "sofern nicht anders gewünscht."
    ),
    TextTemplate(
        "mail", "E-Mail", Icons.Outlined.Email,
        "Worum geht es in der E-Mail, an wen, welcher Ton?",
        "Du formulierst klare, höfliche und professionelle E-Mails auf Deutsch. Gib eine Betreffzeile und den " +
            "fertigen E-Mail-Text aus. Passe den Ton an die Beschreibung an."
    ),
    TextTemplate(
        "summary", "Zusammenfassen", Icons.Outlined.Summarize,
        "Füge hier den Text ein, den ich zusammenfassen soll.",
        "Fasse den Text des Nutzers präzise auf Deutsch zusammen: zuerst ein Satz als Kernaussage, " +
            "dann die wichtigsten Punkte als Liste."
    ),
    TextTemplate(
        "translate", "Übersetzen", Icons.Outlined.Translate,
        "Text eingeben – optional Zielsprache angeben (z. B. „ins Französische: …“).",
        "Du bist ein professioneller Übersetzer. Ist eine Zielsprache genannt, übersetze dorthin. Sonst: deutschen " +
            "Text ins Englische, alle anderen Sprachen ins Deutsche. Gib nur die Übersetzung aus."
    ),
    TextTemplate(
        "improve", "Verbessern", Icons.Outlined.Spellcheck,
        "Füge deinen Text ein – ich korrigiere Rechtschreibung, Grammatik und Stil.",
        "Korrigiere Rechtschreibung, Grammatik und Stil des Textes, ohne den Inhalt zu verändern. Gib zuerst den " +
            "verbesserten Text aus und danach kurz die wichtigsten Änderungen als Liste."
    ),
    TextTemplate(
        "social", "Social Media", Icons.Outlined.Campaign,
        "Thema, Plattform (Instagram, LinkedIn, …) und Zielgruppe?",
        "Du bist Social-Media-Experte. Schreibe einen ansprechenden Beitrag passend zur Plattform, mit Emojis " +
            "wo passend und 3–6 relevanten Hashtags. Biete zwei Varianten an."
    ),
    TextTemplate(
        "ideas", "Ideen", Icons.Outlined.Lightbulb,
        "Wofür brauchst du Ideen?",
        "Du bist ein kreativer Ideengeber. Liefere 10 originelle, konkrete und umsetzbare Ideen als nummerierte " +
            "Liste, jeweils mit einem kurzen Satz zur Umsetzung."
    ),
    TextTemplate(
        "imgprompt", "Bild-Prompt", Icons.Outlined.Brush,
        "Beschreibe grob, welches Bild du möchtest – ich mache einen detaillierten Prompt daraus.",
        "Verwandle die Idee des Nutzers in einen einzigen, detaillierten Bild-Prompt auf Deutsch (Motiv, Stil, " +
            "Licht, Perspektive, Farben, Stimmung). Gib nur den Prompt aus, ohne Einleitung."
    ),
)

data class GeneratedImageItem(val file: File, val prompt: String, val caption: String)

data class StudioUiState(
    val template: TextTemplate = TEXT_TEMPLATES.first(),
    val textOutput: String = "",
    val textBusy: Boolean = false,
    val imageBusy: Boolean = false,
    val images: List<GeneratedImageItem> = emptyList(),
    val error: String? = null,
)

class StudioViewModel(private val app: KiApp) : ViewModel() {

    private val _state = MutableStateFlow(StudioUiState())
    val state: StateFlow<StudioUiState> = _state.asStateFlow()
    private var textJob: Job? = null

    init {
        viewModelScope.launch {
            val existing = withContext(Dispatchers.IO) {
                ImageUtils.generatedDir(app).listFiles { f -> f.extension == "png" }.orEmpty()
                    .sortedByDescending { it.lastModified() }
                    .map { GeneratedImageItem(it, "", "") }
            }
            _state.update { it.copy(images = existing) }
        }
    }

    fun selectTemplate(t: TextTemplate) = _state.update { it.copy(template = t) }
    fun clearError() = _state.update { it.copy(error = null) }

    private suspend fun requireSettings(): AppSettings? {
        val s = app.settings.current()
        if (!s.hasKey) {
            _state.update { it.copy(error = "Bitte zuerst deinen Mistral-API-Schlüssel unter „Einstellungen“ eintragen.") }
            return null
        }
        return s
    }

    fun generateText(input: String) {
        if (input.isBlank() || _state.value.textBusy) return
        textJob = viewModelScope.launch {
            val s = requireSettings() ?: return@launch
            val template = _state.value.template
            _state.update { it.copy(textBusy = true, textOutput = "", error = null) }
            val out = StringBuilder()
            try {
                app.mistral.streamChat(
                    apiKey = s.apiKey,
                    model = s.chatModel,
                    messages = listOf(ApiMessage("system", template.system), ApiMessage("user", input.trim())),
                    temperature = if (template.id == "translate" || template.id == "improve") 0.2 else 0.8,
                ).collect { delta ->
                    out.append(delta)
                    _state.update { it.copy(textOutput = out.toString()) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = e.friendlyMessage()) }
            } finally {
                _state.update { it.copy(textBusy = false) }
            }
        }
    }

    fun stopText() {
        textJob?.cancel()
    }

    fun generateImage(prompt: String) {
        if (prompt.isBlank() || _state.value.imageBusy) return
        viewModelScope.launch {
            val s = requireSettings() ?: return@launch
            _state.update { it.copy(imageBusy = true, error = null) }
            try {
                val images = try {
                    val agent = s.imageAgentId ?: createAgent(s)
                    app.mistral.generateImages(s.apiKey, agent, prompt.trim())
                } catch (e: MistralException) {
                    // Agent wurde evtl. gelöscht oder gehört zu einem anderen Konto → einmal neu anlegen.
                    if (e.code == 404 || e.code == 422) {
                        val agent = createAgent(s)
                        app.mistral.generateImages(s.apiKey, agent, prompt.trim())
                    } else {
                        throw e
                    }
                }
                val items = withContext(Dispatchers.IO) {
                    images.map { GeneratedImageItem(ImageUtils.saveGenerated(app, it.bytes), prompt.trim(), it.caption) }
                }
                _state.update { it.copy(images = items + it.images) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = "Bild konnte nicht erzeugt werden: ${e.friendlyMessage()}") }
            } finally {
                _state.update { it.copy(imageBusy = false) }
            }
        }
    }

    private suspend fun createAgent(s: AppSettings): String {
        val id = app.mistral.createImageAgent(s.apiKey, AppSettings.IMAGE_AGENT_MODEL)
        app.settings.setImageAgentId(id)
        return id
    }

    fun deleteImage(item: GeneratedImageItem) {
        viewModelScope.launch(Dispatchers.IO) { item.file.delete() }
        _state.update { s -> s.copy(images = s.images.filterNot { it.file == item.file }) }
    }

    fun saveToGallery(item: GeneratedImageItem) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { ImageUtils.saveToGallery(app, item.file) }
            _state.update { it.copy(error = if (ok) "In der Galerie gespeichert (Bilder/EU KI-GenC)." else "Speichern fehlgeschlagen.") }
        }
    }
}
