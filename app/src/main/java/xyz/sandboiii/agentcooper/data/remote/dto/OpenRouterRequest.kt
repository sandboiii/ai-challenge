package xyz.sandboiii.agentcooper.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class OpenRouterRequest(
    val model: String,
    val messages: List<OpenRouterMessage>,
    val stream: Boolean = true,
    val temperature: Double? = null,
    val usage: UsageRequest? = null
)

@Serializable
data class UsageRequest(
    val include: Boolean // No default value to ensure it's always serialized
)

@Serializable
data class OpenRouterMessage(
    val role: String,
    val content: String
)

