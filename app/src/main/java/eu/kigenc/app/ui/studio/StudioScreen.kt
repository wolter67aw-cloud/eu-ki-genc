@file:OptIn(ExperimentalMaterial3Api::class)

package eu.kigenc.app.ui.studio

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.kigenc.app.app
import eu.kigenc.app.ui.components.MarkdownText
import eu.kigenc.app.ui.components.rememberCopyToClipboard
import eu.kigenc.app.ui.components.shareImage
import eu.kigenc.app.ui.components.shareText

private val LANGUAGES = listOf(
    "Englische", "Deutsche", "Französische", "Spanische", "Italienische", "Niederländische", "Polnische",
    "Portugiesische", "Tschechische", "Griechische", "Schwedische", "Dänische", "Kroatische", "Rumänische",
    "Ungarische", "Türkische", "Ukrainische", "Russische", "Arabische", "Chinesische", "Japanische", "Koreanische", "Hindi"
)

private val STYLES = listOf(
    "Fotorealistisch", "Comic", "Illustration", "Aquarell", "Ölgemälde", "Pop-Art", "Impressionismus",
    "3D-Render", "Anime", "Pixel-Art", "Minimalistisch", "Skizze", "Vintage-Foto", "Fantasy"
)

@Composable
fun StudioScreen(vm: StudioViewModel, modifier: Modifier = Modifier) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var imagePrompt by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            vm.clearError()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column {
                TopAppBar(title = { Text("Studio", fontWeight = FontWeight.SemiBold) })
                PrimaryTabRow(selectedTabIndex = tab) {
                    Tab(
                        selected = tab == 0, onClick = { tab = 0 },
                        text = { Text("Texte") }, icon = { Icon(Icons.Outlined.TextFields, null) }
                    )
                    Tab(
                        selected = tab == 1, onClick = { tab = 1 },
                        text = { Text("Bilder") }, icon = { Icon(Icons.Outlined.Palette, null) }
                    )
                }
            }
        }
    ) { padding ->
        when (tab) {
            0 -> TextStudio(
                state = state,
                vm = vm,
                contentPadding = padding,
                onUseAsImagePrompt = {
                    imagePrompt = it
                    tab = 1
                }
            )
            else -> ImageStudio(
                state = state,
                vm = vm,
                prompt = imagePrompt,
                onPromptChange = { imagePrompt = it },
                contentPadding = padding,
            )
        }
    }
}

@Composable
private fun TextStudio(
    state: StudioUiState,
    vm: StudioViewModel,
    contentPadding: PaddingValues,
    onUseAsImagePrompt: (String) -> Unit,
) {
    val context = LocalContext.current
    val tts = context.app.tts
    val speakingId by tts.speakingId.collectAsStateWithLifecycle()
    val copy = rememberCopyToClipboard()
    var input by rememberSaveable { mutableStateOf("") }
    var targetLanguage by rememberSaveable { mutableStateOf("Englische") }

    LazyColumn(
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(TEXT_TEMPLATES, key = { it.id }) { t ->
                    FilterChip(
                        selected = state.template.id == t.id,
                        onClick = { vm.selectTemplate(t) },
                        label = { Text(t.title) },
                        leadingIcon = { Icon(t.icon, null, Modifier.size(18.dp)) }
                    )
                }
            }
        }
        item {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text(state.template.hint) },
                minLines = 4,
                maxLines = 12,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
        }
        if (state.template.id == "translate") {
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(LANGUAGES) { lang ->
                        FilterChip(selected = targetLanguage == lang, onClick = { targetLanguage = lang }, label = { Text(lang) })
                    }
                }
            }
        }
        item {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (state.textBusy) {
                    OutlinedButton(onClick = vm::stopText) { Text("Stoppen") }
                    Spacer(Modifier.size(12.dp))
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Button(onClick = { vm.generateText(input, targetLanguage) }, enabled = input.isNotBlank()) {
                        Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("Generieren")
                    }
                }
            }
        }
        if (state.textOutput.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        MarkdownText(state.textOutput, onCopyCode = copy)
                        if (!state.textBusy) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { copy(state.textOutput) }) {
                                    Icon(Icons.Outlined.ContentCopy, "Kopieren", Modifier.size(20.dp))
                                }
                                IconButton(onClick = {
                                    if (speakingId == "studio") tts.stop() else tts.speak("studio", state.textOutput)
                                }) {
                                    Icon(
                                        if (speakingId == "studio") Icons.Outlined.StopCircle else Icons.Outlined.RecordVoiceOver,
                                        "Vorlesen", Modifier.size(20.dp)
                                    )
                                }
                                IconButton(onClick = { context.shareText(state.textOutput) }) {
                                    Icon(Icons.Outlined.Share, "Teilen", Modifier.size(20.dp))
                                }
                                if (state.template.id == "imgprompt") {
                                    Spacer(Modifier.weight(1f))
                                    FilledTonalButton(onClick = { onUseAsImagePrompt(state.textOutput.trim()) }) {
                                        Icon(Icons.Outlined.PhotoLibrary, null, Modifier.size(18.dp))
                                        Spacer(Modifier.size(6.dp))
                                        Text("Bild erzeugen")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageStudio(
    state: StudioUiState,
    vm: StudioViewModel,
    prompt: String,
    onPromptChange: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    val context = LocalContext.current
    var style by rememberSaveable { mutableStateOf<String?>(null) }
    var fullscreen by remember { mutableStateOf<GeneratedImageItem?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding() + 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            OutlinedTextField(
                value = prompt,
                onValueChange = onPromptChange,
                placeholder = { Text("Beschreibe dein Bild, z. B. „Ein Leuchtturm an der Ostsee bei Sonnenuntergang“") },
                minLines = 3,
                maxLines = 8,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(STYLES) { s ->
                    FilterChip(
                        selected = style == s,
                        onClick = { style = if (style == s) null else s },
                        label = { Text(s) }
                    )
                }
            }
        }
        item {
            Column {
                Button(
                    onClick = {
                        val full = if (style != null) "$prompt\nStil: $style" else prompt
                        vm.generateImage(full)
                    },
                    enabled = prompt.isNotBlank() && !state.imageBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.Palette, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(if (state.imageBusy) "Bild wird gemalt …" else "Bild erzeugen")
                }
                if (state.imageBusy) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(
                        "Das dauert meist 10–30 Sekunden.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
        if (state.images.isEmpty() && !state.imageBusy) {
            item {
                Text(
                    "Bilder werden von Mistral AI mit dem Modell FLUX von Black Forest Labs (Freiburg) erzeugt " +
                        "und nur auf deinem Gerät gespeichert.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(state.images, key = { it.file.absolutePath }) { item ->
            val bitmap = remember(item.file) {
                val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                BitmapFactory.decodeFile(item.file.absolutePath, opts)?.asImageBitmap()
            }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                if (bitmap != null) {
                    Image(
                        bitmap,
                        contentDescription = item.prompt.ifBlank { "Erzeugtes Bild" },
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1))
                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                            .clickable { fullscreen = item }
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                    Text(
                        item.prompt.ifBlank { item.file.name },
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp)
                    )
                    IconButton(onClick = { vm.saveToGallery(item) }) { Icon(Icons.Outlined.Download, "In Galerie speichern") }
                    IconButton(onClick = { context.shareImage(item.file) }) { Icon(Icons.Outlined.Share, "Teilen") }
                    IconButton(onClick = { vm.deleteImage(item) }) { Icon(Icons.Outlined.Delete, "Löschen") }
                }
            }
        }
    }

    fullscreen?.let { item ->
        Dialog(onDismissRequest = { fullscreen = null }) {
            val bitmap = remember(item.file) { BitmapFactory.decodeFile(item.file.absolutePath)?.asImageBitmap() }
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp)
                    .clickable { fullscreen = null },
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap, contentDescription = item.prompt,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                    )
                }
            }
        }
    }
}
