package xyz.sandboiii.agentcooper.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ExportedChatMessage(
    val id: String,
    val content: String,
    val role: String, // Serialize role as string
    val timestamp: Long,
    val modelId: String? = null,
    val summarizationContent: String? = null
)

@Serializable
data class ExportedChat(
    val messages: List<ExportedChatMessage>
)

