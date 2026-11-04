package eu.kigenc.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import eu.kigenc.app.R
import eu.kigenc.app.app
import eu.kigenc.app.data.AppSettings
import eu.kigenc.app.data.report
import eu.kigenc.app.ui.theme.AppBackground
import kotlinx.coroutines.launch

/** Einrichtungs-Assistent beim ersten Start: Schlüssel, Modell, Bildgenerator. */
@Composable
fun OnboardingDialog(onDone: () -> Unit) {
    val context = LocalContext.current
    val app = context.app
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    var step by remember { mutableIntStateOf(0) }
    var key by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }
    var keyResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var model by remember { mutableStateOf("mistral-small-latest") }
    var provider by remember { mutableStateOf(AppSettings.IMAGE_AUTO) }
    val steps = 4

    fun finish() {
        scope.launch {
            if (key.isNotBlank()) app.settings.setApiKey(key)
            app.settings.setChatModel(model)
            app.settings.setImageProvider(provider)
            onDone()
        }
    }

    Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        AppBackground {
            Column(
                Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(24.dp)
            ) {
                LinearProgressIndicator(progress = { (step + 1f) / steps }, modifier = Modifier.fillMaxWidth())
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(top = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    when (step) {
                        0 -> {
                            Image(painterResource(R.drawable.logo_kigenc), null, Modifier.size(120.dp).align(Alignment.CenterHorizontally))
                            Text("Willkommen bei KI-GenC", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Deine europäische KI-App: Chatten, Texte schreiben, übersetzen, Bilder erzeugen und bearbeiten, " +
                                    "Videos und Daten analysieren, Sprache in Text umwandeln, Aufgaben planen und programmieren.\n\n" +
                                    "In drei kurzen Schritten ist alles eingerichtet."
                            )
                        }
                        1 -> {
                            Text("1. Mistral-Schlüssel", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            Text(
                                "KI-GenC nutzt Mistral AI (Server in der EU). Dafür brauchst du einen eigenen, kostenlosen Schlüssel:\n" +
                                    "• console.mistral.ai öffnen und anmelden\n" +
                                    "• unter „Subscriptions“ den Tarif „Experiment“ (kostenlos) aktivieren\n" +
                                    "• unter „API Keys“ einen Schlüssel erstellen und hier einfügen"
                            )
                            TextButton(onClick = { uri.openUri("https://console.mistral.ai/api-keys") }) { Text("console.mistral.ai öffnen") }
                            OutlinedTextField(
                                value = key, onValueChange = { key = it; keyResult = null },
                                label = { Text("API-Schlüssel") }, singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            testing = true
                                            keyResult = runCatching { app.mistral.listModels(key.trim()) }
                                                .fold({ true to "Verbunden ✓ – ${it.size} Modelle verfügbar" }, { false to it.report("Einrichtung") })
                                            testing = false
                                        }
                                    },
                                    enabled = key.isNotBlank() && !testing
                                ) { Text("Testen") }
                                Spacer(Modifier.size(12.dp))
                                if (testing) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                            }
                            keyResult?.let { (ok, msg) ->
                                Text(msg, color = if (ok) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error)
                            }
                        }
                        2 -> {
                            Text("2. KI-Modell", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            listOf(
                                Triple("mistral-small-latest", "Mistral Small (empfohlen)", "Schnell und sparsam – ideal für den Gratis-Tarif, versteht auch Bilder."),
                                Triple("mistral-medium-latest", "Mistral Medium", "Ausgewogen, etwas gründlicher."),
                                Triple("mistral-large-latest", "Mistral Large", "Am stärksten, aber langsamer und schneller am Limit."),
                            ).forEach { (id, title, desc) -> Choice(model == id, title, desc) { model = id } }
                        }
                        else -> {
                            Text("3. Bildgenerator", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            listOf(
                                Triple(AppSettings.IMAGE_AUTO, "Automatisch (empfohlen)", "Erst Mistral, sonst kostenlos über Pollinations."),
                                Triple(AppSettings.IMAGE_POLLINATIONS, "Pollinations (kostenlos)", "Ohne Schlüssel, ca. 1 Bild pro 15 Sekunden."),
                                Triple(AppSettings.IMAGE_MISTRAL, "Nur Mistral", "Im Gratis-Tarif oft nicht verfügbar."),
                            ).forEach { (id, title, desc) -> Choice(provider == id, title, desc) { provider = id } }
                            Text(
                                "Tipp: Für gesichtstreue Fotobearbeitung kannst du später unter Optionen → Bildgenerator einen " +
                                    "Black-Forest-Labs-Schlüssel eintragen.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = ::finish) { Text("Überspringen") }
                    Spacer(Modifier.weight(1f))
                    if (step > 0) OutlinedButton(onClick = { step-- }) { Text("Zurück") }
                    Spacer(Modifier.size(8.dp))
                    Button(onClick = { if (step < steps - 1) step++ else finish() }) {
                        Text(if (step < steps - 1) "Weiter" else "Los geht's")
                    }
                }
            }
        }
    }
}

@Composable
private fun Choice(selected: Boolean, title: String, desc: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 6.dp)
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.size(8.dp))
        Column {
            Text(title, fontWeight = FontWeight.Medium)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
