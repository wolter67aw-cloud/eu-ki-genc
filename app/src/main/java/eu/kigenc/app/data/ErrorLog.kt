package eu.kigenc.app.data

import android.content.Context
import android.os.Build
import eu.kigenc.app.BuildConfig
import eu.kigenc.app.ai.friendlyMessage
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Fehlerprotokoll: hält Abstürze und Fehlermeldungen lokal fest, damit sie unter
 * „Optionen → Fehlerprotokoll“ angesehen und geteilt werden können. Enthält keine API-Schlüssel.
 */
object ErrorLog {
    private const val MAX_BYTES = 200_000L
    private var file: File? = null
    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.GERMANY)

    fun init(context: Context) {
        file = File(context.filesDir, "fehlerprotokoll.txt")
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            log("ABSTURZ (${thread.name})", e)
            previous?.uncaughtException(thread, e)
        }
    }

    @Synchronized
    fun log(where: String, e: Throwable? = null, message: String? = null) {
        val f = file ?: return
        runCatching {
            val sb = StringBuilder()
            sb.append("[").append(fmt.format(Date())).append("] ").append(where)
            if (message != null) sb.append(": ").append(message)
            if (e != null) {
                sb.append(": ").append(e.javaClass.simpleName).append(" – ").append(sanitize(e.message.orEmpty())).append('\n')
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                sb.append(sanitize(sw.toString().lineSequence().take(25).joinToString("\n")))
            }
            sb.append("\n\n")
            f.appendText(sb.toString())
            if (f.length() > MAX_BYTES) {
                val text = f.readText()
                f.writeText(text.takeLast((MAX_BYTES / 2).toInt()))
            }
        }
    }

    /** Entfernt mögliche Schlüssel (lange Zeichenketten nach „Bearer“ oder typische Schlüsselmuster). */
    private fun sanitize(s: String): String = s
        .replace(Regex("Bearer\\s+[A-Za-z0-9._\\-]+"), "Bearer ***")
        .replace(Regex("(mstrl_|sk-|github_pat_)[A-Za-z0-9_\\-]{8,}"), "$1***")

    fun read(): String = runCatching { file?.readText().orEmpty() }.getOrDefault("")

    fun clear() {
        runCatching { file?.writeText("") }
    }

    fun header(): String =
        "KI-GenC ${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})\n" +
            "Gerät: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n\n"
}

/** Protokolliert den Fehler und liefert eine verständliche deutsche Meldung. */
fun Throwable.report(where: String = "Fehler"): String {
    ErrorLog.log(where, this)
    return friendlyMessage()
}
