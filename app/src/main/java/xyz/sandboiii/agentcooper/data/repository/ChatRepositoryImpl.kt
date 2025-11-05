package xyz.sandboiii.agentcooper.data.repository

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.util.UUID
import xyz.sandboiii.agentcooper.data.local.database.AppDatabase
import xyz.sandboiii.agentcooper.data.local.entity.ChatMessageEntity
import xyz.sandboiii.agentcooper.data.remote.api.ChatApi
import xyz.sandboiii.agentcooper.data.remote.api.ChatMessageDto
import xyz.sandboiii.agentcooper.data.remote.dto.AiResponseDto
import xyz.sandboiii.agentcooper.domain.model.ChatMessage
import xyz.sandboiii.agentcooper.domain.model.MessageRole
import xyz.sandboiii.agentcooper.domain.repository.ChatRepository
import xyz.sandboiii.agentcooper.domain.repository.SessionRepository
import xyz.sandboiii.agentcooper.util.Constants
import xyz.sandboiii.agentcooper.util.PreferencesManager

import javax.inject.Inject

class ChatRepositoryImpl @Inject constructor(
    private val chatApi: ChatApi,
    private val database: AppDatabase,
    private val sessionRepository: SessionRepository,
    private val preferencesManager: PreferencesManager
) : ChatRepository {
    
    companion object {
        private const val TAG = "ChatRepositoryImpl"
    }
    
    override fun getMessages(sessionId: String): Flow<List<ChatMessage>> {
        return database.chatMessageDao()
            .getMessagesBySession(sessionId)
            .map { entities ->
                entities.map { it.toDomain() }
            }
    }
    
    override suspend fun sendMessage(
        sessionId: String,
        content: String,
        modelId: String
    ): Flow<String> = flow {
        // Get conversation history before adding new message
        val existingMessages = database.chatMessageDao()
            .getMessagesBySessionSync(sessionId)
        
        // Check if this is the first user message (to generate title)
        val isFirstUserMessage = existingMessages.none { it.role == MessageRole.USER }
        
        // Save user message
        val userMessage = ChatMessageEntity(
            id = UUID.randomUUID().toString(),
            content = content,
            role = MessageRole.USER,
            timestamp = System.currentTimeMillis(),
            sessionId = sessionId
        )
        database.chatMessageDao().insertMessage(userMessage)
        
        // Check if suggestions are enabled
        val suggestionsEnabled = preferencesManager.getSuggestionsEnabled()
        
        // Get system prompt from preferences, fallback to default if not set
        var systemPrompt = preferencesManager.getSystemPrompt() ?: Constants.DEFAULT_SYSTEM_PROMPT
        
        // If suggestions are enabled, append JSON format instructions to system prompt
        if (suggestionsEnabled) {
            systemPrompt += "\n\n${Constants.JSON_FORMAT_INSTRUCTION}"
        }
        
        val messages = mutableListOf<ChatMessageDto>().apply {
            // Add system prompt as first message
            add(ChatMessageDto(role = "system", content = systemPrompt))
            // Add existing messages, but exclude welcome message (don't send it to AI context)
            existingMessages.forEach { msg ->
                // Skip welcome message - it's just for display, not for AI context
                if (!msg.id.startsWith("welcome-")) {
                    add(ChatMessageDto(
                        role = when (msg.role) {
                            MessageRole.USER -> "user"
                            MessageRole.ASSISTANT -> "assistant"
                            MessageRole.SYSTEM -> "system"
                        },
                        content = msg.content
                    ))
                } else {
                    Log.d(TAG, "Skipping welcome message from AI context: ${msg.id}")
                }
            }
            // Add current user message
            add(ChatMessageDto(role = "user", content = content))
        }
        
        // Create assistant message entity
        val assistantMessageId = UUID.randomUUID().toString()
        var accumulatedContent = ""
        
        // Stream response from API
        try {
            chatApi.sendMessage(messages, modelId, stream = true).collect { chunk ->
                accumulatedContent += chunk
                emit(chunk)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error streaming message", e)
            throw e
        }
        
        // Parse JSON response if suggestions are enabled
        var messageContent = accumulatedContent
        var mood: String? = null
        var suggestions: List<String> = emptyList()
        
        if (suggestionsEnabled) {
            try {
                // Try to parse as JSON
                val json = Json { ignoreUnknownKeys = true; isLenient = true }
                // Clean the content - remove markdown code blocks if present
                var cleanedContent = accumulatedContent.trim()
                if (cleanedContent.startsWith("```json")) {
                    cleanedContent = cleanedContent.removePrefix("```json").trim()
                }
                if (cleanedContent.startsWith("```")) {
                    cleanedContent = cleanedContent.removePrefix("```").trim()
                }
                if (cleanedContent.endsWith("```")) {
                    cleanedContent = cleanedContent.removeSuffix("```").trim()
                }
                
                val aiResponse = json.decodeFromString<AiResponseDto>(cleanedContent)
                messageContent = aiResponse.message
                mood = aiResponse.mood
                suggestions = aiResponse.suggestions
                Log.d(TAG, "Parsed JSON response: mood=$mood, suggestions=${suggestions.size}")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse JSON response, using raw content", e)
                // If parsing fails, use the raw content as message
                messageContent = accumulatedContent
            }
        }
        
        // Save assistant message (store as JSON if suggestions enabled, otherwise plain text)
        val contentToStore = if (suggestionsEnabled && mood != null && suggestions.isNotEmpty()) {
            // Store as JSON string that can be parsed later
            val json = Json { ignoreUnknownKeys = true }
            json.encodeToString(AiResponseDto(mood = mood, message = messageContent, suggestions = suggestions))
        } else {
            messageContent
        }
        
        val assistantMessage = ChatMessageEntity(
            id = assistantMessageId,
            content = contentToStore,
            role = MessageRole.ASSISTANT,
            timestamp = System.currentTimeMillis(),
            sessionId = sessionId
        )
        database.chatMessageDao().insertMessage(assistantMessage)
        
        // Generate title from API if this is the first user message
        if (isFirstUserMessage && accumulatedContent.isNotEmpty()) {
            try {
                val generatedTitle = chatApi.generateTitle(content, accumulatedContent, modelId)
                sessionRepository.updateSessionTitle(sessionId, generatedTitle)
                Log.d(TAG, "Updated session title: $generatedTitle")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to generate title from API, using fallback", e)
                // Fallback to simple title generation
                val fallbackTitle = generateTitleFromMessage(content)
                sessionRepository.updateSessionTitle(sessionId, fallbackTitle)
            }
        }
    }
    
    override suspend fun deleteMessages(sessionId: String) {
        database.chatMessageDao().deleteMessagesBySession(sessionId)
    }
    
    override suspend fun deleteAllMessages() {
        database.chatMessageDao().deleteAllMessages()
    }
    
    private fun ChatMessageEntity.toDomain(): ChatMessage {
        // Try to parse JSON if content looks like JSON
        var messageContent = content
        var mood: String? = null
        var suggestions: List<String> = emptyList()
        var rawJson: String? = null
        
        if (role == MessageRole.ASSISTANT && content.trim().startsWith("{") && content.contains("\"message\"")) {
            rawJson = content // Store raw JSON
            try {
                val json = Json { ignoreUnknownKeys = true; isLenient = true }
                val parsed = json.decodeFromString<AiResponseDto>(content)
                messageContent = parsed.message
                mood = parsed.mood
                suggestions = parsed.suggestions
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse message JSON, using raw content", e)
                // Use raw content if parsing fails
            }
        }
        
        return ChatMessage(
            id = id,
            content = messageContent,
            role = role,
            timestamp = timestamp,
            sessionId = sessionId,
            mood = mood,
            suggestions = suggestions,
            rawJson = rawJson
        )
    }
    
    /**
     * Generates a title from the first user message, similar to ChatGPT/Grok/DeepSeek.
     * Takes the first few words (up to 6 words or 50 characters) and truncates if needed.
     */
    private fun generateTitleFromMessage(message: String): String {
        // Remove extra whitespace and newlines
        val cleaned = message.trim().replace(Regex("\\s+"), " ")
        
        // Take first 6 words or first 50 characters, whichever comes first
        val words = cleaned.split(" ")
        val maxWords = 6
        val maxLength = 50
        
        val title = if (words.size <= maxWords) {
            cleaned
        } else {
            words.take(maxWords).joinToString(" ")
        }
        
        // Truncate to max length if needed and add ellipsis
        return if (title.length > maxLength) {
            title.take(maxLength - 3) + "..."
        } else {
            title
        }
    }
}

