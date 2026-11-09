package eu.kigenc.app.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Datensicherung als ZIP-Datei: Unterhaltungen, Aufgaben, erzeugte Bilder, Werkzeug-Ergebnisse
 * und Einstellungen. API-Schlüssel werden aus Sicherheitsgründen NICHT mitgesichert.
 */
object BackupManager {
    private val FOLDERS = listOf("chats", "images", "generated", "results", "ideas")
    private val FILES = listOf("tasks.json")
    private const val SETTINGS_ENTRY = "einstellungen.json"

    suspend fun export(context: Context, settings: SettingsRepository, target: Uri): Int = withContext(Dispatchers.IO) {
        var count = 0
        val root = context.filesDir
        context.contentResolver.openOutputStream(target)?.use { raw ->
            ZipOutputStream(raw.buffered()).use { zip ->
                fun add(file: File, name: String) {
                    zip.putNextEntry(ZipEntry(name))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    count++
                }
                FOLDERS.forEach { folder ->
                    val dir = File(root, folder)
                    dir.walkTopDown().filter { it.isFile }.forEach { f -> add(f, f.relativeTo(root).path) }
                }
                FILES.map { File(root, it) }.filter { it.exists() }.forEach { add(it, it.name) }
                val s = settings.settings.first()
                val json = JSONObject()
                    .put("chatModel", s.chatModel)
                    .put("codeModel", s.codeModel)
                    .put("autoSpeak", s.autoSpeak)
                    .put("autoUpdate", s.autoUpdate)
                    .put("dynamicColor", s.dynamicColor)
                    .put("design", s.design)
                    .put("imageProvider", s.imageProvider)
                zip.putNextEntry(ZipEntry(SETTINGS_ENTRY))
                zip.write(json.toString(2).toByteArray())
                zip.closeEntry()
            }
        } ?: error("Speicherort konnte nicht geöffnet werden.")
        count
    }

    suspend fun import(context: Context, settings: SettingsRepository, source: Uri): Int = withContext(Dispatchers.IO) {
        var count = 0
        val root = context.filesDir.canonicalFile
        var settingsJson: String? = null
        context.contentResolver.openInputStream(source)?.use { raw ->
            ZipInputStream(raw.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    if (entry.isDirectory) continue
                    if (name == SETTINGS_ENTRY) {
                        settingsJson = zip.readBytes().toString(Charsets.UTF_8)
                        continue
                    }
                    val allowed = FILES.contains(name) || FOLDERS.any { name.startsWith("$it/") }
                    val target = File(root, name).canonicalFile
                    // Schutz vor manipulierten ZIP-Dateien (Pfade außerhalb des App-Ordners)
                    if (!allowed || !target.path.startsWith(root.path + File.separator)) continue
                    target.parentFile?.mkdirs()
                    target.outputStream().use { zip.copyTo(it) }
                    count++
                }
            }
        } ?: error("Datei konnte nicht geöffnet werden.")
        settingsJson?.let { text ->
            runCatching {
                val o = JSONObject(text)
                o.optString("chatModel").takeIf { it.isNotBlank() }?.let { settings.setChatModel(it) }
                o.optString("codeModel").takeIf { it.isNotBlank() }?.let { settings.setCodeModel(it) }
                if (o.has("autoSpeak")) settings.setAutoSpeak(o.getBoolean("autoSpeak"))
                if (o.has("autoUpdate")) settings.setAutoUpdate(o.getBoolean("autoUpdate"))
                if (o.has("dynamicColor")) settings.setDynamicColor(o.getBoolean("dynamicColor"))
                if (o.has("design")) settings.setDesign(o.getString("design"))
                o.optString("imageProvider").takeIf { it.isNotBlank() }?.let { settings.setImageProvider(it) }
            }
        }
        if (count == 0 && settingsJson == null) error("Die Datei enthält keine KI-GenC-Sicherung.")
        count
    }
}
