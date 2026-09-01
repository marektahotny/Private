package sk.planx4.core.model

import kotlinx.serialization.Serializable
import sk.planx4.core.geometry.Point
import sk.planx4.core.geometry.distanceBetween

/**
 * Which side of the *drawn* (measured) line the wall's thickness is added to.
 *
 * The drawn line is always a wall **face**, never the centerline by default — per the
 * decision in the "Plán X4" concept doc, section 06:
 *  - OUTSIDE: the drawn line is the interior face; thickness is added outward, away from
 *    the room. This is the common case when you measure a room from the inside.
 *  - INSIDE: the drawn line is the exterior face; thickness is added inward.
 *  - CENTER: the drawn line is the centerline; thickness is split evenly both ways.
 */
@Serializable
enum class WallSide { INSIDE, OUTSIDE, CENTER }

@Serializable
enum class WallType { LOAD_BEARING, PARTITION }

@Serializable
data class Wall(
    val id: String,
    val start: Point,
    val end: Point,
    val thicknessM: Double,
    val side: WallSide,
    val type: WallType = WallType.PARTITION,
    val source: MeasurementSource = MeasurementSource.MANUAL,
    val openings: List<Opening> = emptyList()
) {
    init {
        require(thicknessM >= 0.0) { "thicknessM musí byť >= 0 (bolo $thicknessM)" }
    }

    val lengthM: Double get() = distanceBetween(start, end)

    /**
     * How far the NET (interior-face) polygon edge sits from the drawn line, measured
     * outward-positive. Feed this into [sk.planx4.core.geometry.FloorPlanMath.offsetPolygon].
     */
    fun netEdgeOffsetM(): Double = when (side) {
        WallSide.OUTSIDE -> 0.0
        WallSide.INSIDE -> -thicknessM
        WallSide.CENTER -> -thicknessM / 2.0
    }

    /** Same as [netEdgeOffsetM] but for the GROSS (exterior-face) polygon edge. */
    fun grossEdgeOffsetM(): Double = when (side) {
        WallSide.OUTSIDE -> thicknessM
        WallSide.INSIDE -> 0.0
        WallSide.CENTER -> thicknessM / 2.0
    }

    fun withOpening(opening: Opening): Wall {
        require(opening.wallId == id) { "Opening ${opening.id} patrí stene ${opening.wallId}, nie $id" }
        require(opening.fitsWithin(lengthM)) {
            "Otvor ${opening.id} (${opening.offsetM}+${opening.widthM} m) presahuje dĺžku steny ($lengthM m)"
        }
        return copy(openings = openings + opening)
    }
}
