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
 * Менеджер для управления локацией хранения файлов сессий.
 * Поддерживает внутреннее хранилище приложения и внешнюю папку, выбранную пользователем.
 */
@Singleton
class StorageLocationManager @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "StorageLocationManager"
        private const val DEFAULT_SESSIONS_DIR_NAME = "sessions"
    }
    
    /**
     * Получает директорию для хранения сессий.
     * Если установлена внешняя папка, возвращает её, иначе возвращает внутреннюю директорию.
     * 
     * @param externalStorageUri URI внешней папки или null для внутреннего хранилища
     * @return File объект директории для хранения сессий
     */
    suspend fun getSessionsDirectory(externalStorageUri: String?): File = withContext(Dispatchers.IO) {
        if (externalStorageUri != null) {
            // Используем внешнее хранилище через DocumentFile API
            try {
                val uri = Uri.parse(externalStorageUri)
                val documentFile = DocumentFile.fromTreeUri(context, uri)
                if (documentFile != null && documentFile.exists() && documentFile.isDirectory) {
                    // Для внешнего хранилища возвращаем File через DocumentFile
                    // Но DocumentFile не предоставляет прямой доступ к File, поэтому используем другой подход
                    // Создаем временный файл для проверки доступа
                    val testFile = File(context.cacheDir, "storage_test")
                    testFile.writeText(externalStorageUri)
                    // Возвращаем специальный File объект, который будет обрабатываться отдельно
                    return@withContext File(externalStorageUri) // Используем путь как идентификатор
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error accessing external storage", e)
            }
        }
        
        // Возвращаем внутреннее хранилище по умолчанию
        val internalDir = File(context.filesDir, DEFAULT_SESSIONS_DIR_NAME)
        if (!internalDir.exists()) {
            internalDir.mkdirs()
        }
        internalDir
    }
    
    /**
     * Получает URI для внешнего хранилища из строки.
     * 
     * @param uriString Строка URI или null
     * @return Uri объект или null
     */
    fun getExternalStorageUri(uriString: String?): Uri? {
        return uriString?.let { Uri.parse(it) }
    }
    
    /**
     * Проверяет, является ли указанный путь внешним хранилищем.
     * 
     * @param path Путь к хранилищу
     * @return true если это внешнее хранилище, false если внутреннее
     */
    fun isExternalStorage(path: String?): Boolean {
        return path != null && path.startsWith("content://")
    }
    
    /**
     * Получает DocumentFile для внешнего хранилища.
     * 
     * @param uriString Строка URI внешнего хранилища
     * @return DocumentFile или null если не удалось получить доступ
     */
    fun getExternalDocumentFile(uriString: String?): DocumentFile? {
        return uriString?.let { uri ->
            try {
                DocumentFile.fromTreeUri(context, Uri.parse(uri))
            } catch (e: Exception) {
                Log.e(TAG, "Error getting DocumentFile", e)
                null
            }
        }
    }
}

