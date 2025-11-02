package xyz.sandboiii.agentcooper.data.remote.api

import kotlinx.coroutines.flow.Flow

interface ChatApi {
    suspend fun sendMessage(
        messages: List<ChatMessageDto>,
        model: String,
        stream: Boolean = true
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
    val pricing: PricingInfo? = null
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

