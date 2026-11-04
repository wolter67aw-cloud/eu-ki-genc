package eu.kigenc.app.ui.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import eu.kigenc.app.KiApp
import eu.kigenc.app.data.ResultStore
import eu.kigenc.app.data.SavedResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class ResultsViewModel(app: KiApp) : AiToolViewModel(app) {
    val results = MutableStateFlow<List<SavedResult>>(emptyList())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { results.value = ResultStore.list(app) }
    }

    fun delete(r: SavedResult) {
        viewModelScope.launch {
            ResultStore.delete(app, r.id)
            results.value = ResultStore.list(app)
        }
    }
}

@Composable
fun ResultsScreen(vm: ResultsViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val results by vm.results.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf<SavedResult?>(null) }
    val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }

    val current = open
    ToolScaffold(if (current == null) "Gespeicherte Ergebnisse" else current.tool, { if (open != null) { open = null } else { onBack() } }, modifier) { inner ->
        LazyColumn(contentPadding = toolPadding(inner), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            if (current != null) {
                item { Text(current.title, style = MaterialTheme.typography.titleMedium) }
                item { Text(fmt.format(Date(current.createdAt)), style = MaterialTheme.typography.labelSmall) }
                item { ResultCard(current.text, false, "saved-${current.id}") }
            } else {
                if (results.isEmpty()) {
                    item {
                        Text(
                            "Noch nichts gespeichert. Ergebnisse von Recherche, Transkription, Daten-Analyse, " +
                                "Video-Analyse und Tagesplan landen automatisch hier.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(results, key = { it.id }) { r ->
                    ListItem(
                        overlineContent = { Text("${r.tool} · ${fmt.format(Date(r.createdAt))}") },
                        headlineContent = { Text(r.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(r.text.take(120), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        trailingContent = {
                            IconButton(onClick = { vm.delete(r) }) { Icon(Icons.Outlined.Delete, "Löschen") }
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.clickable { open = r }
                    )
                }
            }
        }
    }
}
