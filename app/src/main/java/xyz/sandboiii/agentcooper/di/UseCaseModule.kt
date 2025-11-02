package xyz.sandboiii.agentcooper.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import xyz.sandboiii.agentcooper.domain.repository.ChatRepository
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
        sessionRepository: SessionRepository,
        chatRepository: ChatRepository
    ): DeleteSessionUseCase {
        return DeleteSessionUseCase(sessionRepository, chatRepository)
    }
    
    @Provides
    @Singleton
    fun provideDeleteAllSessionsUseCase(
        sessionRepository: SessionRepository,
        chatRepository: ChatRepository
    ): xyz.sandboiii.agentcooper.domain.usecase.DeleteAllSessionsUseCase {
        return xyz.sandboiii.agentcooper.domain.usecase.DeleteAllSessionsUseCase(sessionRepository, chatRepository)
    }
}

