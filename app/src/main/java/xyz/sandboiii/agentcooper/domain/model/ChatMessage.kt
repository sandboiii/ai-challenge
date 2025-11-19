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
    val summarizationContent: String? = null, // Summary text (only for SUMMARY role messages)
    val toolCalls: List<ToolCall>? = null, // Tool calls made by the assistant
    val toolResults: List<ToolResult>? = null // Results from tool executions
)

data class ToolCall(
    val id: String,
    val name: String,
    val arguments: String // JSON string
)

data class ToolResult(
    val toolCallId: String,
    val toolName: String,
    val result: String,
    val isError: Boolean = false
)

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM,
    SUMMARY
}

