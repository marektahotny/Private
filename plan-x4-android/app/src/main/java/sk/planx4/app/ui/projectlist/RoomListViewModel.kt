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
import sk.planx4.core.model.RoomPlan

class RoomListViewModel(private val projectId: String, private val repository: ProjectRepository) : ViewModel() {

    private val _project = MutableStateFlow<Project?>(null)
    val project: StateFlow<Project?> = _project.asStateFlow()

    init {
        viewModelScope.launch { reload() }
    }

    private suspend fun reload() {
        _project.value = repository.loadAll().firstOrNull { it.id == projectId }
    }

    fun createRoom(name: String, onCreated: (RoomPlan) -> Unit) {
        viewModelScope.launch {
            val current = _project.value ?: return@launch
            val room = RoomPlan(id = UUID.randomUUID().toString(), name = name)
            repository.upsert(current.copy(rooms = current.rooms + room))
            reload()
            onCreated(room)
        }
    }

    fun deleteRoom(roomId: String) {
        viewModelScope.launch {
            val current = _project.value ?: return@launch
            repository.upsert(current.copy(rooms = current.rooms.filterNot { it.id == roomId }))
            reload()
        }
    }
}
