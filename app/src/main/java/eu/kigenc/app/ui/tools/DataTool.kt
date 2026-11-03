package eu.kigenc.app.ui.tools

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import eu.kigenc.app.KiApp
import eu.kigenc.app.ai.ApiMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.sqrt

/** CSV-Einlesen und einfache Statistik – komplett auf dem Gerät. */
object DataAnalysis {

    fun parseCsv(text: String): List<List<String>> {
        val src = text.replace("\r\n", "\n").replace('\r', '\n')
        val first = src.lineSequence().firstOrNull { it.isNotBlank() } ?: return emptyList()
        val delimiter = listOf(';', ',', '\t', '|').maxByOrNull { d -> first.count { it == d } } ?: ','
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < src.length) {
            val c = src[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < src.length && src[i + 1] == '"') {
                        field.append('"'); i++
                    } else inQuotes = false
                } else field.append(c)
            } else when (c) {
                '"' -> inQuotes = true
                delimiter -> { row.add(field.toString().trim()); field.clear() }
                '\n' -> {
                    row.add(field.toString().trim()); field.clear()
                    if (row.any { it.isNotEmpty() }) rows.add(row)
                    row = mutableListOf()
                }
                else -> field.append(c)
            }
            i++
        }
        row.add(field.toString().trim())
        if (row.any { it.isNotEmpty() }) rows.add(row)
        val width = rows.maxOfOrNull { it.size } ?: 0
        return rows.map { r -> if (r.size < width) r + List(width - r.size) { "" } else r }
    }

    fun toNumber(raw: String): Double? {
        var s = raw.trim().replace(" ", "").replace(" ", "").trimEnd('%', '€', '$').trimStart('€', '$')
        if (s.isEmpty()) return null
        val comma = s.lastIndexOf(',')
        val dot = s.lastIndexOf('.')
        s = when {
            comma >= 0 && dot >= 0 -> if (comma > dot) s.replace(".", "").replace(',', '.') else s.replace(",", "")
            comma >= 0 -> s.replace(',', '.')
            else -> s
        }
        return s.toDoubleOrNull()
    }

    private fun median(sorted: List<Double>): Double =
        if (sorted.size % 2 == 1) sorted[sorted.size / 2] else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2

    private fun fmt(v: Double): String = if (abs(v) >= 1000 || v == v.toLong().toDouble()) "%,.2f".format(v) else "%.4g".format(v)

    /** Liefert eine Markdown-Übersicht mit Kennzahlen, Trends, Zusammenhängen und einfacher Prognose. */
    fun summarize(table: List<List<String>>): String {
        if (table.size < 2) return "Zu wenige Zeilen für eine Auswertung."
        val header = table.first().mapIndexed { i, h -> h.ifBlank { "Spalte ${i + 1}" } }
        val rows = table.drop(1)
        val sb = StringBuilder("**${rows.size} Datenzeilen, ${header.size} Spalten**\n\n")
        val numericCols = mutableMapOf<Int, List<Double?>>()
        header.forEachIndexed { col, name ->
            val values = rows.map { it.getOrElse(col) { "" } }
            val nonEmpty = values.filter { it.isNotBlank() }
            val nums = values.map { toNumber(it) }
            val numCount = nums.count { it != null }
            if (nonEmpty.isNotEmpty() && numCount >= nonEmpty.size * 0.6) {
                numericCols[col] = nums
                val list = nums.filterNotNull()
                val sorted = list.sorted()
                val mean = list.average()
                val std = sqrt(list.sumOf { (it - mean) * (it - mean) } / maxOf(1, list.size - 1))
                sb.append("### $name (Zahlen)\n")
                sb.append("- Anzahl: ${list.size}, Summe: ${fmt(list.sum())}\n")
                sb.append("- Mittelwert: ${fmt(mean)}, Median: ${fmt(median(sorted))}, Std.-Abw.: ${fmt(std)}\n")
                sb.append("- Minimum: ${fmt(sorted.first())}, Maximum: ${fmt(sorted.last())}\n")
                if (list.size >= 3) {
                    val (slope, intercept) = regression(list)
                    val trend = when {
                        abs(slope) < 1e-9 || abs(slope * list.size) < std * 0.1 -> "kein deutlicher Trend"
                        slope > 0 -> "steigend (+${fmt(slope)} pro Zeile)"
                        else -> "fallend (${fmt(slope)} pro Zeile)"
                    }
                    sb.append("- Trend über die Zeilen: $trend\n")
                    val next = (1..3).map { fmt(intercept + slope * (list.size - 1 + it)) }
                    sb.append("- Lineare Prognose nächste 3 Werte: ${next.joinToString(", ")} _(grobe Schätzung)_\n")
                    val outliers = list.count { std > 0 && abs(it - mean) > 2.5 * std }
                    if (outliers > 0) sb.append("- Auffällige Ausreißer (> 2,5 σ): $outliers\n")
                }
                sb.append('\n')
            } else {
                val counts = nonEmpty.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }
                sb.append("### $name (Text)\n")
                sb.append("- Verschiedene Werte: ${counts.size}, leer: ${values.size - nonEmpty.size}\n")
                if (counts.isNotEmpty()) {
                    sb.append("- Häufigste: ${counts.take(3).joinToString(", ") { "${it.key} (${it.value}×)" }}\n")
                }
                sb.append('\n')
            }
        }
        val cols = numericCols.keys.toList()
        val pairs = mutableListOf<Triple<String, String, Double>>()
        for (a in cols.indices) for (b in a + 1 until cols.size) {
            val xs = numericCols.getValue(cols[a])
            val ys = numericCols.getValue(cols[b])
            val pts = xs.indices.mapNotNull { k -> val x = xs[k]; val y = ys[k]; if (x != null && y != null) x to y else null }
            if (pts.size >= 5) pearson(pts)?.let { r -> pairs += Triple(header[cols[a]], header[cols[b]], r) }
        }
        val strong = pairs.filter { abs(it.third) >= 0.5 }.sortedByDescending { abs(it.third) }.take(5)
        if (strong.isNotEmpty()) {
            sb.append("### Zusammenhänge (Korrelation)\n")
            strong.forEach { (a, b, r) ->
                sb.append("- $a ↔ $b: r = ${"%.2f".format(r)} (${if (r > 0) "gleichläufig" else "gegenläufig"})\n")
            }
        }
        return sb.toString().trim()
    }

    private fun regression(ys: List<Double>): Pair<Double, Double> {
        val n = ys.size
        val mx = (n - 1) / 2.0
        val my = ys.average()
        var num = 0.0
        var den = 0.0
        ys.forEachIndexed { x, y -> num += (x - mx) * (y - my); den += (x - mx) * (x - mx) }
        val slope = if (den == 0.0) 0.0 else num / den
        return slope to (my - slope * mx)
    }

    private fun pearson(pts: List<Pair<Double, Double>>): Double? {
        val mx = pts.map { it.first }.average()
        val my = pts.map { it.second }.average()
        var sxy = 0.0
        var sxx = 0.0
        var syy = 0.0
        pts.forEach { (x, y) -> sxy += (x - mx) * (y - my); sxx += (x - mx) * (x - mx); syy += (y - my) * (y - my) }
        if (sxx == 0.0 || syy == 0.0) return null
        return sxy / sqrt(sxx * syy)
    }

    fun toCsvText(table: List<List<String>>, maxRows: Int, maxChars: Int): String {
        val text = table.take(maxRows + 1).joinToString("\n") { r -> r.joinToString(";") }
        return if (text.length > maxChars) text.take(maxChars) + "\n…" else text
    }
}

class DataViewModel(app: KiApp) : AiToolViewModel(app) {
    val table = MutableStateFlow<List<List<String>>>(emptyList())
    val stats = MutableStateFlow("")
    val sourceName = MutableStateFlow<String?>(null)

    private fun setTable(t: List<List<String>>, name: String) {
        table.value = t
        sourceName.value = name
        stats.value = if (t.size >= 2) DataAnalysis.summarize(t) else ""
        output.value = ""
        if (t.size < 2) message.value = "Keine Tabelle erkannt. Erwartet wird CSV mit Kopfzeile."
    }

    fun load(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val bytes = app.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
                    if (bytes.size > 5 * 1024 * 1024) error("Datei zu groß (max. 5 MB).")
                    if (bytes.size >= 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
                        error("Excel-Dateien bitte vorher als CSV speichern (Datei → Exportieren → CSV).")
                    }
                    val text = runCatching { Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString() }
                        .getOrElse { String(bytes, Charsets.ISO_8859_1) }
                    DataAnalysis.parseCsv(text.removePrefix("﻿"))
                }
            }.onSuccess { setTable(it, uri.lastPathSegment ?: "Datei") }
                .onFailure { message.value = it.message ?: "Datei konnte nicht gelesen werden." }
        }
    }

    fun parsePasted(text: String) {
        viewModelScope.launch {
            setTable(withContext(Dispatchers.Default) { DataAnalysis.parseCsv(text) }, "Eingefügte Daten")
        }
    }

    fun analyze(question: String) {
        val t = table.value
        if (t.size < 2) return
        val prompt = buildString {
            append(question.ifBlank { "Werte diese Tabelle aus: Was sind die wichtigsten Erkenntnisse, Muster, Auffälligkeiten und Trends? Gib eine vorsichtige Prognose." })
            append("\n\nBerechnete Kennzahlen (exakt, vom Gerät):\n").append(stats.value)
            append("\n\nAuszug der Daten (CSV, Semikolon-getrennt):\n").append(DataAnalysis.toCsvText(t, 60, 12_000))
        }
        stream(
            "Du bist ein sorgfältiger Datenanalyst. Antworte auf Deutsch in Markdown mit Überschriften und Listen. " +
                "Nutze die exakt berechneten Kennzahlen, statt selbst zu rechnen. Kennzeichne Prognosen klar als unsicher " +
                "und nenne ihre Grenzen (z. B. wenige Datenpunkte, Saisonalität).",
            ApiMessage("user", prompt),
            temperature = 0.2
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DataScreen(vm: DataViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val table by vm.table.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val output by vm.output.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val name by vm.sourceName.collectAsStateWithLifecycle()
    var pasted by rememberSaveable { mutableStateOf("") }
    var question by rememberSaveable { mutableStateOf("") }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::load) }

    ToolScaffold("Daten-Analyse", onBack, modifier, message, vm::clearMessage) { inner ->
        LazyColumn(contentPadding = toolPadding(inner), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
            item {
                Text(
                    "Tabellen auswerten, Statistiken berechnen, Muster erkennen und vorsichtige Prognosen erstellen. " +
                        "Die Zahlen werden exakt auf dem Gerät berechnet, die KI liefert die Interpretation.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                OutlinedButton(
                    onClick = { open.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "text/tab-separated-values", "*/*")) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.TableChart, null); Spacer(Modifier.size(8.dp)); Text("CSV-Datei öffnen")
                }
            }
            item {
                OutlinedTextField(
                    value = pasted, onValueChange = { pasted = it },
                    placeholder = { Text("…oder Tabelle hier einfügen (z. B. aus Excel kopiert)") },
                    minLines = 3, maxLines = 8, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()
                )
                if (pasted.isNotBlank()) {
                    Button(onClick = { vm.parsePasted(pasted) }, modifier = Modifier.padding(top = 6.dp)) { Text("Übernehmen") }
                }
            }
            if (table.size >= 2) {
                item {
                    Text("${name ?: "Tabelle"} – Vorschau", style = MaterialTheme.typography.titleSmall)
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        Column(Modifier.horizontalScroll(rememberScrollState()).padding(10.dp)) {
                            table.take(9).forEachIndexed { i, r ->
                                Row {
                                    r.forEach { cell ->
                                        Text(
                                            cell.take(18).padEnd(18), fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                                            fontWeight = if (i == 0) FontWeight.Bold else FontWeight.Normal, softWrap = false
                                        )
                                    }
                                }
                            }
                            if (table.size > 9) Text("… ${table.size - 9} weitere Zeilen", fontSize = 12.sp)
                        }
                    }
                }
                item { ResultCard(stats, false, "data-stats") }
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(
                            "Welche Muster und Auffälligkeiten gibt es?",
                            "Erstelle eine Prognose für die nächsten Perioden.",
                            "Fasse die Daten für einen Bericht zusammen.",
                            "Welche Diagramme würden die Daten gut darstellen?",
                        ).forEach { q -> AssistChip(onClick = { question = q }, label = { Text(q) }) }
                    }
                }
                item {
                    OutlinedTextField(
                        value = question, onValueChange = { question = it },
                        placeholder = { Text("Eigene Frage zu den Daten (optional)") },
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)
                    )
                }
                item {
                    Button(onClick = { vm.analyze(question) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text(if (busy) "Analysiere …" else "Mit KI auswerten")
                    }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                if (output.isNotEmpty()) item { ResultCard(output, busy, "data-ai") }
            }
        }
    }
}
