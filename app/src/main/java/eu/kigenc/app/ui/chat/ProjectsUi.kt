@file:OptIn(ExperimentalMaterial3Api::class)

package eu.kigenc.app.ui.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.kigenc.app.KiApp
import eu.kigenc.app.ai.ApiMessage
import eu.kigenc.app.ai.MistralClient
import eu.kigenc.app.ai.MistralException
import eu.kigenc.app.app
import eu.kigenc.app.data.Attachments
import eu.kigenc.app.data.Project
import eu.kigenc.app.data.ProjectFile
import eu.kigenc.app.data.report
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Auswahl „Projekt: … ▾“ in der leeren Chat-Ansicht. */
@Composable
fun ProjectChooser(current: Project?, onSelect: (Project?) -> Unit, onManage: () -> Unit, modifier: Modifier = Modifier) {
    val projects by LocalContext.current.app.projects.projects.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        AssistChip(
            onClick = { open = true },
            label = { Text(current?.let { "Projekt: ${it.name}" } ?: "Ohne Projekt", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { Icon(Icons.Outlined.Folder, null, Modifier.size(18.dp)) },
            trailingIcon = { Icon(Icons.Outlined.ArrowDropDown, "Projekt wählen", Modifier.size(18.dp)) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Ohne Projekt") }, onClick = { open = false; onSelect(null) })
            projects.forEach { p ->
                DropdownMenuItem(
                    text = { Text(p.name, color = if (p.id == current?.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) },
                    leadingIcon = { Icon(Icons.Outlined.Folder, null) },
                    onClick = { open = false; onSelect(p) }
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(if (projects.isEmpty()) "Projekt anlegen …" else "Projekte verwalten …") },
                leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                onClick = { open = false; onManage() }
            )
        }
    }
}

/** Liste aller Projekte mit Anlegen, Bearbeiten und Löschen. */
@Composable
fun ProjectsSheet(onDismiss: () -> Unit, onChanged: () -> Unit, onUse: (Project) -> Unit) {
    val app = LocalContext.current.app
    val projects by app.projects.projects.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<Project?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Project?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Projekte", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Button(onClick = { creating = true }) {
                    Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text("Neu")
                }
            }
            Text(
                "Ein Projekt hat feste Anweisungen und Wissen (Dateien, Notizen). Jeder Chat im Projekt kennt beides automatisch.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            if (projects.isEmpty()) {
                Text("Noch keine Projekte.", modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LazyColumn {
                items(projects, key = { it.id }) { p ->
                    ListItem(
                        headlineContent = { Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Text(
                                buildString {
                                    append(if (p.files.size == 1) "1 Wissens-Eintrag" else "${p.files.size} Wissens-Einträge")
                                    if (p.instructions.isNotBlank()) append(" · mit Anweisungen")
                                }
                            )
                        },
                        leadingContent = { Icon(Icons.Outlined.Folder, null) },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { editing = p }) { Icon(Icons.Outlined.Edit, "Projekt „${p.name}“ bearbeiten") }
                                IconButton(onClick = { deleting = p }) { Icon(Icons.Outlined.Delete, "Projekt „${p.name}“ löschen") }
                            }
                        },
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    TextButton(onClick = { onUse(p) }, modifier = Modifier.padding(start = 64.dp)) { Text("Neuen Chat in diesem Projekt") }
                }
            }
        }
    }

    if (creating || editing != null) {
        ProjectEditorDialog(
            initial = editing,
            onDismiss = { creating = false; editing = null },
            onSaved = { creating = false; editing = null; onChanged() },
        )
    }
    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Projekt löschen?") },
            text = { Text("„${p.name}“ mit Anweisungen und Wissen wird gelöscht. Die Chats bleiben im Verlauf erhalten.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { app.projects.delete(p.id); onChanged() }
                    deleting = null
                }) { Text("Löschen") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Abbrechen") } }
        )
    }
}

/** Projekt anlegen oder bearbeiten: Name, Anweisungen, Wissen (Dateien und Notizen). */
@Composable
fun ProjectEditorDialog(initial: Project?, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val app = LocalContext.current.app
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var instructions by remember { mutableStateOf(initial?.instructions.orEmpty()) }
    var files by remember { mutableStateOf(initial?.files.orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var noteOpen by remember { mutableStateOf(false) }
    var noteName by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            var ok = 0
            val errors = mutableListOf<String>()
            for (uri in uris) {
                status = "Lese „${Attachments.displayName(app, uri)}“ …"
                runCatching { readKnowledge(app, uri) }
                    .onSuccess { files = files + it; ok++ }
                    .onFailure { errors += it.report("Projektwissen") }
            }
            busy = false
            status = buildString {
                if (ok > 0) append("$ok hinzugefügt. ")
                if (errors.isNotEmpty()) append("Nicht gelesen: ${errors.first()}")
            }.trim().ifBlank { null }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (initial == null) "Neues Projekt" else "Projekt bearbeiten") },
                    navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Schließen ohne Speichern") } },
                    actions = {
                        TextButton(
                            enabled = name.isNotBlank() && !busy,
                            onClick = {
                                scope.launch {
                                    val base = initial ?: Project(name = name.trim())
                                    app.projects.save(base.copy(name = name.trim().take(60), instructions = instructions.trim(), files = files))
                                    onSaved()
                                }
                            }
                        ) { Text("Speichern") }
                    }
                )
            },
            modifier = Modifier.fillMaxSize().imePadding()
        ) { inner ->
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, top = inner.calculateTopPadding() + 8.dp, bottom = inner.calculateBottomPadding() + 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it.take(60) },
                        label = { Text("Name") }, placeholder = { Text("z. B. Verein, Bewerbung, Hausbau") },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = instructions, onValueChange = { instructions = it },
                        label = { Text("Anweisungen (optional)") },
                        placeholder = { Text("z. B. „Antworte immer kurz und förmlich. Ich bin Kassenwart des TSV Musterstadt.“") },
                        minLines = 4, maxLines = 12, modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Text("Wissen", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Texte, PDFs, Word-Dateien oder Notizen – die KI kennt sie in jedem Chat dieses Projekts " +
                            "(bis ca. ${Project.MAX_CONTEXT / 1000}.000 Zeichen pro Anfrage). PDFs und Fotos werden einmalig von der KI abgeschrieben.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(files, key = { it.id }) { f ->
                    ListItem(
                        leadingContent = { Icon(Icons.Outlined.Description, null) },
                        headlineContent = { Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text("${f.text.length} Zeichen") },
                        trailingContent = {
                            IconButton(onClick = { files = files.filterNot { it.id == f.id } }) { Icon(Icons.Outlined.Delete, "„${f.name}“ entfernen") }
                        }
                    )
                }
                item {
                    val total = files.sumOf { it.text.length }
                    if (total > Project.MAX_CONTEXT) {
                        Text(
                            "Hinweis: Das Wissen ist länger als ${Project.MAX_CONTEXT / 1000}.000 Zeichen – der Rest wird pro Anfrage weggelassen.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { pick.launch(arrayOf("*/*")) }, enabled = !busy) {
                            Icon(Icons.Outlined.AttachFile, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text("Dateien")
                        }
                        OutlinedButton(onClick = { noteName = ""; noteText = ""; noteOpen = true }, enabled = !busy) {
                            Icon(Icons.Outlined.NoteAdd, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text("Notiz")
                        }
                        if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    }
                    status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }

    if (noteOpen) {
        AlertDialog(
            onDismissRequest = { noteOpen = false },
            title = { Text("Notiz hinzufügen") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(noteName, { noteName = it.take(60) }, label = { Text("Titel") }, singleLine = true)
                    OutlinedTextField(noteText, { noteText = it }, label = { Text("Inhalt") }, minLines = 4, maxLines = 10)
                }
            },
            confirmButton = {
                TextButton(enabled = noteText.isNotBlank(), onClick = {
                    files = files + ProjectFile(name = noteName.trim().ifBlank { "Notiz" }, text = noteText.trim())
                    noteOpen = false
                }) { Text("Hinzufügen") }
            },
            dismissButton = { TextButton(onClick = { noteOpen = false }) { Text("Abbrechen") } }
        )
    }
}

/** Liest eine Datei als Projektwissen. Text/Word direkt; PDF-Seiten, Fotos und Videos lässt die KI abschreiben. */
private suspend fun readKnowledge(app: KiApp, uri: Uri): ProjectFile {
    val a = withContext(Dispatchers.IO) { Attachments.file(app, uri) }
    a.text?.let { return ProjectFile(name = a.name, text = it) }
    if (a.images.isEmpty()) error("In „${a.name}“ wurde kein Inhalt gefunden.")
    val s = app.settings.current()
    if (!s.hasKey) error("Für PDFs und Bilder wird der Mistral-Schlüssel gebraucht.")
    val instruction = "Gib den gesamten Text dieser Seiten bzw. Bilder vollständig und wortgetreu wieder (Reihenfolge beibehalten, " +
        "Tabellen als Markdown). Beschreibe Abbildungen ohne Text in einem kurzen Satz. Keine Einleitung."
    // Vision-fähiges Modell; bei Fehler auf das sparsame Standardmodell ausweichen
    var text = ""
    var last: Exception? = null
    for (m in listOf(s.chatModel, MistralClient.FALLBACK_MODEL).distinct()) {
        try {
            text = app.mistral.complete(s.apiKey, m, listOf(ApiMessage("user", instruction, extraImages = a.images.take(8))), temperature = 0.0).trim()
            if (text.isNotBlank()) break
        } catch (e: MistralException) {
            if (e.code == 401 || e.code == 429) throw e
            last = e
        }
    }
    if (text.isBlank() && last != null) throw last
    if (text.isBlank()) error("„${a.name}“ konnte nicht gelesen werden.")
    return ProjectFile(name = a.name + if (a.note.isNotBlank()) " (${a.note})" else "", text = text)
}
