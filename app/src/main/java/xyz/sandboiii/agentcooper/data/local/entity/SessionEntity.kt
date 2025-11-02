package xyz.sandboiii.agentcooper.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val createdAt: Long,
    val modelId: String
)

