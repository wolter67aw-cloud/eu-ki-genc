package eu.kigenc.app.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.content.IntentCompat
import eu.kigenc.app.app

/** Empfängt Statusmeldungen des System-Installers. */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (isForeground()) {
                        runCatching { context.startActivity(confirm) }
                            .onFailure { askViaNotification(context, confirm) }
                    } else {
                        // Hintergrund-Installation braucht eine Bestätigung: per Mitteilung nachfragen
                        askViaNotification(context, confirm)
                    }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> context.app.updates.onInstallResult(true, null)
            else -> context.app.updates.onInstallResult(false, message)
        }
    }

    private fun isForeground(): Boolean {
        val info = android.app.ActivityManager.RunningAppProcessInfo()
        android.app.ActivityManager.getMyMemoryState(info)
        return info.importance <= android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
    }

    private fun askViaNotification(context: Context, confirm: Intent) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            context.app.updates.onInstallResult(false, "Bestätigung nötig – bitte die App öffnen")
            return
        }
        val pending = android.app.PendingIntent.getActivity(
            context, 5, confirm,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val n = androidx.core.app.NotificationCompat.Builder(context, eu.kigenc.app.KiApp.CHANNEL_UPDATES)
            .setSmallIcon(eu.kigenc.app.R.drawable.ic_stat_update)
            .setContentTitle("KI-GenC-Update bereit")
            .setContentText("Tippen, um die neue Version zu installieren.")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        runCatching { androidx.core.app.NotificationManagerCompat.from(context).notify(4712, n) }
    }
}
