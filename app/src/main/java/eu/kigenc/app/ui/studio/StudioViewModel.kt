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
import eu.kigenc.app.ai.ImageGenerator
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
    val imageReferences: List<Attachment> = emptyList(),
)

class StudioViewModel(private val app: KiApp) : ViewModel() {

    companion object {
        const val MAX_ATTACHMENTS = 10
        const val MAX_REFERENCES = 6
        /** Höchstzahl Bilder pro Anfrage – gleichmäßig auf alle Anhänge verteilt. */
        const val MAX_IMAGES = 8

        fun selectImages(attachments: List<Attachment>, max: Int): List<String> {
            val queues = attachments.map { it.images.toMutableList() }.filter { it.isNotEmpty() }
            val result = mutableListOf<String>()
            var progress = true
            while (result.size < max && progress) {
                progress = false
                for (q in queues) {
                    if (result.size >= max) break
                    if (q.isNotEmpty()) {
                        result += q.removeAt(0)
                        progress = true
                    }
                }
            }
            return result
        }
    }

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

    /**
     * Fügt einen oder mehrere Anhänge hinzu. [kind] = null → Foto/Video wird am Dateityp erkannt.
     * Für [forImage] = true wird der erste als Bildvorlage verwendet.
     */
    fun addAttachments(kind: AttachmentKind?, uris: List<Uri>, forImage: Boolean = false) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(attachBusy = true) }
            val added = mutableListOf<Attachment>()
            var failed = 0
            for (uri in uris.take(if (forImage) MAX_REFERENCES else MAX_ATTACHMENTS)) {
                runCatching {
                    withContext(Dispatchers.IO) {
                        val k = kind ?: run {
                            val mime = app.contentResolver.getType(uri).orEmpty()
                            if (mime.startsWith("video/")) AttachmentKind.VIDEO else AttachmentKind.PHOTO
                        }
                        when (k) {
                            AttachmentKind.PHOTO -> Attachments.photo(app, uri)
                            AttachmentKind.VIDEO -> Attachments.video(app, uri)
                            AttachmentKind.FILE -> Attachments.file(app, uri)
                        }
                    }
                }.onSuccess { added += it }
                    .onFailure { e ->
                        failed++
                        _state.update { it.copy(error = e.message ?: "Anhang konnte nicht gelesen werden.") }
                    }
            }
            _state.update { st ->
                when {
                    forImage -> {
                        val all = st.imageReferences + added
                        st.copy(
                            imageReferences = all.takeLast(MAX_REFERENCES),
                            attachBusy = false,
                            error = if (all.size > MAX_REFERENCES) "Maximal $MAX_REFERENCES Vorlagen – die ältesten wurden entfernt." else st.error
                        )
                    }
                    else -> {
                        val all = st.attachments + added
                        st.copy(
                            attachments = all.takeLast(MAX_ATTACHMENTS),
                            attachBusy = false,
                            error = when {
                                all.size > MAX_ATTACHMENTS -> "Maximal $MAX_ATTACHMENTS Anhänge – die ältesten wurden entfernt."
                                failed > 0 && added.isNotEmpty() -> "${added.size} hinzugefügt, $failed konnte(n) nicht gelesen werden."
                                else -> st.error
                            }
                        )
                    }
                }
            }
        }
    }

    fun addAttachment(kind: AttachmentKind, uri: Uri, forImage: Boolean = false) = addAttachments(kind, listOf(uri), forImage)

    fun clearAttachments() = _state.update { it.copy(attachments = emptyList()) }

    fun removeAttachment(id: String) = _state.update { st -> st.copy(attachments = st.attachments.filterNot { it.id == id }) }
    fun removeImageReference(id: String) = _state.update { st -> st.copy(imageReferences = st.imageReferences.filterNot { it.id == id }) }
    fun clearImageReferences() = _state.update { it.copy(imageReferences = emptyList()) }

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
                            extraImages = selectImages(attachments, MAX_IMAGES)
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
                val refs = _state.value.imageReferences
                val photos = refs.filter { it.kind == AttachmentKind.PHOTO && it.images.isNotEmpty() }
                if (photos.isNotEmpty() && ImageGenerator.canEditFaithfully(s)) {
                    // Echte Fotos gesichtstreu bearbeiten statt neu malen (bis zu 4 Bilder als Referenz).
                    val first = photos.first()
                    val extra = (photos.drop(1).map { it.images.first() } +
                        refs.filter { it.kind == AttachmentKind.VIDEO }.mapNotNull { v -> v.images.getOrNull(v.images.size / 2) })
                        .take(3).map { it.substringAfter("base64,") }
                    val wish = if (extra.isEmpty()) prompt else
                        "$prompt (Bild 1 ist das Hauptfoto; die weiteren Bilder sind zusätzliche Vorlagen für Personen, Stil oder Inhalt)"
                    val file = ImageGenerator.editFaithfully(
                        app, s, first.images.first().substringAfter("base64,"),
                        first.thumbnail?.width ?: 1, first.thumbnail?.height ?: 1, wish, extra
                    )
                    _state.update { it.copy(images = listOf(GeneratedImageItem(file, prompt.trim(), "")) + it.images) }
                    return@launch
                }
                val finalPrompt = if (refs.isNotEmpty()) {
                    val description = app.mistral.complete(
                        s.apiKey, s.chatModel,
                        listOf(
                            ApiMessage(
                                "user",
                                "Hier sind ${refs.size} Vorlage(n) (Fotos bzw. Standbilder aus Videos). Beschreibe sie präzise als " +
                                    "EINEN gemeinsamen Bild-Prompt: wichtige Motive, bei Personen Gesicht, Haare, Hautton, Alter, " +
                                    "Ausdruck und Pose genau; Komposition, Farben, Licht, Stil. Gib nur die Beschreibung aus.",
                                extraImages = selectImages(refs, MAX_IMAGES)
                            )
                        )
                    ).take(1800)
                    "Vorlagen: $description\n\nWunsch/Änderung: ${prompt.trim()}. Natürliche, unverzerrte Gesichter mit korrekten Proportionen."
                } else prompt.trim()
                val firstThumb = refs.firstOrNull()?.thumbnail
                val (gw, gh) = firstThumb?.let { ImageGenerator.sizeFor(it.width, it.height) } ?: (1024 to 1024)
                val files = ImageGenerator.generate(app, s, finalPrompt, gw, gh)
                val items = files.map { GeneratedImageItem(it, prompt.trim(), "") }
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
