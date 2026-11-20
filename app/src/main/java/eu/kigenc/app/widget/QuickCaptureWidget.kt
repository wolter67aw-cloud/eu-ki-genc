package eu.kigenc.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import eu.kigenc.app.HomeActivity
import eu.kigenc.app.R

/** Schnellerfassung vom Startbildschirm aus: was beim Antippen geöffnet wird. */
enum class QuickAction(val id: String) {
    /** Neue Unterhaltung im Chat. */
    CHAT("chat"),
    /** Neue Notiz in der Ideen-Sammlung. */
    NOTE("note"),
    /** Neues Sprachmemo in der Ideen-Sammlung. */
    MEMO("memo"),
    /** Neue Aufgabe (Eingabefeld bekommt den Fokus). */
    TASK("task");

    companion object {
        fun from(id: String?): QuickAction? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Widget „KI-GenC Schnellerfassung“ für den Android-Startbildschirm (4×1, in der Breite anpassbar).
 * Ohne Hintergrunddienst und ohne Daten: Jede Schaltfläche öffnet die App direkt an der passenden Stelle.
 */
class QuickCaptureWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { manager.updateAppWidget(it, views(context)) }
    }

    companion object {
        const val EXTRA_QUICK = "quick_action"

        private fun views(context: Context) = RemoteViews(context.packageName, R.layout.widget_quick).apply {
            setOnClickPendingIntent(R.id.quick_chat, pending(context, QuickAction.CHAT))
            setOnClickPendingIntent(R.id.quick_note, pending(context, QuickAction.NOTE))
            setOnClickPendingIntent(R.id.quick_memo, pending(context, QuickAction.MEMO))
            setOnClickPendingIntent(R.id.quick_task, pending(context, QuickAction.TASK))
        }

        private fun pending(context: Context, action: QuickAction): PendingIntent {
            val intent = Intent(context, HomeActivity::class.java)
                // Eigene Aktion je Schaltfläche, damit Android die PendingIntents nicht zusammenlegt
                .setAction("eu.kigenc.app.action.QUICK_${action.name}")
                .putExtra(EXTRA_QUICK, action.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            return PendingIntent.getActivity(
                context, 4100 + action.ordinal, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
