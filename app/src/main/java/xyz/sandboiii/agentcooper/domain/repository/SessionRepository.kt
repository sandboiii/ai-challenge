package xyz.sandboiii.agentcooper.domain.repository

import kotlinx.coroutines.flow.Flow
import xyz.sandboiii.agentcooper.domain.model.ChatSession

interface SessionRepository {
    fun getAllSessions(): Flow<List<ChatSession>>
    suspend fun getSessionById(sessionId: String): ChatSession?
    suspend fun createSession(modelId: String): ChatSession
    suspend fun updateSession(session: ChatSession)
    suspend fun deleteSession(sessionId: String)
}

