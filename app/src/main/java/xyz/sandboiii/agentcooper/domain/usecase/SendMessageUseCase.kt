package xyz.sandboiii.agentcooper.domain.usecase

import kotlinx.coroutines.flow.Flow
import xyz.sandboiii.agentcooper.domain.repository.ChatRepository
import xyz.sandboiii.agentcooper.domain.model.ToolCall
import xyz.sandboiii.agentcooper.domain.model.ToolResult

data class StreamingUpdate(
    val content: String? = null,
    val toolCalls: List<ToolCall>? = null,
    val toolResults: List<ToolResult>? = null
)

class SendMessageUseCase(
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke(
        sessionId: String,
        content: String,
        modelId: String
    ): Flow<String> {
        return chatRepository.sendMessage(sessionId, content, modelId)
    }
    
    suspend fun invokeWithToolCallUpdates(
        sessionId: String,
        content: String,
        modelId: String,
        onToolCallsUpdate: (List<ToolCall>) -> Unit = {},
        onToolResultsUpdate: (List<ToolResult>) -> Unit = {}
    ): Flow<String> {
        return chatRepository.sendMessageWithToolCallUpdates(sessionId, content, modelId, onToolCallsUpdate, onToolResultsUpdate)
    }
}
