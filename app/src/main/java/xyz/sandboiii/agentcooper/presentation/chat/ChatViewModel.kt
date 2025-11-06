package xyz.sandboiii.agentcooper.presentation.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.UUID
import xyz.sandboiii.agentcooper.domain.model.ChatMessage
import xyz.sandboiii.agentcooper.domain.model.MessageRole
import xyz.sandboiii.agentcooper.domain.usecase.GetMessagesUseCase
import xyz.sandboiii.agentcooper.domain.usecase.SendMessageUseCase
import xyz.sandboiii.agentcooper.util.PreferencesManager
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val sendMessageUseCase: SendMessageUseCase,
    private val getMessagesUseCase: GetMessagesUseCase,
    private val preferencesManager: PreferencesManager
) : ViewModel() {
    
    companion object {
        private const val TAG = "ChatViewModel"
    }
    
    private val _state = MutableStateFlow<ChatState>(ChatState.Loading)
    val state: StateFlow<ChatState> = _state.asStateFlow()
    
    val welcomeMessageEnabled = preferencesManager.welcomeMessageEnabled
    
    private var currentSessionId: String? = null
    private var currentModelId: String? = null
    
    fun initialize(sessionId: String, modelId: String) {
        currentSessionId = sessionId
        currentModelId = modelId
        handleIntent(ChatIntent.LoadMessages)
    }
    
    fun handleIntent(intent: ChatIntent) {
        when (intent) {
            is ChatIntent.SendMessage -> sendMessage(intent.content)
            is ChatIntent.LoadMessages -> loadMessages()
            is ChatIntent.ClearError -> clearError()
            is ChatIntent.RetryLastMessage -> retryLastMessage()
        }
    }
    
    private fun retryLastMessage() {
        val currentState = _state.value
        if (currentState is ChatState.Success && currentState.lastError != null) {
            // Get the last user message and retry
            val lastUserMessage = currentState.messages.lastOrNull { it.role == MessageRole.USER }
            if (lastUserMessage != null) {
                sendMessage(lastUserMessage.content)
            }
        }
    }
    
    private fun loadMessages() {
        val sessionId = currentSessionId ?: return
        
        getMessagesUseCase(sessionId)
            .onEach { messages ->
                val currentState = _state.value
                // Only preserve streaming state if we're actually still streaming
                // If streaming has completed (no streaming message in the list), don't preserve it
                val hasStreamingMessage = messages.any { it.id == "streaming" }
                val preserveStreaming = currentState is ChatState.Success && currentState.isStreaming && hasStreamingMessage
                val preserveWaiting = currentState is ChatState.Success && currentState.isWaitingForResponse
                val preserveStreamingContent = if (preserveStreaming && currentState is ChatState.Success) currentState.streamingContent else ""
                
                Log.d(TAG, "loadMessages: preserveStreaming=$preserveStreaming (hasStreamingMessage=$hasStreamingMessage), preserveWaiting=$preserveWaiting")
                
                _state.value = ChatState.Success(
                    messages = messages,
                    lastError = if (currentState is ChatState.Success) currentState.lastError else null,
                    isStreaming = preserveStreaming,
                    streamingContent = preserveStreamingContent,
                    isWaitingForResponse = preserveWaiting
                )
                
                Log.d(TAG, "loadMessages: Updated state, isStreaming=${(_state.value as? ChatState.Success)?.isStreaming}")
            }
            .catch { e ->
                Log.e(TAG, "Failed to load messages", e)
                _state.value = ChatState.Error(
                    message = e.message ?: "Failed to load messages"
                )
            }
            .launchIn(viewModelScope)
    }
    
    private fun sendMessage(content: String) {
        val sessionId = currentSessionId ?: return
        val modelId = currentModelId ?: return
        
        if (content.isBlank()) return
        
        viewModelScope.launch {
            try {
                val currentState = _state.value
                val currentMessages = if (currentState is ChatState.Success) {
                    currentState.messages
                } else {
                    emptyList()
                }
                
                // Add user message to state immediately
                val userMessage = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    content = content,
                    role = MessageRole.USER,
                    timestamp = System.currentTimeMillis(),
                    sessionId = sessionId
                )
                val messagesWithUser = currentMessages + userMessage
                
                Log.d(TAG, "Sending message: $content")
                
                // Update state to show user message and waiting for response
                _state.value = ChatState.Success(
                    messages = messagesWithUser,
                    isStreaming = true,
                    streamingContent = "",
                    lastError = null, // Clear any previous error
                    isWaitingForResponse = true // Show loading animation while waiting for first chunk
                )
                Log.d(TAG, "Set isWaitingForResponse = true")
                
                var accumulatedContent = ""
                var hasError = false
                var errorMessage: String? = null
                var hasReceivedFirstChunk = false
                
                sendMessageUseCase(sessionId, content, modelId)
                    .catch { e ->
                        hasError = true
                        errorMessage = e.message ?: "Failed to send message"
                        Log.e(TAG, "Failed to send message", e)
                        Log.e(TAG, "Error details: ${e.stackTraceToString()}")
                        
                        // Show error inline with the user message
                        _state.value = ChatState.Success(
                            messages = messagesWithUser,
                            isStreaming = false,
                            streamingContent = "",
                            lastError = errorMessage,
                            isWaitingForResponse = false
                        )
                        Log.d(TAG, "Set isWaitingForResponse = false (error)")
                    }
                    .collect { chunk ->
                        // First chunk received - hide loading animation
                        if (!hasReceivedFirstChunk) {
                            hasReceivedFirstChunk = true
                            Log.d(TAG, "First chunk received, hiding loading indicator")
                        }
                        
                        accumulatedContent += chunk
                        Log.d(TAG, "Received chunk: ${chunk.take(50)}...")
                        
                        // Create temporary assistant message for streaming
                        val streamingMessage = ChatMessage(
                            id = "streaming",
                            content = accumulatedContent,
                            role = MessageRole.ASSISTANT,
                            timestamp = System.currentTimeMillis(),
                            sessionId = sessionId
                        )
                        
                        val updatedMessages = messagesWithUser + streamingMessage
                        
                        // Only hide waiting indicator once we have actual content
                        // This ensures thinking animation shows even if SSE stream starts but first chunk is empty
                        val hasContent = accumulatedContent.isNotBlank()
                        
                        _state.value = ChatState.Success(
                            messages = updatedMessages,
                            isStreaming = true,
                            streamingContent = accumulatedContent,
                            lastError = null, // Clear error when receiving chunks
                            isWaitingForResponse = !hasContent // Keep showing loading if content is still empty
                        )
                    }
                
                if (!hasError) {
                    Log.d(TAG, "Streaming complete. Total content length: ${accumulatedContent.length}")
                    // Ensure isWaitingForResponse and isStreaming are false after streaming completes
                    val finalState = _state.value
                    if (finalState is ChatState.Success) {
                        _state.value = finalState.copy(
                            isWaitingForResponse = false,
                            isStreaming = false,
                            streamingContent = ""
                        )
                        Log.d(TAG, "Cleared isWaitingForResponse and isStreaming after streaming complete")
                    }
                    // Reload messages to get the saved version from database
                    // Note: loadMessages() will preserve streaming state, but we've already set it to false above
                    loadMessages()
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Exception in sendMessage", e)
                Log.e(TAG, "Exception details: ${e.stackTraceToString()}")
                
                val currentState = _state.value
                val currentMessages = if (currentState is ChatState.Success) {
                    currentState.messages
                } else {
                    emptyList()
                }
                
                // Show error inline
                _state.value = ChatState.Success(
                    messages = currentMessages,
                    isStreaming = false,
                    streamingContent = "",
                    lastError = e.message ?: "Failed to send message",
                    isWaitingForResponse = false
                )
            }
        }
    }
    
    private fun clearError() {
        val currentState = _state.value
        if (currentState is ChatState.Success) {
            _state.value = currentState.copy(lastError = null, isWaitingForResponse = false)
        } else if (currentState is ChatState.Error) {
            loadMessages()
        }
    }
}

