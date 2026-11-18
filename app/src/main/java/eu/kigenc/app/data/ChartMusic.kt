package eu.kigenc.app.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Ein Chart-Titel mit 30-Sekunden-Hörprobe (Deezer). */
data class ChartTrack(
    val title: String,
    val artist: String,
    val previewUrl: String,
    val link: String,
    val genre: String,
)

/** Musik-Genres der Deezer-Charts (Kennung → Name). 0 = alle Genres. */
val MUSIC_GENRES: List<Pair<Int, String>> = listOf(
    0 to "Alle Charts",
    132 to "Pop",
    116 to "Hip-Hop & Rap",
    152 to "Rock",
    113 to "Dance",
    106 to "Electro",
    165 to "R&B",
    85 to "Alternative",
    464 to "Metal",
    169 to "Soul & Funk",
    197 to "Latin",
    144 to "Reggae",
    84 to "Country",
    129 to "Jazz",
    98 to "Klassik",
)

fun musicGenreName(id: Int): String = MUSIC_GENRES.firstOrNull { it.first == id }?.second ?: "Alle Charts"

/**
 * Spielt beim App-Start die aktuelle Nr. 1 der Charts (gewähltes Genre) als 30-Sekunden-Hörprobe.
 * Quelle: öffentliche Deezer-API (ohne Schlüssel, ohne Anmeldung). Spielt nicht im Lautlos-/Vibrationsmodus
 * und nicht, wenn bereits andere Musik läuft.
 */
class ChartMusic(context: Context) {
    private val appContext = context.applicationContext
    private val audio = appContext.getSystemService(AudioManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var player: MediaPlayer? = null
    private var job: Job? = null
    private var focus: AudioFocusRequest? = null
    private var lastAutoPlay = 0L

    private val _track = MutableStateFlow<ChartTrack?>(null)
    /** Gerade angezeigter bzw. gespielter Titel (null = nichts). */
    val track: StateFlow<ChartTrack?> = _track.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Beim App-Start: nur wenn eingeschaltet, höchstens alle 10 Minuten, nicht lautlos, keine andere Musik. */
    fun playOnStart(settings: AppSettings) {
        if (!settings.startMusic) return
        val now = System.currentTimeMillis()
        if (now - lastAutoPlay < AUTO_PAUSE_MS) return
        lastAutoPlay = now
        if (audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        if (audio.isMusicActive) return
        play(settings.musicGenre, quietOnError = true)
    }

    /** Nr. 1 des Genres laden und abspielen (auch zum Ausprobieren in den Optionen). */
    fun play(genreId: Int, quietOnError: Boolean = false) {
        stop()
        _error.value = null
        job = scope.launch {
            _loading.value = true
            try {
                val t = fetchTop(genreId)
                _track.value = t
                start(t)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                ErrorLog.log("Musik beim Start", e)
                _track.value = null
                if (!quietOnError) _error.value = "Die Charts konnten gerade nicht geladen werden."
            } finally {
                _loading.value = false
            }
        }
    }

    private suspend fun fetchTop(genreId: Int): ChartTrack = withContext(Dispatchers.IO) {
        // Leere Genre-Charts → auf die allgemeinen Charts ausweichen
        loadChart(genreId) ?: (if (genreId != 0) loadChart(0) else null)
            ?: throw IllegalStateException("Keine Chart-Titel mit Hörprobe gefunden")
    }

    private fun loadChart(genreId: Int): ChartTrack? {
        val c = URL("https://api.deezer.com/chart/$genreId/tracks?limit=10").openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        c.setRequestProperty("Accept", "application/json")
        try {
            if (c.responseCode !in 200..299) throw IllegalStateException("Deezer HTTP ${c.responseCode}")
            val json = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val data = json.optJSONArray("data") ?: return null
            for (i in 0 until data.length()) {
                val o = data.getJSONObject(i)
                val preview = o.optString("preview")
                if (preview.isBlank()) continue
                return ChartTrack(
                    title = o.optString("title_short").ifBlank { o.optString("title") },
                    artist = o.optJSONObject("artist")?.optString("name").orEmpty(),
                    previewUrl = preview,
                    link = o.optString("link").ifBlank { "https://www.deezer.com/track/${o.optLong("id")}" },
                    genre = musicGenreName(genreId),
                )
            }
            return null
        } finally {
            c.disconnect()
        }
    }

    private fun start(t: ChartTrack) {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener { change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                    scope.launch { stop() }
                }
            }
            .build()
        if (audio.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            _track.value = null
            return
        }
        focus = request
        val mp = MediaPlayer()
        player = mp
        mp.setAudioAttributes(attrs)
        try {
            mp.setDataSource(t.previewUrl)
        } catch (e: Exception) {
            ErrorLog.log("Musik beim Start", e)
            stop()
            return
        }
        mp.setVolume(0f, 0f)
        mp.setOnPreparedListener {
            it.start()
            // Sanft einblenden
            scope.launch {
                for (step in 1..10) {
                    if (player !== mp) return@launch
                    val v = step / 10f * MAX_VOLUME
                    runCatching { mp.setVolume(v, v) }
                    delay(150)
                }
            }
        }
        mp.setOnCompletionListener { scope.launch { stop() } }
        mp.setOnErrorListener { _, what, extra ->
            ErrorLog.log("Musik beim Start", message = "Wiedergabe-Fehler $what/$extra")
            scope.launch { stop() }
            true
        }
        mp.prepareAsync()
    }

    /** Wiedergabe beenden und Anzeige ausblenden. */
    fun stop() {
        job?.cancel()
        job = null
        _loading.value = false
        player?.let { p ->
            player = null
            runCatching { p.stop() }
            p.release()
        }
        focus?.let { audio.abandonAudioFocusRequest(it) }
        focus = null
        _track.value = null
    }

    fun clearError() { _error.value = null }

    private companion object {
        const val AUTO_PAUSE_MS = 10 * 60 * 1000L
        const val MAX_VOLUME = 0.6f
    }
}
