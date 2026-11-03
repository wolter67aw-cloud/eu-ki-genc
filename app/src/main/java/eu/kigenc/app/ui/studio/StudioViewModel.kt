package eu.kigenc.app.ui.studio

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Spellcheck
import androidx.compose.material.icons.outlined.Summarize
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.ui.graphics.vector.ImageVector
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.kigenc.app.KiApp
import eu.kigenc.app.ai.ApiMessage
import eu.kigenc.app.ai.MistralException
import eu.kigenc.app.ai.friendlyMessage
import eu.kigenc.app.data.AppSettings
import eu.kigenc.app.data.Attachment
import eu.kigenc.app.data.AttachmentKind
import eu.kigenc.app.data.Attachments
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
        "report", "Bericht", Icons.Outlined.Description,
        "Worüber soll der Bericht sein? Stichpunkte, Zahlen, Zielgruppe …",
        "Du erstellst professionelle, sachliche Berichte auf Deutsch: Titel, kurze Zusammenfassung (Management Summary), " +
            "gegliederte Abschnitte mit Überschriften, ggf. Tabellen, Fazit und nächste Schritte. Erfinde keine Fakten – " +
            "markiere fehlende Angaben als [bitte ergänzen]."
    ),
    TextTemplate(
        "analyze", "Analysieren", Icons.Outlined.Psychology,
        "Füge einen Text ein – ich analysiere Tonfall, Struktur und Logik.",
        "Analysiere den Text des Nutzers sorgfältig auf Deutsch und gliedere die Antwort in: 1) Tonfall & Wirkung " +
            "(z. B. freundlich, sachlich, aggressiv; Zielgruppe), 2) Struktur & Aufbau, 3) Logik & Argumentation " +
            "(Schlüssigkeit, Lücken, Widersprüche, Fehlschlüsse), 4) Stärken, 5) konkrete Verbesserungsvorschläge."
    ),
    TextTemplate(
        "explain", "Erklären", Icons.Outlined.School,
        "Welches Thema soll ich erklären? Optional: für welches Alter/Niveau?",
        "Du bist ein geduldiger, motivierender Lehrer. Erkläre das Thema verständlich auf Deutsch: zuerst in einem Satz, " +
            "dann Schritt für Schritt mit Alltagsbeispielen und Analogien, wichtige Begriffe fett, am Ende eine kurze " +
            "Zusammenfassung und drei Verständnisfragen."
    ),
    TextTemplate(
        "exercises", "Übungen", Icons.Outlined.Quiz,
        "Zu welchem Thema/Fach sollen Übungen erstellt werden? Niveau?",
        "Erstelle abwechslungsreiche Übungsaufgaben auf Deutsch (z. B. Multiple Choice, Lückentext, offene Fragen, " +
            "Rechenaufgaben) mit steigendem Schwierigkeitsgrad. Nummeriere sie. Gib die Lösungen mit kurzer Erklärung " +
            "erst ganz am Ende unter der Überschrift „Lösungen“ aus."
    ),
    TextTemplate(
        "translate", "Übersetzen", Icons.Outlined.Translate,
        "Text eingeben und unten die Zielsprache wählen.",
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
    val attachments: List<Attachment> = emptyList(),
    val attachBusy: Boolean = false,
    val imageReference: Attachment? = null,
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

    /** Fügt einen Anhang (Foto, Video oder Datei) hinzu. Für [forImage] = true als Bildvorlage. */
    fun addAttachment(kind: AttachmentKind, uri: Uri, forImage: Boolean = false) {
        viewModelScope.launch {
            _state.update { it.copy(attachBusy = true) }
            runCatching {
                withContext(Dispatchers.IO) {
                    when (kind) {
                        AttachmentKind.PHOTO -> Attachments.photo(app, uri)
                        AttachmentKind.VIDEO -> Attachments.video(app, uri)
                        AttachmentKind.FILE -> Attachments.file(app, uri)
                    }
                }
            }.onSuccess { a ->
                _state.update { st ->
                    if (forImage) st.copy(imageReference = a, attachBusy = false)
                    else st.copy(attachments = (st.attachments + a).takeLast(5), attachBusy = false)
                }
            }.onFailure { e ->
                _state.update { it.copy(attachBusy = false, error = e.message ?: "Anhang konnte nicht gelesen werden.") }
            }
        }
    }

    fun removeAttachment(id: String) = _state.update { st -> st.copy(attachments = st.attachments.filterNot { it.id == id }) }
    fun removeImageReference() = _state.update { it.copy(imageReference = null) }

    fun generateText(input: String, targetLanguage: String? = null) {
        val attachments = _state.value.attachments
        if ((input.isBlank() && attachments.isEmpty()) || _state.value.textBusy) return
        textJob = viewModelScope.launch {
            val s = requireSettings() ?: return@launch
            val template = _state.value.template
            _state.update { it.copy(textBusy = true, textOutput = "", error = null) }
            val out = StringBuilder()
            try {
                app.mistral.streamChat(
                    apiKey = s.apiKey,
                    model = s.chatModel,
                    messages = listOf(
                        ApiMessage("system", template.system),
                        ApiMessage(
                            "user",
                            Attachments.composePrompt(
                                (if (template.id == "translate" && targetLanguage != null) "Übersetze ins $targetLanguage:\n\n" else "") +
                                    input.trim().ifBlank { "Bitte bearbeite die Anhänge entsprechend deiner Aufgabe." },
                                attachments
                            ),
                            extraImages = attachments.flatMap { it.images }.take(10)
                        )
                    ),
                    temperature = if (template.id in setOf("translate", "improve", "analyze", "report")) 0.2 else 0.8,
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
                val reference = _state.value.imageReference
                val finalPrompt = if (reference != null && reference.images.isNotEmpty()) {
                    val description = app.mistral.complete(
                        s.apiKey, s.chatModel,
                        listOf(
                            ApiMessage(
                                "user",
                                "Beschreibe dieses Bild präzise als Bild-Prompt (Motiv, Komposition, Perspektive, Farben, " +
                                    "Licht, Stil). Gib nur die Beschreibung aus.",
                                extraImages = reference.images.take(3)
                            )
                        )
                    )
                    "Vorlage: $description\n\nWunsch/Änderung: ${prompt.trim()}"
                } else prompt.trim()
                val images = try {
                    val agent = s.imageAgentId ?: createAgent(s)
                    app.mistral.generateImages(s.apiKey, agent, finalPrompt)
                } catch (e: MistralException) {
                    // Agent wurde evtl. gelöscht oder gehört zu einem anderen Konto → einmal neu anlegen.
                    if (e.code == 404 || e.code == 422) {
                        val agent = createAgent(s)
                        app.mistral.generateImages(s.apiKey, agent, finalPrompt)
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
            _state.update { it.copy(error = if (ok) "In der Galerie gespeichert (Bilder/KI-GenC)." else "Speichern fehlgeschlagen.") }
        }
    }
}
