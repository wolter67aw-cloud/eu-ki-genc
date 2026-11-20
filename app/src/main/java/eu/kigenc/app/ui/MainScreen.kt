@file:OptIn(ExperimentalLayoutApi::class)

package eu.kigenc.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Home
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.kigenc.app.app
import eu.kigenc.app.data.ChatMode
import eu.kigenc.app.ui.chat.ChatScreen
import eu.kigenc.app.ui.chat.ChatViewModel
import eu.kigenc.app.ui.chat.SharedContent
import eu.kigenc.app.ui.settings.SettingsScreen
import eu.kigenc.app.ui.settings.UpdateDialog
import eu.kigenc.app.ui.studio.StudioScreen
import eu.kigenc.app.ui.studio.StudioViewModel
import eu.kigenc.app.ui.tools.ToolsScreen
import eu.kigenc.app.update.UpdateState
import kotlinx.coroutines.launch

/** Kennung der aktuellen App-Sitzung – ändert sich, wenn die App neu geöffnet wird. */
val LocalSessionId = compositionLocalOf { 0 }

enum class Tab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    START("Start", Icons.Outlined.Home, Icons.Filled.Home),
    CHAT("Chat", Icons.Outlined.ChatBubbleOutline, Icons.Filled.ChatBubble),
    STUDIO("Studio", Icons.Outlined.AutoAwesome, Icons.Filled.AutoAwesome),
    TOOLS("Werkzeuge", Icons.Outlined.Apps, Icons.Filled.Apps),
    CODE("Code", Icons.Outlined.Code, Icons.Filled.Code),
    SETTINGS("Optionen", Icons.Outlined.Settings, Icons.Filled.Settings),
}

@Composable
fun MainScreen(
    sessionId: Int,
    shared: SharedContent?,
    onSharedConsumed: () -> Unit,
    openUpdate: Boolean,
    onOpenUpdateHandled: () -> Unit,
    quick: eu.kigenc.app.widget.QuickAction? = null,
    onQuickHandled: () -> Unit = {},
) {
    val context = LocalContext.current
    val app = context.app
    val scope = rememberCoroutineScope()
    // Neue Sitzung (App neu geöffnet) → alle Eingaben, Anhänge und Zwischenergebnisse leer.
    key(sessionId) {
    CompositionLocalProvider(LocalSessionId provides sessionId) {
    var tab by rememberSaveable { mutableStateOf(Tab.START) }
    // Werkzeug, das von der Startseite aus geöffnet werden soll
    var toolRequest by remember { mutableStateOf<eu.kigenc.app.ui.tools.Tool?>(null) }
    // Schnellerfassung (Widget): neue Idee dieser Art bzw. Fokus auf „Neue Aufgabe“
    var ideaRequest by remember { mutableStateOf<eu.kigenc.app.data.IdeaKind?>(null) }
    var focusNewTask by remember { mutableStateOf(false) }
    val stateHolder = rememberSaveableStateHolder()

    val chatVm: ChatViewModel = viewModel(key = "chat_$sessionId") { ChatViewModel(app, ChatMode.CHAT) }
    val codeVm: ChatViewModel = viewModel(key = "code_$sessionId") { ChatViewModel(app, ChatMode.CODE) }
    val studioVm: StudioViewModel = viewModel(key = "studio_$sessionId") { StudioViewModel(app) }
    val startVm: eu.kigenc.app.ui.start.StartViewModel = viewModel(key = "start_$sessionId") { eu.kigenc.app.ui.start.StartViewModel(app) }

    val updateState by app.updates.state.collectAsStateWithLifecycle()
    var showOnboarding by rememberSaveable { mutableStateOf(false) }
    // Datenschutz-Hinweis: einmal zustimmen, bevor Daten an KI-Dienste gehen (auch für bisherige Nutzer)
    var showConsent by rememberSaveable { mutableStateOf(!AiConsent.given(context)) }
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("app_state", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("onboarding_done", false) && !app.settings.current().hasKey) showOnboarding = true
    }
    var updateDismissed by rememberSaveable { mutableStateOf(false) }

    // Benachrichtigungen (für Update-Hinweise) einmalig erfragen.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("app_state", Context.MODE_PRIVATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !prefs.getBoolean("asked_notifications", false) &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            prefs.edit().putBoolean("asked_notifications", true).apply()
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        // Musik beim Start (falls eingeschaltet): Nr. 1 der Charts als Hörprobe
        app.music.playOnStart(app.settings.current())
        // Beim Start auf Updates prüfen.
        if (app.settings.current().autoUpdate) app.updates.check()
    }

    LaunchedEffect(shared) {
        if (shared != null) tab = Tab.CHAT
    }

    // Startbildschirm-Widget „Schnellerfassung“: direkt an die passende Stelle springen
    LaunchedEffect(quick) {
        when (quick ?: return@LaunchedEffect) {
            eu.kigenc.app.widget.QuickAction.CHAT -> {
                // Laufende Antwort nicht abbrechen; sonst mit leerer Unterhaltung beginnen
                if (!chatVm.state.value.streaming) chatVm.newChat()
                tab = Tab.CHAT
            }
            eu.kigenc.app.widget.QuickAction.NOTE -> {
                ideaRequest = eu.kigenc.app.data.IdeaKind.NOTE
                toolRequest = eu.kigenc.app.ui.tools.Tool.IDEAS
                tab = Tab.TOOLS
            }
            eu.kigenc.app.widget.QuickAction.MEMO -> {
                ideaRequest = eu.kigenc.app.data.IdeaKind.MEMO
                toolRequest = eu.kigenc.app.ui.tools.Tool.IDEAS
                tab = Tab.TOOLS
            }
            eu.kigenc.app.widget.QuickAction.TASK -> {
                focusNewTask = true
                toolRequest = eu.kigenc.app.ui.tools.Tool.TASKS
                tab = Tab.TOOLS
            }
        }
        onQuickHandled()
    }

    LaunchedEffect(openUpdate) {
        if (openUpdate) {
            updateDismissed = false
            onOpenUpdateHandled()
            app.updates.check(userInitiated = true)
        }
    }

    val imeVisible = WindowInsets.isImeVisible

    Scaffold(
        // Leiste „Nr. 1 der Charts“ erscheint über der Navigationsleiste, solange die Hörprobe läuft
        snackbarHost = { eu.kigenc.app.ui.components.NowPlayingBar() },
        bottomBar = {
            if (!imeVisible) {
                NavigationBar {
                    // „Code“ ist über Start und Werkzeuge erreichbar (max. 5 Einträge in der Leiste)
                    Tab.entries.filter { it != Tab.CODE }.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t || (t == Tab.TOOLS && tab == Tab.CODE),
                            onClick = { tab = t },
                            icon = { Icon(if (tab == t) t.selectedIcon else t.icon, contentDescription = null) },
                            label = { Text(t.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        val screenModifier = Modifier
            .padding(padding)
            .consumeWindowInsets(padding)
        stateHolder.SaveableStateProvider(tab.name) {
            when (tab) {
                Tab.START -> eu.kigenc.app.ui.start.StartScreen(
                    startVm,
                    onOpenTool = { toolRequest = it; tab = Tab.TOOLS },
                    modifier = screenModifier,
                )
                Tab.CHAT -> ChatScreen(chatVm, screenModifier, shared, onSharedConsumed)
                Tab.STUDIO -> StudioScreen(studioVm, screenModifier)
                Tab.TOOLS -> ToolsScreen(
                    screenModifier,
                    request = toolRequest,
                    onRequestHandled = { toolRequest = null },
                    onOpenCode = { tab = Tab.CODE },
                    ideaRequest = ideaRequest,
                    onIdeaRequestHandled = { ideaRequest = null },
                    focusNewTask = focusNewTask,
                    onFocusNewTaskHandled = { focusNewTask = false },
                )
                Tab.CODE -> {
                    BackHandler { tab = Tab.TOOLS }
                    ChatScreen(codeVm, screenModifier)
                }
                Tab.SETTINGS -> SettingsScreen(screenModifier)
            }
        }
    }

    if (showConsent) {
        ConsentDialog(
            onAccept = { AiConsent.set(context, true); showConsent = false },
            // Ohne Zustimmung keine KI-Anfragen → App schließen; beim nächsten Start wird erneut gefragt.
            onDecline = {
                var c: Context? = context
                while (c is android.content.ContextWrapper && c !is android.app.Activity) c = c.baseContext
                (c as? android.app.Activity)?.finish()
            },
        )
    }

    if (showOnboarding && !showConsent) {
        OnboardingDialog(onDone = {
            context.getSharedPreferences("app_state", Context.MODE_PRIVATE).edit().putBoolean("onboarding_done", true).apply()
            showOnboarding = false
        })
    }

    val showUpdateDialog = when (val s = updateState) {
        // Nur fragen, wenn wirklich eine neue, noch nicht abgelehnte Version ansteht
        is UpdateState.Available -> !updateDismissed && app.updates.shouldPrompt(s.info)
        is UpdateState.Downloading, is UpdateState.Installing, is UpdateState.NeedsPermission -> true
        else -> false
    }
    // „Was ist neu“ einmal nach einem Update (nicht bei Neuinstallation, nicht über anderen Dialogen)
    var whatsNew by remember { mutableStateOf(eu.kigenc.app.update.WhatsNew.pending(context)) }
    if (whatsNew.isNotEmpty() && !showConsent && !showOnboarding && !showUpdateDialog) {
        eu.kigenc.app.ui.settings.WhatsNewDialog(whatsNew) {
            eu.kigenc.app.update.WhatsNew.markSeen(context)
            whatsNew = emptyList()
        }
    }
    if (showUpdateDialog && !showConsent) {
        UpdateDialog(
            state = updateState,
            onInstall = { info -> scope.launch { app.updates.downloadAndInstall(info) } },
            onGrantPermission = { context.startActivity(app.updates.installPermissionIntent()) },
            onDismiss = {
                updateDismissed = true
                (updateState as? UpdateState.Available)?.let { app.updates.dismiss(it.info) }
            },
        )
    }
    }
    }
}
