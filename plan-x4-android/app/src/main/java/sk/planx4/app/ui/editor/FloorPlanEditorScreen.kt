package sk.planx4.app.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import sk.planx4.app.ble.DistoConnectionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FloorPlanEditorScreen(
    viewModel: FloorPlanEditorViewModel,
    roomName: String,
    onOpenDeviceScreen: () -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val connection by viewModel.connectionState.collectAsState()

    var showOpeningSheetForWallId by remember { mutableStateOf<String?>(null) }
    val wallSheetState = rememberModalBottomSheetState()
    val openingSheetState = rememberModalBottomSheetState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(roomName) },
                actions = { ConnectionPill(connection, onClick = onOpenDeviceScreen) }
            )
        },
        bottomBar = {
            EditorBottomBar(
                state = state,
                onUndo = viewModel::undoLastWall,
                onModeChange = viewModel::setMode,
                onCloseRoom = viewModel::closeRoom
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            FloorPlanCanvas(
                state = state,
                onAddManualPoint = viewModel::addPointManual,
                onAddFromDirection = viewModel::addPointFromLiveDistance,
                onMoveVertex = viewModel::moveVertex,
                onSelectWall = viewModel::selectWall
            )

            if (state.closed) {
                Row(
                    modifier = Modifier
                        .padding(12.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("%.1f m²  ·  obvod %.1f m".format(state.netAreaM2, state.netPerimeterM))
                }
            }
        }
    }

    state.selectedWall?.let { wall ->
        WallPropertiesSheet(
            wall = wall,
            sheetState = wallSheetState,
            onDismiss = { viewModel.selectWall(null) },
            onThicknessChange = { cm -> viewModel.updateWallThicknessCm(wall.id, cm) },
            onSideChange = { side -> viewModel.updateWallSide(wall.id, side) },
            onTypeChange = { type -> viewModel.updateWallType(wall.id, type) },
            onAddOpeningClick = { showOpeningSheetForWallId = wall.id }
        )
    }

    showOpeningSheetForWallId?.let { wallId ->
        val wall = state.room.walls.firstOrNull { it.id == wallId }
        if (wall != null) {
            OpeningPropertiesSheet(
                wallLengthM = wall.lengthM,
                liveDistanceM = state.liveDistanceM,
                sheetState = openingSheetState,
                onDismiss = { showOpeningSheetForWallId = null },
                onConfirm = { type, offsetM, widthM, heightM, sillM, source ->
                    viewModel.addOpening(wallId, type, offsetM, widthM, heightM, sillM, source)
                }
            )
        }
    }
}

@Composable
private fun ConnectionPill(state: DistoConnectionState, onClick: () -> Unit) {
    val label = when (state) {
        DistoConnectionState.READY -> "X4 pripojené"
        DistoConnectionState.DISCONNECTED -> "X4 nepripojené"
        DistoConnectionState.SCANNING -> "X4 hľadám…"
        DistoConnectionState.CONNECTING, DistoConnectionState.DISCOVERING_SERVICES -> "X4 pripájam…"
    }
    val color = if (state == DistoConnectionState.READY) {
        MaterialTheme.colorScheme.secondary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .padding(end = 12.dp)
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(color))
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, color = color, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun EditorBottomBar(
    state: EditorUiState,
    onUndo: () -> Unit,
    onModeChange: (EditorMode) -> Unit,
    onCloseRoom: () -> Unit
) {
    Column {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            SegmentedButton(
                selected = state.mode == EditorMode.DRAW,
                onClick = { onModeChange(EditorMode.DRAW) },
                shape = SegmentedButtonDefaults.itemShape(0, 2)
            ) { Text("Kreslenie") }
            SegmentedButton(
                selected = state.mode == EditorMode.EDIT,
                onClick = { onModeChange(EditorMode.EDIT) },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
                enabled = state.room.walls.isNotEmpty()
            ) { Text("Úprava") }
        }
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onUndo, enabled = state.room.walls.isNotEmpty() && !state.closed) {
                Icon(Icons.Outlined.Undo, contentDescription = "Vrátiť poslednú stenu")
            }

            if (state.liveDistanceM != null && state.mode == EditorMode.DRAW && !state.closed) {
                Text("%.2f m".format(state.liveDistanceM), style = MaterialTheme.typography.titleMedium)
            }

            if (!state.closed && state.room.walls.size >= 2) {
                SmallFloatingActionButton(onClick = onCloseRoom) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = "Uzavrieť miestnosť")
                }
            } else {
                Box(modifier = Modifier.size(40.dp))
            }
        }
    }
}
