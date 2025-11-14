package xyz.sandboiii.agentcooper.data.remote.api

import kotlinx.coroutines.flow.Flow

data class MessageMetadata(
    val modelId: String? = null,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null,
    val cost: Double? = null // Cost in credits from OpenRouter usage accounting
)

interface ChatApi {
    suspend fun sendMessage(
        messages: List<ChatMessageDto>,
        model: String,
        stream: Boolean = true,
        onMetadata: ((MessageMetadata) -> Unit)? = null // Callback to receive metadata
    ): Flow<String>
    
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
    val content: String
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

