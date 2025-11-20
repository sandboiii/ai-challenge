package xyz.sandboiii.agentcooper.presentation.chat

import xyz.sandboiii.agentcooper.domain.model.ChatMessage
import xyz.sandboiii.agentcooper.domain.model.ToolCall
import xyz.sandboiii.agentcooper.domain.model.ToolResult

sealed class ChatState {
    data object Loading : ChatState()
    
    data class Success(
        val messages: List<ChatMessage>,
        val isStreaming: Boolean = false,
        val streamingContent: String = "",
        val streamingToolCalls: List<ToolCall> = emptyList(), // Tool calls received during streaming
        val streamingToolResults: List<ToolResult> = emptyList(), // Tool results from streaming
        val lastError: String? = null, // Error message for the last failed message
        val isWaitingForResponse: Boolean = false // True when waiting for first chunk from API
    ) : ChatState()
    
    data class Error(
        val message: String
    ) : ChatState()
}

