package xyz.sandboiii.agentcooper.data.remote.api

import kotlinx.coroutines.flow.Flow
import xyz.sandboiii.agentcooper.data.remote.mcp.OpenAITool

data class MessageMetadata(
    val modelId: String? = null,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null,
    val cost: Double? = null // Cost in credits from OpenRouter usage accounting
)

data class ToolCallInfo(
    val id: String,
    val name: String,
    val arguments: String // JSON string
)

data class MessageChunk(
    val content: String? = null,
    val toolCalls: List<ToolCallInfo>? = null,
    val finishReason: String? = null
)

interface ChatApi {
    suspend fun sendMessage(
        messages: List<ChatMessageDto>,
        model: String,
        stream: Boolean = true,
        onMetadata: ((MessageMetadata) -> Unit)? = null, // Callback to receive metadata
        tools: List<OpenAITool>? = null, // Optional tools for function calling
        onToolCalls: ((List<ToolCallInfo>) -> Unit)? = null // Callback to receive tool calls
    ): Flow<MessageChunk>
    
    suspend fun getModels(): List<ModelDto>
    
    /**
     * Generates a title for a conversation based on the first user message and AI response.
     * Returns a short, descriptive title (typically 2-6 words).
     */
    suspend fun generateTitle(
        userMessage: String,
        aiResponse: String,
        model: String
    ): String
}

data class ChatMessageDto(
    val role: String,
    val content: String? = null,
    val toolCallId: String? = null,
    val toolCalls: List<ToolCallInfo>? = null // Tool calls for assistant messages
)

data class ModelDto(
    val id: String,
    val name: String,
    val provider: String,
    val description: String? = null,
    val pricing: PricingInfo? = null,
    val contextLength: Int? = null, // Context window size (numeric)
    val contextLengthDisplay: String? = null // Context window size (formatted string, e.g., "8K", "128K")
)

data class PricingInfo(
    val prompt: String? = null,
    val completion: String? = null
) {
    val isFree: Boolean
        get() = (prompt == "0" || prompt == null) && (completion == "0" || completion == null)
    
    val displayText: String
        get() = when {
            isFree -> "Бесплатно"
            prompt != null && completion != null -> "$prompt / $completion"
            prompt != null -> prompt
            completion != null -> completion
            else -> "Платно"
        }
}

