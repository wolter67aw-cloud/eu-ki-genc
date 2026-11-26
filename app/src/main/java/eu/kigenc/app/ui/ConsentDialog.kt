package eu.kigenc.app.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

/** Zustimmung zur Übertragung an KI-Dienste (einmalig vor der ersten Nutzung, widerrufbar unter Optionen). */
object AiConsent {
    const val PRIVACY_URL = "https://github.com/wolter67aw-cloud/eu-ki-genc/blob/main/docs/datenschutz.md"
    private const val KEY = "ai_consent"

    private fun prefs(context: Context) = context.getSharedPreferences("app_state", Context.MODE_PRIVATE)
    fun given(context: Context): Boolean = prefs(context).getBoolean(KEY, false)
    fun set(context: Context, value: Boolean) = prefs(context).edit().putBoolean(KEY, value).apply()

    const val TEXT =
        "KI-GenC arbeitet mit künstlicher Intelligenz externer Anbieter. Wenn du eine KI-Funktion nutzt, werden " +
            "deine Eingaben – Texte, angehängte Fotos, Videos, Dateien und Sprachaufnahmen – an Mistral AI " +
            "(Paris, Rechenzentren in der EU) gesendet und dort verarbeitet. Für Bilder kann je nach Einstellung " +
            "Pollinations.ai oder Black Forest Labs genutzt werden, für KI-Videoclips im Werbevideo Runway (USA, nur mit eigenem Schlüssel). Im kostenlosen Mistral-Tarif können Anfragen " +
            "zum Training der KI-Modelle verwendet werden.\n\n" +
            "Unterhaltungen, Bilder und Einstellungen bleiben auf deinem Gerät. Es gibt keine Werbung und kein Tracking.\n\n" +
            "Bitte gib keine sensiblen Daten wie Passwörter oder Gesundheitsdaten ein. " +
            "Deine Zustimmung kannst du jederzeit unter Optionen → Daten & Datenschutz widerrufen."
}

@Composable
fun ConsentDialog(onAccept: () -> Unit, onDecline: () -> Unit) {
    val uri = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        icon = { Icon(Icons.Outlined.Shield, null) },
        title = { Text("Datenschutz-Hinweis", modifier = Modifier.semantics { heading() }) },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                Text(AiConsent.TEXT, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { uri.openUri(AiConsent.PRIVACY_URL) }) {
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Datenschutzerklärung lesen")
                }
            }
        },
        confirmButton = { Button(onClick = onAccept) { Text("Zustimmen") } },
        dismissButton = { TextButton(onClick = onDecline) { Text("Ablehnen") } },
    )
}
