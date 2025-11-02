package xyz.sandboiii.agentcooper.domain.model

data class ChatSession(
    val id: String,
    val title: String,
    val createdAt: Long,
    val modelId: String
)

