package eu.kigenc.app.update

import android.content.Context
import eu.kigenc.app.BuildConfig
import org.json.JSONObject

data class WhatsNewEntry(val code: Int, val version: String, val date: String, val items: List<String>)

/**
 * „Was ist neu“: Die Liste wird beim Release-Build aus der Git-Historie erzeugt
 * (scripts/whatsnew.py → assets/whatsnew.json) und mit der App ausgeliefert – funktioniert also auch offline.
 * Nach einem Update zeigt die App einmal die Neuerungen aller Versionen seit der zuletzt gesehenen.
 */
object WhatsNew {
    private const val PREFS = "updates"
    private const val KEY_SEEN = "whatsnew_seen_code"

    fun all(context: Context): List<WhatsNewEntry> = runCatching {
        val text = context.assets.open("whatsnew.json").bufferedReader().use { it.readText() }
        val arr = JSONObject(text).optJSONArray("entries") ?: return@runCatching emptyList()
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val items = o.optJSONArray("items")
            WhatsNewEntry(
                code = o.optInt("code"),
                version = o.optString("version"),
                date = o.optString("date"),
                items = if (items == null) emptyList() else (0 until items.length()).map { items.getString(it) },
            )
        }.filter { it.code <= BuildConfig.VERSION_CODE }
    }.getOrDefault(emptyList())

    /** Neuerungen, die seit dem letzten Öffnen dazugekommen sind (leer bei Neuinstallation). */
    fun pending(context: Context): List<WhatsNewEntry> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = BuildConfig.VERSION_CODE
        var seen = prefs.getInt(KEY_SEEN, -1)
        if (seen == -1) {
            if (isFreshInstall(context)) {
                markSeen(context)
                return emptyList()
            }
            seen = current - 1 // Update von einer Version ohne diese Funktion: nur die aktuelle zeigen
        }
        if (seen >= current) return emptyList()
        return all(context).filter { it.code in (seen + 1)..current }
    }

    fun markSeen(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_SEEN, BuildConfig.VERSION_CODE).apply()
    }

    private fun isFreshInstall(context: Context): Boolean = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.firstInstallTime == info.lastUpdateTime
    }.getOrDefault(false)

    /** Release-Text („- Punkt“) lesbar machen. */
    fun pretty(notes: String): String = notes.lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("**") }
        .joinToString("\n") { if (it.startsWith("- ")) "• " + it.removePrefix("- ") else it }
}
