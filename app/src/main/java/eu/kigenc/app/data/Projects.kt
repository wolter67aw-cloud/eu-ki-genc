package eu.kigenc.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Ein Wissens-Eintrag eines Projekts (Text aus Datei oder eigene Notiz). */
data class ProjectFile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val text: String,
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("text", text)

    companion object {
        fun fromJson(o: JSONObject) = ProjectFile(
            id = o.optString("id", UUID.randomUUID().toString()),
            name = o.optString("name", "Datei"),
            text = o.optString("text", ""),
        )
    }
}

/**
 * Ein Projekt wie bei Claude: feste Anweisungen und Wissen (Dateien/Notizen),
 * das in jeder Unterhaltung des Projekts automatisch mitgegeben wird.
 */
data class Project(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val instructions: String = "",
    val files: List<ProjectFile> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val knowledgeChars: Int get() = files.sumOf { it.text.length }

    /** Systemtext für die KI: Anweisungen + Wissen (gekürzt auf [maxChars]). */
    fun systemText(maxChars: Int = MAX_CONTEXT): String = buildString {
        append("\n\nDiese Unterhaltung gehört zum Projekt „$name“.")
        if (instructions.isNotBlank()) {
            append("\nAnweisungen des Nutzers für dieses Projekt (immer befolgen):\n")
            append(instructions.trim())
        }
        if (files.isNotEmpty()) {
            append("\n\nProjektwissen (nutze es, wenn es zur Frage passt, und nenne die Quelle):")
            var left = maxChars
            for (f in files) {
                if (left <= 0) {
                    append("\n\n[Weiteres Projektwissen wegen der Länge ausgelassen]")
                    break
                }
                val t = f.text.take(left)
                append("\n\n--- ${f.name} ---\n").append(t)
                if (t.length < f.text.length) append("\n[gekürzt]")
                left -= t.length
            }
        }
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("instructions", instructions)
        .put("updatedAt", updatedAt)
        .put("files", JSONArray().apply { files.forEach { put(it.toJson()) } })

    companion object {
        /** So viel Projektwissen (Zeichen) geht höchstens pro Anfrage an die KI. */
        const val MAX_CONTEXT = 60_000

        fun fromJson(o: JSONObject): Project {
            val arr = o.optJSONArray("files") ?: JSONArray()
            return Project(
                id = o.optString("id", UUID.randomUUID().toString()),
                name = o.optString("name", "Projekt"),
                instructions = o.optString("instructions", ""),
                updatedAt = o.optLong("updatedAt", 0L),
                files = List(arr.length()) { ProjectFile.fromJson(arr.getJSONObject(it)) },
            )
        }
    }
}

/** Speichert Projekte lokal (filesDir/projects.json). */
class ProjectStore(context: Context) {
    private val file = File(context.filesDir, "projects.json")
    private val _projects = MutableStateFlow(load())
    val projects: StateFlow<List<Project>> = _projects.asStateFlow()

    private fun load(): List<Project> = runCatching {
        if (!file.exists()) return emptyList()
        val arr = JSONArray(file.readText())
        List(arr.length()) { Project.fromJson(arr.getJSONObject(it)) }.sortedByDescending { it.updatedAt }
    }.getOrDefault(emptyList())

    fun byId(id: String?): Project? = id?.let { i -> _projects.value.firstOrNull { it.id == i } }

    /** Stand von der Festplatte neu laden (z. B. nach dem Einspielen einer Sicherung). */
    suspend fun reload() {
        _projects.value = withContext(Dispatchers.IO) { load() }
    }

    suspend fun save(project: Project) {
        val updated = project.copy(updatedAt = System.currentTimeMillis())
        // Immer vom gespeicherten Stand ausgehen – so geht nach einer Wiederherstellung nichts verloren.
        val current = withContext(Dispatchers.IO) { load() }
        write(listOf(updated) + current.filterNot { it.id == project.id })
    }

    suspend fun delete(id: String) = write(withContext(Dispatchers.IO) { load() }.filterNot { it.id == id })

    private suspend fun write(list: List<Project>) {
        _projects.value = list
        withContext(Dispatchers.IO) {
            synchronized(this@ProjectStore) {
                val tmp = File(file.parentFile, "projects.tmp")
                tmp.writeText(JSONArray().apply { list.forEach { put(it.toJson()) } }.toString())
                if (!tmp.renameTo(file)) {
                    file.writeText(tmp.readText())
                    tmp.delete()
                }
            }
        }
    }
}
