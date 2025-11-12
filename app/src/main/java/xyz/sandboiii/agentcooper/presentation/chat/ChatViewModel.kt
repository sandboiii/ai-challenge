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
import xyz.sandboiii.agentcooper.domain.repository.ChatRepository
import xyz.sandboiii.agentcooper.data.remote.api.ChatApi
import xyz.sandboiii.agentcooper.domain.usecase.GetMessagesUseCase
import xyz.sandboiii.agentcooper.domain.usecase.SendMessageUseCase
import xyz.sandboiii.agentcooper.util.PreferencesManager
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val sendMessageUseCase: SendMessageUseCase,
    private val getMessagesUseCase: GetMessagesUseCase,
    private val chatRepository: ChatRepository,
    private val preferencesManager: PreferencesManager,
    private val chatApi: ChatApi
) : ViewModel() {
    
    companion object {
        private const val TAG = "ChatViewModel"
    }
    
    private val _state = MutableStateFlow<ChatState>(ChatState.Loading)
    val state: StateFlow<ChatState> = _state.asStateFlow()
    
    private val _modelInfo = MutableStateFlow<ModelInfo?>(null)
    val modelInfo: StateFlow<ModelInfo?> = _modelInfo.asStateFlow()
    
    val welcomeMessageEnabled = preferencesManager.welcomeMessageEnabled
    
    private var currentSessionId: String? = null
    private var currentModelId: String? = null
    
    data class ModelInfo(
        val name: String,
        val contextLengthDisplay: String?
    )
    
    fun initialize(sessionId: String, modelId: String) {
        currentSessionId = sessionId
        currentModelId = modelId
        loadModelInfo(modelId)
        handleIntent(ChatIntent.LoadMessages)
    }
    
    private fun loadModelInfo(modelId: String) {
        viewModelScope.launch {
            try {
                val models = chatApi.getModels()
                val model = models.find { it.id == modelId }
                if (model != null) {
                    val contextDisplay = model.contextLengthDisplay ?: 
                        model.contextLength?.let { formatContextLength(it) }
                    _modelInfo.value = ModelInfo(
                        name = model.name,
                        contextLengthDisplay = contextDisplay
                    )
                } else {
                    // Fallback to modelId if not found
                    _modelInfo.value = ModelInfo(
                        name = modelId,
                        contextLengthDisplay = null
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load model info", e)
                // Fallback to modelId
                _modelInfo.value = ModelInfo(
                    name = modelId,
                    contextLengthDisplay = null
                )
            }
        }
    }
    
    private fun formatContextLength(contextLength: Int): String {
        return when {
            contextLength >= 1_000_000 -> "${contextLength / 1_000_000}M"
            contextLength >= 1_000 -> "${contextLength / 1_000}K"
            else -> contextLength.toString()
        }
    }
    
    fun handleIntent(intent: ChatIntent) {
        when (intent) {
            is ChatIntent.SendMessage -> sendMessage(intent.content)
            is ChatIntent.LoadMessages -> loadMessages()
            is ChatIntent.ClearError -> clearError()
            is ChatIntent.RetryLastMessage -> retryLastMessage()
            is ChatIntent.ClearMessages -> clearMessages()
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
                // Preserve streaming message if we're currently streaming
                // The streaming message is not in the database, so we need to add it back
                val isCurrentlyStreaming = currentState is ChatState.Success && currentState.isStreaming
                
                // IMPORTANT: Read streamingContent AFTER checking isStreaming to avoid race conditions
                // If we're streaming, we should NOT update the messages list from database
                // because sendMessage() is already updating it with the streaming message
                if (isCurrentlyStreaming) {
                    // During streaming, don't update messages from database - let sendMessage() handle it
                    // This prevents race conditions where loadMessages() overwrites the streaming message
                    Log.d(TAG, "loadMessages: Skipping update during streaming - sendMessage() is handling updates")
                    return@onEach
                }
                
                // Not streaming - update messages from database normally
                val finalMessages = messages
                val preserveWaiting = currentState is ChatState.Success && currentState.isWaitingForResponse
                
                Log.d(TAG, "loadMessages: Not streaming, updating from database, messages.count=${finalMessages.size}")
                
                _state.value = ChatState.Success(
                    messages = finalMessages,
                    lastError = if (currentState is ChatState.Success) currentState.lastError else null,
                    isStreaming = false,
                    streamingContent = "",
                    isWaitingForResponse = preserveWaiting
                )
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
                        // First chunk received - hide loading animation and create streaming message immediately
                        if (!hasReceivedFirstChunk) {
                            hasReceivedFirstChunk = true
                            Log.d(TAG, "First chunk received, hiding loading indicator and creating streaming message")
                        }
                        
                        accumulatedContent += chunk
                        Log.d(TAG, "Received chunk (length: ${chunk.length}): ${chunk.take(50)}...")
                        Log.d(TAG, "Accumulated content length: ${accumulatedContent.length}")
                        
                        // Create temporary assistant message for streaming immediately when first chunk arrives
                        // This ensures the streaming content is displayed in real-time
                        // Create the message even if content is empty initially (it will update as more chunks arrive)
                        val streamingMessage = ChatMessage(
                            id = "streaming",
                            content = accumulatedContent,
                            role = MessageRole.ASSISTANT,
                            timestamp = System.currentTimeMillis(),
                            sessionId = sessionId
                        )
                        
                        val updatedMessages = messagesWithUser + streamingMessage
                        
                        // Hide waiting indicator as soon as we receive any chunk
                        // The streaming message will be displayed immediately, even if empty
                        _state.value = ChatState.Success(
                            messages = updatedMessages,
                            isStreaming = true,
                            streamingContent = accumulatedContent,
                            lastError = null, // Clear error when receiving chunks
                            isWaitingForResponse = false // Hide thinking animation once streaming starts
                        )
                        
                        Log.d(TAG, "Updated state: isStreaming=true, isWaitingForResponse=false, messages.count=${updatedMessages.size}, hasStreamingMessage=${updatedMessages.any { it.id == "streaming" }}")
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
    
    private fun clearMessages() {
        val sessionId = currentSessionId ?: return
        
        viewModelScope.launch {
            try {
                chatRepository.deleteMessages(sessionId)
                // Reload messages to show empty state
                loadMessages()
                Log.d(TAG, "Cleared all messages for session: $sessionId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear messages", e)
                _state.value = ChatState.Error(
                    message = e.message ?: "Failed to clear messages"
                )
            }
        }
    }
}

