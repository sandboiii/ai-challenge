package xyz.sandboiii.agentcooper.presentation.chat

import xyz.sandboiii.agentcooper.domain.model.ChatMessage

sealed class ChatState {
    data object Loading : ChatState()
    
    data class Success(
        val messages: List<ChatMessage>,
        val isStreaming: Boolean = false,
        val streamingContent: String = "",
        val lastError: String? = null, // Error message for the last failed message
        val isWaitingForResponse: Boolean = false // True when waiting for first chunk from API
    ) : ChatState()
    
    data class Error(
        val message: String
    ) : ChatState()
}

