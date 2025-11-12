package xyz.sandboiii.agentcooper.presentation.model_selection

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import xyz.sandboiii.agentcooper.data.remote.api.ChatApi
import xyz.sandboiii.agentcooper.domain.model.AIModel
import xyz.sandboiii.agentcooper.util.PreferencesManager
import javax.inject.Inject

@HiltViewModel
class ModelSelectionViewModel @Inject constructor(
    private val chatApi: ChatApi,
    private val preferencesManager: PreferencesManager
) : ViewModel() {
    
    companion object {
        private const val TAG = "ModelSelectionViewModel"
    }
    
    private val _state = MutableStateFlow<ModelSelectionState>(ModelSelectionState.Loading)
    val state: StateFlow<ModelSelectionState> = _state.asStateFlow()
    
    val selectedModel = preferencesManager.selectedModel
    
    init {
        loadModels()
    }
    
    fun loadModels() {
        viewModelScope.launch {
            _state.value = ModelSelectionState.Loading
            try {
                Log.d(TAG, "Loading models...")
                val models = chatApi.getModels()
                Log.d(TAG, "Received ${models.size} models from API")
                val domainModels = models.map { dto ->
                    AIModel(
                        id = dto.id,
                        name = dto.name,
                        provider = dto.provider,
                        description = dto.description,
                        pricing = dto.pricing,
                        contextLength = dto.contextLength,
                        contextLengthDisplay = dto.contextLengthDisplay
                    )
                }
                Log.d(TAG, "Successfully loaded ${domainModels.size} models")
                _state.value = ModelSelectionState.Success(models = domainModels)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load models", e)
                Log.e(TAG, "Error message: ${e.message}")
                Log.e(TAG, "Error cause: ${e.cause?.message}")
                Log.e(TAG, "Error stack trace: ${e.stackTraceToString()}")
                _state.value = ModelSelectionState.Error(
                    message = e.message ?: "Failed to load models"
                )
            }
        }
    }
    
    fun selectModel(modelId: String) {
        viewModelScope.launch {
            try {
                preferencesManager.setSelectedModel(modelId)
            } catch (e: Exception) {
                _state.value = ModelSelectionState.Error(
                    message = e.message ?: "Failed to select model"
                )
            }
        }
    }
}

sealed class ModelSelectionState {
    data object Loading : ModelSelectionState()
    
    data class Success(
        val models: List<AIModel>
    ) : ModelSelectionState()
    
    data class Error(
        val message: String
    ) : ModelSelectionState()
}

