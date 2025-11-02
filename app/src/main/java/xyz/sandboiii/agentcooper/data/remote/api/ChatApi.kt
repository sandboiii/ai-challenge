package xyz.sandboiii.agentcooper.data.remote.api

import kotlinx.coroutines.flow.Flow

interface ChatApi {
    suspend fun sendMessage(
        messages: List<ChatMessageDto>,
        model: String,
        stream: Boolean = true
    ): Flow<String>
    
    suspend fun getModels(): List<ModelDto>
}

data class ChatMessageDto(
    val role: String,
    val content: String
)

data class ModelDto(
    val id: String,
    val name: String,
    val provider: String,
    val description: String? = null
)

