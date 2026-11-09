package eu.kigenc.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.kigenc.app.ui.BetaGate
import eu.kigenc.app.ui.MainScreen
import eu.kigenc.app.ui.chat.SharedContent
import eu.kigenc.app.ui.theme.AppBackground
import eu.kigenc.app.ui.theme.KiTheme

class MainActivity : ComponentActivity() {

    private var shared by mutableStateOf<SharedContent?>(null)
    private var openUpdate by mutableStateOf(false)

    /** Ändert sich, wenn die App neu geöffnet wird → alle Eingaben werden geleert. */
    private var sessionId by mutableIntStateOf(0)
    private val prefs by lazy { getSharedPreferences("app_state", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        // Nach längerer Pause (auch wenn Android die App zwischendurch beendet hat) frisch starten.
        if (savedInstanceState != null && shouldReset()) sessionId = 1

        setContent {
            val settings by app.settings.settings.collectAsStateWithLifecycle(initialValue = null)
            val dynamic = settings?.dynamicColor == true && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            val lightDesign = !eu.kigenc.app.ui.theme.designById(settings?.design).dark
            LaunchedEffect(dynamic, lightDesign) {
                if (dynamic) {
                    enableEdgeToEdge()
                } else if (lightDesign) {
                    // Helles Design → dunkle Symbole in Status- und Navigationsleiste.
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
                        navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
                    )
                } else {
                    // Logo-Design ist immer dunkel → helle Symbole in Status- und Navigationsleiste.
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
                        navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
                    )
                }
            }
            KiTheme(dynamicColor = settings?.dynamicColor ?: false, designId = settings?.design) {
                AppBackground {
                    BetaGate {
                    MainScreen(
                        sessionId = sessionId,
                        shared = shared,
                        onSharedConsumed = { shared = null },
                        openUpdate = openUpdate,
                        onOpenUpdateHandled = { openUpdate = false },
                    )
                    }
                }
            }
        }
    }

    /**
     * Neue Sitzung, wenn die App vom Nutzer verlassen (Home-Taste/App-Wechsel) und wieder geöffnet wurde
     * oder länger als 10 Minuten im Hintergrund war. Wechsel zu Kamera, Galerie oder Dateiauswahl zählen nicht.
     */
    private fun shouldReset(): Boolean {
        val lastStop = prefs.getLong("last_stop", 0L)
        if (lastStop == 0L) return false
        val leftByUser = prefs.getBoolean("left_by_user", false)
        return leftByUser || System.currentTimeMillis() - lastStop > RESET_AFTER_MS
    }

    override fun onStart() {
        super.onStart()
        launchingExternal = false
        if (shouldReset()) sessionId++
        prefs.edit().putLong("last_stop", 0L).putBoolean("left_by_user", false).apply()
    }

    /** true, solange die App selbst eine andere App öffnet (Kamera, Galerie, Teilen, Links …). */
    private var launchingExternal = false

    @Deprecated("Wird nur zum Erkennen eigener App-Wechsel überschrieben")
    @Suppress("DEPRECATION")
    override fun startActivityForResult(intent: Intent, requestCode: Int, options: Bundle?) {
        launchingExternal = true
        super.startActivityForResult(intent, requestCode, options)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (launchingExternal) {
            launchingExternal = false
            return
        }
        prefs.edit().putBoolean("left_by_user", true).apply()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) prefs.edit().putLong("last_stop", System.currentTimeMillis()).apply()
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
        private const val RESET_AFTER_MS = 10 * 60 * 1000L
    }
}
