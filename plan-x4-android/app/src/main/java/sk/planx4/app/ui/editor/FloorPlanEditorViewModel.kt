package sk.planx4.app.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import sk.planx4.app.ble.DistoBleManager
import sk.planx4.app.ble.DistoConnectionState
import sk.planx4.app.ble.DistoMeasurement
import sk.planx4.app.data.ProjectRepository
import sk.planx4.core.geometry.FloorPlanMath
import sk.planx4.core.geometry.Point
import sk.planx4.core.model.MeasurementSource
import sk.planx4.core.model.Opening
import sk.planx4.core.model.OpeningType
import sk.planx4.core.model.RoomPlan
import sk.planx4.core.model.Wall
import sk.planx4.core.model.WallSide
import sk.planx4.core.model.WallType

/** Default thickness (cm) new walls start with — matches the concept doc's suggested defaults. */
private const val DEFAULT_PARTITION_THICKNESS_CM = 15.0
private const val DEFAULT_LOAD_BEARING_THICKNESS_CM = 40.0

enum class EditorMode { DRAW, EDIT }

data class EditorUiState(
    val room: RoomPlan,
    val mode: EditorMode = EditorMode.DRAW,
    val selectedWallId: String? = null,
    val closed: Boolean = false,
    val liveDistanceM: Double? = null,
    val connectionState: DistoConnectionState = DistoConnectionState.DISCONNECTED
) {
    val netAreaM2: Double get() = if (closed) FloorPlanMath.netAreaM2(room) else 0.0
    val netPerimeterM: Double get() = if (closed) FloorPlanMath.netPerimeterM(room) else 0.0
    val selectedWall: Wall? get() = room.walls.firstOrNull { it.id == selectedWallId }
}

/**
 * Owns one room's draw/edit state. Combined-mode workflow (concept doc, section 05, option 3):
 *  - DRAW: [addPointManual] / [addPointFromLiveDistance] append one wall at a time, walking
 *    the room boundary; [closeRoom] connects the last point back to the first.
 *  - EDIT: [moveVertex] drags a shared corner (updates both walls that meet there);
 *    [updateWallThickness]/[updateWallSide]/[updateWallType] edit the selected wall;
 *    [addOpening]/[removeOpening] manage doors/windows on the selected wall.
 */
class FloorPlanEditorViewModel(
    private val projectId: String,
    initialRoom: RoomPlan,
    private val repository: ProjectRepository?,
    private val distoManager: DistoBleManager?
) : ViewModel() {

    private val _state = MutableStateFlow(
        EditorUiState(room = initialRoom, closed = initialRoom.isClosed)
    )
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    val connectionState: StateFlow<DistoConnectionState> =
        distoManager?.connectionState ?: MutableStateFlow(DistoConnectionState.DISCONNECTED)

    init {
        distoManager?.let { manager ->
            viewModelScope.launch {
                manager.measurements.collect { measurement ->
                    if (measurement is DistoMeasurement.Distance) {
                        _state.value = _state.value.copy(liveDistanceM = measurement.meters)
                    }
                }
            }
        }
    }

    fun setMode(mode: EditorMode) {
        _state.value = _state.value.copy(mode = mode, selectedWallId = null)
    }

    // ---- DRAW mode ---------------------------------------------------------------------

    private fun appendWall(end: Point, source: MeasurementSource) {
        val s = _state.value
        if (s.closed) return
        val walls = s.room.walls
        val start = walls.lastOrNull()?.end ?: Point(0.0, 0.0)
        val newWall = Wall(
            id = UUID.randomUUID().toString(),
            start = start,
            end = end,
            thicknessM = DEFAULT_PARTITION_THICKNESS_CM / 100.0,
            side = WallSide.OUTSIDE,
            source = source
        )
        updateRoom(s.room.copy(walls = walls + newWall))
    }

    /** Tap-to-place a corner by hand (no DISTO reading available/needed). */
    fun addPointManual(point: Point) = appendWall(point, MeasurementSource.MANUAL)

    /** Places the next corner along [directionDeg] (0° = +x axis, measured counter-clockwise)
     *  at the distance last reported by the connected X4. No-op if we haven't heard from it yet. */
    fun addPointFromLiveDistance(directionDeg: Double) {
        val distance = _state.value.liveDistanceM ?: return
        val walls = _state.value.room.walls
        val start = walls.lastOrNull()?.end ?: Point(0.0, 0.0)
        val rad = Math.toRadians(directionDeg)
        val end = Point(start.x + distance * Math.cos(rad), start.y + distance * Math.sin(rad))
        appendWall(end, MeasurementSource.DISTO_X4)
    }

    fun undoLastWall() {
        val s = _state.value
        if (s.room.walls.isEmpty()) return
        updateRoom(s.room.copy(walls = s.room.walls.dropLast(1)))
        _state.value = _state.value.copy(closed = false)
    }

    /** Connects the last point back to the first, turning the wall chain into a closed room. */
    fun closeRoom() {
        val s = _state.value
        val walls = s.room.walls
        if (walls.size < 2) return
        val first = walls.first().start
        val last = walls.last().end
        val closingWall = Wall(
            id = UUID.randomUUID().toString(),
            start = last,
            end = first,
            thicknessM = DEFAULT_PARTITION_THICKNESS_CM / 100.0,
            side = WallSide.OUTSIDE,
            source = MeasurementSource.MANUAL
        )
        updateRoom(s.room.copy(walls = walls + closingWall))
        _state.value = _state.value.copy(closed = true, mode = EditorMode.EDIT)
    }

    // ---- EDIT mode ----------------------------------------------------------------------

    fun selectWall(wallId: String?) {
        _state.value = _state.value.copy(selectedWallId = wallId)
    }

    /** Drags the shared corner at `walls[index].start` (== `walls[index-1].end`) to [newPoint]. */
    fun moveVertex(index: Int, newPoint: Point) {
        val walls = _state.value.room.walls.toMutableList()
        if (walls.isEmpty()) return
        val n = walls.size
        val prevIndex = (index - 1 + n) % n
        walls[index] = walls[index].copy(start = newPoint)
        walls[prevIndex] = walls[prevIndex].copy(end = newPoint)
        updateRoom(_state.value.room.copy(walls = walls))
    }

    fun updateWallThicknessCm(wallId: String, cm: Double) =
        updateWall(wallId) { it.copy(thicknessM = (cm / 100.0).coerceAtLeast(0.0)) }

    fun updateWallSide(wallId: String, side: WallSide) =
        updateWall(wallId) { it.copy(side = side) }

    fun updateWallType(wallId: String, type: WallType) {
        val cm = if (type == WallType.LOAD_BEARING) DEFAULT_LOAD_BEARING_THICKNESS_CM else DEFAULT_PARTITION_THICKNESS_CM
        updateWall(wallId) { it.copy(type = type, thicknessM = cm / 100.0) }
    }

    private fun updateWall(wallId: String, transform: (Wall) -> Wall) {
        val walls = _state.value.room.walls.map { if (it.id == wallId) transform(it) else it }
        updateRoom(_state.value.room.copy(walls = walls))
    }

    // ---- Openings -------------------------------------------------------------------------

    fun addOpening(wallId: String, type: OpeningType, offsetM: Double, widthM: Double, heightM: Double, sillM: Double?, source: MeasurementSource) {
        val opening = Opening(
            id = UUID.randomUUID().toString(),
            wallId = wallId,
            type = type,
            offsetM = offsetM,
            widthM = widthM,
            heightM = heightM,
            sillM = sillM,
            source = source
        )
        updateWall(wallId) { it.withOpening(opening) }
    }

    fun removeOpening(wallId: String, openingId: String) =
        updateWall(wallId) { wall -> wall.copy(openings = wall.openings.filterNot { it.id == openingId }) }

    // ---- Persistence ------------------------------------------------------------------------

    private fun updateRoom(room: RoomPlan) {
        _state.value = _state.value.copy(room = room)
        persist()
    }

    private fun persist() {
        val repo = repository ?: return
        val room = _state.value.room
        viewModelScope.launch {
            val projects = repo.loadAll()
            val project = projects.firstOrNull { it.id == projectId } ?: return@launch
            val updatedRooms = if (project.rooms.any { it.id == room.id }) {
                project.rooms.map { if (it.id == room.id) room else it }
            } else {
                project.rooms + room
            }
            repo.upsert(project.copy(rooms = updatedRooms))
        }
    }
}
