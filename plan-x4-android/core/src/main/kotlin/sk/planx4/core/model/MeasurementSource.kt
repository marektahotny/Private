package sk.planx4.core.model

import kotlinx.serialization.Serializable

/** Where a length/position value came from — shown in the UI so the user can tell a live DISTO reading from a manual override. */
@Serializable
enum class MeasurementSource { DISTO_X4, MANUAL }
