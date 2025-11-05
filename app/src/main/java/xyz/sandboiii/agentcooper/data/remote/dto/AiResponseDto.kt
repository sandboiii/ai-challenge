package xyz.sandboiii.agentcooper.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class AiResponseDto(
    val mood: String,
    val message: String,
    val suggestions: List<String>
)

