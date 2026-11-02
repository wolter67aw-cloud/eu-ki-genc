package eu.kigenc.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.kigenc.app.ui.MainScreen
import eu.kigenc.app.ui.chat.SharedContent
import eu.kigenc.app.ui.theme.KiTheme

class MainActivity : ComponentActivity() {

    private var shared by mutableStateOf<SharedContent?>(null)
    private var openUpdate by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            val settings by app.settings.settings.collectAsStateWithLifecycle(initialValue = null)
            KiTheme(dynamicColor = settings?.dynamicColor ?: false) {
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                    MainScreen(
                        shared = shared,
                        onSharedConsumed = { shared = null },
                        openUpdate = openUpdate,
                        onOpenUpdateHandled = { openUpdate = false },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.getBooleanExtra(EXTRA_OPEN_UPDATE, false)) openUpdate = true
        if (intent.action == Intent.ACTION_SEND) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            if (text != null || uri != null) shared = SharedContent(text, uri)
        }
    }

    companion object {
        const val EXTRA_OPEN_UPDATE = "open_update"
    }
}
