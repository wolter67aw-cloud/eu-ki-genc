@file:OptIn(ExperimentalLayoutApi::class)

package eu.kigenc.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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

enum class Tab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    CHAT("Chat", Icons.Outlined.ChatBubbleOutline, Icons.Filled.ChatBubble),
    STUDIO("Studio", Icons.Outlined.AutoAwesome, Icons.Filled.AutoAwesome),
    TOOLS("Werkzeuge", Icons.Outlined.Apps, Icons.Filled.Apps),
    CODE("Code", Icons.Outlined.Code, Icons.Filled.Code),
    SETTINGS("Optionen", Icons.Outlined.Settings, Icons.Filled.Settings),
}

@Composable
fun MainScreen(
    shared: SharedContent?,
    onSharedConsumed: () -> Unit,
    openUpdate: Boolean,
    onOpenUpdateHandled: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.app
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf(Tab.CHAT) }
    val stateHolder = rememberSaveableStateHolder()

    val chatVm: ChatViewModel = viewModel(key = "chat") { ChatViewModel(app, ChatMode.CHAT) }
    val codeVm: ChatViewModel = viewModel(key = "code") { ChatViewModel(app, ChatMode.CODE) }
    val studioVm: StudioViewModel = viewModel(key = "studio") { StudioViewModel(app) }

    val updateState by app.updates.state.collectAsStateWithLifecycle()
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
        // Beim Start auf Updates prüfen.
        if (app.settings.current().autoUpdate) app.updates.check()
    }

    LaunchedEffect(shared) {
        if (shared != null) tab = Tab.CHAT
    }

    LaunchedEffect(openUpdate) {
        if (openUpdate) {
            updateDismissed = false
            onOpenUpdateHandled()
            app.updates.check()
        }
    }

    val imeVisible = WindowInsets.isImeVisible

    Scaffold(
        bottomBar = {
            if (!imeVisible) {
                NavigationBar {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
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
                Tab.CHAT -> ChatScreen(chatVm, screenModifier, shared, onSharedConsumed)
                Tab.STUDIO -> StudioScreen(studioVm, screenModifier)
                Tab.TOOLS -> ToolsScreen(screenModifier)
                Tab.CODE -> ChatScreen(codeVm, screenModifier)
                Tab.SETTINGS -> SettingsScreen(screenModifier)
            }
        }
    }

    val showUpdateDialog = when (updateState) {
        is UpdateState.Available -> !updateDismissed
        is UpdateState.Downloading, is UpdateState.Installing, is UpdateState.NeedsPermission -> true
        else -> false
    }
    if (showUpdateDialog) {
        UpdateDialog(
            state = updateState,
            onInstall = { info -> scope.launch { app.updates.downloadAndInstall(info) } },
            onGrantPermission = { context.startActivity(app.updates.installPermissionIntent()) },
            onDismiss = { updateDismissed = true },
        )
    }
}
