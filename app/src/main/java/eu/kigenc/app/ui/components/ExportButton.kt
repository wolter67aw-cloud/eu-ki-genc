package eu.kigenc.app.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.TextSnippet
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import eu.kigenc.app.data.DocExport
import eu.kigenc.app.data.ErrorLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** „Als Datei“: KI-Antwort als Word, PDF, Excel (bei Tabellen) oder Textdatei erzeugen und teilen/speichern. */
@Composable
fun ExportButton(text: String, iconSize: Int = 20) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    val hasTable = remember(text) { DocExport.hasTable(text) }

    fun export(mime: String, make: (android.content.Context) -> File) {
        open = false
        scope.launch {
            val file = runCatching { withContext(Dispatchers.IO) { make(context) } }
            file.onSuccess { context.shareFile(it, mime, "Datei teilen oder speichern") }
                .onFailure { e ->
                    ErrorLog.log("Datei erzeugen", e)
                    Toast.makeText(context, "Datei konnte nicht erstellt werden: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }
    }

    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Outlined.FileDownload, "Als Datei (Word, PDF, Excel)", Modifier.size(iconSize.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { androidx.compose.material3.Text("Word-Datei (.docx)") },
                leadingIcon = { Icon(Icons.Outlined.Description, null) },
                onClick = { export("application/vnd.openxmlformats-officedocument.wordprocessingml.document") { DocExport.docx(it, text) } }
            )
            DropdownMenuItem(
                text = { androidx.compose.material3.Text("PDF") },
                leadingIcon = { Icon(Icons.Outlined.PictureAsPdf, null) },
                onClick = { export("application/pdf") { DocExport.pdf(it, text) } }
            )
            if (hasTable) {
                DropdownMenuItem(
                    text = { androidx.compose.material3.Text("Tabelle als Excel (.xlsx)") },
                    leadingIcon = { Icon(Icons.Outlined.TableChart, null) },
                    onClick = { export("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") { DocExport.xlsx(it, text) } }
                )
            }
            DropdownMenuItem(
                text = { androidx.compose.material3.Text("Textdatei (.md)") },
                leadingIcon = { Icon(Icons.Outlined.TextSnippet, null) },
                onClick = {
                    export("text/markdown") { ctx ->
                        File(File(ctx.cacheDir, "share").apply { mkdirs() }, DocExport.fileName(text, "md")).also { it.writeText(text) }
                    }
                }
            )
        }
    }
}
