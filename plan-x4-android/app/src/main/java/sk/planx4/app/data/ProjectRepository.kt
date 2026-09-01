package sk.planx4.app.data

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import sk.planx4.core.model.Project

/**
 * Local-only persistence: one JSON file with all projects, written on every save. Fine for a
 * concept/MVP build with a handful of small projects; if project count/size grows this is the
 * obvious place to switch to one-file-per-project or a real database.
 */
class ProjectRepository(private val storageDir: File) {

    private val file = File(storageDir, "projects.json")

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    suspend fun loadAll(): List<Project> = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext emptyList()
        runCatching {
            json.decodeFromString<List<Project>>(file.readText())
        }.getOrElse {
            // Corrupt or unreadable file shouldn't crash the app — surface an empty list and
            // leave the bad file on disk (under projects.json.bak) so it's not silently lost.
            file.copyTo(File(storageDir, "projects.json.bak"), overwrite = true)
            emptyList()
        }
    }

    suspend fun saveAll(projects: List<Project>) = withContext(Dispatchers.IO) {
        file.writeText(json.encodeToString(projects))
    }

    suspend fun upsert(project: Project) {
        val current = loadAll().toMutableList()
        val index = current.indexOfFirst { it.id == project.id }
        if (index >= 0) current[index] = project else current += project
        saveAll(current)
    }

    suspend fun delete(projectId: String) {
        saveAll(loadAll().filterNot { it.id == projectId })
    }
}
