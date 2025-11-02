package xyz.sandboiii.agentcooper.domain.usecase

import xyz.sandboiii.agentcooper.domain.repository.ChatRepository
import xyz.sandboiii.agentcooper.domain.repository.SessionRepository

class DeleteAllSessionsUseCase(
    private val sessionRepository: SessionRepository,
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke() {
        chatRepository.deleteAllMessages()
        sessionRepository.deleteAllSessions()
    }
}

