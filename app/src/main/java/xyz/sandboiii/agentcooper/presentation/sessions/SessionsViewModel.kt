package xyz.sandboiii.agentcooper.presentation.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import xyz.sandboiii.agentcooper.data.remote.api.ChatApi
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
    private val preferencesManager: PreferencesManager,
    private val chatApi: ChatApi
) : ViewModel() {
    
    private val _state = MutableStateFlow<SessionsState>(SessionsState.Loading)
    val state: StateFlow<SessionsState> = _state.asStateFlow()
    
    private val _selectedModelName = MutableStateFlow<String?>(null)
    val selectedModelName: StateFlow<String?> = _selectedModelName.asStateFlow()
    
    private val _createdSession = MutableStateFlow<xyz.sandboiii.agentcooper.domain.model.ChatSession?>(null)
    val createdSession: StateFlow<xyz.sandboiii.agentcooper.domain.model.ChatSession?> = _createdSession.asStateFlow()
    
    private var cachedModels: List<xyz.sandboiii.agentcooper.data.remote.api.ModelDto> = emptyList()
    
    val selectedModel = preferencesManager.selectedModel
    val temperature = preferencesManager.temperature
    
    init {
        loadSessions()
        loadSelectedModelName()
    }
    
    fun updateTemperature(newTemp: Float) {
        viewModelScope.launch {
            try {
                preferencesManager.setTemperature(newTemp)
            } catch (e: Exception) {
                // Handle error if needed
            }
        }
    }
    
    private fun loadSelectedModelName() {
        viewModelScope.launch {
            preferencesManager.selectedModel.collect { modelId ->
                if (modelId != null) {
                    // Try to find in cached models first
                    val model = cachedModels.find { it.id == modelId }
                    if (model != null) {
                        _selectedModelName.value = model.name
                    } else {
                        // If not cached, fetch models and update cache
                        try {
                            cachedModels = chatApi.getModels()
                            val foundModel = cachedModels.find { it.id == modelId }
                            _selectedModelName.value = foundModel?.name ?: modelId
                        } catch (e: Exception) {
                            // If fetch fails, use modelId as fallback
                            _selectedModelName.value = modelId
                        }
                    }
                } else {
                    _selectedModelName.value = null
                }
            }
        }
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
                val newSession = createSessionUseCase(modelId)
                _createdSession.value = newSession // Emit the created session
                loadSessions()
            } catch (e: Exception) {
                _state.value = SessionsState.Error(
                    message = e.message ?: "Failed to create session"
                )
            }
        }
    }
    
    fun clearCreatedSession() {
        _createdSession.value = null
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
    
    fun refreshSelectedModelName() {
        viewModelScope.launch {
            val modelId = preferencesManager.selectedModel.first()
            if (modelId != null) {
                try {
                    cachedModels = chatApi.getModels()
                    val model = cachedModels.find { it.id == modelId }
                    _selectedModelName.value = model?.name ?: modelId
                } catch (e: Exception) {
                    _selectedModelName.value = modelId
                }
            } else {
                _selectedModelName.value = null
            }
        }
    }
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

