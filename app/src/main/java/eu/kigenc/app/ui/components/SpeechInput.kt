package eu.kigenc.app.ui.components

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Sprachen, in denen man ins Mikrofon sprechen kann (Name → BCP-47-Tag). */
val SPEECH_LANGUAGES: List<Pair<String, String>> = listOf(
    "Deutsch" to "de-DE",
    "Englisch" to "en-US",
    "Französisch" to "fr-FR",
    "Spanisch" to "es-ES",
    "Italienisch" to "it-IT",
    "Niederländisch" to "nl-NL",
    "Polnisch" to "pl-PL",
    "Portugiesisch" to "pt-PT",
    "Türkisch" to "tr-TR",
    "Ukrainisch" to "uk-UA",
    "Russisch" to "ru-RU",
    "Griechisch" to "el-GR",
    "Tschechisch" to "cs-CZ",
    "Arabisch" to "ar-SA",
)

/**
 * Mikrofon-Knopf mit System-Spracherkennung. Das Erkannte wird über [onText] geliefert.
 * [languageTag] = null → Gerätesprache.
 */
@Composable
fun SpeechInputButton(
    prompt: String,
    onText: (String) -> Unit,
    languageTag: String? = null,
    contentDescription: String = "Spracheingabe (Mikrofon)",
) {
    val context = LocalContext.current
    val deliver by rememberUpdatedState(onText)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
                ?.takeIf { it.isNotBlank() }?.let(deliver)
        }
    }
    IconButton(onClick = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag ?: java.util.Locale.getDefault().toLanguageTag())
            .putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
        try {
            launcher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "Auf diesem Gerät ist keine Spracherkennung verfügbar.", Toast.LENGTH_LONG).show()
        }
    }) { Icon(Icons.Outlined.Mic, contentDescription = contentDescription) }
}

/** Auswahl der Sprache, in der ins Mikrofon gesprochen wird (z. B. beim Übersetzen). */
@Composable
fun SpeechLanguageChip(selectedTag: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val name = SPEECH_LANGUAGES.firstOrNull { it.second == selectedTag }?.first ?: selectedTag
    Box(modifier) {
        AssistChip(
            onClick = { open = true },
            label = { Text("Mikrofon spricht: $name") },
            leadingIcon = { Icon(Icons.Outlined.Mic, null, Modifier.size(18.dp)) },
            trailingIcon = { Icon(Icons.Outlined.ArrowDropDown, "Sprache fürs Mikrofon wählen", Modifier.size(18.dp)) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SPEECH_LANGUAGES.forEach { (label, tag) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            label,
                            color = if (tag == selectedTag) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = { onSelect(tag); open = false }
                )
            }
        }
    }
}

/** Hängt erkannten Text mit Leerzeichen an vorhandenen Text an. */
fun appendSpoken(current: String, spoken: String): String =
    listOf(current, spoken).filter { it.isNotBlank() }.joinToString(" ")
