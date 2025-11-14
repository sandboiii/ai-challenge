package xyz.sandboiii.agentcooper.data.local.storage

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import xyz.sandboiii.agentcooper.data.local.model.SessionFile
import xyz.sandboiii.agentcooper.domain.model.ChatMessage
import xyz.sandboiii.agentcooper.domain.model.ChatSession
import java.io.File
import java.io.FileNotFoundException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Хранилище для работы с файлами сессий в формате JSON.
 * Поддерживает чтение и запись сессий из/в JSON файлы.
 * Использует событийную модель для уведомления об изменениях через SharedFlow.
 */
@Singleton
class SessionFileStorage @Inject constructor(
    private val context: Context,
    private val storageLocationManager: StorageLocationManager
) {
    companion object {
        private const val TAG = "SessionFileStorage"
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
    
    /**
     * SharedFlow для уведомления об изменениях файлов.
     * Эмитит sessionId при изменении файла сессии.
     */
    private val _changeEvents = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 64)
    val changeEvents: SharedFlow<String> = _changeEvents.asSharedFlow()
    
    /**
     * Mutex для синхронизации операций записи.
     */
    private val writeMutex = Mutex()
    
    /**
     * Читает файл сессии по sessionId.
     * 
     * @param sessionId Идентификатор сессии
     * @param storageUri URI хранилища или null для внутреннего
     * @return SessionFile или null если файл не найден
     */
    suspend fun readSessionFile(
        sessionId: String,
        storageUri: String?
    ): SessionFile? = withContext(Dispatchers.IO) {
        try {
            val fileName = "$sessionId.json"
            
            if (storageLocationManager.isExternalStorage(storageUri)) {
                // Чтение из внешнего хранилища через DocumentFile
                val documentFile = storageLocationManager.getExternalDocumentFile(storageUri)
                val sessionFile = documentFile?.findFile(fileName)
                
                if (sessionFile != null && sessionFile.exists()) {
                    val content = readDocumentFileContent(sessionFile)
                    return@withContext json.decodeFromString<SessionFile>(content)
                }
            } else {
                // Чтение из внутреннего хранилища
                val directory = storageLocationManager.getSessionsDirectory(storageUri)
                val file = File(directory, fileName)
                
                if (file.exists()) {
                    val content = file.readText()
                    return@withContext json.decodeFromString<SessionFile>(content)
                }
            }
            
            null
        } catch (e: FileNotFoundException) {
            Log.d(TAG, "Session file not found: $sessionId")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error reading session file: $sessionId", e)
            null
        }
    }
    
    /**
     * Записывает файл сессии.
     * 
     * @param sessionFile Данные сессии для записи
     * @param storageUri URI хранилища или null для внутреннего
     */
    suspend fun writeSessionFile(
        sessionFile: SessionFile,
        storageUri: String?
    ) = withContext(Dispatchers.IO) {
        val sessionId = sessionFile.session.id
        writeMutex.withLock {
            try {
                val fileName = "$sessionId.json"
                
                if (storageLocationManager.isExternalStorage(storageUri)) {
                    // Запись во внешнее хранилище через DocumentFile
                    val documentFile = storageLocationManager.getExternalDocumentFile(storageUri)
                    val sessionFileDoc = documentFile?.findFile(fileName)
                        ?: documentFile?.createFile("application/json", fileName)
                    
                    if (sessionFileDoc != null) {
                        val content = json.encodeToString(SessionFile.serializer(), sessionFile)
                        writeDocumentFileContent(sessionFileDoc, content)
                    } else {
                        throw IllegalStateException("Cannot create file in external storage")
                    }
                } else {
                    // Запись во внутреннее хранилище
                    val directory = storageLocationManager.getSessionsDirectory(storageUri)
                    if (!directory.exists()) {
                        directory.mkdirs()
                    }
                    
                    val file = File(directory, fileName)
                    val content = json.encodeToString(SessionFile.serializer(), sessionFile)
                    file.writeText(content)
                }
                
                Log.d(TAG, "File written successfully for session: $sessionId")
            } catch (e: Exception) {
                Log.e(TAG, "Error writing session file: $sessionId", e)
                throw e
            }
        }
        
        // Эмитим событие об изменении ВНЕ мьютекса, чтобы не блокировать другие операции
        Log.d(TAG, "Emitting change event for session: $sessionId")
        _changeEvents.emit(sessionId)
        Log.d(TAG, "Change event emitted for session: $sessionId")
    }
    
    /**
     * Удаляет файл сессии.
     * 
     * @param sessionId Идентификатор сессии
     * @param storageUri URI хранилища или null для внутреннего
     */
    suspend fun deleteSessionFile(
        sessionId: String,
        storageUri: String?
    ) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            try {
                val fileName = "$sessionId.json"
                
                if (storageLocationManager.isExternalStorage(storageUri)) {
                    // Удаление из внешнего хранилища
                    val documentFile = storageLocationManager.getExternalDocumentFile(storageUri)
                    val sessionFile = documentFile?.findFile(fileName)
                    sessionFile?.delete()
                } else {
                    // Удаление из внутреннего хранилища
                    val directory = storageLocationManager.getSessionsDirectory(storageUri)
                    val file = File(directory, fileName)
                    if (file.exists()) {
                        file.delete()
                    }
                }
                
                Log.d(TAG, "Session file deleted successfully: $sessionId")
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting session file: $sessionId", e)
                throw e
            }
        }
        
        // Эмитим событие об изменении ВНЕ мьютекса, чтобы не блокировать другие операции
        Log.d(TAG, "Emitting change event for deleted session: $sessionId")
        _changeEvents.emit(sessionId)
        Log.d(TAG, "Change event emitted for deleted session: $sessionId")
    }
    
    /**
     * Получает список всех файлов сессий.
     * 
     * @param storageUri URI хранилища или null для внутреннего
     * @return Список SessionFile
     */
    suspend fun getAllSessionFiles(
        storageUri: String?
    ): List<SessionFile> = withContext(Dispatchers.IO) {
        try {
            val sessionFiles = mutableListOf<SessionFile>()
            
            if (storageLocationManager.isExternalStorage(storageUri)) {
                // Чтение из внешнего хранилища
                val documentFile = storageLocationManager.getExternalDocumentFile(storageUri)
                val files = documentFile?.listFiles()
                
                files?.forEach { file: DocumentFile ->
                    if (file.isFile && file.name?.endsWith(".json") == true) {
                        try {
                            val content = readDocumentFileContent(file)
                            val sessionFile = json.decodeFromString<SessionFile>(content)
                            sessionFiles.add(sessionFile)
                        } catch (e: Exception) {
                            Log.w(TAG, "Error reading session file: ${file.name}", e)
                        }
                    }
                }
            } else {
                // Чтение из внутреннего хранилища
                val directory = storageLocationManager.getSessionsDirectory(storageUri)
                if (directory.exists()) {
                    directory.listFiles()?.forEach { file ->
                        if (file.isFile && file.name.endsWith(".json")) {
                            try {
                                val content = file.readText()
                                val sessionFile = json.decodeFromString<SessionFile>(content)
                                sessionFiles.add(sessionFile)
                            } catch (e: Exception) {
                                Log.w(TAG, "Error reading session file: ${file.name}", e)
                            }
                        }
                    }
                }
            }
            
            // Сортируем по дате создания (новые первыми)
            sessionFiles.sortedByDescending { it.session.createdAt }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all session files", e)
            emptyList()
        }
    }
    
    /**
     * Читает содержимое DocumentFile.
     */
    private fun readDocumentFileContent(documentFile: DocumentFile): String {
        return context.contentResolver.openInputStream(documentFile.uri)?.use { inputStream ->
            inputStream.bufferedReader().use { it.readText() }
        } ?: throw IllegalStateException("Cannot read file content")
    }
    
    /**
     * Записывает содержимое в DocumentFile.
     */
    private fun writeDocumentFileContent(documentFile: DocumentFile, content: String) {
        context.contentResolver.openOutputStream(documentFile.uri)?.use { outputStream ->
            outputStream.bufferedWriter().use { it.write(content) }
        } ?: throw IllegalStateException("Cannot write file content")
    }
    
    /**
     * Добавляет сообщение к существующей сессии или создает новую сессию.
     * 
     * @param session Сессия
     * @param message Сообщение для добавления
     * @param storageUri URI хранилища или null для внутреннего
     */
    suspend fun addMessageToSession(
        session: ChatSession,
        message: ChatMessage,
        storageUri: String?
    ) = withContext(Dispatchers.IO) {
        // НЕ используем writeMutex здесь, так как writeSessionFile уже использует его внутри
        // Использование mutex здесь приведет к deadlock
        try {
            // Читаем существующий файл или создаем новый
            val existingFile = readSessionFile(session.id, storageUri)
            
            // Используем сессию из файла, если она существует, иначе используем переданную сессию
            val sessionToUse = existingFile?.session?.toDomain() ?: session
            val existingMessages = existingFile?.messages?.map { it.toDomain() } ?: emptyList()
            
            // Добавляем новое сообщение
            val updatedMessages = existingMessages + message
            
            // Создаем обновленный SessionFile
            val updatedSessionFile = SessionFile.fromDomain(sessionToUse, updatedMessages)
            
            // Записываем обратно - writeSessionFile уже использует writeMutex внутри
            writeSessionFile(updatedSessionFile, storageUri)
            
            Log.d(TAG, "Added message ${message.id} to session ${session.id}, total messages: ${updatedMessages.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding message to session: ${session.id}", e)
            throw e
        }
    }
    
    /**
     * Обновляет метаданные сессии (например, заголовок).
     * 
     * @param session Обновленная сессия
     * @param storageUri URI хранилища или null для внутреннего
     */
    suspend fun updateSessionMetadata(
        session: ChatSession,
        storageUri: String?
    ) = withContext(Dispatchers.IO) {
        // НЕ используем writeMutex здесь, так как writeSessionFile уже использует его внутри
        // Использование mutex здесь приведет к deadlock
        try {
            val existingFile = readSessionFile(session.id, storageUri)
            val messages = existingFile?.messages?.map { it.toDomain() } ?: emptyList()
            
            val updatedSessionFile = SessionFile.fromDomain(session, messages)
            // writeSessionFile уже использует writeMutex внутри, поэтому не нужно блокировать здесь
            writeSessionFile(updatedSessionFile, storageUri)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating session metadata: ${session.id}", e)
            throw e
        }
    }
    
    /**
     * Удаляет все сообщения из сессии.
     * 
     * @param sessionId Идентификатор сессии
     * @param storageUri URI хранилища или null для внутреннего
     */
    suspend fun deleteSessionMessages(
        sessionId: String,
        storageUri: String?
    ) = withContext(Dispatchers.IO) {
        // НЕ используем writeMutex здесь, так как writeSessionFile уже использует его внутри
        // Использование mutex здесь приведет к deadlock
        try {
            val existingFile = readSessionFile(sessionId, storageUri)
            if (existingFile != null) {
                val session = existingFile.session.toDomain()
                val emptySessionFile = SessionFile.fromDomain(session, emptyList())
                // writeSessionFile уже использует writeMutex внутри, поэтому не нужно блокировать здесь
                writeSessionFile(emptySessionFile, storageUri)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting session messages: $sessionId", e)
            throw e
        }
    }
}

