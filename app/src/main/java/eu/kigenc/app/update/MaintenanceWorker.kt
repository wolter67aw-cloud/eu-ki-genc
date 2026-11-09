package eu.kigenc.app.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import eu.kigenc.app.app
import eu.kigenc.app.data.AppSettings
import java.util.concurrent.TimeUnit

/**
 * Hält die KI-Modelle aktuell (läuft wöchentlich im Hintergrund):
 * – lädt die Liste der verfügbaren Mistral-Modelle neu, damit neue Modelle sofort auswählbar sind,
 * – stellt auf das aktuelle Standardmodell um, wenn ein gewähltes Modell abgeschaltet wurde.
 */
class MaintenanceWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext.app
        val s = app.settings.current()
        if (s.ownApiKey.isBlank()) return Result.success()
        return try {
            val models = app.mistral.listModels(s.ownApiKey).map { it.id }
            if (models.isEmpty()) return Result.success()
            app.settings.setCachedModels(models)
            if (s.chatModel !in models) app.settings.setChatModel(AppSettings.DEFAULT_CHAT_MODEL)
            if (s.codeModel !in models) app.settings.setCodeModel(AppSettings.DEFAULT_CODE_MODEL)
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.success()
        }
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<MaintenanceWorker>(7, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "eu_kigenc_model_refresh", ExistingPeriodicWorkPolicy.KEEP, request
            )
        }
    }
}
