package xyz.sandboiii.agentcooper.domain.repository

import kotlinx.coroutines.flow.Flow
import xyz.sandboiii.agentcooper.domain.model.ChatMessage

interface ChatRepository {
    fun getMessages(sessionId: String): Flow<List<ChatMessage>>
    suspend fun sendMessage(
        sessionId: String,
        content: String,
        modelId: String
    ): Flow<String>
    suspend fun deleteMessages(sessionId: String)
}

