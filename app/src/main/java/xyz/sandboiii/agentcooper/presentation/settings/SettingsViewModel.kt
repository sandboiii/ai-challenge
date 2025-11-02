package xyz.sandboiii.agentcooper.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import xyz.sandboiii.agentcooper.domain.usecase.DeleteAllSessionsUseCase
import xyz.sandboiii.agentcooper.util.PreferencesManager
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val deleteAllSessionsUseCase: DeleteAllSessionsUseCase
) : ViewModel() {
    
    private val _apiKey = MutableStateFlow<String>("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess: StateFlow<Boolean> = _saveSuccess.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    private val _isDeletingSessions = MutableStateFlow(false)
    val isDeletingSessions: StateFlow<Boolean> = _isDeletingSessions.asStateFlow()
    
    private val _deleteSessionsSuccess = MutableStateFlow(false)
    val deleteSessionsSuccess: StateFlow<Boolean> = _deleteSessionsSuccess.asStateFlow()
    
    init {
        loadApiKey()
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
}

