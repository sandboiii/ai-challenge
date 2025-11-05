package xyz.sandboiii.agentcooper.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import xyz.sandboiii.agentcooper.domain.usecase.DeleteAllSessionsUseCase
import xyz.sandboiii.agentcooper.util.Constants
import xyz.sandboiii.agentcooper.util.PreferencesManager
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val deleteAllSessionsUseCase: DeleteAllSessionsUseCase
) : ViewModel() {
    
    private val _apiKey = MutableStateFlow<String>("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()
    
    private val _systemPrompt = MutableStateFlow<String>("")
    val systemPrompt: StateFlow<String> = _systemPrompt.asStateFlow()
    
    private val _suggestionsEnabled = MutableStateFlow(false)
    val suggestionsEnabled: StateFlow<Boolean> = _suggestionsEnabled.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess: StateFlow<Boolean> = _saveSuccess.asStateFlow()
    
    private val _saveSystemPromptSuccess = MutableStateFlow(false)
    val saveSystemPromptSuccess: StateFlow<Boolean> = _saveSystemPromptSuccess.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    private val _isDeletingSessions = MutableStateFlow(false)
    val isDeletingSessions: StateFlow<Boolean> = _isDeletingSessions.asStateFlow()
    
    private val _deleteSessionsSuccess = MutableStateFlow(false)
    val deleteSessionsSuccess: StateFlow<Boolean> = _deleteSessionsSuccess.asStateFlow()
    
    init {
        loadApiKey()
        loadSystemPrompt()
        loadSuggestionsEnabled()
    }
    
    private fun loadApiKey() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val key = preferencesManager.getApiKey() ?: ""
                _apiKey.value = key
            } catch (e: Exception) {
                _errorMessage.value = "Ошибка загрузки API ключа: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun updateApiKey(newKey: String) {
        _apiKey.value = newKey
    }
    
    fun saveApiKey() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _saveSuccess.value = false
            
            try {
                val keyToSave = _apiKey.value.trim()
                if (keyToSave.isBlank()) {
                    _errorMessage.value = "API ключ не может быть пустым"
                    return@launch
                }
                
                preferencesManager.setApiKey(keyToSave)
                _saveSuccess.value = true
            } catch (e: Exception) {
                _errorMessage.value = "Ошибка сохранения: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun clearSaveSuccess() {
        _saveSuccess.value = false
    }
    
    private fun loadSystemPrompt() {
        viewModelScope.launch {
            try {
                val prompt = preferencesManager.getSystemPrompt() ?: Constants.DEFAULT_SYSTEM_PROMPT
                _systemPrompt.value = prompt
            } catch (e: Exception) {
                _errorMessage.value = "Ошибка загрузки системного промпта: ${e.message}"
                _systemPrompt.value = Constants.DEFAULT_SYSTEM_PROMPT
            }
        }
    }
    
    fun updateSystemPrompt(newPrompt: String) {
        _systemPrompt.value = newPrompt
    }
    
    fun saveSystemPrompt() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _saveSystemPromptSuccess.value = false
            
            try {
                val promptToSave = _systemPrompt.value.trim()
                if (promptToSave.isBlank()) {
                    _errorMessage.value = "Системный промпт не может быть пустым"
                    return@launch
                }
                
                preferencesManager.setSystemPrompt(promptToSave)
                _saveSystemPromptSuccess.value = true
            } catch (e: Exception) {
                _errorMessage.value = "Ошибка сохранения системного промпта: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun clearSaveSystemPromptSuccess() {
        _saveSystemPromptSuccess.value = false
    }
    
    fun clearError() {
        _errorMessage.value = null
    }
    
    fun deleteAllSessions() {
        viewModelScope.launch {
            _isDeletingSessions.value = true
            _errorMessage.value = null
            _deleteSessionsSuccess.value = false
            
            try {
                deleteAllSessionsUseCase()
                _deleteSessionsSuccess.value = true
            } catch (e: Exception) {
                _errorMessage.value = "Ошибка удаления сессий: ${e.message}"
            } finally {
                _isDeletingSessions.value = false
            }
        }
    }
    
    fun clearDeleteSessionsSuccess() {
        _deleteSessionsSuccess.value = false
    }
    
    private fun loadSuggestionsEnabled() {
        viewModelScope.launch {
            try {
                val enabled = preferencesManager.getSuggestionsEnabled()
                _suggestionsEnabled.value = enabled
            } catch (e: Exception) {
                _errorMessage.value = "Ошибка загрузки настроек предложений: ${e.message}"
                _suggestionsEnabled.value = false
            }
        }
    }
    
    fun updateSuggestionsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                preferencesManager.setSuggestionsEnabled(enabled)
                _suggestionsEnabled.value = enabled
            } catch (e: Exception) {
                _errorMessage.value = "Ошибка сохранения настроек предложений: ${e.message}"
            }
        }
    }
}

