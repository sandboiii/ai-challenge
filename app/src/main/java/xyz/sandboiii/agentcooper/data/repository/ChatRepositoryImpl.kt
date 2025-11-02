package xyz.sandboiii.agentcooper.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import xyz.sandboiii.agentcooper.data.local.database.AppDatabase
import xyz.sandboiii.agentcooper.data.local.entity.ChatMessageEntity
import xyz.sandboiii.agentcooper.data.remote.api.ChatApi
import xyz.sandboiii.agentcooper.data.remote.api.ChatMessageDto
import xyz.sandboiii.agentcooper.domain.model.ChatMessage
import xyz.sandboiii.agentcooper.domain.model.MessageRole
import xyz.sandboiii.agentcooper.domain.repository.ChatRepository
import xyz.sandboiii.agentcooper.util.Constants

import javax.inject.Inject

class ChatRepositoryImpl @Inject constructor(
    private val chatApi: ChatApi,
    private val database: AppDatabase
) : ChatRepository {
    
    override fun getMessages(sessionId: String): Flow<List<ChatMessage>> {
        return database.chatMessageDao()
            .getMessagesBySession(sessionId)
            .map { entities ->
                entities.map { it.toDomain() }
            }
    }
    
    override suspend fun sendMessage(
        sessionId: String,
        content: String,
        modelId: String
    ): Flow<String> = flow {
        // Save user message
        val userMessage = ChatMessageEntity(
            id = UUID.randomUUID().toString(),
            content = content,
            role = MessageRole.USER,
            timestamp = System.currentTimeMillis(),
            sessionId = sessionId
        )
        database.chatMessageDao().insertMessage(userMessage)
        
        // Get conversation history with system prompt
        val existingMessages = database.chatMessageDao()
            .getMessagesBySessionSync(sessionId)
        
        val messages = mutableListOf<ChatMessageDto>().apply {
            // Add system prompt as first message
            add(ChatMessageDto(role = "system", content = Constants.DEFAULT_SYSTEM_PROMPT))
            // Add existing messages
            existingMessages.forEach { msg ->
                add(ChatMessageDto(
                    role = when (msg.role) {
                        MessageRole.USER -> "user"
                        MessageRole.ASSISTANT -> "assistant"
                        MessageRole.SYSTEM -> "system"
                    },
                    content = msg.content
                ))
            }
            // Add current user message
            add(ChatMessageDto(role = "user", content = content))
        }
        
        // Create assistant message entity
        val assistantMessageId = UUID.randomUUID().toString()
        var accumulatedContent = ""
        
        // Stream response from API
        chatApi.sendMessage(messages, modelId, stream = true).collect { chunk ->
            accumulatedContent += chunk
            emit(chunk)
        }
        
        // Save complete assistant message
        val assistantMessage = ChatMessageEntity(
            id = assistantMessageId,
            content = accumulatedContent,
            role = MessageRole.ASSISTANT,
            timestamp = System.currentTimeMillis(),
            sessionId = sessionId
        )
        database.chatMessageDao().insertMessage(assistantMessage)
    }
    
    override suspend fun deleteMessages(sessionId: String) {
        database.chatMessageDao().deleteMessagesBySession(sessionId)
    }
    
    private fun ChatMessageEntity.toDomain(): ChatMessage {
        return ChatMessage(
            id = id,
            content = content,
            role = role,
            timestamp = timestamp,
            sessionId = sessionId
        )
    }
}

