package eu.kigenc.app.ui.components

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/** Vorlesen über die Text-to-Speech-Engine des Geräts (offline, keine Daten verlassen das Handy). */
class TtsController(context: Context) : TextToSpeech.OnInitListener {

    private val tts = TextToSpeech(context.applicationContext, this)
    private var ready = false
    private var pending: Pair<String, String>? = null
    private var lastUtterance: String? = null

    private val _speakingId = MutableStateFlow<String?>(null)
    val speakingId: StateFlow<String?> = _speakingId.asStateFlow()

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (!ready) return
        val locale = Locale.getDefault()
        if (tts.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE) tts.language = locale
        else tts.language = Locale.GERMAN
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) {
                if (utteranceId == lastUtterance) _speakingId.value = null
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                _speakingId.value = null
            }
        })
        pending?.let { (id, text) -> speak(id, text) }
        pending = null
    }

    fun speak(id: String, text: String) {
        if (!ready) {
            pending = id to text
            return
        }
        val clean = Markdown.toPlainText(text)
        if (clean.isBlank()) return
        val max = (TextToSpeech.getMaxSpeechInputLength() - 100).coerceAtLeast(500)
        val chunks = splitText(clean, max)
        _speakingId.value = id
        chunks.forEachIndexed { i, chunk ->
            val utteranceId = "$id#$i"
            if (i == chunks.lastIndex) lastUtterance = utteranceId
            tts.speak(chunk, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, utteranceId)
        }
    }

    fun stop() {
        tts.stop()
        _speakingId.value = null
    }

    private fun splitText(text: String, max: Int): List<String> {
        if (text.length <= max) return listOf(text)
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        for (sentence in text.split(Regex("(?<=[.!?\\n])\\s+"))) {
            if (sb.length + sentence.length > max && sb.isNotEmpty()) {
                out += sb.toString()
                sb.clear()
            }
            if (sentence.length > max) {
                sentence.chunked(max).forEach { out += it }
            } else {
                sb.append(sentence).append(' ')
            }
        }
        if (sb.isNotBlank()) out += sb.toString()
        return out
    }
}
