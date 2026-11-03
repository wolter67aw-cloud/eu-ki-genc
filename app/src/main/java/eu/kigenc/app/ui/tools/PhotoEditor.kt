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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
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
    val source = MutableStateFlow<String?>(null)
    val version = MutableStateFlow(0)
    val aiImage = MutableStateFlow<File?>(null)
    val aiError = MutableStateFlow<String?>(null)

    fun load(uri: Uri) {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { ImageUtils.importImage(app, uri) } }
                .onSuccess { source.value = it; aiImage.value = null; version.value++ }
                .onFailure { message.value = "Bild konnte nicht geladen werden." }
        }
    }

    fun rotate() {
        val path = source.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val bmp = BitmapFactory.decodeFile(path) ?: return@launch
            val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(90f) }, true)
            File(path).outputStream().use { rotated.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            version.value++
        }
    }

    private suspend fun render(adjust: PhotoAdjust): File? = withContext(Dispatchers.IO) {
        val path = source.value ?: return@withContext null
        val src = BitmapFactory.decodeFile(path) ?: return@withContext null
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(src, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(buildColorMatrix(adjust)) })
        val file = File(ImageUtils.generatedDir(app), "foto_${System.currentTimeMillis()}.jpg")
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

    fun share(adjust: PhotoAdjust, onReady: (File) -> Unit) {
        viewModelScope.launch { render(adjust)?.let(onReady) }
    }

    /** KI-Neugestaltung: Foto beschreiben lassen und daraus ein neues Bild im gewünschten Stil erzeugen. */
    fun aiRemix(instruction: String) {
        val path = source.value ?: return
        aiError.value = null
        launchAi { s ->
            try {
                val dataUrl = ImageUtils.toDataUrl(path) ?: error("Bild nicht lesbar")
                val description = app.mistral.complete(
                    s.apiKey, s.chatModel,
                    listOf(
                        ApiMessage(
                            "user",
                            "Beschreibe dieses Foto präzise als Bild-Prompt für einen Bildgenerator: Motiv, Personen " +
                                "(ohne Namen), Komposition, Perspektive, Farben, Licht, Stimmung. Höchstens 120 Wörter. " +
                                "Gib nur die Beschreibung aus.",
                            imageDataUrl = dataUrl
                        )
                    )
                ).take(1500)
                val prompt = "$description\n\nGewünschte Änderung bzw. Stil: ${instruction.ifBlank { "fotorealistisch, verbesserte Qualität" }}"
                val file = ImageGenerator.generate(app, s, prompt).firstOrNull()
                    ?: error("Mistral hat kein Bild zurückgeschickt. Bitte erneut versuchen.")
                aiImage.value = file
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                aiError.value = e.friendlyMessage()
                throw e
            }
        }
    }
}

@Composable
fun PhotoEditorScreen(vm: PhotoEditorViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val source by vm.source.collectAsStateWithLifecycle()
    val version by vm.version.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val aiImage by vm.aiImage.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val aiError by vm.aiError.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var showAi by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(aiImage) {
        if (aiImage != null) {
            showAi = true
            listState.animateScrollToItem(0)
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

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(vm::load) }
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
                    Text(if (source == null) "Foto auswählen" else "Anderes Foto wählen")
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
                            Icon(Icons.Outlined.RotateRight, null, Modifier.size(18.dp))
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
                item { HorizontalDivider() }
                item {
                    Text("KI-Neugestaltung", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Die KI beschreibt dein Foto und erzeugt daraus ein neues Bild im gewünschten Stil.",
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
                item {
                    Button(onClick = { vm.aiRemix(instruction) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
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
                aiError?.let { err ->
                    item {
                        Text("⚠️ $err", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
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
