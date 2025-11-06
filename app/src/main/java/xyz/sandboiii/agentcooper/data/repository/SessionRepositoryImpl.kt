package xyz.sandboiii.agentcooper.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import xyz.sandboiii.agentcooper.data.local.database.AppDatabase
import xyz.sandboiii.agentcooper.data.local.entity.SessionEntity
import xyz.sandboiii.agentcooper.domain.model.ChatSession
import xyz.sandboiii.agentcooper.domain.repository.SessionRepository
import xyz.sandboiii.agentcooper.util.PreferencesManager

import javax.inject.Inject

class SessionRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val preferencesManager: PreferencesManager
) : SessionRepository {
    
    companion object {
        private const val TAG = "SessionRepositoryImpl"
    }
    
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
        
        // Create welcome message only if enabled in settings
        val welcomeMessageEnabled = preferencesManager.getWelcomeMessageEnabled()
        if (welcomeMessageEnabled) {
            val welcomeMessage = xyz.sandboiii.agentcooper.data.local.entity.ChatMessageEntity(
                id = "welcome-${session.id}",
                content = xyz.sandboiii.agentcooper.util.Constants.WELCOME_MESSAGE,
                role = xyz.sandboiii.agentcooper.domain.model.MessageRole.ASSISTANT,
                timestamp = System.currentTimeMillis(),
                sessionId = session.id
            )
            database.chatMessageDao().insertMessage(welcomeMessage)
            android.util.Log.d(TAG, "Created welcome message for session: ${session.id}")
        } else {
            android.util.Log.d(TAG, "Welcome message disabled, skipping for session: ${session.id}")
        }
        
        return session.toDomain()
    }
    
    override suspend fun updateSession(session: ChatSession) {
        database.sessionDao().updateSession(session.toEntity())
    }
    
    override suspend fun updateSessionTitle(sessionId: String, title: String) {
        val session = database.sessionDao().getSessionById(sessionId)
        if (session != null) {
            val updatedSession = session.copy(title = title)
            database.sessionDao().updateSession(updatedSession)
        }
    }
    
    override suspend fun deleteSession(sessionId: String) {
        database.sessionDao().deleteSession(sessionId)
    }
    
    override suspend fun deleteAllSessions() {
        database.sessionDao().deleteAllSessions()
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

