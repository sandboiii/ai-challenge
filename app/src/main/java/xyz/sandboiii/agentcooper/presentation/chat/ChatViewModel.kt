package xyz.sandboiii.agentcooper.presentation.chat

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
    
    private val _state = MutableStateFlow<ChatState>(ChatState.Loading)
    val state: StateFlow<ChatState> = _state.asStateFlow()
    
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
        }
    }
    
    private fun loadMessages() {
        val sessionId = currentSessionId ?: return
        
        getMessagesUseCase(sessionId)
            .onEach { messages ->
                _state.value = ChatState.Success(messages = messages)
            }
            .catch { e ->
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
                
                // Update state to show streaming
                _state.value = ChatState.Success(
                    messages = currentMessages,
                    isStreaming = true,
                    streamingContent = ""
                )
                
                var accumulatedContent = ""
                
                sendMessageUseCase(sessionId, content, modelId)
                    .catch { e ->
                        _state.value = ChatState.Error(
                            message = e.message ?: "Failed to send message"
                        )
                    }
                    .collect { chunk ->
                        accumulatedContent += chunk
                        
                        // Create temporary assistant message for streaming
                        val streamingMessage = ChatMessage(
                            id = "streaming",
                            content = accumulatedContent,
                            role = MessageRole.ASSISTANT,
                            timestamp = System.currentTimeMillis(),
                            sessionId = sessionId
                        )
                        
                        val updatedMessages = currentMessages + streamingMessage
                        
                        _state.value = ChatState.Success(
                            messages = updatedMessages,
                            isStreaming = true,
                            streamingContent = accumulatedContent
                        )
                    }
                
                // Streaming complete
                _state.value = ChatState.Success(
                    messages = currentMessages,
                    isStreaming = false,
                    streamingContent = ""
                )
                
                // Reload messages to get the saved version
                loadMessages()
                
            } catch (e: Exception) {
                _state.value = ChatState.Error(
                    message = e.message ?: "Failed to send message"
                )
            }
        }
    }
    
    private fun clearError() {
        val currentState = _state.value
        if (currentState is ChatState.Error) {
            loadMessages()
        }
    }
}

