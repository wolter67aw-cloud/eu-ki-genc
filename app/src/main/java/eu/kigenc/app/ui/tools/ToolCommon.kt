@file:OptIn(ExperimentalMaterial3Api::class)

package eu.kigenc.app.ui.tools

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import eu.kigenc.app.KiApp
import eu.kigenc.app.ai.ApiMessage
import eu.kigenc.app.ai.friendlyMessage
import eu.kigenc.app.app
import eu.kigenc.app.data.AppSettings
import eu.kigenc.app.data.ResultStore
import eu.kigenc.app.ui.components.MarkdownText
import eu.kigenc.app.ui.components.ReportDialog
import eu.kigenc.app.ui.components.rememberCopyToClipboard
import eu.kigenc.app.ui.components.shareText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import eu.kigenc.app.data.report

/** Basis für alle KI-Werkzeuge: Statusanzeige, Fehlermeldungen und gestreamte Antworten. */
open class AiToolViewModel(protected val app: KiApp) : ViewModel() {
    val output = MutableStateFlow("")
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    private var job: Job? = null

    protected suspend fun settingsOrNull(): AppSettings? {
        val s = app.settings.current()
        if (!s.hasKey) {
            message.value = "Bitte zuerst deinen Mistral-API-Schlüssel unter „Einstellungen“ eintragen."
            return null
        }
        return s
    }

    /** Streamt eine Antwort in [output]. */
    fun stream(
        system: String,
        user: ApiMessage,
        temperature: Double = 0.4,
        model: ((AppSettings) -> String)? = null,
        saveAs: String? = null,
    ) {
        if (busy.value) return
        job = viewModelScope.launch {
            val s = settingsOrNull() ?: return@launch
            busy.value = true
            output.value = ""
            val sb = StringBuilder()
            var lastUi = 0L
            try {
                app.mistral.streamChat(
                    s.apiKey, model?.invoke(s) ?: s.chatModel,
                    listOf(ApiMessage("system", system), user), temperature
                ).collect {
                    sb.append(it)
                    val now = System.currentTimeMillis()
                    if (now - lastUi >= 50) {
                        lastUi = now
                        output.value = sb.toString()
                    }
                }
                output.value = sb.toString()
                if (saveAs != null) saveResult(saveAs, user.text, sb.toString())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message.value = e.report(javaClass.simpleName)
            } finally {
                busy.value = false
            }
        }
    }

    /** Führt eine KI-Aufgabe mit Fortschrittsanzeige und Fehlerbehandlung aus. */
    protected fun launchAi(block: suspend (AppSettings) -> Unit) {
        if (busy.value) return
        job = viewModelScope.launch {
            val s = settingsOrNull() ?: return@launch
            busy.value = true
            try {
                block(s)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message.value = e.report(javaClass.simpleName)
            } finally {
                busy.value = false
            }
        }
    }

    /** Ergebnis dauerhaft unter „Werkzeuge → Gespeicherte Ergebnisse“ ablegen. */
    protected suspend fun saveResult(tool: String, title: String, text: String) {
        runCatching { ResultStore.add(app, tool, title, text) }
    }

    fun stop() {
        job?.cancel()
    }

    fun clearMessage() {
        message.value = null
    }
}

@Composable
fun ToolScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    onMessageShown: () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            onMessageShown()
        }
    }
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        },
        content = content,
    )
}

/** Karte mit einer KI-Antwort und Aktionen (Kopieren, Vorlesen, Teilen). */
@Composable
fun ResultCard(text: String, busy: Boolean, speakId: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val tts = context.app.tts
    val speaking by tts.speakingId.collectAsStateWithLifecycle()
    val copy = rememberCopyToClipboard()
    var reporting by remember { mutableStateOf(false) }
    if (reporting) ReportDialog(contentExcerpt = text, kind = "KI-Ergebnis", onDismiss = { reporting = false })
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            if (text.isEmpty() && busy) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(10.dp))
                    Text("Arbeite daran …", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                MarkdownText(text, onCopyCode = copy)
            }
            if (!busy && text.isNotEmpty()) {
                Row {
                    IconButton(onClick = { copy(text) }) { Icon(Icons.Outlined.ContentCopy, "Kopieren", Modifier.size(20.dp)) }
                    IconButton(onClick = { if (speaking == speakId) tts.stop() else tts.speak(speakId, text) }) {
                        Icon(
                            if (speaking == speakId) Icons.Outlined.StopCircle else Icons.Outlined.RecordVoiceOver,
                            "Vorlesen", Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = { context.shareText(text) }) { Icon(Icons.Outlined.Share, "Teilen", Modifier.size(20.dp)) }
                    IconButton(onClick = { reporting = true }) { Icon(Icons.Outlined.Flag, "Inhalt melden", Modifier.size(20.dp)) }
                }
            }
        }
    }
}

/** Standard-Abstände für scrollbare Werkzeug-Inhalte. */
fun toolPadding(inner: PaddingValues) = PaddingValues(
    start = 16.dp, end = 16.dp,
    top = inner.calculateTopPadding() + 8.dp,
    bottom = inner.calculateBottomPadding() + 24.dp
)
