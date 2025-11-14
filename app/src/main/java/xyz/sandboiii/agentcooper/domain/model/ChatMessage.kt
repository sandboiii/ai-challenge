package xyz.sandboiii.agentcooper.domain.model

data class ChatMessage(
    val id: String,
    val content: String,
    val role: MessageRole,
    val timestamp: Long,
    val sessionId: String,
    val modelId: String? = null, // Which AI model was used
    val responseTimeMs: Long? = null, // Response time in milliseconds
    val promptTokens: Int? = null, // Number of prompt tokens
    val completionTokens: Int? = null, // Number of completion tokens
    val contextWindowUsedPercent: Double? = null, // Percentage of context window used
    val totalCost: Double? = null, // Total cost in dollars
    val summarizationContent: String? = null // Summary text (only for SUMMARY role messages)
)

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM,
    SUMMARY
}

