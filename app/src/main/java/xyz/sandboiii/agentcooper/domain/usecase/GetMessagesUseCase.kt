package xyz.sandboiii.agentcooper.domain.usecase

import kotlinx.coroutines.flow.Flow
import xyz.sandboiii.agentcooper.domain.model.ChatMessage
import xyz.sandboiii.agentcooper.domain.repository.ChatRepository

class GetMessagesUseCase(
    private val chatRepository: ChatRepository
) {
    operator fun invoke(sessionId: String): Flow<List<ChatMessage>> {
        return chatRepository.getMessages(sessionId)
    }
}

