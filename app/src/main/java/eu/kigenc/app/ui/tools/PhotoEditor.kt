package eu.kigenc.app.ui.tools

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Switch
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import eu.kigenc.app.KiApp
import eu.kigenc.app.ai.ApiMessage
import eu.kigenc.app.ai.ImageGenerator
import eu.kigenc.app.ai.friendlyMessage
import eu.kigenc.app.data.ImageUtils
import eu.kigenc.app.ui.components.shareImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import eu.kigenc.app.data.report

private const val MAX_PHOTOS = 10

/** Mehrere Bilder auf einmal teilen. */
private fun android.content.Context.shareImages(files: List<File>) {
    if (files.size == 1) { shareImage(files[0], "image/jpeg"); return }
    val uris = ArrayList(files.map { androidx.core.content.FileProvider.getUriForFile(this, "$packageName.fileprovider", it) })
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND_MULTIPLE)
        .setType("image/jpeg")
        .putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, uris)
        .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    startActivity(android.content.Intent.createChooser(intent, "Fotos teilen").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
}

enum class PhotoFilter(val label: String) {
    NONE("Original"), VIVID("Lebendig"), WARM("Warm"), COOL("Kühl"), BW("Schwarz-Weiß"), SEPIA("Sepia"), VINTAGE("Vintage")
}

data class PhotoAdjust(
    val brightness: Float = 0f,   // -100 … 100
    val contrast: Float = 1f,     // 0.5 … 1.8
    val saturation: Float = 1f,   // 0 … 2
    val warmth: Float = 0f,       // -1 … 1
    val filter: PhotoFilter = PhotoFilter.NONE,
)

/** Baut die 4×5-Farbmatrix für Vorschau und Export (gleiche Werte → identisches Ergebnis). */
fun buildColorMatrix(a: PhotoAdjust): FloatArray {
    val m = android.graphics.ColorMatrix()
    val filterSat = when (a.filter) {
        PhotoFilter.BW, PhotoFilter.SEPIA -> 0f
        PhotoFilter.VIVID -> 1.35f
        PhotoFilter.VINTAGE -> 0.7f
        else -> 1f
    }
    m.setSaturation(a.saturation * filterSat)
    val c = a.contrast * (if (a.filter == PhotoFilter.VIVID) 1.1f else 1f)
    val t = (1f - c) * 128f + a.brightness
    m.postConcat(android.graphics.ColorMatrix(floatArrayOf(c, 0f, 0f, 0f, t, 0f, c, 0f, 0f, t, 0f, 0f, c, 0f, t, 0f, 0f, 0f, 1f, 0f)))
    val w = a.warmth + when (a.filter) {
        PhotoFilter.WARM -> 0.6f
        PhotoFilter.COOL -> -0.6f
        PhotoFilter.VINTAGE -> 0.3f
        else -> 0f
    }
    if (w != 0f) {
        m.postConcat(android.graphics.ColorMatrix(floatArrayOf(1f, 0f, 0f, 0f, w * 25f, 0f, 1f, 0f, 0f, w * 5f, 0f, 0f, 1f, 0f, -w * 25f, 0f, 0f, 0f, 1f, 0f)))
    }
    if (a.filter == PhotoFilter.SEPIA) {
        m.postConcat(
            android.graphics.ColorMatrix(
                floatArrayOf(
                    0.393f, 0.769f, 0.189f, 0f, 0f,
                    0.349f, 0.686f, 0.168f, 0f, 0f,
                    0.272f, 0.534f, 0.131f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
    }
    return m.array
}

class PhotoEditorViewModel(app: KiApp) : AiToolViewModel(app) {
    /** Alle ausgewählten Fotos (Pfade); [source] ist das gerade angezeigte. */
    val sources = MutableStateFlow<List<String>>(emptyList())
    val selected = MutableStateFlow(0)
    val source = MutableStateFlow<String?>(null)

    fun select(index: Int) {
        val list = sources.value
        if (index !in list.indices) return
        selected.value = index
        source.value = list[index]
        version.value++
    }

    /** Mehrere Fotos auf einmal hinzufügen (max. 10). */
    fun loadMany(uris: List<Uri>, replace: Boolean = false) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val imported = withContext(Dispatchers.IO) {
                uris.mapNotNull { runCatching { ImageUtils.importImage(app, it) }.getOrNull() }
            }
            if (imported.isEmpty()) {
                message.value = "Bilder konnten nicht geladen werden."
                return@launch
            }
            val base = if (replace) emptyList() else sources.value
            val all = (base + imported).take(MAX_PHOTOS)
            if (base.size + imported.size > MAX_PHOTOS) message.value = "Höchstens $MAX_PHOTOS Fotos – die übrigen wurden ignoriert."
            sources.value = all
            aiImage.value = null
            select(if (replace) 0 else (base.size).coerceAtMost(all.lastIndex))
        }
    }

    fun remove(index: Int) {
        val list = sources.value.toMutableList()
        if (index !in list.indices) return
        list.removeAt(index)
        sources.value = list
        if (list.isEmpty()) {
            source.value = null
            selected.value = 0
        } else {
            select(selected.value.coerceAtMost(list.lastIndex))
        }
    }
    val version = MutableStateFlow(0)
    val aiImage = MutableStateFlow<File?>(null)
    val aiError = MutableStateFlow<String?>(null)

    fun load(uri: Uri) = loadMany(listOf(uri))

    fun rotate() {
        val path = source.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val bmp = BitmapFactory.decodeFile(path) ?: return@launch
            val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(90f) }, true)
            File(path).outputStream().use { rotated.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            version.value++
        }
    }

    private suspend fun render(adjust: PhotoAdjust, path: String? = source.value): File? = withContext(Dispatchers.IO) {
        if (path == null) return@withContext null
        val src = BitmapFactory.decodeFile(path) ?: return@withContext null
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(src, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(buildColorMatrix(adjust)) })
        // Bearbeitete Fotos nur zwischenspeichern (zum Speichern/Teilen) – nicht in der Studio-Galerie.
        val file = File(File(app.cacheDir, "share").apply { mkdirs() }, "foto_${System.nanoTime()}.jpg")
        file.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        file
    }

    fun save(adjust: PhotoAdjust) {
        viewModelScope.launch {
            val file = render(adjust) ?: return@launch
            val ok = withContext(Dispatchers.IO) { ImageUtils.saveToGallery(app, file, "image/jpeg") }
            message.value = if (ok) "In der Galerie gespeichert (Bilder/KI-GenC)." else "Speichern fehlgeschlagen."
        }
    }

    /** Gleiche Einstellungen auf alle ausgewählten Fotos anwenden und in der Galerie speichern. */
    fun saveAll(adjust: PhotoAdjust) {
        val list = sources.value
        if (list.isEmpty()) return
        viewModelScope.launch {
            busy.value = true
            var ok = 0
            try {
                list.forEach { path ->
                    val file = render(adjust, path)
                    if (file != null && withContext(Dispatchers.IO) { ImageUtils.saveToGallery(app, file, "image/jpeg") }) ok++
                }
            } finally {
                busy.value = false
            }
            message.value = "$ok von ${list.size} Fotos in der Galerie gespeichert (Bilder/KI-GenC)."
        }
    }

    fun shareAll(adjust: PhotoAdjust, onReady: (List<File>) -> Unit) {
        viewModelScope.launch {
            val files = sources.value.mapNotNull { render(adjust, it) }
            if (files.isNotEmpty()) onReady(files)
        }
    }

    fun share(adjust: PhotoAdjust, onReady: (File) -> Unit) {
        viewModelScope.launch { render(adjust)?.let(onReady) }
    }

    /** KI-Neugestaltung: Foto beschreiben lassen und daraus ein neues Bild im gewünschten Stil erzeugen. */
    /** Schritt-für-Schritt-Status der KI-Neugestaltung (sichtbar unter dem Button). */
    val aiStatus = MutableStateFlow<String?>(null)
    private var remixJob: kotlinx.coroutines.Job? = null

    fun aiRemix(instruction: String, useAll: Boolean = false) {
        val path = source.value
        val others = if (useAll) sources.value.filterNot { it == path } else emptyList()
        if (path == null) {
            aiError.value = "Bitte zuerst ein Foto auswählen."
            return
        }
        if (remixJob?.isActive == true) return
        aiError.value = null
        aiStatus.value = "Starte …"
        remixJob = viewModelScope.launch {
            busy.value = true
            try {
                val s = app.settings.current()
                val bounds = withContext(Dispatchers.IO) {
                    BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { BitmapFactory.decodeFile(path, it) }
                }
                val w = bounds.outWidth
                val h = bounds.outHeight
                if (ImageGenerator.canEditFaithfully(s)) {
                    // Echte Bild-zu-Bild-Bearbeitung: Gesichter bleiben erhalten.
                    val refs = others.take(3)
                    aiStatus.value = if (refs.isEmpty()) "Das Foto wird gesichtstreu bearbeitet (FLUX Kontext) … ca. 10–30 Sekunden"
                    else "${refs.size + 1} Fotos werden gesichtstreu kombiniert (FLUX Kontext) … ca. 20–40 Sekunden"
                    fun b64(p: String) = android.util.Base64.encodeToString(File(p).readBytes(), android.util.Base64.NO_WRAP)
                    val main = withContext(Dispatchers.IO) { b64(path) }
                    val extra = withContext(Dispatchers.IO) { refs.map(::b64) }
                    val wish = if (refs.isEmpty()) instruction
                    else "${instruction.ifBlank { "Combine the people and elements of all images naturally into one photo" }}. " +
                        "Image 1 is the main photo; images 2–${refs.size + 1} are additional references (people, objects, style)."
                    aiImage.value = ImageGenerator.editFaithfully(app, s, main, w, h, wish, extra)
                    aiStatus.value = "Fertig ✓ (${ImageGenerator.lastProvider}) – Gesicht und Person bleiben erhalten."
                    return@launch
                }
                if (!s.hasKey) error("Bitte zuerst deinen Mistral-API-Schlüssel unter „Optionen“ eintragen.")
                aiStatus.value = if (others.isEmpty()) "Schritt 1/2: Die KI sieht sich dein Foto an …"
                else "Schritt 1/2: Die KI sieht sich ${others.size + 1} Fotos an …"
                val dataUrl = withContext(Dispatchers.IO) { ImageUtils.toDataUrl(path) } ?: error("Foto nicht lesbar.")
                val extraUrls = withContext(Dispatchers.IO) { others.take(5).mapNotNull { ImageUtils.toDataUrl(it) } }
                val description = ImageGenerator.describeImages(
                    app, s,
                    "Beschreibe dieses Foto sehr präzise als Bild-Prompt für einen Bildgenerator. Bei Personen: " +
                        "Gesichtsform, Augen, Nase, Mund, Haare, Hautton, ungefähres Alter, Ausdruck, Kleidung und Pose " +
                        "genau beschreiben (ohne Namen). Außerdem Komposition, Perspektive, Farben, Licht, Hintergrund. " +
                        (if (extraUrls.isEmpty()) "" else "Es sind mehrere Fotos: Bild 1 ist das Hauptfoto, die weiteren sind Vorlagen – " +
                            "beschreibe eine Szene, die Personen und Elemente aller Bilder vereint. ") +
                        "Höchstens 150 Wörter. Gib nur die Beschreibung aus.",
                    listOf(dataUrl) + extraUrls
                )
                if (description.isBlank()) error("Die KI konnte das Foto nicht beschreiben.")
                aiStatus.value = "Schritt 2/2: Das neue Bild wird gemalt … (ca. 10–30 Sekunden)"
                val prompt = "$description\n\nGewünschte Änderung bzw. Stil: ${instruction.ifBlank { "fotorealistisch, verbesserte Qualität" }}. " +
                    "Natürliche, unverzerrte Gesichter mit korrekten Proportionen."
                val (gw, gh) = ImageGenerator.sizeFor(w, h)
                val file = ImageGenerator.generate(app, s, prompt, gw, gh).firstOrNull()
                    ?: error("Es wurde kein Bild zurückgeschickt. Bitte erneut versuchen.")
                aiImage.value = file
                aiStatus.value = "Fertig ✓ (${ImageGenerator.lastProvider}). Hinweis: Das Bild wurde nach einer Beschreibung neu gemalt – " +
                    "Gesichter können abweichen. Für echte Gesichtstreue unter „Optionen → Bildgenerator“ einen " +
                    "Black-Forest-Labs-Schlüssel eintragen."
            } catch (e: kotlinx.coroutines.CancellationException) {
                aiStatus.value = null
                throw e
            } catch (e: Exception) {
                aiStatus.value = null
                aiError.value = e.report("Foto-Editor")
            } finally {
                busy.value = false
            }
        }
    }
}

@Composable
fun PhotoEditorScreen(vm: PhotoEditorViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val source by vm.source.collectAsStateWithLifecycle()
    val sources by vm.sources.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    var useAll by rememberSaveable { mutableStateOf(true) }
    val version by vm.version.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val aiImage by vm.aiImage.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val aiError by vm.aiError.collectAsStateWithLifecycle()
    val aiStatus by vm.aiStatus.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var showAi by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(aiImage) {
        if (aiImage != null) {
            showAi = true
        } else {
            showAi = false
        }
    }

    var brightness by rememberSaveable { mutableFloatStateOf(0f) }
    var contrast by rememberSaveable { mutableFloatStateOf(1f) }
    var saturation by rememberSaveable { mutableFloatStateOf(1f) }
    var warmth by rememberSaveable { mutableFloatStateOf(0f) }
    var filter by rememberSaveable { mutableStateOf(PhotoFilter.NONE) }
    var instruction by rememberSaveable { mutableStateOf("") }
    val adjust = PhotoAdjust(brightness, contrast, saturation, warmth, filter)

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PHOTOS)) { uris -> vm.loadMany(uris) }
    fun reset() {
        brightness = 0f; contrast = 1f; saturation = 1f; warmth = 0f; filter = PhotoFilter.NONE
    }

    ToolScaffold("Foto-Editor", onBack, modifier, message, vm::clearMessage) { inner ->
        LazyColumn(state = listState, contentPadding = toolPadding(inner), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
            item {
                OutlinedButton(
                    onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.PhotoLibrary, null)
                    Spacer(Modifier.size(8.dp))
                    Text(if (sources.isEmpty()) "Fotos auswählen (auch mehrere)" else "Weitere Fotos hinzufügen (${sources.size}/$MAX_PHOTOS)")
                }
            }
            if (sources.size > 1) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(sources.size) { i ->
                            val p = sources[i]
                            val thumb = remember(p, version) {
                                BitmapFactory.decodeFile(p, BitmapFactory.Options().apply { inSampleSize = 8 })?.asImageBitmap()
                            }
                            Box {
                                if (thumb != null) {
                                    Image(
                                        thumb, "Foto ${i + 1}", contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .border(
                                                if (i == selected) 3.dp else 0.dp,
                                                if (i == selected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                                                RoundedCornerShape(12.dp)
                                            )
                                            .clickable { vm.select(i) }
                                    )
                                }
                                Text(
                                    "${i + 1}", style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.align(Alignment.BottomStart).padding(4.dp)
                                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 5.dp),
                                    color = androidx.compose.ui.graphics.Color.White
                                )
                                IconButton(onClick = { vm.remove(i) }, modifier = Modifier.align(Alignment.TopEnd).size(26.dp)) {
                                    Icon(Icons.Outlined.Cancel, "Entfernen", tint = androidx.compose.ui.graphics.Color.White,
                                        modifier = Modifier.background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f), androidx.compose.foundation.shape.CircleShape))
                                }
                            }
                        }
                    }
                }
            }
            val path = source
            if (path != null) {
                item {
                    val aiFile = aiImage
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (aiFile != null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = !showAi, onClick = { showAi = false }, label = { Text("Original") })
                                FilterChip(selected = showAi, onClick = { showAi = true }, label = { Text("KI-Ergebnis") })
                            }
                        }
                        if (showAi && aiFile != null) {
                            val aiBitmap = remember(aiFile) { BitmapFactory.decodeFile(aiFile.absolutePath)?.asImageBitmap() }
                            if (aiBitmap != null) {
                                Image(
                                    aiBitmap, "KI-Ergebnis", contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).clip(RoundedCornerShape(16.dp))
                                )
                            } else {
                                Text("Das KI-Bild konnte nicht angezeigt werden.", color = MaterialTheme.colorScheme.error)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    val ok = ImageUtils.saveToGallery(context, aiFile)
                                    vm.message.value = if (ok) "In der Galerie gespeichert." else "Speichern fehlgeschlagen."
                                }) { Text("KI-Bild speichern") }
                                OutlinedButton(onClick = { context.shareImage(aiFile) }) { Text("Teilen") }
                            }
                        } else {
                            val bitmap = remember(path, version) { BitmapFactory.decodeFile(path)?.asImageBitmap() }
                            if (bitmap != null) {
                                Image(
                                    bitmap, "Vorschau",
                                    contentScale = ContentScale.Fit,
                                    colorFilter = ColorFilter.colorMatrix(ColorMatrix(buildColorMatrix(adjust))),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 380.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                )
                            }
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { brightness = 8f; contrast = 1.15f; saturation = 1.2f; warmth = 0.1f }) {
                            Icon(Icons.Outlined.AutoFixHigh, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text("Auto")
                        }
                        OutlinedButton(onClick = vm::rotate) {
                            Icon(Icons.AutoMirrored.Outlined.RotateRight, null, Modifier.size(18.dp))
                        }
                        OutlinedButton(onClick = ::reset) {
                            Icon(Icons.Outlined.Restore, null, Modifier.size(18.dp))
                        }
                    }
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(PhotoFilter.entries) { f ->
                            FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
                        }
                    }
                }
                item { LabeledSlider("Helligkeit", brightness, -100f..100f) { brightness = it } }
                item { LabeledSlider("Kontrast", contrast, 0.5f..1.8f) { contrast = it } }
                item { LabeledSlider("Sättigung", saturation, 0f..2f) { saturation = it } }
                item { LabeledSlider("Farbtemperatur", warmth, -1f..1f) { warmth = it } }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.save(adjust) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Outlined.Download, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text("Speichern")
                        }
                        OutlinedButton(onClick = { vm.share(adjust) { context.shareImage(it, "image/jpeg") } }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Outlined.Share, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text("Teilen")
                        }
                    }
                }
                if (sources.size > 1) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = { vm.saveAll(adjust) }, enabled = !busy, modifier = Modifier.weight(1f)) {
                                Text("Alle ${sources.size} speichern")
                            }
                            OutlinedButton(onClick = { vm.shareAll(adjust) { context.shareImages(it) } }, enabled = !busy, modifier = Modifier.weight(1f)) {
                                Text("Alle teilen")
                            }
                        }
                        Text(
                            "Die aktuellen Filter und Regler werden auf alle Fotos angewendet.",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                item { HorizontalDivider() }
                item {
                    Text("KI-Neugestaltung", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Mit Black-Forest-Labs-Schlüssel (Optionen → Bildgenerator) wird dein echtes Foto bearbeitet – " +
                            "Gesichter bleiben erhalten. Ohne Schlüssel malt die KI ein neues Bild nach einer Beschreibung, " +
                            "Gesichter können dann abweichen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item {
                    OutlinedTextField(
                        value = instruction, onValueChange = { instruction = it },
                        placeholder = { Text("z. B. „als Aquarell“, „im Comic-Stil“, „bei Sonnenuntergang“") },
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)
                    )
                }
                if (sources.size > 1) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Alle Fotos einbeziehen", style = MaterialTheme.typography.labelLarge)
                                Text(
                                    "Foto ${selected + 1} ist das Hauptbild, die anderen dienen als Vorlage – z. B. „alle Personen zusammen auf ein Gruppenfoto“.",
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(checked = useAll, onCheckedChange = { useAll = it })
                        }
                    }
                }
                item {
                    Button(onClick = { vm.aiRemix(instruction, useAll && sources.size > 1) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text(if (busy) "KI gestaltet um …" else "Mit KI neu gestalten")
                    }
                    if (busy) {
                        LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                        Text(
                            "Foto wird beschrieben und neu gemalt – das dauert meist 20–40 Sekunden. Das Ergebnis erscheint oben.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                aiStatus?.let { st ->
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                            Text(st, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                }
                aiError?.let { err ->
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                            Text("⚠️ $err", modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
                aiImage?.let { file ->
                    item {
                        val bmp = remember(file) { BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() }
                        if (bmp != null) {
                            Image(
                                bmp, "KI-Ergebnis", contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            )
                        } else {
                            Text("Bilddatei konnte nicht geöffnet werden (${file.length() / 1024} KB).", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text("%.2f".format(value), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}
