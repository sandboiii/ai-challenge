package xyz.sandboiii.agentcooper.domain.usecase

import kotlinx.coroutines.flow.Flow
import xyz.sandboiii.agentcooper.domain.model.ChatSession
import xyz.sandboiii.agentcooper.domain.repository.SessionRepository

class GetSessionsUseCase(
    private val sessionRepository: SessionRepository
) {
    operator fun invoke(): Flow<List<ChatSession>> {
        return sessionRepository.getAllSessions()
    }
}

