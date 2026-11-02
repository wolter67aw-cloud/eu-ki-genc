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

data class ChatUiState(
    val conversations: List<Conversation> = emptyList(),
    val current: Conversation? = null,
    val streaming: Boolean = false,
    val attachedImage: String? = null,
    val error: String? = null,
)

class ChatViewModel(private val app: KiApp, val mode: ChatMode) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()
    private var job: Job? = null

    init {
        viewModelScope.launch { reload(selectFirst = true) }
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
        _state.update { it.copy(current = null, attachedImage = null, error = null) }
    }

    fun open(conversation: Conversation) {
        stop()
        _state.update { it.copy(current = conversation, error = null) }
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

    fun attachImage(uri: Uri) {
        viewModelScope.launch {
            val path = runCatching { withContext(Dispatchers.IO) { ImageUtils.importImage(app, uri) } }
            path.onSuccess { p -> _state.update { it.copy(attachedImage = p) } }
                .onFailure { e -> _state.update { it.copy(error = e.message ?: "Bild konnte nicht geladen werden.") } }
        }
    }

    fun removeImage() = _state.update { it.copy(attachedImage = null) }

    fun stop() {
        job?.cancel()
        job = null
    }

    fun send(text: String) {
        val image = _state.value.attachedImage
        if ((text.isBlank() && image == null) || _state.value.streaming) return

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
                text = text.trim().ifEmpty { "Beschreibe dieses Bild." },
                imagePath = image,
            )
            val assistant = ChatMessage(role = Role.ASSISTANT, text = "")
            val base = _state.value.current ?: Conversation(
                title = makeTitle(userMessage.text),
                mode = mode,
                model = model,
            )
            val history = base.messages + userMessage
            var conversation = base.copy(
                messages = history + assistant,
                model = model,
                updatedAt = System.currentTimeMillis(),
            )
            _state.update { it.copy(current = conversation, attachedImage = null, streaming = true, error = null) }

            val answer = StringBuilder()
            var failed: String? = null
            try {
                app.mistral.streamChat(
                    apiKey = settings.apiKey,
                    model = model,
                    messages = buildApiMessages(history),
                    temperature = if (mode == ChatMode.CODE) 0.2 else 0.7,
                ).collect { delta ->
                    answer.append(delta)
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
                failed = e.friendlyMessage()
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
                            current = if (s.current == null || s.current.id == conversation.id) conversation else s.current,
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

    private fun buildApiMessages(history: List<ChatMessage>): List<ApiMessage> {
        val today = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", Locale.GERMAN))
        val system = when (mode) {
            ChatMode.CHAT ->
                "Du bist EU KI-GenC, ein freundlicher, kompetenter KI-Assistent aus Europa. " +
                    "Antworte standardmäßig auf Deutsch, außer der Nutzer schreibt in einer anderen Sprache. " +
                    "Sei präzise und hilfreich, nutze Markdown (Überschriften, Listen, Codeblöcke) wo es die Lesbarkeit verbessert. " +
                    "Wenn du etwas nicht weißt, sag es ehrlich. Heute ist $today."
            ChatMode.CODE ->
                "Du bist EU KI-GenC Code, ein erfahrener Softwareentwickler. Schreibe sauberen, modernen, " +
                    "gut kommentierten Code und gib ihn immer in Markdown-Codeblöcken mit Sprachangabe aus. " +
                    "Erkläre kurz auf Deutsch, was der Code tut, und weise auf Sicherheits- oder Performance-Fallen hin. " +
                    "Heute ist $today."
        }
        val recent = history.filterNot { it.isError && it.role == Role.ASSISTANT }.takeLast(40)
        // Nur die letzten Bilder mitsenden, um die Anfrage klein zu halten.
        val imageIds = recent.filter { it.imagePath != null }.takeLast(3).map { it.id }.toSet()
        return buildList {
            add(ApiMessage("system", system))
            recent.forEach { m ->
                if (m.role == Role.ASSISTANT && m.text.isBlank()) return@forEach
                add(
                    ApiMessage(
                        role = if (m.role == Role.USER) "user" else "assistant",
                        text = m.text,
                        imageDataUrl = if (m.id in imageIds) m.imagePath?.let(ImageUtils::toDataUrl) else null,
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
