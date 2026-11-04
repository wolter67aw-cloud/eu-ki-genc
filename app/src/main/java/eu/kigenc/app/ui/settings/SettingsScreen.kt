@file:OptIn(ExperimentalMaterial3Api::class)

package eu.kigenc.app.ui.settings

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.kigenc.app.ai.friendlyMessage
import eu.kigenc.app.app
import eu.kigenc.app.data.AppSettings
import eu.kigenc.app.ui.components.EuFlag
import eu.kigenc.app.update.UpdateState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val DEFAULT_CHAT_MODELS = listOf(
    "mistral-medium-latest", "mistral-large-latest", "mistral-small-latest",
    "magistral-medium-latest", "ministral-8b-latest",
)
private val DEFAULT_CODE_MODELS = listOf(
    "codestral-latest", "devstral-medium-latest", "devstral-small-latest", "mistral-medium-latest",
)

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.app
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    val settings by app.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val updateState by app.updates.state.collectAsStateWithLifecycle()

    var keyInput by remember { mutableStateOf("") }
    var keyLoaded by remember { mutableStateOf(false) }
    var showKey by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var keyStatus by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var modelPicker by remember { mutableStateOf<String?>(null) } // "chat" | "code"
    var confirmDelete by remember { mutableStateOf(false) }
    var showImageProvider by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        keyInput = app.settings.current().apiKey
        keyLoaded = true
    }

    fun testKey(key: String) {
        scope.launch {
            testing = true
            keyStatus = null
            try {
                val models = app.mistral.listModels(key.trim())
                app.settings.setCachedModels(models.map { it.id })
                keyStatus = true to "Verbunden ✓ – ${models.size} Modelle verfügbar"
            } catch (e: Exception) {
                keyStatus = false to e.friendlyMessage()
            } finally {
                testing = false
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("Einstellungen", fontWeight = FontWeight.SemiBold) }) }
    ) { padding ->
        LazyColumn(
            contentPadding = padding,
            modifier = Modifier.fillMaxSize()
        ) {
            item { SectionTitle("Mistral AI") }
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = keyInput,
                            onValueChange = { keyInput = it; keyStatus = null },
                            label = { Text("API-Schlüssel") },
                            leadingIcon = { Icon(Icons.Outlined.Key, null) },
                            trailingIcon = {
                                IconButton(onClick = { showKey = !showKey }) {
                                    Icon(
                                        if (showKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                        if (showKey) "Verbergen" else "Anzeigen"
                                    )
                                }
                            },
                            singleLine = true,
                            enabled = keyLoaded,
                            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = {
                                    scope.launch { app.settings.setApiKey(keyInput) }
                                    if (keyInput.isNotBlank()) testKey(keyInput)
                                },
                                enabled = keyLoaded && !testing
                            ) { Text("Speichern & testen") }
                            Spacer(Modifier.size(12.dp))
                            if (testing) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        }
                        keyStatus?.let { (ok, msg) ->
                            Text(
                                msg,
                                color = if (ok) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Text(
                            "Noch keinen Schlüssel? Auf console.mistral.ai kostenlos registrieren, " +
                                "unter „API Keys“ einen Schlüssel erstellen und hier einfügen.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = { uri.openUri("https://console.mistral.ai/api-keys") }) {
                            Text("Schlüssel bei Mistral erstellen")
                        }
                    }
                }
            }
            item {
                SettingRow(Icons.Outlined.AutoAwesome, "Chat-Modell", settings.chatModel) { modelPicker = "chat" }
            }
            item {
                SettingRow(Icons.Outlined.Code, "Code-Modell", settings.codeModel) { modelPicker = "code" }
            }
            item {
                SettingRow(Icons.Outlined.Palette, "Bildgenerator", imageProviderLabel(settings.imageProvider)) { showImageProvider = true }
            }

            item { SectionTitle("Darstellung & Sprache") }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                item {
                    SwitchRow(Icons.Outlined.Palette, "Material-You-Farben", "Statt der Logo-Farben die Farben deines Hintergrundbilds nutzen", settings.dynamicColor) {
                        scope.launch { app.settings.setDynamicColor(it) }
                    }
                }
            }
            item {
                SwitchRow(Icons.Outlined.RecordVoiceOver, "Antworten automatisch vorlesen", "Nutzt die Sprachausgabe deines Geräts", settings.autoSpeak) {
                    scope.launch { app.settings.setAutoSpeak(it) }
                }
            }

            item { SectionTitle("Updates") }
            item {
                SwitchRow(Icons.Outlined.SystemUpdate, "Automatisch aktualisieren", "Sucht regelmäßig nach neuen Versionen", settings.autoUpdate) {
                    scope.launch { app.settings.setAutoUpdate(it) }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Installierte Version: ${app.updates.currentVersionName} (Build ${app.updates.currentVersionCode})",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    val statusText = when (val s = updateState) {
                        UpdateState.Checking -> "Suche nach Updates …"
                        UpdateState.UpToDate -> "Du hast die neueste Version ✓"
                        UpdateState.NotConfigured -> "Diese Version wurde lokal gebaut – Selbst-Update ist deaktiviert."
                        is UpdateState.Available -> "Version ${s.info.versionName} ist verfügbar."
                        is UpdateState.Downloading -> "Lade herunter … ${(s.progress * 100).toInt()} %"
                        is UpdateState.Installing -> "Installation läuft …"
                        is UpdateState.NeedsPermission -> "Berechtigung zum Installieren erforderlich."
                        is UpdateState.Failed -> s.message
                        UpdateState.Idle -> null
                    }
                    statusText?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (updateState is UpdateState.Failed) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { scope.launch { app.updates.check() } },
                            enabled = updateState !is UpdateState.Checking && updateState !is UpdateState.Downloading
                        ) { Text("Nach Updates suchen") }
                        (updateState as? UpdateState.Available)?.let { s ->
                            Button(onClick = { scope.launch { app.updates.downloadAndInstall(s.info) } }) {
                                Text("Installieren")
                            }
                        }
                    }
                }
            }

            item { SectionTitle("Daten & Datenschutz") }
            item {
                ListItem(
                    leadingContent = { Icon(Icons.Outlined.Shield, null) },
                    headlineContent = { Text("Europäisch & privat") },
                    supportingContent = {
                        Text(
                            "Anfragen gehen ausschließlich an Mistral AI (Paris, Rechenzentren in der EU, DSGVO). " +
                                "Dein Schlüssel wird mit dem Android-Keystore verschlüsselt. Unterhaltungen und Bilder " +
                                "bleiben auf deinem Gerät. Es gibt keine Werbung und kein Tracking."
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                )
            }
            item {
                ListItem(
                    leadingContent = { Icon(Icons.Outlined.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
                    headlineContent = { Text("Alle Unterhaltungen & Bilder löschen", color = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.clickable { confirmDelete = true },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                )
            }
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item {
                ListItem(
                    leadingContent = { EuFlag(Modifier.height(20.dp)) },
                    headlineContent = { Text("KI-GenC ${app.updates.currentVersionName}") },
                    supportingContent = {
                        Text(
                            "Unabhängige App – kein offizielles Angebot der Europäischen Union.\n" +
                                "Kotlin · Jetpack Compose · Material 3 · Mistral AI\n" +
                                (if (app.updates.repo.isNotBlank()) "Updates: github.com/${app.updates.repo}" else "")
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                )
            }
        }
    }

    modelPicker?.let { which ->
        val current = if (which == "chat") settings.chatModel else settings.codeModel
        val defaults = if (which == "chat") DEFAULT_CHAT_MODELS else DEFAULT_CODE_MODELS
        val fetched = settings.cachedModels.filter { id ->
            if (which == "code") id.contains("code") || id.contains("devstral") || id.startsWith("mistral-") || id.startsWith("magistral")
            else !id.contains("codestral") && !id.contains("embed")
        }
        val options = (listOf(current) + defaults + fetched).distinct()
        AlertDialog(
            onDismissRequest = { modelPicker = null },
            title = { Text(if (which == "chat") "Chat-Modell wählen" else "Code-Modell wählen") },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (settings.cachedModels.isEmpty()) {
                        Text(
                            "Tipp: Nach „Speichern & testen“ erscheinen hier alle für dein Konto verfügbaren Modelle.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    options.forEach { id ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(selected = id == current, role = Role.RadioButton) {
                                    scope.launch {
                                        if (which == "chat") app.settings.setChatModel(id) else app.settings.setCodeModel(id)
                                    }
                                    modelPicker = null
                                }
                                .padding(vertical = 6.dp)
                        ) {
                            RadioButton(selected = id == current, onClick = null)
                            Spacer(Modifier.size(8.dp))
                            Column {
                                Text(id)
                                if (id.endsWith("-latest")) {
                                    Text(
                                        "aktualisiert sich automatisch auf die neueste Version",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { modelPicker = null }) { Text("Schließen") } }
        )
    }

    if (showImageProvider) {
        var bflInput by remember { mutableStateOf(settings.bflKey) }
        AlertDialog(
            onDismissRequest = { showImageProvider = false },
            title = { Text("Bildgenerator wählen") },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    IMAGE_PROVIDERS.forEach { (id, title, desc) ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(selected = settings.imageProvider == id, role = Role.RadioButton) {
                                    scope.launch { app.settings.setImageProvider(id) }
                                }
                                .padding(vertical = 6.dp)
                        ) {
                            RadioButton(selected = settings.imageProvider == id, onClick = null)
                            Spacer(Modifier.size(8.dp))
                            Column {
                                Text(title)
                                Text(desc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Text(
                        "Gesichtstreue Fotobearbeitung: Mit einem Black-Forest-Labs-Schlüssel bearbeitet die App dein echtes " +
                            "Foto (FLUX Kontext), Gesichter bleiben erhalten – egal welcher Generator oben gewählt ist.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                    run {
                        OutlinedTextField(
                            value = bflInput, onValueChange = { bflInput = it },
                            label = { Text("Black-Forest-Labs-Schlüssel (optional)") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        )
                        TextButton(onClick = { uri.openUri("https://dashboard.bfl.ai/") }) { Text("Schlüssel bei bfl.ai erstellen") }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { app.settings.setBflKey(bflInput) }
                    showImageProvider = false
                }) { Text("Speichern") }
            }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Alles löschen?") },
            text = { Text("Alle Unterhaltungen und erzeugten Bilder werden unwiderruflich von diesem Gerät gelöscht.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        app.chatStore.deleteAll()
                        Toast.makeText(context, "Alle Daten gelöscht – die App startet neu.", Toast.LENGTH_SHORT).show()
                        delay(1200)
                        android.os.Process.killProcess(android.os.Process.myPid())
                    }
                }) { Text("Löschen", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") } }
        )
    }
}

private val IMAGE_PROVIDERS = listOf(
    Triple(AppSettings.IMAGE_AUTO, "Automatisch (empfohlen)", "Erst Mistral – klappt das nicht (z. B. im Gratis-Tarif), kostenlos über Pollinations."),
    Triple(AppSettings.IMAGE_MISTRAL, "Mistral AI", "FLUX über Mistral (EU). Im Gratis-Tarif oft nicht verfügbar."),
    Triple(AppSettings.IMAGE_POLLINATIONS, "Pollinations (kostenlos)", "Ohne Schlüssel, etwa 1 Bild pro 15 Sekunden. Nur der Bildtext wird übertragen."),
    Triple(AppSettings.IMAGE_BFL, "Black Forest Labs (FLUX)", "Deutscher Anbieter aus Freiburg, eigener Schlüssel, Abrechnung pro Bild."),
)

private fun imageProviderLabel(id: String) = IMAGE_PROVIDERS.firstOrNull { it.first == id }?.second ?: "Automatisch (empfohlen)"

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, value: String, onClick: () -> Unit) {
    ListItem(
        leadingContent = { Icon(icon, null) },
        headlineContent = { Text(title) },
        supportingContent = { Text(value) },
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
    )
}

@Composable
private fun SwitchRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        leadingContent = { Icon(icon, null) },
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        modifier = Modifier.clickable { onChange(!checked) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
    )
}
