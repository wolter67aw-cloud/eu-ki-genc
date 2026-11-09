package eu.kigenc.app.ui.tools

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Path
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewModelScope
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import eu.kigenc.app.KiApp
import eu.kigenc.app.HomeActivity
import eu.kigenc.app.R
import eu.kigenc.app.ai.ApiMessage
import eu.kigenc.app.ai.MistralClient
import eu.kigenc.app.data.Idea
import eu.kigenc.app.data.IdeaKind
import eu.kigenc.app.data.IdeaStore
import eu.kigenc.app.data.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Ein Strich auf dem Whiteboard. */
data class SketchStroke(val points: List<Offset>, val color: Color, val width: Float)

sealed interface IdeaScreen {
    data object Overview : IdeaScreen
    data class Edit(val kind: IdeaKind) : IdeaScreen
    data class Detail(val id: String) : IdeaScreen
    data object Cross : IdeaScreen
}

val MOODS = listOf("😊", "🤩", "🤔", "😐", "😴", "😔", "😤", "❤️")

fun formatDate(ms: Long): String = SimpleDateFormat("dd.MM.yyyy, HH:mm", Locale.GERMANY).format(Date(ms))

class IdeasViewModel(app: KiApp) : AiToolViewModel(app) {
    val ideas = MutableStateFlow<List<Idea>>(emptyList())
    val screen = MutableStateFlow<IdeaScreen>(IdeaScreen.Overview)
    val tagging = MutableStateFlow<Set<String>>(emptySet())

    // Entwurf
    val draftTitle = MutableStateFlow("")
    val draftText = MutableStateFlow("")
    val draftMood = MutableStateFlow("")
    val draftImage = MutableStateFlow<File?>(null)
    val draftAudio = MutableStateFlow<File?>(null)
    val draftOpenAt = MutableStateFlow<Long?>(null)
    val strokes = mutableStateListOf<SketchStroke>()

    // Sprachmemo
    val recording = MutableStateFlow(false)
    val elapsedSec = MutableStateFlow(0)
    val transcribing = MutableStateFlow(false)
    private var recorder: MediaRecorder? = null
    private var timer: Job? = null

    // Wiedergabe
    val playingId = MutableStateFlow<String?>(null)
    private var player: MediaPlayer? = null

    // Ideen-Kreuzung
    val crossA = MutableStateFlow<Idea?>(null)
    val crossB = MutableStateFlow<Idea?>(null)

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { ideas.value = IdeaStore.list(app) }
    }

    fun open(s: IdeaScreen) {
        if (s is IdeaScreen.Edit) resetDraft(s.kind)
        if (s is IdeaScreen.Cross) { output.value = ""; if (crossA.value == null) randomPair() }
        if (s is IdeaScreen.Detail) output.value = ""
        screen.value = s
    }

    fun back(): Boolean {
        if (screen.value == IdeaScreen.Overview) return false
        if (recording.value) stopRecording(transcribe = false)
        stopPlayback()
        screen.value = IdeaScreen.Overview
        return true
    }

    private fun resetDraft(kind: IdeaKind) {
        draftTitle.value = ""
        draftText.value = ""
        draftMood.value = ""
        draftImage.value = null
        draftAudio.value = null
        draftOpenAt.value = if (kind == IdeaKind.CAPSULE) System.currentTimeMillis() + 365L * 24 * 3600 * 1000 else null
        strokes.clear()
        output.value = ""
    }

    fun setImage(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val tmp = File(ImageUtils.importImage(app, uri))
                    val target = IdeaStore.newFile(app, "jpg")
                    tmp.copyTo(target, overwrite = true)
                    tmp.delete()
                    target
                }
            }.onSuccess { draftImage.value = it }
                .onFailure { message.value = "Bild konnte nicht geladen werden." }
        }
    }

    // MARK: Sprachmemo

    fun startRecording() {
        if (recording.value) return
        val file = IdeaStore.newFile(app, "m4a")
        try {
            val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(app) else @Suppress("DEPRECATION") MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioSamplingRate(44_100)
            r.setAudioEncodingBitRate(96_000)
            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            draftAudio.value?.delete()
            draftAudio.value = file
            recording.value = true
            elapsedSec.value = 0
            timer = viewModelScope.launch {
                while (true) { delay(1000); elapsedSec.value++ }
            }
        } catch (e: Exception) {
            message.value = "Aufnahme nicht möglich: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    fun stopRecording(transcribe: Boolean = true) {
        timer?.cancel()
        val ok = runCatching { recorder?.stop() }.isSuccess
        recorder?.release()
        recorder = null
        recording.value = false
        if (!ok) {
            draftAudio.value?.delete()
            draftAudio.value = null
            message.value = "Aufnahme zu kurz."
            return
        }
        if (transcribe) transcribeDraft()
    }

    private fun transcribeDraft() {
        val file = draftAudio.value ?: return
        viewModelScope.launch {
            val s = app.settings.current()
            if (!s.hasKey) {
                message.value = "Ohne Mistral-Schlüssel wird nur der Ton gespeichert (keine Transkription)."
                return@launch
            }
            transcribing.value = true
            try {
                val text = app.mistral.transcribe(s.apiKey, withContext(Dispatchers.IO) { file.readBytes() }, file.name, "audio/mp4", null)
                draftText.value = listOf(draftText.value, text).filter { it.isNotBlank() }.joinToString("\n")
                if (draftTitle.value.isBlank()) draftTitle.value = text.lineSequence().firstOrNull().orEmpty().take(60)
            } catch (e: Exception) {
                message.value = "Transkription fehlgeschlagen: " + e.message
            } finally {
                transcribing.value = false
            }
        }
    }

    fun togglePlay(idea: Idea) {
        if (playingId.value == idea.id) { stopPlayback(); return }
        stopPlayback()
        val f = IdeaStore.media(app, idea.audioFile) ?: return
        runCatching {
            player = MediaPlayer().apply {
                setDataSource(f.absolutePath)
                setOnCompletionListener { stopPlayback() }
                prepare()
                start()
            }
            playingId.value = idea.id
        }.onFailure { message.value = "Wiedergabe nicht möglich." }
    }

    fun stopPlayback() {
        runCatching { player?.stop() }
        player?.release()
        player = null
        playingId.value = null
    }

    // MARK: Speichern

    fun save(kind: IdeaKind, canvasWidth: Int = 0, canvasHeight: Int = 0) {
        viewModelScope.launch {
            var image = draftImage.value
            if (kind == IdeaKind.SKETCH) {
                if (strokes.isEmpty()) { message.value = "Die Skizze ist noch leer."; return@launch }
                image = withContext(Dispatchers.IO) { renderSketch(canvasWidth, canvasHeight) }
            }
            val text = draftText.value.trim()
            if (text.isBlank() && image == null && draftAudio.value == null) {
                message.value = "Bitte etwas eingeben."
                return@launch
            }
            val title = draftTitle.value.trim().ifBlank {
                text.lineSequence().firstOrNull()?.take(60)?.takeIf { it.isNotBlank() } ?: "${kind.label} ${formatDate(System.currentTimeMillis())}"
            }
            val idea = Idea(
                kind = kind, title = title, text = text, mood = draftMood.value,
                imageFile = image?.name, audioFile = draftAudio.value?.name,
                openAt = if (kind == IdeaKind.CAPSULE) draftOpenAt.value else null,
            )
            IdeaStore.upsert(app, idea)
            draftImage.value = null
            draftAudio.value = null
            if (kind == IdeaKind.CAPSULE) scheduleCapsule(idea)
            refresh()
            screen.value = if (idea.locked) IdeaScreen.Overview else IdeaScreen.Detail(idea.id)
            if (idea.locked) message.value = "Zeitkapsel verschlossen bis ${formatDate(idea.openAt!!)}."
            autoTag(idea)
        }
    }

    private fun renderSketch(w: Int, h: Int): File {
        val width = w.coerceAtLeast(300)
        val height = h.coerceAtLeast(300)
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        c.drawColor(android.graphics.Color.WHITE)
        val paint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        strokes.forEach { s ->
            paint.color = s.color.toArgb()
            paint.strokeWidth = s.width
            if (s.points.size == 1) {
                paint.style = Paint.Style.FILL
                c.drawCircle(s.points[0].x, s.points[0].y, s.width / 2, paint)
                paint.style = Paint.Style.STROKE
            } else {
                val p = Path()
                s.points.forEachIndexed { i, o -> if (i == 0) p.moveTo(o.x, o.y) else p.lineTo(o.x, o.y) }
                c.drawPath(p, paint)
            }
        }
        val file = IdeaStore.newFile(app, "png")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        return file
    }

    private fun scheduleCapsule(idea: Idea) {
        val at = idea.openAt ?: return
        val delayMs = at - System.currentTimeMillis()
        if (delayMs <= 0) return
        val req = OneTimeWorkRequestBuilder<CapsuleWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("title" to idea.title))
            .build()
        WorkManager.getInstance(app).enqueueUniqueWork("capsule_${idea.id}", ExistingWorkPolicy.REPLACE, req)
    }

    // MARK: KI-Tags

    private fun autoTag(idea: Idea) {
        if (idea.locked) {
            update(idea.copy(tags = listOf("zeitkapsel")))
            return
        }
        viewModelScope.launch {
            val s = app.settings.current()
            if (!s.hasKey) return@launch
            tagging.value = tagging.value + idea.id
            try {
                val image = IdeaStore.media(app, idea.imageFile)?.let { ImageUtils.toDataUrl(it.absolutePath) }
                val prompt = "Schlage 3 bis 5 kurze, treffende Schlagwörter (deutsch, kleingeschrieben, je 1–2 Wörter) für diesen Eintrag vor, " +
                    "damit man ihn später wiederfindet. Antworte nur als JSON: {\"tags\":[\"…\"]}\n\n" +
                    "Art: ${idea.kind.label}\nTitel: ${idea.title}\nInhalt: ${idea.text.take(4000)}"
                val json = app.mistral.complete(
                    s.apiKey, MistralClient.FALLBACK_MODEL,
                    listOf(ApiMessage("user", prompt, image)), json = true, temperature = 0.2
                )
                val arr = JSONObject(json).optJSONArray("tags")
                val tags = if (arr == null) emptyList() else List(arr.length()) { arr.optString(it).trim().lowercase().removePrefix("#") }
                    .filter { it.isNotBlank() && it.length <= 30 }.distinct().take(5)
                if (tags.isNotEmpty()) {
                    val current = IdeaStore.list(app).firstOrNull { it.id == idea.id } ?: return@launch
                    update(current.copy(tags = (current.tags + tags).distinct()))
                }
            } catch (e: Exception) {
                eu.kigenc.app.data.ErrorLog.log("KI-Tags", e)
            } finally {
                tagging.value = tagging.value - idea.id
            }
        }
    }

    fun retag(idea: Idea) = autoTag(idea.copy(tags = emptyList()).also { update(it) })

    fun update(idea: Idea) {
        viewModelScope.launch {
            IdeaStore.upsert(app, idea)
            refresh()
        }
    }

    fun delete(idea: Idea) {
        viewModelScope.launch {
            WorkManager.getInstance(app).cancelUniqueWork("capsule_${idea.id}")
            stopPlayback()
            IdeaStore.delete(app, idea)
            refresh()
            screen.value = IdeaScreen.Overview
        }
    }

    // MARK: KI-Auswertung (Skizze / Foto)

    fun analyze(idea: Idea) {
        val image = IdeaStore.media(app, idea.imageFile)?.let { ImageUtils.toDataUrl(it.absolutePath) }
        val instruction = when (idea.kind) {
            IdeaKind.SKETCH -> "Das ist eine Handskizze (z. B. Mindmap, Entwurf, Diagramm). Beschreibe, was dargestellt ist, " +
                "übertrage Text und Struktur sauber in Markdown (Mindmaps als verschachtelte Liste) und gib 3 Ideen, wie man sie weiterentwickeln kann."
            else -> "Werte diese Momentaufnahme aus: Was ist zu sehen bzw. worum geht es, welche Idee steckt darin, und welche 3 konkreten nächsten Schritte bieten sich an?"
        }
        stream(
            "Du bist ein kreativer Assistent. Antworte auf Deutsch in Markdown.",
            ApiMessage("user", "$instruction\n\nTitel: ${idea.title}\nNotiz: ${idea.text}\nStimmung: ${idea.mood}", image),
            model = { if (image != null) MistralClient.FALLBACK_MODEL else it.chatModel },
            saveAs = "Ideen"
        )
    }

    // MARK: Ideen-Kreuzung

    fun available(): List<Idea> = ideas.value.filterNot { it.locked || it.kind == IdeaKind.CAPSULE && it.text.isBlank() }

    fun randomPair() {
        val pool = available().shuffled()
        crossA.value = pool.getOrNull(0)
        crossB.value = pool.getOrNull(1)
        output.value = ""
    }

    fun cross() {
        val a = crossA.value ?: return
        val b = crossB.value ?: return
        stream(
            "Du bist ein Ideen-Generator für Brainstorming. Antworte auf Deutsch in Markdown.",
            ApiMessage(
                "user",
                "Kreuze diese zwei Ideen kreativ miteinander. Entwickle daraus 3 neue, überraschende, aber konkret umsetzbare Ansätze " +
                    "(je: kurzer Titel, 2–3 Sätze, erster Schritt). Zum Schluss: welcher Ansatz am vielversprechendsten ist und warum.\n\n" +
                    "Idee A – ${a.title}:\n${a.text.take(3000)}\n\nIdee B – ${b.title}:\n${b.text.take(3000)}"
            ),
            temperature = 0.9
        )
    }

    fun saveCross() {
        val a = crossA.value ?: return
        val b = crossB.value ?: return
        val text = output.value.trim()
        if (text.isBlank()) return
        val idea = Idea(kind = IdeaKind.CROSS, title = "${a.title.take(28)} × ${b.title.take(28)}", text = text)
        viewModelScope.launch {
            IdeaStore.upsert(app, idea)
            refresh()
            message.value = "Als neue Idee gespeichert."
            autoTag(idea)
        }
    }

    override fun onCleared() {
        recorder?.release()
        player?.release()
        super.onCleared()
    }
}

/** Meldet, wenn eine Zeitkapsel geöffnet werden kann. */
class CapsuleWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return Result.success()
        val title = inputData.getString("title").orEmpty()
        val open = PendingIntent.getActivity(
            ctx, 3, Intent(ctx, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(ctx, KiApp.CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_update)
            .setContentTitle("⏳ Deine Zeitkapsel ist offen")
            .setContentText(title.ifBlank { "Werkzeuge → Ideen-Sammlung" })
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(("capsule$title").hashCode(), n) }
        return Result.success()
    }
}
