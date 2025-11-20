package xyz.sandboiii.agentcooper.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import xyz.sandboiii.agentcooper.domain.model.ChatMessage

interface ChatRepository {
    fun getMessages(sessionId: String): Flow<List<ChatMessage>>
    val isSummarizing: StateFlow<Boolean> // StateFlow to observe summarization state
    suspend fun sendMessage(
        sessionId: String,
        content: String,
        modelId: String
    ): Flow<String>
    
    suspend fun sendMessageWithToolCallUpdates(
        sessionId: String,
        content: String,
        modelId: String,
        onToolCallsUpdate: (List<xyz.sandboiii.agentcooper.domain.model.ToolCall>) -> Unit = {},
        onToolResultsUpdate: (List<xyz.sandboiii.agentcooper.domain.model.ToolResult>) -> Unit = {}
    ): Flow<String>
    suspend fun deleteMessages(sessionId: String)
    suspend fun deleteAllMessages()
}

