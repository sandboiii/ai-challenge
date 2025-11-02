package xyz.sandboiii.agentcooper.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import xyz.sandboiii.agentcooper.data.local.database.AppDatabase
import xyz.sandboiii.agentcooper.data.local.entity.SessionEntity
import xyz.sandboiii.agentcooper.domain.model.ChatSession
import xyz.sandboiii.agentcooper.domain.repository.SessionRepository

class SessionRepositoryImpl(
    private val database: AppDatabase
) : SessionRepository {
    
    override fun getAllSessions(): Flow<List<ChatSession>> {
        return database.sessionDao()
            .getAllSessions()
            .map { entities ->
                entities.map { it.toDomain() }
            }
    }
    
    override suspend fun getSessionById(sessionId: String): ChatSession? {
        return database.sessionDao()
            .getSessionById(sessionId)
            ?.toDomain()
    }
    
    override suspend fun createSession(modelId: String): ChatSession {
        val session = SessionEntity(
            id = UUID.randomUUID().toString(),
            title = "Новый разговор",
            createdAt = System.currentTimeMillis(),
            modelId = modelId
        )
        database.sessionDao().insertSession(session)
        return session.toDomain()
    }
    
    override suspend fun updateSession(session: ChatSession) {
        database.sessionDao().updateSession(session.toEntity())
    }
    
    override suspend fun deleteSession(sessionId: String) {
        database.sessionDao().deleteSession(sessionId)
    }
    
    private fun SessionEntity.toDomain(): ChatSession {
        return ChatSession(
            id = id,
            title = title,
            createdAt = createdAt,
            modelId = modelId
        )
    }
    
    private fun ChatSession.toEntity(): SessionEntity {
        return SessionEntity(
            id = id,
            title = title,
            createdAt = createdAt,
            modelId = modelId
        )
    }
}

