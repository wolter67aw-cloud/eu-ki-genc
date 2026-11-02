package eu.kigenc.app.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import eu.kigenc.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val notes: String,
    val sizeBytes: Long,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data object NotConfigured : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data class Installing(val info: UpdateInfo) : UpdateState
    data class NeedsPermission(val info: UpdateInfo) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * Selbst-Update über GitHub Releases:
 * Jeder Push auf "main" baut per GitHub Actions eine signierte APK und veröffentlicht sie als Release.
 * Die App vergleicht ihre versionCode mit dem neuesten Release und installiert Updates über den
 * PackageInstaller (gleiche Signatur → Daten bleiben erhalten).
 */
class UpdateManager(private val context: Context) {

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    val repo: String get() = BuildConfig.UPDATE_REPO
    val currentVersionCode: Int get() = BuildConfig.VERSION_CODE
    val currentVersionName: String get() = BuildConfig.VERSION_NAME

    /** Prüft auf ein neues Release. Gibt die Update-Info zurück, falls eine neuere Version existiert. */
    suspend fun check(): UpdateInfo? {
        if (repo.isBlank()) {
            _state.value = UpdateState.NotConfigured
            return null
        }
        if (_state.value is UpdateState.Downloading || _state.value is UpdateState.Installing) return null
        _state.value = UpdateState.Checking
        return try {
            val info = fetchLatest()
            if (info != null && info.versionCode > currentVersionCode) {
                _state.value = UpdateState.Available(info)
                info
            } else {
                _state.value = UpdateState.UpToDate
                null
            }
        } catch (e: Exception) {
            _state.value = UpdateState.Failed("Update-Prüfung fehlgeschlagen: ${e.message ?: e.javaClass.simpleName}")
            null
        }
    }

    private suspend fun fetchLatest(): UpdateInfo? = withContext(Dispatchers.IO) {
        val c = URL("https://api.github.com/repos/$repo/releases/latest").openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 20_000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        c.setRequestProperty("User-Agent", "EU-KI-GenC-Updater")
        try {
            when (c.responseCode) {
                404 -> return@withContext null
                !in 200..299 -> error("GitHub antwortet mit ${c.responseCode}")
            }
            val json = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val tag = json.optString("tag_name")
            val code = Regex("(\\d+)$").find(tag)?.groupValues?.get(1)?.toIntOrNull() ?: return@withContext null
            val assets = json.optJSONArray("assets") ?: return@withContext null
            var url: String? = null
            var size = 0L
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk")) {
                    url = a.optString("browser_download_url")
                    size = a.optLong("size")
                    break
                }
            }
            if (url.isNullOrBlank()) return@withContext null
            UpdateInfo(
                versionCode = code,
                versionName = tag.removePrefix("v"),
                apkUrl = url,
                notes = json.optString("body").trim(),
                sizeBytes = size,
            )
        } finally {
            c.disconnect()
        }
    }

    fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun installPermissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Lädt die APK herunter und übergibt sie dem System-Installer. */
    suspend fun downloadAndInstall(info: UpdateInfo) {
        if (!canInstall()) {
            _state.value = UpdateState.NeedsPermission(info)
            return
        }
        try {
            val apk = download(info)
            _state.value = UpdateState.Installing(info)
            install(apk)
        } catch (e: Exception) {
            _state.value = UpdateState.Failed("Update fehlgeschlagen: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private suspend fun download(info: UpdateInfo): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "EU-KI-GenC-${info.versionCode}.apk")
        _state.value = UpdateState.Downloading(info, 0f)

        val c = openFollowingRedirects(URL(info.apkUrl))
        try {
            val total = c.contentLengthLong.takeIf { it > 0 } ?: info.sizeBytes
            c.inputStream.use { input ->
                target.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var lastReport = 0f
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val p = (done.toFloat() / total).coerceIn(0f, 1f)
                            if (p - lastReport >= 0.01f) {
                                lastReport = p
                                _state.value = UpdateState.Downloading(info, p)
                            }
                        }
                    }
                }
            }
        } finally {
            c.disconnect()
        }
        target
    }

    /** GitHub leitet Asset-Downloads auf einen CDN-Host um – Weiterleitungen manuell folgen. */
    private fun openFollowingRedirects(start: URL): HttpURLConnection {
        var url = start
        repeat(6) {
            val c = url.openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 20_000
            c.readTimeout = 60_000
            c.setRequestProperty("User-Agent", "EU-KI-GenC-Updater")
            val code = c.responseCode
            if (code in 300..399) {
                val location = c.getHeaderField("Location") ?: error("Weiterleitung ohne Ziel")
                c.disconnect()
                url = URL(url, location)
            } else {
                if (code !in 200..299) {
                    c.disconnect()
                    error("Download-Fehler $code")
                }
                return c
            }
        }
        error("Zu viele Weiterleitungen")
    }

    private suspend fun install(apk: File) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                setPackageSource(PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE)
            }
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("base.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val intent = Intent(context, InstallReceiver::class.java).setPackage(context.packageName)
            val pending = PendingIntent.getBroadcast(context, sessionId, intent, flags)
            session.commit(pending.intentSender)
        }
    }

    fun onInstallResult(success: Boolean, message: String?) {
        _state.value = if (success) UpdateState.UpToDate
        else UpdateState.Failed("Installation abgebrochen${message?.let { ": $it" } ?: "."}")
    }

    fun reset() {
        _state.value = UpdateState.Idle
    }
}
