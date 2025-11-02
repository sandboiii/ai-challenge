package xyz.sandboiii.agentcooper.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import xyz.sandboiii.agentcooper.domain.model.MessageRole

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey
    val id: String,
    val content: String,
    val role: MessageRole,
    val timestamp: Long,
    val sessionId: String
)

