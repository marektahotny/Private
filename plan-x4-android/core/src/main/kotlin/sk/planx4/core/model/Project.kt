package sk.planx4.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Project(
    val id: String,
    val name: String,
    val rooms: List<RoomPlan> = emptyList(),
    val createdAtEpochMs: Long = System.currentTimeMillis()
)
