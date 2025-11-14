package xyz.sandboiii.agentcooper.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import xyz.sandboiii.agentcooper.data.local.storage.SessionFileStorage
import xyz.sandboiii.agentcooper.data.local.storage.StorageLocationManager
import xyz.sandboiii.agentcooper.data.local.storage.StorageMigration
import javax.inject.Singleton

/**
 * DI модуль для предоставления зависимостей хранилища файлов сессий.
 */
@Module
@InstallIn(SingletonComponent::class)
object StorageModule {
    
    /**
     * Предоставляет StorageLocationManager для управления локацией хранения файлов.
     */
    @Provides
    @Singleton
    fun provideStorageLocationManager(
        @ApplicationContext context: Context
    ): StorageLocationManager {
        return StorageLocationManager(context)
    }
    
    /**
     * Предоставляет SessionFileStorage для работы с файлами сессий.
     */
    @Provides
    @Singleton
    fun provideSessionFileStorage(
        @ApplicationContext context: Context,
        storageLocationManager: StorageLocationManager
    ): SessionFileStorage {
        return SessionFileStorage(context, storageLocationManager)
    }
    
    /**
     * Предоставляет StorageMigration для миграции файлов между локациями.
     */
    @Provides
    @Singleton
    fun provideStorageMigration(
        @ApplicationContext context: Context,
        storageLocationManager: StorageLocationManager,
        sessionFileStorage: SessionFileStorage
    ): StorageMigration {
        return StorageMigration(context, storageLocationManager, sessionFileStorage)
    }
}

