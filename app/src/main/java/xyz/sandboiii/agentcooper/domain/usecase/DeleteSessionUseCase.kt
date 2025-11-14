package xyz.sandboiii.agentcooper.domain.usecase

import android.util.Log
import xyz.sandboiii.agentcooper.domain.repository.SessionRepository

class DeleteSessionUseCase(
    private val sessionRepository: SessionRepository
) {
    companion object {
        private const val TAG = "DeleteSessionUseCase"
    }
    
    suspend operator fun invoke(sessionId: String) {
        Log.d(TAG, "DeleteSessionUseCase invoked for sessionId: $sessionId")
        try {
            // Удаляем файл сессии напрямую - не нужно предварительно очищать сообщения
            Log.d(TAG, "Deleting session file: $sessionId")
            sessionRepository.deleteSession(sessionId)
            Log.d(TAG, "Session deleted successfully: $sessionId")
        } catch (e: Exception) {
            Log.e(TAG, "Error in DeleteSessionUseCase for sessionId: $sessionId", e)
            throw e
        }
    }
}

