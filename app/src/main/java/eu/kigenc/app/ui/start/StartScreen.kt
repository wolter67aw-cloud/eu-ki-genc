@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package eu.kigenc.app.ui.start

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.kigenc.app.R
import eu.kigenc.app.ui.components.MarkdownText
import eu.kigenc.app.ui.components.shareImage
import eu.kigenc.app.ui.tools.FileImage
import eu.kigenc.app.ui.tools.ResultCard
import eu.kigenc.app.ui.tools.Tool
import eu.kigenc.app.ui.tools.toolPadding
import java.util.Locale

/**
 * Startseite: Hier beschreibt man in eigenen Worten (oder Schritt für Schritt), was man machen möchte.
 * Die KI ordnet den Wunsch zu, man verfeinert die Auswahl und startet den fertigen Auftrag.
 */
@Composable
fun StartScreen(
    vm: StartViewModel,
    onOpenTool: (Tool) -> Unit,
    modifier: Modifier = Modifier,
) {
    val category by vm.category.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); vm.clearMessage() }
    }
    BackHandler(enabled = category != null) { vm.choose(null) }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (category != null) IconButton(onClick = { vm.choose(null) }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Zurück") }
                },
                title = { Text(category?.let { "${it.emoji} ${it.title}" } ?: "Start", fontWeight = FontWeight.SemiBold) },
                actions = {
                    if (category != null) TextButton(onClick = vm::reset) { Text("Neu") }
                }
            )
        }
    ) { inner ->
        val cat = category
        if (cat == null) StartHome(vm, inner, onOpenTool) else StartDefine(vm, cat, inner, onOpenTool)
    }
}

@Composable
private fun StartHome(vm: StartViewModel, inner: PaddingValues, onOpenTool: (Tool) -> Unit) {
    val wish by vm.wish.collectAsStateWithLifecycle()
    val defining by vm.defining.collectAsStateWithLifecycle()
    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let {
            vm.wish.value = listOf(vm.wish.value, it).filter { s -> s.isNotBlank() }.joinToString(" ")
        }
    }

    LazyColumn(contentPadding = toolPadding(inner), verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painterResource(eu.kigenc.app.ui.theme.LocalDesign.current.symbol ?: R.drawable.logo_kigenc), null,
                    Modifier.size(56.dp).clip(RoundedCornerShape(14.dp))
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Was möchtest du machen?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("Beschreib es in eigenen Worten – oder wähle unten Schritt für Schritt.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            OutlinedTextField(
                value = wish, onValueChange = { vm.wish.value = it },
                placeholder = { Text("z. B. „Eine freundliche E-Mail an meinen Vermieter, dass die Heizung seit Montag ausgefallen ist“") },
                minLines = 3, maxLines = 8,
                trailingIcon = {
                    IconButton(onClick = {
                        runCatching {
                            speech.launch(
                                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                                    .putExtra(RecognizerIntent.EXTRA_PROMPT, "Was möchtest du machen?")
                            )
                        }
                    }) { Icon(Icons.Outlined.Mic, "Spracheingabe") }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Button(onClick = vm::define, enabled = wish.isNotBlank() && !defining, modifier = Modifier.fillMaxWidth()) {
                if (defining) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp)); Text("Die KI ordnet deinen Wunsch ein …")
                } else {
                    Icon(Icons.Outlined.AutoAwesome, null); Spacer(Modifier.width(8.dp)); Text("Genau definieren")
                }
            }
        }
        item {
            Text("Oder Schritt für Schritt", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))
        }
        item {
            val cols = 3
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                START_CATEGORIES.chunked(cols).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { c ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                modifier = Modifier.weight(1f).clickable {
                                    if (c.mode == StartMode.TOOL && c.tool != null) onOpenTool(c.tool) else vm.choose(c)
                                }
                            ) {
                                Column(Modifier.padding(10.dp).fillMaxWidth().heightIn(min = 88.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    Text(c.emoji, fontSize = 26.sp)
                                    Text(c.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium, maxLines = 1)
                                    Text(c.subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                }
                            }
                        }
                        repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun StartDefine(vm: StartViewModel, cat: StartCategory, inner: PaddingValues, onOpenTool: (Tool) -> Unit) {
    val context = LocalContext.current
    val options by vm.options.collectAsStateWithLifecycle()
    val content by vm.content.collectAsStateWithLifecycle()
    val extras by vm.extras.collectAsStateWithLifecycle()
    val custom by vm.customPrompt.collectAsStateWithLifecycle()
    val note by vm.note.collectAsStateWithLifecycle()
    val output by vm.output.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val image by vm.image.collectAsStateWithLifecycle()
    val sources by vm.sources.collectAsStateWithLifecycle()
    var showPrompt by rememberSaveable { mutableStateOf(false) }

    LazyColumn(contentPadding = toolPadding(inner), verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxSize()) {
        if (note.isNotBlank()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Row(Modifier.padding(12.dp)) {
                        Icon(Icons.Outlined.AutoAwesome, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.width(8.dp))
                        Text(note, color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        item {
            Column {
                Text("Kategorie", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    START_CATEGORIES.filter { it.mode != StartMode.TOOL }.forEach { c ->
                        FilterChip(selected = c.id == cat.id, onClick = { if (c.id != cat.id) vm.choose(c) }, label = { Text("${c.emoji} ${c.title}") })
                    }
                }
            }
        }
        item {
            OutlinedTextField(
                content, vm::setContent, label = { Text(cat.contentLabel) }, placeholder = { Text(cat.contentHint) },
                minLines = 3, maxLines = 12, modifier = Modifier.fillMaxWidth()
            )
        }
        cat.groups.forEach { g ->
            item(key = "g_${cat.id}_${g.key}") {
                Column {
                    Text(g.label, style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        g.options.forEach { opt ->
                            FilterChip(selected = options[g.key] == opt, onClick = { vm.setOption(g.key, opt) }, label = { Text(opt) })
                        }
                    }
                }
            }
        }
        cat.extras.forEach { f ->
            item(key = "e_${cat.id}_${f.key}") {
                OutlinedTextField(
                    extras[f.key].orEmpty(), { vm.setExtra(f.key, it) }, label = { Text(f.label) }, placeholder = { Text(f.hint) },
                    singleLine = false, maxLines = 3, modifier = Modifier.fillMaxWidth()
                )
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { showPrompt = !showPrompt }) {
                        Icon(Icons.Outlined.Description, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Dein Auftrag an die KI" + if (custom != null) " (bearbeitet)" else "", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        Icon(if (showPrompt) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
                    }
                    if (showPrompt) {
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            custom ?: vm.builtPrompt(), { vm.customPrompt.value = it },
                            textStyle = MaterialTheme.typography.bodySmall, minLines = 4, modifier = Modifier.fillMaxWidth()
                        )
                        if (custom != null) TextButton(onClick = { vm.customPrompt.value = null }) { Text("Aus der Auswahl neu erstellen") }
                    } else {
                        Text(
                            vm.builtPrompt().lines().filter { it.isNotBlank() }.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3
                        )
                    }
                }
            }
        }
        item {
          Column {
            Button(onClick = vm::run, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text(status.ifBlank { "Wird erstellt …" })
                } else {
                    Icon(Icons.Outlined.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("Ausführen", fontWeight = FontWeight.SemiBold)
                }
            }
            if (busy) TextButton(onClick = vm::stop, modifier = Modifier.fillMaxWidth()) { Text("Abbrechen") }
          }
        }
        image?.let { f ->
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FileImage(f, Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = vm::saveImage) { Icon(Icons.Outlined.Download, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Speichern") }
                        OutlinedButton(onClick = { context.shareImage(f) }) { Icon(Icons.Outlined.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Teilen") }
                        OutlinedButton(onClick = { onOpenTool(Tool.PHOTO) }) { Text("Bearbeiten") }
                    }
                    if (output.isNotBlank()) MarkdownText(output)
                }
            }
        }
        if (image == null && (output.isNotEmpty() || (busy && cat.mode != StartMode.IMAGE))) {
            item { ResultCard(output, busy, "start_${cat.id}") }
        }
        if (sources.isNotEmpty()) {
            item {
                Column {
                    Text("Quellen", style = MaterialTheme.typography.labelLarge)
                    sources.forEach { (t, u) ->
                        Text(
                            "• $t", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 3.dp).clickable {
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(u))) }
                            }
                        )
                    }
                }
            }
        }
        if (output.isNotBlank() && !busy && image == null) {
            item {
                Text(
                    "Gespeichert unter Werkzeuge → Gespeicherte Ergebnisse. Ändere die Auswahl oben und tippe erneut auf „Ausführen“, um das Ergebnis anzupassen.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
