package xyz.sandboiii.agentcooper.domain.usecase

import kotlinx.coroutines.flow.Flow
import xyz.sandboiii.agentcooper.domain.repository.ChatRepository

class SendMessageUseCase(
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke(
        sessionId: String,
        content: String,
        modelId: String
    ): Flow<String> {
        return chatRepository.sendMessage(sessionId, content, modelId)
    }
}
