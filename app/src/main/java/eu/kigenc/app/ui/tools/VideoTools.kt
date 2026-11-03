package eu.kigenc.app.ui.tools

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.media.Image as MediaImage
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import eu.kigenc.app.KiApp
import eu.kigenc.app.ai.ApiMessage
import eu.kigenc.app.data.ImageUtils
import eu.kigenc.app.ui.components.shareFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------------------------
// Video analysieren: Standbilder + optional Tonspur → Vision-Modell
// ---------------------------------------------------------------------------------------------

class VideoAnalysisViewModel(app: KiApp) : AiToolViewModel(app) {
    val video = MutableStateFlow<Uri?>(null)
    val frames = MutableStateFlow<List<Bitmap>>(emptyList())
    val durationSec = MutableStateFlow(0L)
    val transcript = MutableStateFlow<String?>(null)

    fun load(uri: Uri) {
        viewModelScope.launch {
            video.value = uri
            transcript.value = null
            output.value = ""
            frames.value = runCatching { withContext(Dispatchers.IO) { extractFrames(uri) } }.getOrElse {
                message.value = "Video konnte nicht gelesen werden."
                emptyList()
            }
        }
    }

    private fun extractFrames(uri: Uri): List<Bitmap> {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(app, uri)
            val durMs = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            durationSec.value = durMs / 1000
            val count = 6
            return (0 until count).mapNotNull { i ->
                val timeUs = (durMs * (i + 0.5) / count).toLong() * 1000
                r.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { scaleDown(it, 768) }
            }
        } finally {
            r.release()
        }
    }

    /** Kopiert die AAC-Tonspur ohne Neukodierung in eine .m4a-Datei. */
    private fun extractAudio(uri: Uri): File? {
        val ex = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            ex.setDataSource(app, uri, null)
            val track = (0 until ex.trackCount).firstOrNull {
                ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME) == MediaFormat.MIMETYPE_AUDIO_AAC
            } ?: return null
            val out = File(File(app.cacheDir, "audio").apply { mkdirs() }, "videoton.m4a")
            val m = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = m
            val dst = m.addTrack(ex.getTrackFormat(track))
            m.start()
            ex.selectTrack(track)
            val buf = ByteBuffer.allocate(2 * 1024 * 1024)
            val info = MediaCodec.BufferInfo()
            while (true) {
                val size = ex.readSampleData(buf, 0)
                if (size < 0) break
                val flags = if (ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                info.set(0, size, ex.sampleTime, flags)
                m.writeSampleData(dst, buf, info)
                ex.advance()
            }
            m.stop()
            return out
        } catch (e: Exception) {
            return null
        } finally {
            runCatching { muxer?.release() }
            ex.release()
        }
    }

    fun analyze(question: String, withAudio: Boolean) {
        val uri = video.value ?: return
        val shots = frames.value
        if (shots.isEmpty()) {
            message.value = "Keine Standbilder gefunden."
            return
        }
        launchAi { s ->
            if (withAudio && transcript.value == null) {
                val audio = withContext(Dispatchers.IO) { extractAudio(uri) }
                transcript.value = if (audio != null) {
                    runCatching {
                        app.mistral.transcribe(s.apiKey, audio.readBytes(), "ton.m4a", "audio/mp4", null)
                    }.getOrElse { if (it is CancellationException) throw it else "" }
                } else ""
            }
            val images = withContext(Dispatchers.Default) { shots.map { ImageUtils.bitmapToDataUrl(it, 768, 70) } }
            val text = buildString {
                append(question.ifBlank { "Beschreibe ausführlich, was in diesem Video passiert, und fasse es zusammen." })
                append("\n\nHinweis: Das Video dauert etwa ${durationSec.value} Sekunden. Die Bilder sind ${images.size} ")
                append("gleichmäßig verteilte Standbilder in zeitlicher Reihenfolge.")
                transcript.value?.takeIf { it.isNotBlank() }?.let { append("\n\nTranskript der Tonspur:\n").append(it) }
            }
            output.value = ""
            val sb = StringBuilder()
            app.mistral.streamChat(
                s.apiKey, s.chatModel,
                listOf(
                    ApiMessage("system", "Du analysierst Videos anhand von Standbildern (und ggf. Transkript). Antworte auf Deutsch in Markdown. Kennzeichne Vermutungen als solche."),
                    ApiMessage("user", text, extraImages = images)
                ),
                0.3
            ).collect {
                sb.append(it)
                output.value = sb.toString()
            }
        }
    }
}

private fun scaleDown(bmp: Bitmap, maxSide: Int): Bitmap {
    val scale = min(1f, maxSide.toFloat() / max(bmp.width, bmp.height))
    return if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).roundToInt(), (bmp.height * scale).roundToInt(), true) else bmp
}

@Composable
fun VideoAnalysisScreen(vm: VideoAnalysisViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val video by vm.video.collectAsStateWithLifecycle()
    val frames by vm.frames.collectAsStateWithLifecycle()
    val output by vm.output.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val transcript by vm.transcript.collectAsStateWithLifecycle()
    var question by rememberSaveable { mutableStateOf("") }
    var withAudio by rememberSaveable { mutableStateOf(true) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(vm::load) }

    ToolScaffold("Video analysieren", onBack, modifier, message, vm::clearMessage) { inner ->
        LazyColumn(contentPadding = toolPadding(inner), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
            item {
                OutlinedButton(
                    onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.Movie, null); Spacer(Modifier.size(8.dp))
                    Text(if (video == null) "Video auswählen" else "Anderes Video wählen")
                }
            }
            if (frames.isNotEmpty()) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(frames) { f ->
                            Image(
                                f.asImageBitmap(), null, contentScale = ContentScale.Crop,
                                modifier = Modifier.size(96.dp, 64.dp).clip(RoundedCornerShape(8.dp))
                            )
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        value = question, onValueChange = { question = it },
                        placeholder = { Text("Was möchtest du wissen? (leer = Zusammenfassung)") },
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), minLines = 2
                    )
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = withAudio, onCheckedChange = { withAudio = it })
                        Text("Ton mit auswerten (Transkription)")
                    }
                }
                item {
                    Button(onClick = { vm.analyze(question, withAudio) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text(if (busy) "Analysiere …" else "Video analysieren")
                    }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
            if (output.isNotEmpty() || busy) item { ResultCard(output, busy, "video") }
            transcript?.takeIf { it.isNotBlank() }?.let { t ->
                item { ResultCard("**Transkript:**\n\n$t", false, "video-transcript") }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Einfaches Video erstellen: Diashow aus Fotos (H.264/MP4, direkt auf dem Gerät)
// ---------------------------------------------------------------------------------------------

object SlideshowEncoder {
    private const val W = 1280
    private const val H = 720
    private const val FPS = 15
    private const val MIME = MediaFormat.MIMETYPE_VIDEO_AVC

    suspend fun encode(
        context: Context,
        uris: List<Uri>,
        secondsPerSlide: Float,
        title: String?,
        onProgress: (Float) -> Unit,
    ): File = withContext(Dispatchers.Default) {
        val out = File(File(context.filesDir, "videos").apply { mkdirs() }, "diashow_${System.currentTimeMillis()}.mp4")
        val format = MediaFormat.createVideoFormat(MIME, W, H).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, 5_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val codec = MediaCodec.createEncoderByType(MIME)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        val muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val info = MediaCodec.BufferInfo()
        var track = -1
        var started = false

        fun drain(endOfStream: Boolean) {
            var idle = 0
            while (true) {
                val index = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    index == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (!endOfStream || ++idle > 500) return
                    }
                    index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        track = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        started = true
                    }
                    index >= 0 -> {
                        val buf = codec.getOutputBuffer(index)
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                        if (buf != null && info.size > 0 && started) {
                            buf.position(info.offset)
                            buf.limit(info.offset + info.size)
                            muxer.writeSampleData(track, buf, info)
                        }
                        codec.releaseOutputBuffer(index, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                    }
                }
            }
        }

        fun nextInput(): Int {
            while (true) {
                val i = codec.dequeueInputBuffer(10_000)
                if (i >= 0) return i
                drain(false)
            }
        }

        try {
            val framesPerSlide = max(1, (secondsPerSlide * FPS).roundToInt())
            val total = framesPerSlide * uris.size
            var frame = 0L
            uris.forEachIndexed { slideIndex, uri ->
                ensureActive()
                val slide = renderSlide(context, uri, if (slideIndex == 0) title else null)
                val yuv = toYuv(slide)
                slide.recycle()
                repeat(framesPerSlide) {
                    val idx = nextInput()
                    val image = codec.getInputImage(idx) ?: error("Der Video-Encoder dieses Geräts wird nicht unterstützt.")
                    writeYuv(image, yuv)
                    codec.queueInputBuffer(idx, 0, W * H * 3 / 2, frame * 1_000_000L / FPS, 0)
                    frame++
                    drain(false)
                    onProgress(frame.toFloat() / total)
                }
            }
            val idx = nextInput()
            codec.queueInputBuffer(idx, 0, 0, frame * 1_000_000L / FPS, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            drain(true)
        } finally {
            runCatching { codec.stop() }
            codec.release()
            if (started) runCatching { muxer.stop() }
            muxer.release()
        }
        out
    }

    private fun renderSlide(context: Context, uri: Uri, title: String?): Bitmap {
        val src = decode(context, uri)
        val canvasBmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(canvasBmp)
        canvas.drawColor(Color.BLACK)
        if (src != null) {
            val scale = min(W.toFloat() / src.width, H.toFloat() / src.height)
            val dw = src.width * scale
            val dh = src.height * scale
            val left = (W - dw) / 2f
            val top = (H - dh) / 2f
            canvas.drawBitmap(src, null, RectF(left, top, left + dw, top + dh), Paint(Paint.FILTER_BITMAP_FLAG))
            src.recycle()
        }
        if (!title.isNullOrBlank()) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 64f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                setShadowLayer(12f, 0f, 4f, Color.BLACK)
            }
            canvas.drawText(title, W / 2f, H - 72f, paint)
        }
        return canvasBmp
    }

    private fun decode(context: Context, uri: Uri): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val s = min(1f, 1920f / max(info.size.width, info.size.height))
                if (s < 1f) decoder.setTargetSize((info.size.width * s).roundToInt(), (info.size.height * s).roundToInt())
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 2 })
            }
        }
    }.getOrNull()

    private class Yuv(val y: ByteArray, val u: ByteArray, val v: ByteArray)

    private fun toYuv(bmp: Bitmap): Yuv {
        val px = IntArray(W * H)
        bmp.getPixels(px, 0, W, 0, 0, W, H)
        val y = ByteArray(W * H)
        val u = ByteArray(W * H / 4)
        val v = ByteArray(W * H / 4)
        for (j in 0 until H) {
            for (i in 0 until W) {
                val c = px[j * W + i]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                y[j * W + i] = (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16).coerceIn(0, 255).toByte()
                if (j % 2 == 0 && i % 2 == 0) {
                    val k = (j / 2) * (W / 2) + i / 2
                    u[k] = (((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128).coerceIn(0, 255).toByte()
                    v[k] = (((112 * r - 94 * g - 18 * b + 128) shr 8) + 128).coerceIn(0, 255).toByte()
                }
            }
        }
        return Yuv(y, u, v)
    }

    private fun writeYuv(image: MediaImage, yuv: Yuv) {
        val planes = image.planes
        writePlane(planes[0], yuv.y, W, H)
        writePlane(planes[1], yuv.u, W / 2, H / 2)
        writePlane(planes[2], yuv.v, W / 2, H / 2)
    }

    private fun writePlane(plane: MediaImage.Plane, data: ByteArray, w: Int, h: Int) {
        val buf = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        for (row in 0 until h) {
            if (pixelStride == 1) {
                buf.position(row * rowStride)
                buf.put(data, row * w, w)
            } else {
                var pos = row * rowStride
                val base = row * w
                for (col in 0 until w) {
                    buf.put(pos, data[base + col])
                    pos += pixelStride
                }
            }
        }
    }
}

class SlideshowViewModel(app: KiApp) : AiToolViewModel(app) {
    val images = MutableStateFlow<List<Uri>>(emptyList())
    val progress = MutableStateFlow<Float?>(null)
    val result = MutableStateFlow<File?>(null)

    fun setImages(uris: List<Uri>) {
        images.value = uris
        result.value = null
    }

    /** Neue Datei für eine Live-Aufnahme mit der Kamera. */
    fun newRecordingFile(): File =
        File(File(app.filesDir, "videos").apply { mkdirs() }, "aufnahme_${System.currentTimeMillis()}.mp4")

    fun onRecorded(file: File?, ok: Boolean) {
        // Manche Kamera-Apps melden „abgebrochen“, obwohl gespeichert wurde – daher zählt die Datei.
        if (file != null && file.exists() && file.length() > 0) {
            result.value = file
            message.value = "Aufnahme fertig – du kannst sie abspielen, speichern oder teilen."
        } else {
            file?.delete()
            message.value = if (ok) "Die Kamera-App hat kein Video gespeichert." else "Aufnahme abgebrochen."
        }
    }

    fun create(seconds: Float, title: String) {
        val list = images.value
        if (list.isEmpty() || progress.value != null) return
        viewModelScope.launch {
            progress.value = 0f
            try {
                result.value = SlideshowEncoder.encode(app, list, seconds, title.trim().ifBlank { null }) { progress.value = it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message.value = "Video konnte nicht erstellt werden: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                progress.value = null
            }
        }
    }

    fun saveToGallery() {
        val file = result.value ?: return
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { ImageUtils.saveVideoToGallery(app, file) }
            message.value = if (ok) "In der Galerie gespeichert (Filme/KI-GenC)." else "Speichern fehlgeschlagen."
        }
    }
}

@Composable
fun SlideshowScreen(vm: SlideshowViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val images by vm.images.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val result by vm.result.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var seconds by rememberSaveable { mutableFloatStateOf(3f) }
    var title by rememberSaveable { mutableStateOf("") }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(30)) { uris ->
        if (uris.isNotEmpty()) vm.setImages(uris)
    }
    var recordingPath by rememberSaveable { mutableStateOf<String?>(null) }
    val capture = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { ok ->
        val file = recordingPath?.let(::File)
        vm.onRecorded(file, ok)
        recordingPath = null
    }
    val startRecording = {
        val file = vm.newRecordingFile()
        recordingPath = file.absolutePath
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        try {
            capture.launch(uri)
        } catch (_: ActivityNotFoundException) {
            vm.message.value = "Keine Kamera-App gefunden."
        }
    }

    ToolScaffold("Video erstellen", onBack, modifier, message, vm::clearMessage) { inner ->
        LazyColumn(contentPadding = toolPadding(inner), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
            item {
                Text(
                    "Nimm ein Video live mit der Handykamera auf oder erstelle aus deinen Fotos ein Diashow-Video (MP4, 720p). " +
                        "Alles passiert direkt auf deinem Handy.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                Button(onClick = startRecording, enabled = progress == null, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Videocam, null); Spacer(Modifier.size(8.dp))
                    Text("Video live aufnehmen")
                }
            }
            item {
                OutlinedButton(
                    onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.PhotoLibrary, null); Spacer(Modifier.size(8.dp))
                    Text(if (images.isEmpty()) "Fotos auswählen (bis 30)" else "${images.size} Fotos gewählt – ändern")
                }
            }
            if (images.isNotEmpty()) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(images) { uri ->
                            val thumb = remember(uri) {
                                runCatching {
                                    context.contentResolver.openInputStream(uri)?.use {
                                        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 8 })
                                    }?.asImageBitmap()
                                }.getOrNull()
                            }
                            if (thumb != null) {
                                Image(thumb, null, contentScale = ContentScale.Crop, modifier = Modifier.size(72.dp).clip(RoundedCornerShape(8.dp)))
                            }
                        }
                    }
                }
                item { LabeledSlider("Sekunden pro Foto", seconds, 1f..6f) { seconds = it } }
                item {
                    OutlinedTextField(
                        value = title, onValueChange = { title = it },
                        label = { Text("Titel (optional, auf dem ersten Bild)") },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Button(onClick = { vm.create(seconds, title) }, enabled = progress == null, modifier = Modifier.fillMaxWidth()) {
                        Text(if (progress != null) "Erstelle Video … ${((progress ?: 0f) * 100).toInt()} %" else "Video erstellen")
                    }
                    progress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
                }
            }
            result?.let { file ->
                item {
                    Text("Video bereit: ${file.name} (${file.length() / 1024} KB)", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "video/mp4")
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                            runCatching { context.startActivity(intent) }
                        }) {
                            Icon(Icons.Outlined.PlayCircle, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text("Abspielen")
                        }
                        OutlinedButton(onClick = vm::saveToGallery) { Text("Speichern") }
                        OutlinedButton(onClick = { context.shareFile(file, "video/mp4", "Video teilen") }) { Text("Teilen") }
                    }
                }
            }
        }
    }
}
