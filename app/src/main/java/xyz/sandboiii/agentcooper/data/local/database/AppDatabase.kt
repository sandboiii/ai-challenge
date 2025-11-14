package xyz.sandboiii.agentcooper.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import xyz.sandboiii.agentcooper.data.local.entity.ChatMessageEntity
import xyz.sandboiii.agentcooper.data.local.entity.SessionEntity

@Database(
    entities = [ChatMessageEntity::class, SessionEntity::class],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun sessionDao(): SessionDao
}

