package sk.planx4.app.ui.projectlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import sk.planx4.app.data.ProjectRepository
import sk.planx4.core.model.Project

class ProjectListViewModel(private val repository: ProjectRepository) : ViewModel() {

    private val _projects = MutableStateFlow<List<Project>>(emptyList())
    val projects: StateFlow<List<Project>> = _projects.asStateFlow()

    init {
        viewModelScope.launch { _projects.value = repository.loadAll() }
    }

    fun createProject(name: String, onCreated: (Project) -> Unit) {
        viewModelScope.launch {
            val project = Project(id = UUID.randomUUID().toString(), name = name)
            repository.upsert(project)
            _projects.value = repository.loadAll()
            onCreated(project)
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            repository.delete(projectId)
            _projects.value = repository.loadAll()
        }
    }
}
