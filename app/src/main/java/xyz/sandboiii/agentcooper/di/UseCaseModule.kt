package xyz.sandboiii.agentcooper.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import xyz.sandboiii.agentcooper.data.worker.ScheduledTaskWorkManager
import xyz.sandboiii.agentcooper.domain.repository.ChatRepository
import xyz.sandboiii.agentcooper.domain.repository.ScheduledTaskRepository
import xyz.sandboiii.agentcooper.domain.repository.SessionRepository
import xyz.sandboiii.agentcooper.domain.usecase.*
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {
    
    @Provides
    @Singleton
    fun provideSendMessageUseCase(
        chatRepository: ChatRepository
    ): SendMessageUseCase {
        return SendMessageUseCase(chatRepository)
    }
    
    @Provides
    @Singleton
    fun provideGetMessagesUseCase(
        chatRepository: ChatRepository
    ): GetMessagesUseCase {
        return GetMessagesUseCase(chatRepository)
    }
    
    @Provides
    @Singleton
    fun provideGetSessionsUseCase(
        sessionRepository: SessionRepository
    ): GetSessionsUseCase {
        return GetSessionsUseCase(sessionRepository)
    }
    
    @Provides
    @Singleton
    fun provideCreateSessionUseCase(
        sessionRepository: SessionRepository
    ): CreateSessionUseCase {
        return CreateSessionUseCase(sessionRepository)
    }
    
    @Provides
    @Singleton
    fun provideDeleteSessionUseCase(
        sessionRepository: SessionRepository
    ): DeleteSessionUseCase {
        return DeleteSessionUseCase(sessionRepository)
    }
    
    @Provides
    @Singleton
    fun provideDeleteAllSessionsUseCase(
        sessionRepository: SessionRepository,
        chatRepository: ChatRepository
    ): xyz.sandboiii.agentcooper.domain.usecase.DeleteAllSessionsUseCase {
        return xyz.sandboiii.agentcooper.domain.usecase.DeleteAllSessionsUseCase(sessionRepository, chatRepository)
    }
    
    @Provides
    @Singleton
    fun provideCreateScheduledTaskUseCase(
        taskRepository: ScheduledTaskRepository,
        workManager: ScheduledTaskWorkManager,
        @ApplicationContext context: Context
    ): CreateScheduledTaskUseCase {
        return CreateScheduledTaskUseCase(taskRepository, workManager, context)
    }
    
    @Provides
    @Singleton
    fun provideUpdateScheduledTaskUseCase(
        taskRepository: ScheduledTaskRepository,
        workManager: ScheduledTaskWorkManager,
        @ApplicationContext context: Context
    ): UpdateScheduledTaskUseCase {
        return UpdateScheduledTaskUseCase(taskRepository, workManager, context)
    }
    
    @Provides
    @Singleton
    fun provideDeleteScheduledTaskUseCase(
        taskRepository: ScheduledTaskRepository,
        workManager: ScheduledTaskWorkManager,
        @ApplicationContext context: Context
    ): DeleteScheduledTaskUseCase {
        return DeleteScheduledTaskUseCase(taskRepository, workManager, context)
    }
    
    @Provides
    @Singleton
    fun provideToggleScheduledTaskUseCase(
        taskRepository: ScheduledTaskRepository,
        workManager: ScheduledTaskWorkManager,
        @ApplicationContext context: Context
    ): ToggleScheduledTaskUseCase {
        return ToggleScheduledTaskUseCase(taskRepository, workManager, context)
    }
}

