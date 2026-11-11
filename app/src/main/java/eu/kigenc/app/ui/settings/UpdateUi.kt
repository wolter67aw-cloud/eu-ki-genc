package eu.kigenc.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kigenc.app.update.UpdateInfo
import eu.kigenc.app.update.UpdateState
import eu.kigenc.app.update.WhatsNew
import eu.kigenc.app.update.WhatsNewEntry

/** Dialog für ein verfügbares Update inkl. Download-Fortschritt. */
@Composable
fun UpdateDialog(
    state: UpdateState,
    onInstall: (UpdateInfo) -> Unit,
    onGrantPermission: () -> Unit,
    onDismiss: () -> Unit,
) {
    val info = when (state) {
        is UpdateState.Available -> state.info
        is UpdateState.Downloading -> state.info
        is UpdateState.Installing -> state.info
        is UpdateState.NeedsPermission -> state.info
        else -> return
    }
    AlertDialog(
        onDismissRequest = { if (state is UpdateState.Available) onDismiss() },
        icon = { Icon(Icons.Outlined.SystemUpdate, null) },
        title = { Text("Neue Version ${info.versionName}") },
        text = {
            Column {
                when (state) {
                    is UpdateState.Downloading -> {
                        Text("Update wird heruntergeladen … ${(state.progress * 100).toInt()} %")
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                    }
                    is UpdateState.Installing -> {
                        Text("Installation wird gestartet. Bitte im folgenden Fenster bestätigen.")
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    is UpdateState.NeedsPermission -> Text(
                        "Damit sich KI-GenC selbst aktualisieren kann, erlaube bitte einmalig " +
                            "„Unbekannte Apps installieren“ für diese App. Danach erneut auf „Installieren“ tippen."
                    )
                    else -> {
                        Text("Eine neue Version von KI-GenC ist verfügbar.")
                        if (info.notes.isNotBlank()) {
                            Spacer(Modifier.height(12.dp))
                            Text("Neu:", style = MaterialTheme.typography.labelLarge)
                            Text(
                                WhatsNew.pretty(info.notes),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .heightIn(max = 180.dp)
                                    .verticalScroll(rememberScrollState())
                            )
                        }
                        if (info.sizeBytes > 0) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Größe: %.1f MB".format(info.sizeBytes / 1_048_576.0),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (state) {
                is UpdateState.Available -> Button(onClick = { onInstall(info) }) { Text("Jetzt installieren") }
                is UpdateState.NeedsPermission -> Button(onClick = onGrantPermission) { Text("Erlauben") }
                else -> {}
            }
        },
        dismissButton = {
            when (state) {
                is UpdateState.Available -> TextButton(onClick = onDismiss) { Text("Später") }
                is UpdateState.NeedsPermission -> TextButton(onClick = { onInstall(info) }) { Text("Installieren") }
                else -> {}
            }
        }
    )
}

/** „Was ist neu“ – nach einem Update einmalig oder jederzeit unter Optionen → Updates. */
@Composable
fun WhatsNewDialog(entries: List<WhatsNewEntry>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.NewReleases, null) },
        title = {
            Text(
                if (entries.size == 1) "Neu in Version ${entries.first().version}" else "Das ist neu"
            )
        },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (entries.isEmpty()) {
                    Text("Für diese Version liegt keine Liste der Neuerungen vor.")
                }
                entries.forEachIndexed { index, e ->
                    if (index > 0) Spacer(Modifier.height(14.dp))
                    if (entries.size > 1) {
                        Text("Version ${e.version}", style = MaterialTheme.typography.titleSmall)
                    }
                    if (e.date.isNotBlank()) {
                        Text(e.date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(4.dp))
                    e.items.forEach { item ->
                        Text("• $item", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Alles klar") } },
    )
}
