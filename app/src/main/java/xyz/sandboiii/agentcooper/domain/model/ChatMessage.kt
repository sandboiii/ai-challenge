package xyz.sandboiii.agentcooper.domain.model

data class ChatMessage(
    val id: String,
    val content: String,
    val role: MessageRole,
    val timestamp: Long,
    val sessionId: String,
    val mood: String? = null,
    val suggestions: List<String> = emptyList(),
    val rawJson: String? = null
)

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM
}

