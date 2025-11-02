package xyz.sandboiii.agentcooper.domain.model

data class ChatMessage(
    val id: String,
    val content: String,
    val role: MessageRole,
    val timestamp: Long,
    val sessionId: String
)

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM
}

