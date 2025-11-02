package xyz.sandboiii.agentcooper.presentation.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import xyz.sandboiii.agentcooper.domain.usecase.CreateSessionUseCase
import xyz.sandboiii.agentcooper.domain.usecase.DeleteSessionUseCase
import xyz.sandboiii.agentcooper.domain.usecase.GetSessionsUseCase
import xyz.sandboiii.agentcooper.util.PreferencesManager
import javax.inject.Inject

@HiltViewModel
class SessionsViewModel @Inject constructor(
    private val getSessionsUseCase: GetSessionsUseCase,
    private val createSessionUseCase: CreateSessionUseCase,
    private val deleteSessionUseCase: DeleteSessionUseCase,
    private val preferencesManager: PreferencesManager
) : ViewModel() {
    
    private val _state = MutableStateFlow<SessionsState>(SessionsState.Loading)
    val state: StateFlow<SessionsState> = _state.asStateFlow()
    
    init {
        loadSessions()
    }
    
    fun loadSessions() {
        getSessionsUseCase()
            .onEach { sessions ->
                _state.value = SessionsState.Success(sessions = sessions)
            }
            .catch { e ->
                _state.value = SessionsState.Error(
                    message = e.message ?: "Failed to load sessions"
                )
            }
            .launchIn(viewModelScope)
    }
    
    fun createSession() {
        viewModelScope.launch {
            try {
                val modelId = preferencesManager.selectedModel.first() 
                    ?: throw IllegalStateException("No model selected")
                createSessionUseCase(modelId)
                loadSessions()
            } catch (e: Exception) {
                _state.value = SessionsState.Error(
                    message = e.message ?: "Failed to create session"
                )
            }
        }
    }
    
    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            try {
                deleteSessionUseCase(sessionId)
                loadSessions()
            } catch (e: Exception) {
                _state.value = SessionsState.Error(
                    message = e.message ?: "Failed to delete session"
                )
            }
        }
    }
    
    val selectedModel = preferencesManager.selectedModel
}

sealed class SessionsState {
    data object Loading : SessionsState()
    
    data class Success(
        val sessions: List<xyz.sandboiii.agentcooper.domain.model.ChatSession>
    ) : SessionsState()
    
    data class Error(
        val message: String
    ) : SessionsState()
}

