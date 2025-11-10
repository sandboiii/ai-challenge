package xyz.sandboiii.agentcooper.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class OpenRouterRequest(
    val model: String,
    val messages: List<OpenRouterMessage>,
    val stream: Boolean = true,
    val temperature: Double? = null
)

@Serializable
data class OpenRouterMessage(
    val role: String,
    val content: String
)

