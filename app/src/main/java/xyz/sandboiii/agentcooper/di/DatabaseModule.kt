package xyz.sandboiii.agentcooper.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import xyz.sandboiii.agentcooper.data.local.database.AppDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "agent_cooper_database"
        ).build()
    }
    
    @Provides
    fun provideChatMessageDao(database: AppDatabase) = database.chatMessageDao()
    
    @Provides
    fun provideSessionDao(database: AppDatabase) = database.sessionDao()
}

