package xyz.sandboiii.agentcooper.domain.usecase

import xyz.sandboiii.agentcooper.domain.model.ChatSession
import xyz.sandboiii.agentcooper.domain.repository.SessionRepository

class CreateSessionUseCase(
    private val sessionRepository: SessionRepository
) {
    suspend operator fun invoke(modelId: String): ChatSession {
        return sessionRepository.createSession(modelId)
    }
}

