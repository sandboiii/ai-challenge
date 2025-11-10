package xyz.sandboiii.agentcooper.presentation.logical_problem

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import xyz.sandboiii.agentcooper.data.local.database.AppDatabase
import xyz.sandboiii.agentcooper.data.local.entity.SessionEntity
import xyz.sandboiii.agentcooper.util.Constants
import xyz.sandboiii.agentcooper.util.PreferencesManager
import javax.inject.Inject

@HiltViewModel
class LogicalProblemViewModel @Inject constructor(
    private val database: AppDatabase,
    private val preferencesManager: PreferencesManager
) : ViewModel() {
    
    companion object {
        private const val TAG = "LogicalProblemViewModel"
    }
    
    suspend fun getModelId(): String {
        return preferencesManager.selectedModel.first() 
            ?: throw IllegalStateException("No model selected")
    }
    
    fun ensureSessionsExist() {
        viewModelScope.launch {
            try {
                val modelId = getModelId()
                
                // Ensure all 4 logical problem sessions exist
                val sessionIds = listOf(
                    Constants.LOGICAL_CHAT_DIRECT_ID,
                    Constants.LOGICAL_CHAT_STEP_BY_STEP_ID,
                    Constants.LOGICAL_CHAT_PROMPT_WRITER_ID,
                    Constants.LOGICAL_CHAT_EXPERTS_ID
                )
                
                val sessionTitles = listOf(
                    "Прямое решение",
                    "Пошаговое решение",
                    "Создатель промптов",
                    "Команда экспертов"
                )
                
                sessionIds.forEachIndexed { index, sessionId ->
                    val existingSession = database.sessionDao().getSessionById(sessionId)
                    if (existingSession == null) {
                        val session = SessionEntity(
                            id = sessionId,
                            title = sessionTitles[index],
                            createdAt = System.currentTimeMillis(),
                            modelId = modelId
                        )
                        database.sessionDao().insertSession(session)
                        android.util.Log.d(TAG, "Created logical problem session: $sessionId")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to ensure sessions exist", e)
            }
        }
    }
}

