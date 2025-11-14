package xyz.sandboiii.agentcooper.data.local.storage

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Класс для миграции файлов сессий между различными локациями хранения.
 */
@Singleton
class StorageMigration @Inject constructor(
    private val context: Context,
    private val storageLocationManager: StorageLocationManager,
    private val sessionFileStorage: SessionFileStorage
) {
    companion object {
        private const val TAG = "StorageMigration"
    }
    
    /**
     * Мигрирует все файлы сессий из одной локации в другую.
     * 
     * @param fromStorageUri Исходная локация хранилища (null = внутреннее хранилище)
     * @param toStorageUri Целевая локация хранилища (null = внутреннее хранилище)
     * @return Количество успешно мигрированных файлов
     */
    suspend fun migrateFiles(
        fromStorageUri: String?,
        toStorageUri: String?
    ): Int = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting migration from ${fromStorageUri ?: "internal"} to ${toStorageUri ?: "internal"}")
            
            // Получаем все файлы из исходной локации
            val sourceFiles = sessionFileStorage.getAllSessionFiles(fromStorageUri)
            
            if (sourceFiles.isEmpty()) {
                Log.d(TAG, "No files to migrate")
                return@withContext 0
            }
            
            Log.d(TAG, "Found ${sourceFiles.size} session files to migrate")
            
            var successCount = 0
            var errorCount = 0
            
            // Копируем каждый файл в целевую локацию
            sourceFiles.forEach { sessionFile ->
                try {
                    // Записываем файл в целевую локацию
                    sessionFileStorage.writeSessionFile(sessionFile, toStorageUri)
                    successCount++
                    Log.d(TAG, "Migrated session: ${sessionFile.session.id}")
                } catch (e: Exception) {
                    errorCount++
                    Log.e(TAG, "Error migrating session: ${sessionFile.session.id}", e)
                }
            }
            
            // Если все файлы успешно мигрированы, удаляем их из исходной локации
            if (errorCount == 0 && fromStorageUri != toStorageUri) {
                Log.d(TAG, "All files migrated successfully, deleting from source location")
                sourceFiles.forEach { sessionFile ->
                    try {
                        sessionFileStorage.deleteSessionFile(sessionFile.session.id, fromStorageUri)
                    } catch (e: Exception) {
                        Log.w(TAG, "Error deleting source file: ${sessionFile.session.id}", e)
                        // Не прерываем процесс, если не удалось удалить исходный файл
                    }
                }
            } else if (errorCount > 0) {
                Log.w(TAG, "Migration completed with errors: $successCount successful, $errorCount failed")
                throw IllegalStateException("Migration failed: $errorCount files could not be migrated")
            }
            
            Log.d(TAG, "Migration completed successfully: $successCount files migrated")
            successCount
        } catch (e: Exception) {
            Log.e(TAG, "Error during migration", e)
            throw e
        }
    }
    
    /**
     * Проверяет, существует ли файл в указанной локации.
     * 
     * @param sessionId Идентификатор сессии
     * @param storageUri URI хранилища или null для внутреннего
     * @return true если файл существует
     */
    suspend fun fileExists(
        sessionId: String,
        storageUri: String?
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val sessionFile = sessionFileStorage.readSessionFile(sessionId, storageUri)
            sessionFile != null
        } catch (e: Exception) {
            Log.e(TAG, "Error checking file existence: $sessionId", e)
            false
        }
    }
}

