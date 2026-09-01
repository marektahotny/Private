package sk.planx4.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class OpeningType { DOOR, WINDOW }

/**
 * A door or window cut into a [Wall]. Position and width are stored relative to the wall's
 * own length (offset from `wall.start` to the near edge, plus width) rather than as absolute
 * coordinates, so that editing the wall afterwards doesn't leave the opening "floating".
 */
@Serializable
data class Opening(
    val id: String,
    val wallId: String,
    val type: OpeningType,
    val offsetM: Double,
    val widthM: Double,
    val heightM: Double,
    /** Parapet / sill height from the floor. Only meaningful for windows. */
    val sillM: Double? = null,
    val source: MeasurementSource = MeasurementSource.MANUAL
) {
    init {
        require(offsetM >= 0.0) { "offsetM musí byť >= 0 (bolo $offsetM)" }
        require(widthM > 0.0) { "widthM musí byť > 0 (bolo $widthM)" }
        require(heightM > 0.0) { "heightM musí byť > 0 (bolo $heightM)" }
    }

    /** An opening can end up out of bounds if the wall is shortened after the opening was placed. */
    fun fitsWithin(wallLengthM: Double): Boolean = offsetM + widthM <= wallLengthM + 1e-6
}
