package eu.kigenc.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import eu.kigenc.app.ai.MistralClient
import eu.kigenc.app.data.ChatStore
import eu.kigenc.app.data.SettingsRepository
import eu.kigenc.app.ui.components.TtsController
import eu.kigenc.app.update.UpdateManager
import eu.kigenc.app.update.UpdateWorker

/** Application-Klasse – hält die app-weiten Dienste (einfacher Service-Locator). */
class KiApp : Application() {

    lateinit var settings: SettingsRepository
        private set
    lateinit var chatStore: ChatStore
        private set
    lateinit var updates: UpdateManager
        private set
    val mistral = MistralClient()
    val tts: TtsController by lazy { TtsController(this) }

    override fun onCreate() {
        super.onCreate()
        settings = SettingsRepository(this)
        chatStore = ChatStore(this)
        updates = UpdateManager(this)

        val channel = NotificationChannel(
            CHANNEL_UPDATES,
            getString(R.string.channel_updates),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = getString(R.string.channel_updates_desc) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        UpdateWorker.schedule(this)
    }

    companion object {
        const val CHANNEL_UPDATES = "updates"
    }
}

val Context.app: KiApp get() = applicationContext as KiApp
