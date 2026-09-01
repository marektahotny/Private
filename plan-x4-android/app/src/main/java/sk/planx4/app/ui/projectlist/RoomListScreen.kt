package sk.planx4.app.ui.projectlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import sk.planx4.core.geometry.FloorPlanMath
import sk.planx4.core.model.RoomPlan

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomListScreen(
    viewModel: RoomListViewModel,
    onOpenRoom: (roomId: String) -> Unit,
    onExport: () -> Unit
) {
    val project by viewModel.project.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(project?.name ?: "Projekt") },
                actions = {
                    IconButton(onClick = onExport, enabled = project?.rooms?.isNotEmpty() == true) {
                        Icon(Icons.Outlined.Share, contentDescription = "Export")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Outlined.Add, contentDescription = "Nová miestnosť")
            }
        }
    ) { padding ->
        val rooms = project?.rooms.orEmpty()
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (rooms.isNotEmpty()) {
                val totalArea = rooms.filter { it.isClosed }.sumOf { FloorPlanMath.netAreaM2(it) }
                Row(modifier = Modifier.padding(16.dp)) {
                    Text("Spolu %.1f m²".format(totalArea), style = MaterialTheme.typography.titleMedium)
                }
            }
            if (rooms.isEmpty()) {
                Text("Zatiaľ žiadna miestnosť. Pridaj ju tlačidlom + vpravo dole.", modifier = Modifier.padding(24.dp))
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(rooms, key = { it.id }) { room ->
                        RoomRow(room = room, onClick = { onOpenRoom(room.id) })
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        NameDialog(
            title = "Nová miestnosť",
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                showCreateDialog = false
                viewModel.createRoom(name) { onOpenRoom(it.id) }
            }
        )
    }
}

@Composable
private fun RoomRow(room: RoomPlan, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
        )
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(room.name, style = MaterialTheme.typography.titleMedium)
            val subtitle = if (room.isClosed) {
                "%.1f m² · obvod %.1f m".format(FloorPlanMath.netAreaM2(room), FloorPlanMath.netPerimeterM(room))
            } else {
                "rozkreslené, nedokončené"
            }
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
