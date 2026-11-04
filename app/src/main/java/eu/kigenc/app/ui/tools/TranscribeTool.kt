package eu.kigenc.app.ui.tools

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import eu.kigenc.app.KiApp
import eu.kigenc.app.ai.ApiMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class TranscribeViewModel(app: KiApp) : AiToolViewModel(app) {
    val recording = MutableStateFlow(false)
    val elapsedSec = MutableStateFlow(0)
    val audioFile = MutableStateFlow<File?>(null)
    val transcript = MutableStateFlow("")
    private var mime = "audio/mp4"
    private var recorder: MediaRecorder? = null
    private var pendingFile: File? = null
    private var timer: Job? = null

    private fun audioDir() = File(app.cacheDir, "audio").apply { mkdirs() }

    fun startRecording() {
        if (recording.value) return
        val file = File(audioDir(), "aufnahme_${System.currentTimeMillis()}.m4a")
        try {
            val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(app) else @Suppress("DEPRECATION") MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioSamplingRate(44_100)
            r.setAudioEncodingBitRate(96_000)
            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            pendingFile = file
            recording.value = true
            elapsedSec.value = 0
            timer = viewModelScope.launch {
                while (true) {
                    delay(1000)
                    elapsedSec.value++
                }
            }
        } catch (e: Exception) {
            message.value = "Aufnahme nicht möglich: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    fun stopRecording() {
        timer?.cancel()
        val ok = runCatching { recorder?.stop() }.isSuccess
        recorder?.release()
        recorder = null
        recording.value = false
        if (ok) {
            audioFile.value = pendingFile
            mime = "audio/mp4"
            transcript.value = ""
            output.value = ""
        } else {
            message.value = "Aufnahme zu kurz."
        }
    }

    fun pick(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val resolver = app.contentResolver
                    val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                        if (c.moveToFirst()) c.getString(0) else null
                    } ?: "audio"
                    val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
                    val target = File(audioDir(), safe)
                    resolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } }
                    mime = resolver.getType(uri) ?: "audio/mpeg"
                    target
                }
            }.onSuccess {
                audioFile.value = it
                transcript.value = ""
                output.value = ""
            }.onFailure { message.value = "Datei konnte nicht gelesen werden." }
        }
    }

    fun transcribe(language: String?) {
        val file = audioFile.value ?: return
        if (file.length() > 25L * 1024 * 1024) {
            message.value = "Die Datei ist größer als 25 MB. Bitte eine kürzere Aufnahme wählen."
            return
        }
        launchAi { s ->
            val text = app.mistral.transcribe(s.apiKey, withContext(Dispatchers.IO) { file.readBytes() }, file.name, mime, language)
            transcript.value = text.ifBlank { "(Kein Text erkannt.)" }
            if (text.isNotBlank()) saveResult("Transkription", "Transkript: ${file.name}", text)
        }
    }

    fun followUp(instruction: String) {
        val t = transcript.value
        if (t.isBlank()) return
        stream(
            "Du verarbeitest Transkripte gesprochener Sprache. Antworte auf Deutsch in Markdown.",
            ApiMessage("user", "$instruction\n\nTranskript:\n$t"),
            saveAs = "Transkription"
        )
    }

    override fun onCleared() {
        recorder?.release()
        super.onCleared()
    }
}

private val LANGUAGES = listOf(null to "Automatisch", "de" to "Deutsch", "en" to "Englisch", "fr" to "Französisch", "es" to "Spanisch", "it" to "Italienisch", "pl" to "Polnisch", "nl" to "Niederländisch")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TranscribeScreen(vm: TranscribeViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val recording by vm.recording.collectAsStateWithLifecycle()
    val elapsed by vm.elapsedSec.collectAsStateWithLifecycle()
    val file by vm.audioFile.collectAsStateWithLifecycle()
    val transcript by vm.transcript.collectAsStateWithLifecycle()
    val output by vm.output.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var language by rememberSaveable { mutableStateOf<String?>(null) }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            vm.startRecording()
        } else {
            vm.message.value = "Ohne Mikrofon-Berechtigung ist keine Aufnahme möglich."
        }
    }
    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::pick) }

    ToolScaffold("Audio-Transkription", onBack, modifier, message, vm::clearMessage) { inner ->
        LazyColumn(contentPadding = toolPadding(inner), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
            item {
                Text(
                    "Wandle Sprache in Text um – per Aufnahme oder aus einer Audiodatei (z. B. Sprachnachricht, Meeting). Nutzt Voxtral von Mistral.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (recording) {
                        Button(
                            onClick = vm::stopRecording,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Stop, null); Spacer(Modifier.size(6.dp))
                            Text("Stopp  %d:%02d".format(elapsed / 60, elapsed % 60))
                        }
                    } else {
                        Button(onClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                vm.startRecording()
                            } else {
                                micPermission.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Mic, null); Spacer(Modifier.size(6.dp)); Text("Aufnehmen")
                        }
                    }
                    OutlinedButton(onClick = { pickAudio.launch(arrayOf("audio/*")) }, enabled = !recording, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.AudioFile, null); Spacer(Modifier.size(6.dp)); Text("Datei wählen")
                    }
                }
            }
            file?.let { f ->
                item {
                    Text("Audio: ${f.name} (${f.length() / 1024} KB)", style = MaterialTheme.typography.labelLarge)
                }
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LANGUAGES.forEach { (code, label) ->
                            FilterChip(selected = language == code, onClick = { language = code }, label = { Text(label) })
                        }
                    }
                }
                item {
                    Button(onClick = { vm.transcribe(language) }, enabled = !busy && !recording, modifier = Modifier.fillMaxWidth()) {
                        Text(if (busy && transcript.isBlank()) "Transkribiere …" else "In Text umwandeln")
                    }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
            if (transcript.isNotBlank()) {
                item { ResultCard(transcript, false, "transcript") }
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        AssistChip(onClick = { vm.followUp("Fasse das Transkript kurz und übersichtlich zusammen.") }, label = { Text("Zusammenfassen") })
                        AssistChip(onClick = { vm.followUp("Erstelle daraus ein Protokoll mit Teilnehmern (falls erkennbar), Themen, Beschlüssen und offenen Punkten.") }, label = { Text("Protokoll") })
                        AssistChip(onClick = { vm.followUp("Extrahiere alle Aufgaben und To-dos als Checkliste, mit Zuständigen und Terminen, falls genannt.") }, label = { Text("Aufgaben finden") })
                        AssistChip(onClick = { vm.followUp("Übersetze das Transkript ins Englische.") }, label = { Text("Ins Englische") })
                    }
                }
            }
            if (output.isNotEmpty()) item { ResultCard(output, busy, "transcript-followup") }
        }
    }
}
