package xyz.sandboiii.agentcooper.domain.model

data class ChatMessage(
    val id: String,
    val content: String,
    val role: MessageRole,
    val timestamp: Long,
    val sessionId: String,
    val mood: String? = null,
    val suggestions: List<String> = emptyList(),
    val rawJson: String? = null,
    val responseTimeMs: Long? = null, // Response time in milliseconds
    val promptTokens: Int? = null, // Number of prompt tokens
    val completionTokens: Int? = null, // Number of completion tokens
    val totalCost: Double? = null // Total cost in dollars
)

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM
}

