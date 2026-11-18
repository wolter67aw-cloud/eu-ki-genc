@file:OptIn(ExperimentalMaterial3Api::class)

package eu.kigenc.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.kigenc.app.app
import eu.kigenc.app.data.MemoryFact
import kotlinx.coroutines.launch

/** Gedächtnis ansehen, bearbeiten, ergänzen und löschen. */
@Composable
fun MemoryDialog(onDismiss: () -> Unit) {
    val app = LocalContext.current.app
    val facts by app.memory.facts.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<MemoryFact?>(null) }
    var adding by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Gedächtnis") },
                    navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Schließen") } },
                    actions = {
                        if (facts.isNotEmpty()) TextButton(onClick = { confirmClear = true }) { Text("Alles vergessen") }
                    }
                )
            },
            modifier = Modifier.fillMaxSize().imePadding()
        ) { inner ->
            LazyColumn(
                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = inner.calculateTopPadding() + 8.dp, bottom = inner.calculateBottomPadding() + 24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item {
                    Text(
                        "Das weiß die KI über dich. Es liegt nur auf diesem Gerät und wird bei jeder Chat-Anfrage an Mistral mitgeschickt. " +
                            "Im Chat kannst du sagen „Merk dir, dass …“.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (facts.isEmpty()) {
                    item { Text("Noch nichts gemerkt.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                items(facts, key = { it.id }) { f ->
                    ListItem(
                        headlineContent = { Text(f.text) },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { editing = f; text = f.text }) { Icon(Icons.Outlined.Edit, "„${f.text}“ bearbeiten") }
                                IconButton(onClick = { scope.launch { app.memory.delete(f.id) } }) { Icon(Icons.Outlined.Delete, "„${f.text}“ vergessen") }
                            }
                        }
                    )
                }
                item {
                    OutlinedButton(onClick = { adding = true; text = "" }) {
                        Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text("Selbst hinzufügen")
                    }
                }
            }
        }
    }

    if (adding || editing != null) {
        AlertDialog(
            onDismissRequest = { adding = false; editing = null },
            title = { Text(if (adding) "Merken" else "Bearbeiten") },
            text = {
                OutlinedTextField(
                    text, { text = it.take(200) },
                    placeholder = { Text("z. B. „Heißt Andy und wohnt in Köln“") },
                    minLines = 2, maxLines = 5
                )
            },
            confirmButton = {
                TextButton(enabled = text.isNotBlank(), onClick = {
                    val e = editing
                    scope.launch { if (e != null) app.memory.update(e.id, text) else app.memory.add(text) }
                    adding = false; editing = null
                }) { Text("Speichern") }
            },
            dismissButton = { TextButton(onClick = { adding = false; editing = null }) { Text("Abbrechen") } }
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Alles vergessen?") },
            text = { Text("Alle ${facts.size} gemerkten Einträge werden gelöscht.") },
            confirmButton = { TextButton(onClick = { scope.launch { app.memory.clear() }; confirmClear = false }) { Text("Vergessen") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Abbrechen") } }
        )
    }
}
