package xyz.sandboiii.agentcooper.domain.usecase

import xyz.sandboiii.agentcooper.domain.repository.ChatRepository
import xyz.sandboiii.agentcooper.domain.repository.SessionRepository

class DeleteSessionUseCase(
    private val sessionRepository: SessionRepository,
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke(sessionId: String) {
        chatRepository.deleteMessages(sessionId)
        sessionRepository.deleteSession(sessionId)
    }
}

