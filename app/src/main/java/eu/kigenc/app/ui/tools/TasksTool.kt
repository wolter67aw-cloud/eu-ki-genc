package eu.kigenc.app.ui.tools

import android.Manifest
import android.app.DatePickerDialog
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.CalendarContract
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import eu.kigenc.app.KiApp
import eu.kigenc.app.HomeActivity
import eu.kigenc.app.R
import eu.kigenc.app.ai.ApiMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.DateFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

data class TaskItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val due: Long? = null,
    val notes: String = "",
    val done: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("title", title)
        .put("due", due ?: JSONObject.NULL).put("notes", notes).put("done", done)

    companion object {
        fun fromJson(o: JSONObject) = TaskItem(
            id = o.optString("id", UUID.randomUUID().toString()),
            title = o.optString("title"),
            due = if (o.isNull("due")) null else o.optLong("due"),
            notes = o.optString("notes"),
            done = o.optBoolean("done"),
        )
    }
}

/** Erinnert zur Fälligkeit an eine Aufgabe. */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return Result.success()
        val title = inputData.getString("title") ?: return Result.success()
        val open = PendingIntent.getActivity(
            ctx, 2, Intent(ctx, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(ctx, KiApp.CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_update)
            .setContentTitle("Erinnerung: $title")
            .setContentText(inputData.getString("notes").orEmpty().ifBlank { "Jetzt fällig" })
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(title.hashCode(), n) }
        return Result.success()
    }
}

class TasksViewModel(app: KiApp) : AiToolViewModel(app) {
    val tasks = MutableStateFlow<List<TaskItem>>(emptyList())
    private val file = File(app.filesDir, "tasks.json")

    init {
        viewModelScope.launch {
            tasks.value = withContext(Dispatchers.IO) {
                runCatching {
                    val arr = JSONArray(file.readText())
                    List(arr.length()) { TaskItem.fromJson(arr.getJSONObject(it)) }
                }.getOrDefault(emptyList())
            }
        }
    }

    private fun update(list: List<TaskItem>) {
        val sorted = list.sortedWith(compareBy<TaskItem>({ it.done }, { it.due ?: Long.MAX_VALUE }))
        tasks.value = sorted
        viewModelScope.launch(Dispatchers.IO) {
            file.writeText(JSONArray().apply { sorted.forEach { put(it.toJson()) } }.toString())
        }
    }

    private fun scheduleReminder(t: TaskItem) {
        val due = t.due ?: return
        val delay = due - System.currentTimeMillis()
        val wm = WorkManager.getInstance(app)
        if (delay <= 0 || t.done) {
            wm.cancelUniqueWork("reminder_${t.id}")
            return
        }
        val req = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("title" to t.title, "notes" to t.notes))
            .build()
        wm.enqueueUniqueWork("reminder_${t.id}", ExistingWorkPolicy.REPLACE, req)
    }

    fun add(title: String, due: Long?, notes: String = "") {
        if (title.isBlank()) return
        val t = TaskItem(title = title.trim(), due = due, notes = notes)
        update(tasks.value + t)
        scheduleReminder(t)
    }

    fun toggle(t: TaskItem) {
        val n = t.copy(done = !t.done)
        update(tasks.value.map { if (it.id == t.id) n else it })
        scheduleReminder(n)
    }

    fun delete(t: TaskItem) {
        WorkManager.getInstance(app).cancelUniqueWork("reminder_${t.id}")
        update(tasks.value.filterNot { it.id == t.id })
    }

    fun clearDone() = update(tasks.value.filterNot { it.done })

    /** Wandelt Freitext („Morgen 10 Uhr Zahnarzt, Freitag Steuer erledigen …“) in Aufgaben um. */
    fun planWithAi(text: String) {
        if (text.isBlank()) return
        launchAi { s ->
            val now = LocalDateTime.now()
            val today = now.format(DateTimeFormatter.ofPattern("EEEE, yyyy-MM-dd HH:mm", Locale.GERMAN))
            val json = app.mistral.complete(
                s.apiKey, s.chatModel,
                listOf(
                    ApiMessage(
                        "system",
                        "Du bist ein Planungsassistent. Jetzt ist $today. Extrahiere aus dem Text alle Aufgaben und Termine " +
                            "und antworte NUR mit JSON: {\"tasks\":[{\"title\":\"kurzer Titel\",\"due\":\"YYYY-MM-DDTHH:MM\" oder null," +
                            "\"notes\":\"optionale Details\"}]}. Relative Angaben (morgen, Freitag, nächste Woche) in echte Daten umrechnen. " +
                            "Ohne Uhrzeit bei Terminen 09:00 verwenden."
                    ),
                    ApiMessage("user", text)
                ),
                json = true
            )
            val arr = JSONObject(json.substring(json.indexOf('{').coerceAtLeast(0))).optJSONArray("tasks") ?: JSONArray()
            val created = List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                TaskItem(title = o.optString("title").ifBlank { "Aufgabe" }, due = parseDue(o.optString("due")), notes = o.optString("notes"))
            }
            if (created.isEmpty()) {
                message.value = "Keine Aufgaben erkannt."
            } else {
                update(tasks.value + created)
                created.forEach(::scheduleReminder)
                message.value = "${created.size} Aufgabe(n) hinzugefügt."
            }
        }
    }

    private fun parseDue(s: String?): Long? {
        if (s.isNullOrBlank() || s == "null") return null
        val zone = ZoneId.systemDefault()
        return runCatching { LocalDateTime.parse(s.take(16)).atZone(zone).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { LocalDate.parse(s.take(10)).atTime(9, 0).atZone(zone).toInstant().toEpochMilli() }.getOrNull()
    }

    fun dayPlan() {
        val open = tasks.value.filterNot { it.done }
        if (open.isEmpty()) {
            message.value = "Keine offenen Aufgaben."
            return
        }
        val fmt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        val list = open.joinToString("\n") { t -> "- ${t.title}" + (t.due?.let { " (fällig ${fmt.format(Date(it))})" } ?: "") + t.notes.let { if (it.isBlank()) "" else " – $it" } }
        stream(
            "Du bist ein Zeitmanagement-Coach. Antworte auf Deutsch in Markdown.",
            ApiMessage(
                "user",
                "Heute ist ${LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", Locale.GERMAN))}. " +
                    "Erstelle aus diesen offenen Aufgaben einen realistischen Plan für heute und die nächsten Tage " +
                    "mit Prioritäten (Eisenhower), Zeitblöcken und Pausen:\n$list"
            ),
            saveAs = "Tagesplan"
        )
    }
}

private fun addToCalendar(context: Context, t: TaskItem) {
    val begin = t.due ?: System.currentTimeMillis()
    val intent = Intent(Intent.ACTION_INSERT)
        .setData(CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.Events.TITLE, t.title)
        .putExtra(CalendarContract.Events.DESCRIPTION, t.notes)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, begin + 60 * 60 * 1000)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

private fun pickDateTime(context: Context, onPicked: (Long) -> Unit) {
    val cal = Calendar.getInstance()
    DatePickerDialog(context, { _, y, m, d ->
        TimePickerDialog(context, { _, h, min ->
            cal.set(y, m, d, h, min, 0)
            onPicked(cal.timeInMillis)
        }, 9, 0, true).show()
    }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
}

@Composable
fun TasksScreen(vm: TasksViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val output by vm.output.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var aiText by rememberSaveable { mutableStateOf("") }
    var title by rememberSaveable { mutableStateOf("") }
    var due by rememberSaveable { mutableStateOf<Long?>(null) }
    val fmt = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)

    ToolScaffold("Aufgaben & Termine", onBack, modifier, message, vm::clearMessage) { inner ->
        LazyColumn(contentPadding = toolPadding(inner), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Mit KI planen", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding16())
                        OutlinedTextField(
                            value = aiText, onValueChange = { aiText = it },
                            placeholder = { Text("z. B. „Morgen 10 Uhr Zahnarzt, Freitag Steuererklärung, nächste Woche Mama anrufen“") },
                            minLines = 2, shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth().padding16()
                        )
                        Button(
                            onClick = { vm.planWithAi(aiText); aiText = "" },
                            enabled = !busy && aiText.isNotBlank(),
                            modifier = Modifier.padding16()
                        ) {
                            Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text("Aufgaben erkennen")
                        }
                        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                        Spacer(Modifier.size(4.dp))
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = title, onValueChange = { title = it },
                        placeholder = { Text("Neue Aufgabe") }, singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { pickDateTime(context) { due = it } }) {
                        Icon(Icons.Outlined.Event, if (due == null) "Termin wählen" else "Termin ändern",
                            tint = if (due == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { vm.add(title, due); title = ""; due = null }, enabled = title.isNotBlank()) {
                        Icon(Icons.Outlined.Add, "Hinzufügen")
                    }
                }
                due?.let { Text("Fällig: ${fmt.format(Date(it))} (mit Erinnerung)", style = MaterialTheme.typography.labelSmall) }
            }
            if (tasks.isEmpty()) {
                item {
                    Text("Noch keine Aufgaben.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(tasks, key = { it.id }) { t ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = t.done, onCheckedChange = { vm.toggle(t) })
                    Column(Modifier.weight(1f)) {
                        Text(t.title, textDecoration = if (t.done) TextDecoration.LineThrough else null)
                        val sub = listOfNotNull(t.due?.let { fmt.format(Date(it)) }, t.notes.ifBlank { null }).joinToString(" · ")
                        if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { addToCalendar(context, t) }) { Icon(Icons.Outlined.CalendarMonth, "In Kalender eintragen") }
                    IconButton(onClick = { vm.delete(t) }) { Icon(Icons.Outlined.Delete, "Löschen") }
                }
            }
            if (tasks.isNotEmpty()) {
                item { HorizontalDivider() }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = vm::dayPlan, enabled = !busy) { Text("Tagesplan erstellen") }
                        if (tasks.any { it.done }) OutlinedButton(onClick = vm::clearDone) { Text("Erledigte entfernen") }
                    }
                }
            }
            if (output.isNotEmpty()) item { ResultCard(output, busy, "dayplan") }
        }
    }
}

private fun Modifier.padding16() = this.padding(horizontal = 16.dp, vertical = 4.dp)
