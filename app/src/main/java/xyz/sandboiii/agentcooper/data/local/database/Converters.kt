package xyz.sandboiii.agentcooper.data.local.database

import androidx.room.TypeConverter
import xyz.sandboiii.agentcooper.domain.model.MessageRole

class Converters {
    @TypeConverter
    fun fromMessageRole(role: MessageRole): String {
        return role.name
    }
    
    @TypeConverter
    fun toMessageRole(role: String): MessageRole {
        return MessageRole.valueOf(role)
    }
}

