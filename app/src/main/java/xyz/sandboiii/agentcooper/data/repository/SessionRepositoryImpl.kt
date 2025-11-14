package xyz.sandboiii.agentcooper.data.repository

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import java.util.UUID
import xyz.sandboiii.agentcooper.data.local.model.SessionFile
import xyz.sandboiii.agentcooper.data.local.storage.SessionFileStorage
import xyz.sandboiii.agentcooper.domain.model.ChatMessage
import xyz.sandboiii.agentcooper.domain.model.ChatSession
import xyz.sandboiii.agentcooper.domain.model.MessageRole
import xyz.sandboiii.agentcooper.domain.repository.SessionRepository
import xyz.sandboiii.agentcooper.util.Constants
import xyz.sandboiii.agentcooper.util.PreferencesManager

import javax.inject.Inject

class SessionRepositoryImpl @Inject constructor(
    private val sessionFileStorage: SessionFileStorage,
    private val preferencesManager: PreferencesManager
) : SessionRepository {
    
    companion object {
        private const val TAG = "SessionRepositoryImpl"
    }
    
    /**
     * Кэш для хранения текущей локации хранилища.
     */
    private val _currentStorageLocation = MutableStateFlow<String?>(null)
    
    /**
     * Scope для инициализации и подписки на изменения.
     */
    private val initScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    init {
        // Инициализируем локацию хранилища
        initScope.launch {
            _currentStorageLocation.value = preferencesManager.getStorageLocation()
        }
        
        // Подписываемся на изменения локации хранилища
        initScope.launch {
            preferencesManager.storageLocation.collect { location ->
                _currentStorageLocation.value = location
            }
        }
    }
    
    @kotlinx.coroutines.ExperimentalCoroutinesApi
    override fun getAllSessions(): Flow<List<ChatSession>> {
        // Комбинируем изменения локации хранилища и события изменений файлов
        // При любом изменении файла (создание, обновление, удаление) перечитываем все сессии
        Log.d(TAG, "getAllSessions called")
        return combine(
            _currentStorageLocation,
            sessionFileStorage.changeEvents
        ) { storageLocation, changedSessionId ->
            // Возвращаем storageLocation, но также логируем измененную сессию
            Log.d(TAG, "Change detected: sessionId=$changedSessionId, storageLocation=$storageLocation")
            storageLocation
        }
        .onStart {
            // Эмитим начальное значение для загрузки сессий при старте
            Log.d(TAG, "onStart: emitting initial load, current storage location: ${_currentStorageLocation.value}")
            emit(_currentStorageLocation.value)
        }
        .flatMapLatest { storageLocation ->
            flow {
                try {
                    Log.d(TAG, "Loading sessions from storage location: $storageLocation")
                    val sessionFiles = sessionFileStorage.getAllSessionFiles(storageLocation)
                    val sessions = sessionFiles.map { it.session.toDomain() }
                    Log.d(TAG, "Loaded ${sessions.size} sessions: ${sessions.map { it.id }}")
                    emit(sessions)
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting all sessions", e)
                    emit(emptyList())
                }
            }
        }
        .distinctUntilChanged()
    }
    
    override suspend fun getSessionById(sessionId: String): ChatSession? {
        return try {
            val storageLocation = preferencesManager.getStorageLocation()
            val sessionFile = sessionFileStorage.readSessionFile(sessionId, storageLocation)
            sessionFile?.session?.toDomain()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting session by id: $sessionId", e)
            null
        }
    }
    
    override suspend fun createSession(modelId: String): ChatSession {
        val session = ChatSession(
            id = UUID.randomUUID().toString(),
            title = "Новый разговор",
            createdAt = System.currentTimeMillis(),
            modelId = modelId
        )
        
        val storageLocation = preferencesManager.getStorageLocation()
        
        // Создаем файл сессии с пустым списком сообщений
        val sessionFile = SessionFile.fromDomain(session, emptyList())
        sessionFileStorage.writeSessionFile(sessionFile, storageLocation)
        
        // Создаем приветственное сообщение только если включено в настройках
        val welcomeMessageEnabled = preferencesManager.getWelcomeMessageEnabled()
        if (welcomeMessageEnabled) {
            val welcomeMessage = ChatMessage(
                id = "welcome-${session.id}",
                content = Constants.WELCOME_MESSAGE,
                role = MessageRole.ASSISTANT,
                timestamp = System.currentTimeMillis(),
                sessionId = session.id
            )
            sessionFileStorage.addMessageToSession(session, welcomeMessage, storageLocation)
            Log.d(TAG, "Created welcome message for session: ${session.id}")
        } else {
            Log.d(TAG, "Welcome message disabled, skipping for session: ${session.id}")
        }
        
        return session
    }
    
    override suspend fun updateSession(session: ChatSession) {
        try {
            val storageLocation = preferencesManager.getStorageLocation()
            sessionFileStorage.updateSessionMetadata(session, storageLocation)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating session: ${session.id}", e)
            throw e
        }
    }
    
    override suspend fun updateSessionTitle(sessionId: String, title: String) {
        try {
            val storageLocation = preferencesManager.getStorageLocation()
            val existingFile = sessionFileStorage.readSessionFile(sessionId, storageLocation)
            
            if (existingFile != null) {
                val updatedSession = existingFile.session.toDomain().copy(title = title)
                sessionFileStorage.updateSessionMetadata(updatedSession, storageLocation)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating session title: $sessionId", e)
            throw e
        }
    }
    
    override suspend fun deleteSession(sessionId: String) {
        try {
            val storageLocation = preferencesManager.getStorageLocation()
            sessionFileStorage.deleteSessionFile(sessionId, storageLocation)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting session: $sessionId", e)
            throw e
        }
    }
    
    override suspend fun deleteAllSessions() {
        try {
            val storageLocation = preferencesManager.getStorageLocation()
            val allSessions = sessionFileStorage.getAllSessionFiles(storageLocation)
            
            allSessions.forEach { sessionFile ->
                sessionFileStorage.deleteSessionFile(sessionFile.session.id, storageLocation)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting all sessions", e)
            throw e
        }
    }
}
