package sk.planx4.core.model

import kotlinx.serialization.Serializable

/**
 * A single room's floor plan: a closed loop of walls. `walls[i].end` should coincide with
 * `walls[i+1].start` (and `walls.last().end` with `walls.first().start`) — the editor is
 * responsible for keeping that true as the user draws/edits.
 *
 * Named `RoomPlan` (not `Room`) to avoid clashing with Android's Jetpack `Room` persistence
 * library, which the `:app` module also uses.
 */
@Serializable
data class RoomPlan(
    val id: String,
    val name: String,
    val walls: List<Wall> = emptyList()
) {
    val isClosed: Boolean
        get() = walls.size >= 3
}
