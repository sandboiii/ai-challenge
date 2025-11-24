package xyz.sandboiii.agentcooper.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import xyz.sandboiii.agentcooper.data.repository.ChatRepositoryImpl
import xyz.sandboiii.agentcooper.data.repository.McpRepositoryImpl
import xyz.sandboiii.agentcooper.data.repository.SessionRepositoryImpl
import xyz.sandboiii.agentcooper.domain.repository.ChatRepository
import xyz.sandboiii.agentcooper.domain.repository.McpRepository
import xyz.sandboiii.agentcooper.domain.repository.SessionRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    
    @Binds
    @Singleton
    abstract fun bindChatRepository(
        chatRepositoryImpl: ChatRepositoryImpl
    ): ChatRepository
    
    @Binds
    @Singleton
    abstract fun bindSessionRepository(
        sessionRepositoryImpl: SessionRepositoryImpl
    ): SessionRepository
    
    @Binds
    @Singleton
    abstract fun bindMcpRepository(
        mcpRepositoryImpl: McpRepositoryImpl
    ): McpRepository
    
}

