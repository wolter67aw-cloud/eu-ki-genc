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
    /** Kurzer Hinweis (z. B. „Gemerkt“). */
    val notice: String? = null,
    /** Websuche für die nächsten Fragen (Antwort mit aktuellen Quellen). */
    val webSearch: Boolean = false,
    /** Code ausführen: die KI rechnet mit echtem Python-Code (Mistral Code-Interpreter). */
    val codeRun: Boolean = false,
    /** Gründlich nachdenken: Reasoning-Modell (Magistral) statt des normalen Chat-Modells. */
    val thinking: Boolean = false,
)

class ChatViewModel(private val app: KiApp, val mode: ChatMode) : ViewModel() {

    companion object {
        /** Höchstzahl Bilder pro Nachricht. */
        const val MAX_IMAGES = 6
        /** Höchstzahl Bilder pro Anfrage an die KI (über den ganzen Verlauf). */
        private const val MAX_API_IMAGES = 8
        /** Reasoning-Modell für „Gründlich nachdenken“. */
        const val THINKING_MODEL = "magistral-medium-latest"
        /** Modell für den Code-Interpreter, falls das gewählte es nicht unterstützt. */
        private const val CODE_RUN_FALLBACK = "mistral-medium-latest"
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
    fun clearNotice() = _state.update { it.copy(notice = null) }

    fun toggleWebSearch() = _state.update {
        val on = !it.webSearch
        it.copy(webSearch = on, codeRun = if (on) false else it.codeRun, notice = if (on) "Websuche an – Antworten mit aktuellen Quellen" else "Websuche aus")
    }

    fun toggleThinking() = _state.update {
        val on = !it.thinking
        it.copy(thinking = on, notice = if (on) "Gründlich nachdenken an – Antworten dauern etwas länger" else "Gründlich nachdenken aus")
    }

    fun setAnswerStyle(id: String) {
        viewModelScope.launch { app.settings.setAnswerStyle(id) }
        val name = eu.kigenc.app.data.ANSWER_STYLES.firstOrNull { it.first == id }?.second ?: "Normal"
        _state.update { it.copy(notice = "Antwortstil: $name") }
    }

    fun toggleCodeRun() = _state.update {
        val on = !it.codeRun
        it.copy(codeRun = on, webSearch = if (on) false else it.webSearch,
            notice = if (on) "Code ausführen an – die KI rechnet mit echtem Python-Code (auch Diagramme)" else "Code ausführen aus")
    }

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
            val model = when {
                mode == ChatMode.CODE -> settings.codeModel
                _state.value.thinking && !_state.value.codeRun && !_state.value.webSearch -> THINKING_MODEL
                else -> settings.chatModel
            }
            val style = if (mode == ChatMode.CHAT) eu.kigenc.app.data.answerStyleText(settings.answerStyle) else ""
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
            /** Vom Code-Interpreter erzeugte Diagramme (Dateipfade). */
            var answerImages: List<String> = emptyList()
            var failed: String? = null
            var lastUiUpdate = 0L
            try {
                val project = app.projects.byId(base.projectId)
                if (_state.value.codeRun && mode == ChatMode.CHAT && images.isEmpty()) {
                    // Code-Interpreter: echte Berechnung in Python, Ergebnis + Code + Diagramme
                    val memoryText = if (settings.memoryEnabled) app.memory.systemText() else ""
                    val instructions = "Du bist KI-GenC. Antworte auf Deutsch in Markdown. Nutze für Berechnungen, Statistik, " +
                        "Datenanalyse und Diagramme IMMER das Werkzeug code_interpreter (Python) und erkläre das Ergebnis verständlich. " +
                        "Diagramme mit matplotlib, Beschriftungen auf Deutsch." + style + memoryText + (project?.systemText(8_000) ?: "")
                    val question = webQuestion(history, "")
                    val run = try {
                        app.mistral.runCode(settings.apiKey, model, instructions, question)
                    } catch (e: eu.kigenc.app.ai.MistralException) {
                        if (e.code == 401 || e.code == 429 || model == CODE_RUN_FALLBACK) throw e
                        app.mistral.runCode(settings.apiKey, CODE_RUN_FALLBACK, instructions, question)
                    }
                    answer.append(run.markdown())
                    answerImages = withContext(Dispatchers.IO) { run.images.map { ImageUtils.saveImageBytes(app, it) } }
                } else if (_state.value.webSearch && mode == ChatMode.CHAT && images.isEmpty()) {
                    // Websuche: Mistral sucht selbst im Web und liefert Quellen mit
                    val memoryText = if (settings.memoryEnabled) app.memory.systemText() else ""
                    val r = app.mistral.webSearch(settings.apiKey, model, webQuestion(history, style + memoryText + (project?.systemText(8_000) ?: "")))
                    answer.append(r.text.ifBlank { "(keine Antwort)" })
                    if (r.sources.isNotEmpty()) {
                        answer.append("\n\n**Quellen:**\n")
                        answer.append(r.sources.mapIndexed { i, (title, url) -> "${i + 1}. [${title.replace("[", "").replace("]", "")}]($url)" }.joinToString("\n"))
                    }
                } else app.mistral.streamChat(
                    apiKey = settings.apiKey,
                    model = model,
                    messages = buildApiMessages(
                        history, project,
                        memory = style + if (settings.memoryEnabled) app.memory.systemText() else "",
                    ),
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
                            if (it.id == assistant.id) it.copy(
                                text = finalText, isError = failed != null,
                                imagePath = answerImages.firstOrNull(),
                                imagePaths = if (answerImages.size > 1) answerImages else emptyList(),
                            ) else it
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
                    if (failed == null && settings.memoryEnabled && mode == ChatMode.CHAT) learn(settings.apiKey, userMessage.text, settings.memoryAuto)
                }
            }
        }
    }

    private fun buildApiMessages(history: List<ChatMessage>, project: eu.kigenc.app.data.Project? = null, memory: String = ""): List<ApiMessage> {
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
        // Bilder nur aus eigenen Nachrichten mitsenden (Diagramme der KI nicht zurückschicken)
        recent.filter { it.role == Role.USER && it.allImages.isNotEmpty() }.takeLast(3).asReversed().forEach { m ->
            val n = minOf(left, m.allImages.size)
            imageCount[m.id] = n
            left -= n
        }
        return buildList {
            add(ApiMessage("system", system + memory + (project?.systemText() ?: "")))
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

    /** Frage für die Websuche: Hinweise + die letzten Nachrichten als Zusammenhang + aktuelle Frage. */
    private fun webQuestion(history: List<ChatMessage>, extras: String): String = buildString {
        val today = LocalDate.now().format(DateTimeFormatter.ofPattern("d. MMMM yyyy", Locale.GERMAN))
        append("Heute ist $today.")
        if (extras.isNotBlank()) append(extras)
        val previous = history.dropLast(1).filterNot { it.isError }.takeLast(8)
        if (previous.isNotEmpty()) {
            append("\n\nBisheriger Gesprächsverlauf (nur als Zusammenhang):\n")
            previous.forEach { m -> append(if (m.role == Role.USER) "Nutzer: " else "KI: ").append(m.text.take(1500)).append("\n") }
        }
        append("\n\nAktuelle Frage des Nutzers:\n").append(history.last().text)
    }

    /** Gedächtnis pflegen: „Merk dir …“ immer, sonst (falls eingeschaltet) automatisch dazulernen. */
    private fun learn(apiKey: String, text: String, auto: Boolean) {
        val explicit = app.memory.explicitFact(text)
        if (explicit == null && !auto) return
        app.memoryScope.launch {
            val added = app.memory.learnFrom(app.mistral, apiKey, text, explicit)
            if (added > 0) {
                val last = app.memory.facts.value.lastOrNull()?.text.orEmpty()
                _state.update { it.copy(notice = if (added == 1) "Gemerkt: $last" else "$added Dinge gemerkt (Optionen → Gedächtnis)") }
            }
        }
    }

    private fun makeTitle(text: String): String {
        val t = text.lineSequence().firstOrNull().orEmpty().trim()
        return if (t.length > 48) t.take(45).trimEnd() + "…" else t.ifEmpty { "Neue Unterhaltung" }
    }
}
