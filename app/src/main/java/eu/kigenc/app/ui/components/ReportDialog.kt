package eu.kigenc.app.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import eu.kigenc.app.BuildConfig
import eu.kigenc.app.data.ErrorLog
import java.net.URLEncoder

private val REASONS = listOf(
    "Beleidigend oder hasserfüllt",
    "Sexuelle oder unangemessene Inhalte",
    "Gewalt oder gefährliche Inhalte",
    "Falsche oder irreführende Informationen",
    "Verletzt Rechte (Urheberrecht, Persönlichkeitsrecht)",
    "Sonstiges",
)

/**
 * Meldefunktion für KI-erzeugte Inhalte (Pflicht laut Google-Play-Richtlinie für generative KI).
 * Die Meldung geht per E-Mail an den Anbieter (falls hinterlegt), sonst als GitHub-Meldung.
 */
@Composable
fun ReportDialog(contentExcerpt: String, kind: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var reason by remember { mutableStateOf(REASONS.first()) }
    var comment by remember { mutableStateOf("") }
    val hasEmail = BuildConfig.REPORT_EMAIL.isNotBlank()
    var includeExcerpt by remember { mutableStateOf(hasEmail) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("KI-Inhalt melden") },
        text = {
            Column {
                Text("Warum möchtest du diesen $kind melden?", style = MaterialTheme.typography.bodyMedium)
                REASONS.forEach { r ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = reason == r, role = Role.RadioButton) { reason = r }
                            .padding(vertical = 2.dp)
                    ) {
                        RadioButton(selected = reason == r, onClick = null)
                        Spacer(Modifier.size(6.dp))
                        Text(r, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                OutlinedTextField(
                    value = comment, onValueChange = { comment = it },
                    placeholder = { Text("Optional: Details") },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                )
                if (contentExcerpt.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = includeExcerpt, onCheckedChange = { includeExcerpt = it })
                        Text(
                            if (hasEmail) "Ausschnitt des Inhalts mitsenden" else "Ausschnitt mitsenden (öffentlich sichtbar!)",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val body = buildString {
                    append("Grund: ").append(reason).append('\n')
                    if (comment.isNotBlank()) append("Details: ").append(comment).append('\n')
                    append("Art: ").append(kind).append('\n')
                    append("App: KI-GenC ").append(BuildConfig.VERSION_NAME).append('\n')
                    if (includeExcerpt && contentExcerpt.isNotBlank()) append("\nAusschnitt:\n").append(contentExcerpt.take(1500))
                }
                ErrorLog.log("Inhaltsmeldung", message = "$reason ($kind)")
                val subject = "Meldung KI-Inhalt: $reason"
                val intent = if (hasEmail) {
                    Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
                        .putExtra(Intent.EXTRA_EMAIL, arrayOf(BuildConfig.REPORT_EMAIL))
                        .putExtra(Intent.EXTRA_SUBJECT, subject)
                        .putExtra(Intent.EXTRA_TEXT, body)
                } else {
                    val repo = BuildConfig.UPDATE_REPO.ifBlank { "wolter67aw-cloud/eu-ki-genc" }
                    val url = "https://github.com/$repo/issues/new?labels=meldung&title=" +
                        URLEncoder.encode(subject, "UTF-8") + "&body=" + URLEncoder.encode(body, "UTF-8")
                    Intent(Intent.ACTION_VIEW, Uri.parse(url))
                }
                runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                onDismiss()
            }) { Text("Melden") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}
