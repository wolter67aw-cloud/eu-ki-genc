package eu.kigenc.app.ui.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.kigenc.app.KiApp
import kotlinx.coroutines.flow.MutableStateFlow

class ResearchViewModel(app: KiApp) : AiToolViewModel(app) {
    val sources = MutableStateFlow<List<Pair<String, String>>>(emptyList())

    fun search(question: String) {
        if (question.isBlank()) return
        launchAi { s ->
            output.value = ""
            sources.value = emptyList()
            val result = app.mistral.webSearch(s.apiKey, s.chatModel, question)
            output.value = result.text.ifBlank { "Keine Antwort erhalten." }
            sources.value = result.sources
            if (result.text.isNotBlank()) {
                val withSources = result.text + if (result.sources.isEmpty()) "" else
                    "\n\n**Quellen:**\n" + result.sources.mapIndexed { i, (t, u) -> "${i + 1}. [$t]($u)" }.joinToString("\n")
                saveResult("Recherche", question, withSources)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ResearchScreen(vm: ResearchViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val output by vm.output.collectAsStateWithLifecycle()
    val sources by vm.sources.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val uri = LocalUriHandler.current
    var question by rememberSaveable { mutableStateOf("") }

    ToolScaffold("Recherche", onBack, modifier, message, vm::clearMessage) { inner ->
        LazyColumn(contentPadding = toolPadding(inner), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
            item {
                Text(
                    "Aktuelle Informationen aus dem Internet – mit Quellenangaben. Nutzt die Websuche von Mistral AI.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                OutlinedTextField(
                    value = question, onValueChange = { question = it },
                    placeholder = { Text("Was möchtest du wissen?") },
                    minLines = 2, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(
                        "Was gibt es Neues zum EU AI Act?",
                        "Wie wird das Wetter am Wochenende in Berlin?",
                        "Aktuelle Spritpreise in Deutschland",
                        "Vergleiche die neuesten Mittelklasse-Smartphones",
                    ).forEach { q -> AssistChip(onClick = { question = q }, label = { Text(q) }) }
                }
            }
            item {
                Button(onClick = { vm.search(question) }, enabled = !busy && question.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                    Text(if (busy) "Recherchiere …" else "Recherchieren")
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            if (output.isNotEmpty()) item { ResultCard(output, busy, "research") }
            if (sources.isNotEmpty()) {
                item { Text("Quellen", style = MaterialTheme.typography.titleSmall) }
                itemsIndexed(sources) { i, (title, url) ->
                    Text(
                        "${i + 1}. $title",
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { runCatching { uri.openUri(url) } }
                            .padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}
