@file:OptIn(ExperimentalMaterial3Api::class)

package eu.kigenc.app.ui.tools

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PhotoFilter
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.kigenc.app.app

enum class Tool(val title: String, val subtitle: String, val icon: ImageVector, val group: String) {
    PHOTO("Foto-Editor", "Fotos verbessern, Filter, KI-Neugestaltung", Icons.Outlined.PhotoFilter, "Bild & Medien"),
    VIDEO_ANALYSIS("Video analysieren", "Was passiert im Video? Inkl. Ton", Icons.Outlined.Movie, "Bild & Medien"),
    VIDEO_CREATE("Video erstellen", "Diashow-Video aus deinen Fotos", Icons.Outlined.Slideshow, "Bild & Medien"),
    TRANSCRIBE("Audio-Transkription", "Sprache in Text, Protokoll, Zusammenfassung", Icons.Outlined.GraphicEq, "Bild & Medien"),
    DATA("Daten-Analyse", "Tabellen, Statistiken, Muster, Prognosen", Icons.Outlined.Insights, "Analyse & Daten"),
    TASKS("Aufgaben & Termine", "Planen, erinnern, in den Kalender eintragen", Icons.Outlined.Checklist, "Praktische Assistenz"),
    RESEARCH("Recherche", "Aktuelle Infos aus dem Web mit Quellen", Icons.Outlined.TravelExplore, "Praktische Assistenz"),
    RESULTS("Gespeicherte Ergebnisse", "Recherchen, Transkripte, Analysen und Pläne wiederfinden", Icons.Outlined.Inventory2, "Verlauf"),
}

@Composable
fun ToolsScreen(modifier: Modifier = Modifier) {
    var current by rememberSaveable { mutableStateOf<Tool?>(null) }
    BackHandler(enabled = current != null) { current = null }
    val app = LocalContext.current.app
    val back = { current = null }

    when (current) {
        null -> ToolsHub(modifier) { current = it }
        Tool.PHOTO -> PhotoEditorScreen(viewModel(key = "tool_photo") { PhotoEditorViewModel(app) }, back, modifier)
        Tool.VIDEO_ANALYSIS -> VideoAnalysisScreen(viewModel(key = "tool_video") { VideoAnalysisViewModel(app) }, back, modifier)
        Tool.VIDEO_CREATE -> SlideshowScreen(viewModel(key = "tool_slideshow") { SlideshowViewModel(app) }, back, modifier)
        Tool.TRANSCRIBE -> TranscribeScreen(viewModel(key = "tool_transcribe") { TranscribeViewModel(app) }, back, modifier)
        Tool.DATA -> DataScreen(viewModel(key = "tool_data") { DataViewModel(app) }, back, modifier)
        Tool.TASKS -> TasksScreen(viewModel(key = "tool_tasks") { TasksViewModel(app) }, back, modifier)
        Tool.RESEARCH -> ResearchScreen(viewModel(key = "tool_research") { ResearchViewModel(app) }, back, modifier)
        Tool.RESULTS -> {
            val vm: ResultsViewModel = viewModel(key = "tool_results") { ResultsViewModel(app) }
            androidx.compose.runtime.LaunchedEffect(Unit) { vm.refresh() }
            ResultsScreen(vm, back, modifier)
        }
    }
}

@Composable
private fun ToolsHub(modifier: Modifier, onOpen: (Tool) -> Unit) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("Werkzeuge", fontWeight = FontWeight.SemiBold) }) }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            Tool.entries.groupBy { it.group }.forEach { (group, tools) ->
                item {
                    Text(
                        group, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 4.dp)
                    )
                }
                tools.forEach { t ->
                    item {
                        ListItem(
                            leadingContent = {
                                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(44.dp)) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(t.icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                    }
                                }
                            },
                            headlineContent = { Text(t.title, fontWeight = FontWeight.Medium) },
                            supportingContent = { Text(t.subtitle) },
                            trailingContent = { Icon(Icons.AutoMirrored.Outlined.ArrowForward, null) },
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.clickable { onOpen(t) }
                        )
                    }
                }
            }
            item {
                Text(
                    "Texte schreiben, E-Mails, Berichte, Übersetzen, Inhaltsanalyse, Lernhilfe und Bilder erzeugen findest du im Tab „Studio“, " +
                        "Programmieren und Automatisierungen im Tab „Code“.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp)
                )
            }
        }
    }
}
