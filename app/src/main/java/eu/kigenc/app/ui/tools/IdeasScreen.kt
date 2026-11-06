@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package eu.kigenc.app.ui.tools

import android.Manifest
import android.app.DatePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.kigenc.app.data.Idea
import eu.kigenc.app.data.IdeaKind
import eu.kigenc.app.data.IdeaStore
import eu.kigenc.app.ui.components.shareImage
import eu.kigenc.app.ui.components.shareText
import java.io.File
import java.util.Calendar

@Composable
fun IdeasScreen(vm: IdeasViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val screen by vm.screen.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    BackHandler { if (!vm.back()) onBack() }
    val title = when (val s = screen) {
        IdeaScreen.Overview -> "Ideen-Sammlung"
        is IdeaScreen.Edit -> s.kind.label
        is IdeaScreen.Detail -> "Idee"
        IdeaScreen.Cross -> "Ideen-Kreuzung"
    }
    ToolScaffold(title, { if (!vm.back()) onBack() }, modifier, message, vm::clearMessage) { inner ->
        when (val s = screen) {
            IdeaScreen.Overview -> IdeaOverview(vm, inner)
            is IdeaScreen.Edit -> IdeaEditor(vm, s.kind, inner)
            is IdeaScreen.Detail -> IdeaDetail(vm, s.id, inner)
            IdeaScreen.Cross -> IdeaCross(vm, inner)
        }
    }
}

// MARK: Übersicht

@Composable
private fun IdeaOverview(vm: IdeasViewModel, inner: PaddingValues) {
    val ideas by vm.ideas.collectAsStateWithLifecycle()
    val tagging by vm.tagging.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var tagFilter by rememberSaveable { mutableStateOf<String?>(null) }
    val topTags = remember(ideas) {
        ideas.filterNot { it.locked }.flatMap { it.tags }.groupingBy { it }.eachCount().entries
            .sortedByDescending { it.value }.take(15).map { it.key }
    }
    val shown = ideas.filter { idea ->
        (tagFilter == null || tagFilter in idea.tags) &&
            (query.isBlank() || query.trim().split(" ").all { idea.searchable().contains(it, ignoreCase = true) })
    }

    LazyColumn(contentPadding = toolPadding(inner), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Text(
                "Halte Gedanken fest – als Foto, Sprachmemo, Skizze oder Zeitkapsel. Die KI vergibt passende Schlagwörter, damit du alles wiederfindest.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionChip("Momentaufnahme", Icons.Outlined.PhotoCamera) { vm.open(IdeaScreen.Edit(IdeaKind.MOMENT)) }
                ActionChip("Sprachmemo", Icons.Outlined.Mic) { vm.open(IdeaScreen.Edit(IdeaKind.MEMO)) }
                ActionChip("Skizze", Icons.Outlined.Draw) { vm.open(IdeaScreen.Edit(IdeaKind.SKETCH)) }
                ActionChip("Zeitkapsel", Icons.Outlined.HourglassTop) { vm.open(IdeaScreen.Edit(IdeaKind.CAPSULE)) }
                ActionChip("Notiz", Icons.Outlined.EditNote) { vm.open(IdeaScreen.Edit(IdeaKind.NOTE)) }
                ActionChip("Ideen kreuzen", Icons.Outlined.Shuffle, enabled = vm.available().size >= 2) { vm.open(IdeaScreen.Cross) }
            }
        }
        item {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, "Leeren") } },
                placeholder = { Text("Suchen in Texten, Transkripten, Schlagwörtern …") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (topTags.isNotEmpty()) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(topTags) { t ->
                        FilterChip(selected = tagFilter == t, onClick = { tagFilter = if (tagFilter == t) null else t }, label = { Text("#$t") })
                    }
                }
            }
        }
        if (shown.isEmpty()) {
            item {
                Text(
                    if (ideas.isEmpty()) "Noch keine Ideen gespeichert." else "Nichts gefunden.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 24.dp)
                )
            }
        }
        items(shown, key = { it.id }) { idea ->
            IdeaCard(idea, idea.id in tagging) {
                if (!idea.locked) {
                    vm.open(IdeaScreen.Detail(idea.id))
                } else {
                    vm.message.value = "Diese Zeitkapsel öffnet sich am ${formatDate(idea.openAt!!)}."
                }
            }
        }
    }
}

@Composable
private fun ActionChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean = true, onClick: () -> Unit) {
    AssistChip(onClick = onClick, enabled = enabled, label = { Text(label) }, leadingIcon = { Icon(icon, null, Modifier.size(18.dp)) })
}

@Composable
private fun IdeaCard(idea: Idea, tagging: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val thumb = if (idea.locked) null else IdeaStore.media(context, idea.imageFile)
            if (thumb != null) {
                FileImage(thumb, Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)), ContentScale.Crop, maxSide = 200)
            } else {
                Box(
                    Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) { Text(if (idea.locked) "🔒" else idea.kind.emoji, fontSize = 24.sp) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(idea.title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (idea.locked) "Verschlossen bis ${formatDate(idea.openAt!!)}"
                    else "${idea.kind.label} · ${formatDate(idea.createdAt)}" + if (idea.mood.isNotBlank()) " · ${idea.mood}" else "",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!idea.locked && idea.text.isNotBlank()) {
                    Text(idea.text, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (tagging) {
                    Text("Schlagwörter werden erstellt …", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                } else if (idea.tags.isNotEmpty() && !idea.locked) {
                    Text(idea.tags.joinToString(" ") { "#$it" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
fun FileImage(file: File, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Fit, maxSide: Int = 1600) {
    val bitmap by produceState<ImageBitmap?>(null, file) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, bounds)
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
                BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
            }.getOrNull()
        }
    }
    bitmap?.let { Image(it, null, modifier, contentScale = contentScale) } ?: Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
}

// MARK: Erfassen

@Composable
private fun IdeaEditor(vm: IdeasViewModel, kind: IdeaKind, inner: PaddingValues) {
    val context = LocalContext.current
    val title by vm.draftTitle.collectAsStateWithLifecycle()
    val text by vm.draftText.collectAsStateWithLifecycle()
    val mood by vm.draftMood.collectAsStateWithLifecycle()
    val image by vm.draftImage.collectAsStateWithLifecycle()
    val audio by vm.draftAudio.collectAsStateWithLifecycle()
    val openAt by vm.draftOpenAt.collectAsStateWithLifecycle()
    val recording by vm.recording.collectAsStateWithLifecycle()
    val elapsed by vm.elapsedSec.collectAsStateWithLifecycle()
    val transcribing by vm.transcribing.collectAsStateWithLifecycle()
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(vm::setImage) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) cameraUri?.let(vm::setImage) }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            vm.startRecording()
        } else {
            vm.message.value = "Ohne Mikrofon-Berechtigung ist keine Aufnahme möglich."
        }
    }

    LazyColumn(
        contentPadding = toolPadding(inner), verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text(
                when (kind) {
                    IdeaKind.MOMENT -> "Halte einen Moment fest: Foto oder Screenshot, ein paar Worte und deine Stimmung. Datum und Uhrzeit werden automatisch gespeichert."
                    IdeaKind.MEMO -> "Sprich deine Idee ein – sie wird automatisch in Text umgewandelt und ist danach durchsuchbar. Die Aufnahme bleibt erhalten."
                    IdeaKind.SKETCH -> "Zeichne mit dem Finger eine Skizze oder Mindmap. Die KI kann sie danach auswerten und in Text übertragen."
                    IdeaKind.CAPSULE -> "Schreib eine Nachricht an dein zukünftiges Ich. Sie bleibt bis zum gewählten Datum verschlossen – dann erinnert dich die App."
                    else -> "Schreib einen Gedanken auf."
                },
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (kind == IdeaKind.SKETCH) {
            item { SketchPad(vm, Modifier.onSizeChanged { canvasSize = it }) }
        }
        if (kind == IdeaKind.MEMO) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (recording) {
                        Button(onClick = { vm.stopRecording() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                            Icon(Icons.Filled.Stop, null); Spacer(Modifier.width(6.dp)); Text("Stopp  %d:%02d".format(elapsed / 60, elapsed % 60))
                        }
                    } else {
                        Button(onClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) vm.startRecording()
                            else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                        }) { Icon(Icons.Filled.Mic, null); Spacer(Modifier.width(6.dp)); Text(if (audio == null) "Aufnehmen" else "Neu aufnehmen") }
                    }
                    if (transcribing) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Text("Wird transkribiert …") }
                    else if (audio != null && !recording) Text("✓ Aufnahme bereit", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        if (kind == IdeaKind.MOMENT || kind == IdeaKind.CAPSULE || kind == IdeaKind.NOTE) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Icon(Icons.Outlined.Image, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Bild/Screenshot")
                    }
                    OutlinedButton(onClick = {
                        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(dir, "idee_${System.currentTimeMillis()}.jpg"))
                        cameraUri = uri
                        runCatching { takePicture.launch(uri) }
                    }) { Icon(Icons.Outlined.PhotoCamera, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Kamera") }
                }
            }
            image?.let { f -> item { FileImage(f, Modifier.fillMaxWidth().heightIn(max = 260.dp).clip(RoundedCornerShape(12.dp))) } }
        }
        item {
            OutlinedTextField(title, { vm.draftTitle.value = it }, label = { Text("Titel (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        item {
            OutlinedTextField(
                text, { vm.draftText.value = it },
                label = { Text(if (kind == IdeaKind.MEMO) "Transkript" else if (kind == IdeaKind.SKETCH) "Notiz zur Skizze (optional)" else "Text") },
                minLines = if (kind == IdeaKind.SKETCH) 2 else 4, modifier = Modifier.fillMaxWidth()
            )
        }
        if (kind == IdeaKind.MOMENT || kind == IdeaKind.NOTE || kind == IdeaKind.MEMO) {
            item {
                Column {
                    Text("Stimmung", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(MOODS) { m ->
                            FilterChip(selected = mood == m, onClick = { vm.draftMood.value = if (mood == m) "" else m }, label = { Text(m, fontSize = 20.sp) })
                        }
                    }
                }
            }
        }
        if (kind == IdeaKind.CAPSULE) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Öffnen am: ${openAt?.let { formatDate(it) } ?: "–"}", fontWeight = FontWeight.Medium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val day = 24L * 3600 * 1000
                        listOf("1 Woche" to 7 * day, "1 Monat" to 30 * day, "6 Monate" to 182 * day, "1 Jahr" to 365 * day).forEach { (l, d) ->
                            AssistChip(onClick = { vm.draftOpenAt.value = System.currentTimeMillis() + d }, label = { Text(l) })
                        }
                        AssistChip(onClick = { pickDate(context) { vm.draftOpenAt.value = it } }, label = { Text("Datum wählen") },
                            leadingIcon = { Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(18.dp)) })
                    }
                }
            }
        }
        item {
            Button(
                onClick = { vm.save(kind, canvasSize.width, canvasSize.height) },
                enabled = !recording && !transcribing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(if (kind == IdeaKind.CAPSULE) Icons.Outlined.Lock else Icons.Outlined.Save, null)
                Spacer(Modifier.width(8.dp))
                Text(if (kind == IdeaKind.CAPSULE) "Zeitkapsel verschließen" else "Speichern")
            }
        }
    }
}

private fun pickDate(context: Context, onPicked: (Long) -> Unit) {
    val cal = Calendar.getInstance().apply { add(Calendar.YEAR, 1) }
    DatePickerDialog(context, { _, y, m, d ->
        cal.set(y, m, d, 9, 0, 0)
        onPicked(cal.timeInMillis)
    }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).apply {
        datePicker.minDate = System.currentTimeMillis() + 24L * 3600 * 1000
    }.show()
}

private val PEN_COLORS = listOf(Color.Black, Color(0xFF1E5BD8), Color(0xFFD93025), Color(0xFF188038), Color(0xFFF29900), Color(0xFF8B5CF6), Color(0xFF22C3F5))

@Composable
private fun SketchPad(vm: IdeasViewModel, modifier: Modifier = Modifier) {
    var color by remember { mutableStateOf(Color.Black) }
    var width by remember { mutableFloatStateOf(8f) }
    var eraser by remember { mutableStateOf(false) }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    val strokeColor = if (eraser) Color.White else color
    val strokeWidth = if (eraser) 36f else width

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(
            modifier
                .fillMaxWidth()
                .aspectRatio(0.8f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                .pointerInput(strokeColor, strokeWidth) {
                    detectTapGestures { vm.strokes.add(SketchStroke(listOf(it), strokeColor, strokeWidth)) }
                }
                .pointerInput(strokeColor, strokeWidth) {
                    detectDragGestures(
                        onDragStart = { current = listOf(it) },
                        onDrag = { change, _ -> current = current + change.position },
                        onDragEnd = {
                            if (current.isNotEmpty()) vm.strokes.add(SketchStroke(current, strokeColor, strokeWidth))
                            current = emptyList()
                        },
                        onDragCancel = { current = emptyList() },
                    )
                }
        ) {
            (vm.strokes + listOfNotNull(if (current.isNotEmpty()) SketchStroke(current, strokeColor, strokeWidth) else null)).forEach { s ->
                if (s.points.size == 1) {
                    drawCircle(s.color, s.width / 2, s.points[0])
                } else {
                    val p = Path()
                    s.points.forEachIndexed { i, o -> if (i == 0) p.moveTo(o.x, o.y) else p.lineTo(o.x, o.y) }
                    drawPath(p, s.color, style = Stroke(width = s.width, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PEN_COLORS.forEach { c ->
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(c)
                        .border(if (c == color && !eraser) 3.dp else 1.dp, if (c == color && !eraser) MaterialTheme.colorScheme.primary else Color.Gray, CircleShape)
                        .clickable { color = c; eraser = false }
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(4f to "fein", 8f to "mittel", 16f to "dick").forEach { (w, l) ->
                FilterChip(selected = width == w && !eraser, onClick = { width = w; eraser = false }, label = { Text(l) })
            }
            FilterChip(selected = eraser, onClick = { eraser = !eraser }, label = { Text("Radierer") })
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { if (vm.strokes.isNotEmpty()) vm.strokes.removeAt(vm.strokes.lastIndex) }) { Icon(Icons.AutoMirrored.Outlined.Undo, "Rückgängig") }
            IconButton(onClick = { vm.strokes.clear() }) { Icon(Icons.Outlined.DeleteSweep, "Alles löschen") }
        }
    }
}

// MARK: Detail

@Composable
private fun IdeaDetail(vm: IdeasViewModel, id: String, inner: PaddingValues) {
    val context = LocalContext.current
    val ideas by vm.ideas.collectAsStateWithLifecycle()
    val tagging by vm.tagging.collectAsStateWithLifecycle()
    val playing by vm.playingId.collectAsStateWithLifecycle()
    val output by vm.output.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val idea = ideas.firstOrNull { it.id == id } ?: return
    var newTag by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    val image = IdeaStore.media(context, idea.imageFile)

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Idee löschen?") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete(idea) }) { Text("Löschen") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") } },
        )
    }

    LazyColumn(contentPadding = toolPadding(inner), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Column {
            Text("${idea.kind.emoji} ${idea.title}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "${idea.kind.label} · ${formatDate(idea.createdAt)}" + (if (idea.mood.isNotBlank()) " · Stimmung ${idea.mood}" else "") +
                    (idea.openAt?.let { " · geöffnet am ${formatDate(it)}" } ?: ""),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            }
        }
        image?.let { f ->
            item { FileImage(f, Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))) }
        }
        if (idea.audioFile != null) {
            item {
                OutlinedButton(onClick = { vm.togglePlay(idea) }) {
                    Icon(if (playing == idea.id) Icons.Outlined.StopCircle else Icons.Outlined.PlayCircle, null)
                    Spacer(Modifier.width(6.dp)); Text(if (playing == idea.id) "Stopp" else "Aufnahme abspielen")
                }
            }
        }
        if (idea.text.isNotBlank()) {
            item { eu.kigenc.app.ui.components.MarkdownText(idea.text) }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Schlagwörter", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    if (id in tagging) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else TextButton(onClick = { vm.retag(idea) }) { Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("KI-Vorschläge") }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    idea.tags.forEach { t ->
                        InputChip(selected = false, onClick = { vm.update(idea.copy(tags = idea.tags - t)) }, label = { Text("#$t") },
                            trailingIcon = { Icon(Icons.Outlined.Close, "Entfernen", Modifier.size(14.dp)) })
                    }
                }
                OutlinedTextField(
                    newTag, { newTag = it }, singleLine = true, placeholder = { Text("Eigenes Schlagwort") },
                    trailingIcon = {
                        IconButton(onClick = {
                            val t = newTag.trim().lowercase().removePrefix("#")
                            if (t.isNotBlank()) vm.update(idea.copy(tags = (idea.tags + t).distinct()))
                            newTag = ""
                        }) { Icon(Icons.Outlined.Add, "Hinzufügen") }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (image != null) {
                    Button(onClick = { vm.analyze(idea) }, enabled = !busy) {
                        Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                        Text(if (idea.kind == IdeaKind.SKETCH) "Skizze auswerten" else "KI auswerten")
                    }
                }
                OutlinedButton(onClick = {
                    vm.crossA.value = idea
                    vm.crossB.value = vm.available().filterNot { it.id == idea.id }.randomOrNull()
                    vm.open(IdeaScreen.Cross)
                }, enabled = vm.available().size >= 2) { Text("Kreuzen") }
                OutlinedButton(onClick = {
                    if (image != null && idea.text.isBlank()) context.shareImage(image)
                    else context.shareText("${idea.title}\n\n${idea.text}\n\n${idea.tags.joinToString(" ") { "#$it" }}")
                }) { Icon(Icons.Outlined.Share, null, Modifier.size(18.dp)) }
                OutlinedButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, null, Modifier.size(18.dp)) }
            }
        }
        if (output.isNotEmpty() || busy) {
            item { ResultCard(output, busy, "idea_${idea.id}") }
        }
    }
}

// MARK: Ideen-Kreuzung

@Composable
private fun IdeaCross(vm: IdeasViewModel, inner: PaddingValues) {
    val a by vm.crossA.collectAsStateWithLifecycle()
    val b by vm.crossB.collectAsStateWithLifecycle()
    val output by vm.output.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var choosing by remember { mutableStateOf<Int?>(null) }

    choosing?.let { slot ->
        AlertDialog(
            onDismissRequest = { choosing = null },
            title = { Text("Idee ${if (slot == 0) "A" else "B"} wählen") },
            text = {
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(vm.available()) { i ->
                        Text("${i.kind.emoji} ${i.title}", modifier = Modifier.fillMaxWidth().clickable {
                            if (slot == 0) {
                                vm.crossA.value = i
                            } else {
                                vm.crossB.value = i
                            }
                            vm.output.value = ""
                            choosing = null
                        }.padding(vertical = 10.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosing = null }) { Text("Schließen") } },
        )
    }

    LazyColumn(contentPadding = toolPadding(inner), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Text(
                "Zwei Ideen treffen aufeinander – die KI entwickelt daraus neue Ansätze. Ideal fürs Brainstorming.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item { CrossSlot("A", a) { choosing = 0 } }
        item { Text("×", fontSize = 28.sp, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        item { CrossSlot("B", b) { choosing = 1 } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = vm::randomPair, enabled = !busy) {
                    Icon(Icons.Outlined.Shuffle, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Zufällig")
                }
                Button(onClick = vm::cross, enabled = !busy && a != null && b != null && a?.id != b?.id, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Kreuzen")
                }
            }
        }
        if (output.isNotEmpty() || busy) {
            item { ResultCard(output, busy, "cross") }
            if (!busy) item {
                OutlinedButton(onClick = vm::saveCross, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Save, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Als neue Idee speichern")
                }
            }
        }
    }
}

@Composable
private fun CrossSlot(label: String, idea: Idea?, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("Idee $label · antippen zum Wechseln", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(idea?.let { "${it.kind.emoji} ${it.title}" } ?: "Bitte wählen", fontWeight = FontWeight.Medium)
            if (idea != null && idea.text.isNotBlank()) Text(idea.text, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}
