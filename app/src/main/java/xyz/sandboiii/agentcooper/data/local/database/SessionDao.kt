package xyz.sandboiii.agentcooper.data.local.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import xyz.sandboiii.agentcooper.data.local.entity.SessionEntity

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY createdAt DESC")
    fun getAllSessions(): Flow<List<SessionEntity>>
    
    @Query("SELECT * FROM sessions WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: String): SessionEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SessionEntity)
    
    @Update
    suspend fun updateSession(session: SessionEntity)
    
    @Query("DELETE FROM sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String)
    
    @Query("DELETE FROM sessions")
    suspend fun deleteAllSessions()
}

