package xyz.sandboiii.agentcooper.data.local.model

import kotlinx.serialization.Serializable
import xyz.sandboiii.agentcooper.domain.model.ChatMessage
import xyz.sandboiii.agentcooper.domain.model.ChatSession
import xyz.sandboiii.agentcooper.domain.model.MessageRole

/**
 * Сериализуемая модель метаданных сессии для хранения в JSON файле.
 */
@Serializable
data class SessionMetadata(
    val id: String,
    val title: String,
    val createdAt: Long,
    val modelId: String
) {
    /**
     * Преобразует доменную модель ChatSession в сериализуемую модель.
     */
    companion object {
        fun fromDomain(session: ChatSession): SessionMetadata {
            return SessionMetadata(
                id = session.id,
                title = session.title,
                createdAt = session.createdAt,
                modelId = session.modelId
            )
        }
    }
    
    /**
     * Преобразует сериализуемую модель в доменную модель ChatSession.
     */
    fun toDomain(): ChatSession {
        return ChatSession(
            id = id,
            title = title,
            createdAt = createdAt,
            modelId = modelId
        )
    }
}

/**
 * Сериализуемая модель сообщения для хранения в JSON файле.
 */
@Serializable
data class ChatMessageSerializable(
    val id: String,
    val content: String,
    val role: String, // MessageRole сериализуется как строка
    val timestamp: Long,
    val sessionId: String,
    val modelId: String? = null,
    val responseTimeMs: Long? = null,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val contextWindowUsedPercent: Double? = null,
    val totalCost: Double? = null,
    val summarizationContent: String? = null
) {
    /**
     * Преобразует доменную модель ChatMessage в сериализуемую модель.
     */
    companion object {
        fun fromDomain(message: ChatMessage): ChatMessageSerializable {
            return ChatMessageSerializable(
                id = message.id,
                content = message.content,
                role = message.role.name,
                timestamp = message.timestamp,
                sessionId = message.sessionId,
                modelId = message.modelId,
                responseTimeMs = message.responseTimeMs,
                promptTokens = message.promptTokens,
                completionTokens = message.completionTokens,
                contextWindowUsedPercent = message.contextWindowUsedPercent,
                totalCost = message.totalCost,
                summarizationContent = message.summarizationContent
            )
        }
    }
    
    /**
     * Преобразует сериализуемую модель в доменную модель ChatMessage.
     */
    fun toDomain(): ChatMessage {
        return ChatMessage(
            id = id,
            content = content,
            role = MessageRole.valueOf(role),
            timestamp = timestamp,
            sessionId = sessionId,
            modelId = modelId,
            responseTimeMs = responseTimeMs,
            promptTokens = promptTokens,
            completionTokens = completionTokens,
            contextWindowUsedPercent = contextWindowUsedPercent,
            totalCost = totalCost,
            summarizationContent = summarizationContent
        )
    }
}

/**
 * Сериализуемая модель файла сессии, содержащая метаданные сессии и все сообщения.
 * Используется для хранения всей информации о сессии в одном JSON файле.
 */
@Serializable
data class SessionFile(
    val session: SessionMetadata,
    val messages: List<ChatMessageSerializable>
) {
    /**
     * Преобразует доменные модели в сериализуемую модель SessionFile.
     */
    companion object {
        fun fromDomain(session: ChatSession, messages: List<ChatMessage>): SessionFile {
            return SessionFile(
                session = SessionMetadata.fromDomain(session),
                messages = messages.map { ChatMessageSerializable.fromDomain(it) }
            )
        }
    }
    
    /**
     * Преобразует сериализуемую модель в доменные модели.
     */
    fun toDomain(): Pair<ChatSession, List<ChatMessage>> {
        return Pair(
            session.toDomain(),
            messages.map { it.toDomain() }
        )
    }
}

