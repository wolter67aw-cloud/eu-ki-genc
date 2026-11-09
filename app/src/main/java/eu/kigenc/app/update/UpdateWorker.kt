package eu.kigenc.app.update

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import eu.kigenc.app.KiApp
import eu.kigenc.app.HomeActivity
import eu.kigenc.app.R
import eu.kigenc.app.app
import java.util.concurrent.TimeUnit

/** Prüft im Hintergrund regelmäßig auf neue Versionen und benachrichtigt den Nutzer. */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext.app
        if (!app.settings.current().autoUpdate) return Result.success()
        val info = app.updates.check()
            ?: return if (app.updates.lastCheckOffline && runAttemptCount < 3) Result.retry() else Result.success()
        // Vollautomatisch: im Hintergrund herunterladen und installieren, solange die App nicht offen ist
        val s = app.settings.current()
        if (s.autoInstall && !isAppInForeground() && app.updates.canInstall()) {
            app.updates.downloadAndInstall(info)
            if (app.updates.state.value !is UpdateState.Failed) return Result.success()
        }
        // Sonst (oder wenn das nicht klappte): einmal pro neuer Version melden
        if (app.updates.shouldNotify(info)) {
            notify(info)
            app.updates.markNotified(info)
        }
        return Result.success()
    }

    private fun isAppInForeground(): Boolean {
        val info = android.app.ActivityManager.RunningAppProcessInfo()
        android.app.ActivityManager.getMyMemoryState(info)
        return info.importance <= android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
    }

    private fun notify(info: UpdateInfo) {
        val ctx = applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val open = PendingIntent.getActivity(
            ctx, 1,
            Intent(ctx, HomeActivity::class.java)
                .putExtra(HomeActivity.EXTRA_OPEN_UPDATE, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(ctx, KiApp.CHANNEL_UPDATES)
            .setSmallIcon(R.drawable.ic_stat_update)
            .setContentTitle("Update verfügbar: ${info.versionName}")
            .setContentText("Tippen, um KI-GenC zu aktualisieren.")
            .setAutoCancel(true)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(NOTIFICATION_ID, notification) }
    }

    companion object {
        private const val WORK_NAME = "eu_kigenc_update_check"
        private const val NOTIFICATION_ID = 4711

        fun schedule(context: Context) {
            if (!eu.kigenc.app.BuildConfig.SELF_UPDATE) return
            val request = PeriodicWorkRequestBuilder<UpdateWorker>(6, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }
    }
}
