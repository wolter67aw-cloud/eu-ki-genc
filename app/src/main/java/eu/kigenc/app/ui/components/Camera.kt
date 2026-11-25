package eu.kigenc.app.ui.components

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

/**
 * Foto mit der Kamera-App aufnehmen (ohne eigene Kamera-Berechtigung). Liefert eine Funktion zum Starten;
 * das fertige Foto kommt als Content-Uri an [onPhoto]. Der Zielpfad übersteht auch einen Neustart der App.
 */
@Composable
fun rememberTakePhoto(onError: (String) -> Unit = {}, onPhoto: (Uri) -> Unit): () -> Unit {
    val context = LocalContext.current
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    val photo by rememberUpdatedState(onPhoto)
    val error by rememberUpdatedState(onError)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pending?.let(Uri::parse)
        pending = null
        if (ok && uri != null) photo(uri)
    }
    return {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "foto_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        pending = uri.toString()
        try {
            launcher.launch(uri)
        } catch (_: ActivityNotFoundException) {
            pending = null
            error("Keine Kamera-App gefunden.")
        }
    }
}
