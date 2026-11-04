@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package eu.kigenc.app.ui.chat

import android.app.Activity
import androidx.compose.material.icons.outlined.Flag
import eu.kigenc.app.data.Conversation
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.kigenc.app.R
import eu.kigenc.app.app
import eu.kigenc.app.data.ChatMessage
import eu.kigenc.app.data.ChatMode
import eu.kigenc.app.data.Role
import eu.kigenc.app.ui.components.EuFlag
import eu.kigenc.app.ui.components.MarkdownText
import eu.kigenc.app.ui.components.ReportDialog
import eu.kigenc.app.ui.components.rememberCopyToClipboard
import eu.kigenc.app.ui.components.shareText
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Inhalte, die per „Teilen“ aus anderen Apps kommen. */
data class SharedContent(val text: String?, val imageUri: Uri?)

@Composable
fun ChatScreen(
    vm: ChatViewModel,
    modifier: Modifier = Modifier,
    shared: SharedContent? = null,
    onSharedConsumed: () -> Unit = {},
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val app = context.app
    val settings by app.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val speakingId by app.tts.speakingId.collectAsStateWithLifecycle()
    val copy = rememberCopyToClipboard()
    val snackbar = remember { SnackbarHostState() }
    var input by rememberSaveable(vm.mode) { mutableStateOf("") }
    var showHistory by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ChatMessage?>(null) }
    var editText by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<Conversation?>(null) }
    var renameText by remember { mutableStateOf("") }
    var historyQuery by remember { mutableStateOf("") }
    var reporting by remember { mutableStateOf<String?>(null) }
    val isCode = vm.mode == ChatMode.CODE

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            vm.clearError()
        }
    }

    LaunchedEffect(shared) {
        if (shared != null) {
            shared.text?.let { input = it }
            shared.imageUri?.let(vm::attachImage)
            onSharedConsumed()
        }
    }

    // --- Spracheingabe (System-Spracherkennung) ---
    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val spoken = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) input = listOf(input, spoken).filter { it.isNotBlank() }.joinToString(" ")
        }
    }
    val startSpeech = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Sprich jetzt …")
        try {
            speechLauncher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            vm.reportError("Auf diesem Gerät ist keine Spracherkennung verfügbar.")
        }
    }

    // --- Bilder: Galerie & Kamera ---
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(vm::attachImage)
    }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) cameraUri?.let(vm::attachImage)
    }
    val launchCamera = {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "foto_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        cameraUri = uri
        try {
            takePicture.launch(uri)
        } catch (_: ActivityNotFoundException) {
            vm.reportError("Keine Kamera-App gefunden.")
        }
    }

    val messages = state.current?.messages.orEmpty()
    val listState = rememberLazyListState()
    val lastLength = messages.lastOrNull()?.text?.length ?: 0
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex, Int.MAX_VALUE / 2)
    }
    LaunchedEffect(lastLength) {
        // Während des Streamings nur mitscrollen, wenn man unten ist (kein Wegspringen beim Hochscrollen).
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        if (state.streaming && messages.isNotEmpty() && lastVisible >= messages.lastIndex - 1) {
            listState.scrollToItem(messages.lastIndex, Int.MAX_VALUE / 2)
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            EuFlag(Modifier.height(14.dp))
                            Spacer(Modifier.size(8.dp))
                            Text(
                                if (isCode) "Code-Assistent" else "KI-GenC",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        val model = settings?.let { if (isCode) it.codeModel else it.chatModel }
                        if (model != null) {
                            Text(
                                model,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { showHistory = true }) {
                        Icon(Icons.Outlined.History, contentDescription = "Verlauf")
                    }
                },
                actions = {
                    IconButton(onClick = { vm.newChat(); input = "" }) {
                        Icon(Icons.Outlined.EditNote, contentDescription = "Neue Unterhaltung")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            InputBar(
                text = input,
                onTextChange = { input = it },
                attachedImage = state.attachedImage,
                streaming = state.streaming,
                allowImages = !isCode,
                placeholder = if (isCode) "Beschreibe den Code, den du brauchst …" else "Frag KI-GenC …",
                onRemoveImage = vm::removeImage,
                onPickImage = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onCamera = launchCamera,
                onMic = startSpeech,
                onSend = {
                    vm.send(input)
                    input = ""
                },
                onStop = vm::stop,
                modifier = Modifier.imePadding()
            )
        },
    ) { padding ->
        if (messages.isEmpty()) {
            EmptyState(
                isCode = isCode,
                hasKey = settings?.hasKey ?: true,
                onSuggestion = { input = it },
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            )
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(messages, key = { it.id }) { msg ->
                    val isLastStreaming = state.streaming && msg.id == messages.last().id
                    MessageItem(
                        message = msg,
                        streaming = isLastStreaming,
                        speaking = speakingId == msg.id,
                        onCopy = copy,
                        onSpeak = {
                            if (speakingId == msg.id) app.tts.stop() else app.tts.speak(msg.id, msg.text)
                        },
                        onShare = { context.shareText(msg.text) },
                        onRegenerate = if (!state.streaming && msg.role == Role.ASSISTANT && msg.id == messages.last().id) {
                            { vm.regenerate() }
                        } else null,
                        onEdit = if (!state.streaming && msg.role == Role.USER) {
                            { editing = msg; editText = msg.text }
                        } else null,
                        onReport = if (msg.role == Role.ASSISTANT) {
                            { reporting = msg.text }
                        } else null,
                    )
                }
            }
        }
    }

    if (showHistory) {
        ModalBottomSheet(onDismissRequest = { showHistory = false }) {
            Text(
                "Verlauf",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            OutlinedTextField(
                value = historyQuery, onValueChange = { historyQuery = it },
                placeholder = { Text("Verlauf durchsuchen …") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                singleLine = true, shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            )
            val filtered = remember(state.conversations, historyQuery) {
                val q = historyQuery.trim()
                if (q.isEmpty()) state.conversations
                else state.conversations.filter { c ->
                    c.title.contains(q, ignoreCase = true) || c.messages.any { it.text.contains(q, ignoreCase = true) }
                }
            }
            if (filtered.isEmpty()) {
                Text(
                    if (state.conversations.isEmpty()) "Noch keine Unterhaltungen." else "Nichts gefunden.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp)
                )
            }
            LazyColumn(Modifier.heightIn(max = 520.dp)) {
                items(filtered, key = { it.id }) { conv ->
                    ListItem(
                        headlineContent = { Text(conv.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Text(
                                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(conv.updatedAt)) +
                                    " · " + conv.model
                            )
                        },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { renaming = conv; renameText = conv.title }) {
                                    Icon(Icons.Outlined.Edit, contentDescription = "Umbenennen")
                                }
                                IconButton(onClick = { vm.delete(conv) }) {
                                    Icon(Icons.Outlined.Delete, contentDescription = "Löschen")
                                }
                            }
                        },
                        modifier = Modifier.clickable {
                            vm.open(conv)
                            showHistory = false
                        }
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    editing?.let { msg ->
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Nachricht bearbeiten") },
            text = {
                OutlinedTextField(value = editText, onValueChange = { editText = it }, minLines = 3, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(onClick = { vm.editAndResend(msg.id, editText); editing = null }, enabled = editText.isNotBlank()) {
                    Text("Neu senden")
                }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Abbrechen") } }
        )
    }

    reporting?.let { text ->
        ReportDialog(contentExcerpt = text, kind = "Chat-Antwort", onDismiss = { reporting = null })
    }

    renaming?.let { conv ->
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Unterhaltung umbenennen") },
            text = { OutlinedTextField(value = renameText, onValueChange = { renameText = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                TextButton(onClick = { vm.rename(conv, renameText); renaming = null }, enabled = renameText.isNotBlank()) { Text("Speichern") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Abbrechen") } }
        )
    }
}

@Composable
private fun EmptyState(isCode: Boolean, hasKey: Boolean, onSuggestion: (String) -> Unit, modifier: Modifier) {
    val suggestions = if (isCode) listOf(
        "Schreibe eine Kotlin-Funktion, die E-Mail-Adressen validiert",
        "Erkläre mir diesen Code Schritt für Schritt:",
        "Finde den Fehler in meinem Code:",
        "Erstelle eine REST-API in Python mit FastAPI",
        "Schreibe Unit-Tests für folgende Funktion:",
        "Wandle dieses SQL in eine effiziente Abfrage um:",
        "Automatisierung: Python-Skript, das jeden Morgen meine E-Mails nach Rechnungen durchsucht",
        "Erstelle ein Bash-Skript für tägliche Backups meines Ordners",
        "Google-Sheets-Skript, das Zeilen automatisch nach Datum sortiert",
        "Excel-Formel: Summe aller Beträge pro Monat",
    ) else listOf(
        "Erkläre mir den AI Act der EU in einfachen Worten",
        "Plane mir ein Wochenende in Wien",
        "Hilf mir, eine Bewerbung zu formulieren",
        "Was kann ich heute Abend kochen? Ich habe Nudeln und Tomaten.",
        "Fasse mir die Vor- und Nachteile von Wärmepumpen zusammen",
        "Gib mir 5 kreative Ideen für ein Vereinsfest",
    )
    Column(
        modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(R.drawable.logo_kigenc),
            contentDescription = "KI-GenC Logo",
            modifier = Modifier.size(112.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            if (isCode) "Was sollen wir programmieren?" else "Wie kann ich helfen?",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (hasKey) "Europäische KI von Mistral AI – Server in der EU."
            else "Trage zuerst unter „Einstellungen“ deinen kostenlosen Mistral-API-Schlüssel ein.",
            style = MaterialTheme.typography.bodyMedium,
            color = if (hasKey) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(24.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            suggestions.forEach { s ->
                AssistChip(onClick = { onSuggestion(s) }, label = { Text(s, maxLines = 2) })
            }
        }
    }
}

@Composable
private fun MessageItem(
    message: ChatMessage,
    streaming: Boolean,
    speaking: Boolean,
    onCopy: (String) -> Unit,
    onSpeak: () -> Unit,
    onShare: () -> Unit,
    onRegenerate: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onReport: (() -> Unit)? = null,
) {
    if (message.role == Role.USER) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
            message.imagePath?.let { path ->
                val bitmap = remember(path) { BitmapFactory.decodeFile(path)?.asImageBitmap() }
                if (bitmap != null) {
                    Image(
                        bitmap, contentDescription = "Angehängtes Bild",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .padding(bottom = 6.dp)
                            .size(180.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .pointerInput(onEdit) { detectTapGestures(onLongPress = { onEdit?.invoke() }) }
            ) {
                Text(
                    message.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
            if (onEdit != null) {
                TextButton(onClick = onEdit) {
                    Icon(Icons.Outlined.Edit, null, Modifier.size(14.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Bearbeiten", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    } else {
        Row(Modifier.fillMaxWidth()) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(30.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.AutoAwesome, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                if (message.text.isEmpty() && streaming) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(8.dp))
                        Text("Denke nach …", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    MarkdownText(
                        message.text,
                        color = if (message.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        onCopyCode = onCopy,
                    )
                }
                AnimatedVisibility(!streaming && message.text.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                        IconButton(onClick = { onCopy(message.text) }) {
                            Icon(Icons.Outlined.ContentCopy, "Kopieren", Modifier.size(18.dp))
                        }
                        IconButton(onClick = onSpeak) {
                            Icon(
                                if (speaking) Icons.Outlined.StopCircle else Icons.Outlined.RecordVoiceOver,
                                if (speaking) "Vorlesen stoppen" else "Vorlesen",
                                Modifier.size(18.dp)
                            )
                        }
                        IconButton(onClick = onShare) {
                            Icon(Icons.Outlined.Share, "Teilen", Modifier.size(18.dp))
                        }
                        if (onRegenerate != null) {
                            IconButton(onClick = onRegenerate) {
                                Icon(Icons.Outlined.Refresh, "Antwort neu erzeugen", Modifier.size(18.dp))
                            }
                        }
                        if (onReport != null) {
                            IconButton(onClick = onReport) {
                                Icon(Icons.Outlined.Flag, "Inhalt melden", Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InputBar(
    text: String,
    onTextChange: (String) -> Unit,
    attachedImage: String?,
    streaming: Boolean,
    allowImages: Boolean,
    placeholder: String,
    onRemoveImage: () -> Unit,
    onPickImage: () -> Unit,
    onCamera: () -> Unit,
    onMic: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (attachedImage != null) {
                val bitmap = remember(attachedImage) { BitmapFactory.decodeFile(attachedImage)?.asImageBitmap() }
                Box(Modifier.padding(bottom = 8.dp)) {
                    if (bitmap != null) {
                        Image(
                            bitmap, contentDescription = "Ausgewähltes Bild",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(84.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    }
                    FilledIconButton(
                        onClick = onRemoveImage,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(26.dp)
                    ) { Icon(Icons.Filled.Close, "Bild entfernen", Modifier.size(16.dp)) }
                }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                if (allowImages) {
                    var menu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = "Bild hinzufügen")
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Aus Galerie") },
                                leadingIcon = { Icon(Icons.Outlined.PhotoLibrary, null) },
                                onClick = { menu = false; onPickImage() }
                            )
                            DropdownMenuItem(
                                text = { Text("Foto aufnehmen") },
                                leadingIcon = { Icon(Icons.Outlined.PhotoCamera, null) },
                                onClick = { menu = false; onCamera() }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    maxLines = 6,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.size(6.dp))
                when {
                    streaming -> FilledIconButton(onClick = onStop, modifier = Modifier.size(52.dp)) {
                        Icon(Icons.Filled.Stop, contentDescription = "Stopp")
                    }
                    text.isBlank() && attachedImage == null -> FilledIconButton(onClick = onMic, modifier = Modifier.size(52.dp)) {
                        Icon(Icons.Filled.Mic, contentDescription = "Spracheingabe")
                    }
                    else -> FilledIconButton(onClick = onSend, modifier = Modifier.size(52.dp)) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Senden")
                    }
                }
            }
            if (!streaming) {
                Text(
                    "KI kann Fehler machen. Wichtige Infos bitte prüfen.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 4.dp)
                )
            } else {
                TextButton(onClick = onStop, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Antwort wird erzeugt … Stoppen")
                }
            }
        }
    }
}
