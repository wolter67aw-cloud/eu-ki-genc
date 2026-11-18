package eu.kigenc.app.ui.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.kigenc.app.KiApp
import eu.kigenc.app.ai.ApiMessage
import eu.kigenc.app.ai.friendlyMessage
import eu.kigenc.app.data.ChatMessage
import eu.kigenc.app.data.ChatMode
import eu.kigenc.app.data.Conversation
import eu.kigenc.app.data.ImageUtils
import eu.kigenc.app.data.Role
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import eu.kigenc.app.data.report

data class ChatUiState(
    val conversations: List<Conversation> = emptyList(),
    val current: Conversation? = null,
    val streaming: Boolean = false,
    /** Für die nächste Nachricht ausgewählte Bilder (mehrere möglich). */
    val attachedImages: List<String> = emptyList(),
    /** Aktives Projekt für neue bzw. geöffnete Unterhaltung (null = kein Projekt). */
    val project: eu.kigenc.app.data.Project? = null,
    val error: String? = null,
)

class ChatViewModel(private val app: KiApp, val mode: ChatMode) : ViewModel() {

    companion object {
        /** Höchstzahl Bilder pro Nachricht. */
        const val MAX_IMAGES = 6
        /** Höchstzahl Bilder pro Anfrage an die KI (über den ganzen Verlauf). */
        private const val MAX_API_IMAGES = 8
    }

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()
    private var job: Job? = null

    init {
        // Beim Öffnen der App immer mit einer leeren Unterhaltung starten (Verlauf bleibt erhalten).
        viewModelScope.launch { reload(selectFirst = false) }
    }

    private suspend fun reload(selectFirst: Boolean = false) {
        val list = app.chatStore.list(mode)
        _state.update { s ->
            s.copy(
                conversations = list,
                current = if (selectFirst) list.firstOrNull() else s.current,
            )
        }
    }

    fun newChat() {
        stop()
        _state.update { it.copy(current = null, attachedImages = emptyList(), error = null) }
    }

    fun open(conversation: Conversation) {
        stop()
        _state.update { it.copy(current = conversation, error = null, project = app.projects.byId(conversation.projectId)) }
    }

    /** Projekt für die nächste neue Unterhaltung wählen (null = ohne Projekt). Eine laufende wird neu begonnen. */
    fun selectProject(project: eu.kigenc.app.data.Project?) {
        if (_state.value.current?.messages?.isNotEmpty() == true) newChat()
        _state.update { it.copy(project = project) }
    }

    /** Nach dem Bearbeiten/Löschen eines Projekts die Anzeige aktualisieren. */
    fun refreshProject() {
        _state.update { st -> st.copy(project = st.project?.let { app.projects.byId(it.id) }) }
    }

    fun delete(conversation: Conversation) {
        viewModelScope.launch {
            if (_state.value.current?.id == conversation.id) newChat()
            app.chatStore.delete(conversation)
            reload()
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    fun reportError(message: String) = _state.update { it.copy(error = message) }

    fun attachImage(uri: Uri) = attachImages(listOf(uri))

    /** Ein oder mehrere Bilder an die nächste Nachricht anhängen (höchstens [MAX_IMAGES]). */
    fun attachImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val free = MAX_IMAGES - _state.value.attachedImages.size
            if (free <= 0) {
                _state.update { it.copy(error = "Höchstens $MAX_IMAGES Bilder pro Nachricht.") }
                return@launch
            }
            val paths = mutableListOf<String>()
            var failed = 0
            for (uri in uris.take(free)) {
                runCatching { withContext(Dispatchers.IO) { ImageUtils.importImage(app, uri) } }
                    .onSuccess { paths += it }
                    .onFailure { failed++ }
            }
            _state.update {
                it.copy(
                    attachedImages = (it.attachedImages + paths).take(MAX_IMAGES),
                    error = when {
                        uris.size > free -> "Höchstens $MAX_IMAGES Bilder pro Nachricht – die übrigen wurden nicht übernommen."
                        failed > 0 -> "$failed Bild(er) konnten nicht geladen werden."
                        else -> it.error
                    }
                )
            }
        }
    }

    fun removeImage(path: String) = _state.update { it.copy(attachedImages = it.attachedImages - path) }

    /** Letzte Antwort neu erzeugen. */
    fun regenerate() {
        val conv = _state.value.current ?: return
        if (_state.value.streaming) return
        val idx = conv.messages.indexOfLast { it.role == Role.USER }
        if (idx < 0) return
        val user = conv.messages[idx]
        _state.update { it.copy(current = conv.copy(messages = conv.messages.take(idx)), attachedImages = user.allImages) }
        send(user.text)
    }

    /** Eigene Nachricht bearbeiten und ab dort neu senden. */
    fun editAndResend(messageId: String, newText: String) {
        val conv = _state.value.current ?: return
        if (_state.value.streaming || newText.isBlank()) return
        val idx = conv.messages.indexOfFirst { it.id == messageId }
        if (idx < 0) return
        val original = conv.messages[idx]
        _state.update { it.copy(current = conv.copy(messages = conv.messages.take(idx)), attachedImages = original.allImages) }
        send(newText)
    }

    fun rename(conversation: Conversation, title: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val updated = conversation.copy(title = title.trim())
            app.chatStore.save(updated)
            _state.update { st ->
                val cur = st.current
                if (cur != null && cur.id == updated.id) st.copy(current = cur.copy(title = updated.title)) else st
            }
            reload()
        }
    }

    /** Unterhaltung im Verlauf anheften bzw. lösen. */
    fun togglePin(conversation: Conversation) {
        viewModelScope.launch {
            val pinned = !conversation.pinned
            app.chatStore.setPinned(conversation, pinned)
            _state.update { st ->
                val cur = st.current
                if (cur != null && cur.id == conversation.id) st.copy(current = cur.copy(pinned = pinned)) else st
            }
            reload()
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    fun send(text: String) {
        val images = _state.value.attachedImages
        if ((text.isBlank() && images.isEmpty()) || _state.value.streaming) return

        job = viewModelScope.launch {
            val settings = app.settings.current()
            if (!settings.hasKey) {
                _state.update {
                    it.copy(error = "Bitte zuerst deinen Mistral-API-Schlüssel unter „Einstellungen“ eintragen.")
                }
                return@launch
            }
            val model = if (mode == ChatMode.CODE) settings.codeModel else settings.chatModel
            val userMessage = ChatMessage(
                role = Role.USER,
                text = text.trim().ifEmpty {
                    if (images.size > 1) "Beschreibe diese ${images.size} Bilder und was sie verbindet." else "Beschreibe dieses Bild."
                },
                imagePath = images.firstOrNull(),
                imagePaths = if (images.size > 1) images else emptyList(),
            )
            val assistant = ChatMessage(role = Role.ASSISTANT, text = "")
            val base = _state.value.current ?: Conversation(
                title = makeTitle(userMessage.text),
                mode = mode,
                model = model,
                projectId = _state.value.project?.id,
            )
            val history = base.messages + userMessage
            var conversation = base.copy(
                messages = history + assistant,
                model = model,
                updatedAt = System.currentTimeMillis(),
            )
            _state.update { it.copy(current = conversation, attachedImages = emptyList(), streaming = true, error = null) }

            val answer = StringBuilder()
            var failed: String? = null
            var lastUiUpdate = 0L
            try {
                app.mistral.streamChat(
                    apiKey = settings.apiKey,
                    model = model,
                    messages = buildApiMessages(history, app.projects.byId(base.projectId)),
                    temperature = if (mode == ChatMode.CODE) 0.2 else 0.7,
                ).collect { delta ->
                    answer.append(delta)
                    // Anzeige höchstens ~20× pro Sekunde aktualisieren → flüssiges Scrollen auch bei langen Antworten.
                    val now = System.currentTimeMillis()
                    if (now - lastUiUpdate < 50) return@collect
                    lastUiUpdate = now
                    val current = answer.toString()
                    _state.update { s ->
                        val conv = s.current ?: return@update s
                        if (conv.id != conversation.id) return@update s
                        s.copy(current = conv.copy(messages = conv.messages.map {
                            if (it.id == assistant.id) it.copy(text = current) else it
                        }))
                    }
                }
            } catch (e: CancellationException) {
                if (answer.isEmpty()) answer.append("_(abgebrochen)_")
            } catch (e: Exception) {
                failed = e.report("Chat")
            } finally {
                withContext(NonCancellable) {
                    val finalText = buildString {
                        append(answer)
                        if (failed != null) {
                            if (isNotEmpty()) append("\n\n")
                            append("⚠️ ").append(failed)
                        }
                    }
                    conversation = conversation.copy(
                        messages = conversation.messages.map {
                            if (it.id == assistant.id) it.copy(text = finalText, isError = failed != null) else it
                        },
                        updatedAt = System.currentTimeMillis(),
                    )
                    app.chatStore.save(conversation)
                    _state.update { s ->
                        s.copy(
                            // Anheft-Status aus der Anzeige übernehmen – er kann während der Antwort geändert worden sein.
                            current = if (s.current == null || s.current.id == conversation.id) {
                                conversation.copy(pinned = s.current?.pinned ?: conversation.pinned)
                            } else s.current,
                            streaming = false,
                        )
                    }
                    reload()
                    if (failed == null && settings.autoSpeak && mode == ChatMode.CHAT) {
                        app.tts.speak(assistant.id, finalText)
                    }
                }
            }
        }
    }

    private fun buildApiMessages(history: List<ChatMessage>, project: eu.kigenc.app.data.Project? = null): List<ApiMessage> {
        val today = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", Locale.GERMAN))
        val system = when (mode) {
            ChatMode.CHAT ->
                "Du bist KI-GenC, ein freundlicher, kompetenter KI-Assistent aus Europa. " +
                    "Antworte standardmäßig auf Deutsch, außer der Nutzer schreibt in einer anderen Sprache. " +
                    "Sei präzise und hilfreich, nutze Markdown (Überschriften, Listen, Codeblöcke) wo es die Lesbarkeit verbessert. " +
                    "Wenn du etwas nicht weißt, sag es ehrlich. Heute ist $today."
            ChatMode.CODE ->
                "Du bist KI-GenC Code, ein erfahrener Softwareentwickler. Schreibe sauberen, modernen, " +
                    "gut kommentierten Code und gib ihn immer in Markdown-Codeblöcken mit Sprachangabe aus. " +
                    "Erkläre kurz auf Deutsch, was der Code tut, und weise auf Sicherheits- oder Performance-Fallen hin. " +
                    "Heute ist $today."
        }
        val recent = history.filterNot { it.isError && it.role == Role.ASSISTANT }.takeLast(40)
        // Nur die Bilder der letzten Bild-Nachrichten mitsenden (höchstens 8), um die Anfrage klein zu halten.
        // Neueste zuerst bedienen, damit die aktuelle Nachricht alle ihre Bilder behält.
        val imageCount = mutableMapOf<String, Int>()
        var left = MAX_API_IMAGES
        recent.filter { it.allImages.isNotEmpty() }.takeLast(3).asReversed().forEach { m ->
            val n = minOf(left, m.allImages.size)
            imageCount[m.id] = n
            left -= n
        }
        return buildList {
            add(ApiMessage("system", system + (project?.systemText() ?: "")))
            recent.forEach { m ->
                if (m.role == Role.ASSISTANT && m.text.isBlank()) return@forEach
                val urls = m.allImages.take(imageCount[m.id] ?: 0).mapNotNull(ImageUtils::toDataUrl)
                add(
                    ApiMessage(
                        role = if (m.role == Role.USER) "user" else "assistant",
                        // Bei mehreren Bildern dem Modell sagen, dass sie zusammengehören
                        text = if (urls.size > 1) "${m.text}\n\n(${urls.size} Bilder angehängt – Bild 1 bis ${urls.size}, gemeinsam betrachten.)" else m.text,
                        extraImages = urls,
                    )
                )
            }
        }
    }

    private fun makeTitle(text: String): String {
        val t = text.lineSequence().firstOrNull().orEmpty().trim()
        return if (t.length > 48) t.take(45).trimEnd() + "…" else t.ifEmpty { "Neue Unterhaltung" }
    }
}
