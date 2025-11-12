package xyz.sandboiii.agentcooper.data.repository

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import xyz.sandboiii.agentcooper.data.local.database.AppDatabase
import xyz.sandboiii.agentcooper.data.local.entity.ChatMessageEntity
import xyz.sandboiii.agentcooper.data.remote.api.ChatApi
import xyz.sandboiii.agentcooper.data.remote.api.ChatMessageDto
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
        
        // Get system prompt
        val systemPrompt = preferencesManager.getSystemPrompt() ?: Constants.DEFAULT_SYSTEM_PROMPT
        
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
        
        // Track response time and metadata
        val startTime = System.currentTimeMillis()
        var responseModelId: String? = null
        var promptTokens: Int? = null
        var completionTokens: Int? = null
        var totalTokens: Int? = null
        
        // Stream response from API with metadata callback
        try {
            chatApi.sendMessage(
                messages, 
                modelId, 
                stream = true,
                onMetadata = { metadata ->
                    Log.d(TAG, "Metadata callback invoked: model=${metadata.modelId}, prompt=${metadata.promptTokens}, completion=${metadata.completionTokens}, total=${metadata.totalTokens}")
                    responseModelId = metadata.modelId ?: modelId
                    promptTokens = metadata.promptTokens
                    completionTokens = metadata.completionTokens
                    totalTokens = metadata.totalTokens
                    Log.d(TAG, "Updated token variables: prompt=$promptTokens, completion=$completionTokens, total=$totalTokens")
                }
            ).collect { chunk ->
                accumulatedContent += chunk
                emit(chunk)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error streaming message", e)
            throw e
        }
        
        val responseTimeMs = System.currentTimeMillis() - startTime
        
        // Use accumulated content as message
        val messageContent = accumulatedContent
        
        // Log final token values before cost calculation
        Log.d(TAG, "Before cost calculation - Final token values: prompt=$promptTokens, completion=$completionTokens, total=$totalTokens, modelId=${responseModelId ?: modelId}")
        
        // Calculate cost and context window usage
        var totalCost: Double? = null
        var contextWindowUsedPercent: Double? = null
        
        try {
            val models = chatApi.getModels()
            val lookupModelId = responseModelId ?: modelId
            val model = models.find { it.id == lookupModelId }
            
            Log.d(TAG, "Looking up model for cost calculation: $lookupModelId")
            Log.d(TAG, "Token usage: prompt=$promptTokens, completion=$completionTokens, total=$totalTokens")
            
            if (model == null) {
                Log.w(TAG, "Model not found in models list: $lookupModelId")
                Log.w(TAG, "Available model IDs (first 10): ${models.take(10).map { it.id }}")
            }
            
            model?.let { modelInfo ->
                Log.d(TAG, "Found model: ${modelInfo.name}, pricing: ${modelInfo.pricing?.displayText ?: "null"}")
                
                // Calculate cost if model is paid
                modelInfo.pricing?.let { pricing ->
                    Log.d(TAG, "Pricing details: prompt=${pricing.prompt}, completion=${pricing.completion}, isFree=${pricing.isFree}")
                    
                    // Parse pricing values - OpenRouter pricing is per token (not per million)
                    // According to https://openrouter.ai/docs/overview/models: "All pricing values are in USD per token"
                    val promptPricePerToken = pricing.prompt?.let { 
                        val parsed = it.toDoubleOrNull()
                        Log.d(TAG, "Parsing prompt price per token: '$it' -> $parsed")
                        parsed
                    } ?: 0.0
                    val completionPricePerToken = pricing.completion?.let {
                        val parsed = it.toDoubleOrNull()
                        Log.d(TAG, "Parsing completion price per token: '$it' -> $parsed")
                        parsed
                    } ?: 0.0
                    
                    // Model is paid if either price is > 0 (regardless of isFree flag, as some models might have incorrect isFree)
                    val isActuallyPaid = promptPricePerToken > 0.0 || completionPricePerToken > 0.0
                    
                    Log.d(TAG, "Price per token: prompt=$promptPricePerToken, completion=$completionPricePerToken, isActuallyPaid=$isActuallyPaid, isFree=${pricing.isFree}")
                    
                    // Calculate cost if we have tokens and pricing
                    if (promptTokens != null && completionTokens != null && (promptTokens!! > 0 || completionTokens!! > 0)) {
                        if (isActuallyPaid) {
                            // Calculate cost: tokens * price_per_token (OpenRouter pricing is per token, not per million)
                            val promptCost = promptTokens!! * promptPricePerToken
                            val completionCost = completionTokens!! * completionPricePerToken
                            totalCost = promptCost + completionCost
                            
                            Log.d(TAG, "Cost breakdown: promptCost=$promptCost (${promptTokens} tokens * $promptPricePerToken), completionCost=$completionCost (${completionTokens} tokens * $completionPricePerToken), total=$totalCost")
                            
                            // Verify calculation - log error if cost is unexpectedly zero
                            totalCost?.let { cost ->
                                if (cost == 0.0 && (promptTokens!! > 0 || completionTokens!! > 0) && (promptPricePerToken > 0.0 || completionPricePerToken > 0.0)) {
                                    Log.e(TAG, "ERROR: Cost is zero but should not be! promptTokens=$promptTokens, completionTokens=$completionTokens, promptPrice=$promptPricePerToken, completionPrice=$completionPricePerToken")
                                }
                            }
                        } else {
                            Log.d(TAG, "Model is free (both prices are 0 or null), skipping cost calculation")
                        }
                    } else {
                        Log.d(TAG, "Skipping cost calculation: promptTokens=$promptTokens, completionTokens=$completionTokens")
                    }
                } ?: Log.w(TAG, "Model has no pricing information")
                
                // Calculate context window usage percentage
                totalTokens?.let { tokens ->
                    val actualContextLength = modelInfo.contextLength ?: run {
                        // Fallback to default context window sizes for common models
                        when {
                            modelInfo.id.contains("gpt-4", ignoreCase = true) -> 128000
                            modelInfo.id.contains("gpt-3.5", ignoreCase = true) -> 16385
                            modelInfo.id.contains("claude", ignoreCase = true) -> 200000
                            modelInfo.id.contains("llama", ignoreCase = true) -> 4096
                            else -> null // Unknown, can't calculate percentage
                        }
                    }
                    
                    actualContextLength?.let { ctxLength ->
                        if (ctxLength > 0) {
                            contextWindowUsedPercent = (tokens.toDouble() / ctxLength) * 100.0
                            Log.d(TAG, "Context window usage: $tokens / $ctxLength = ${contextWindowUsedPercent}%")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to calculate cost or context window usage", e)
        }
        
        // Save assistant message
        val assistantMessage = ChatMessageEntity(
            id = assistantMessageId,
            content = messageContent,
            role = MessageRole.ASSISTANT,
            timestamp = System.currentTimeMillis(),
            sessionId = sessionId,
            modelId = responseModelId ?: modelId,
            responseTimeMs = responseTimeMs,
            promptTokens = promptTokens,
            completionTokens = completionTokens,
            contextWindowUsedPercent = contextWindowUsedPercent,
            totalCost = totalCost
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
        return ChatMessage(
            id = id,
            content = content,
            role = role,
            timestamp = timestamp,
            sessionId = sessionId,
            modelId = modelId,
            responseTimeMs = responseTimeMs,
            promptTokens = promptTokens,
            completionTokens = completionTokens,
            contextWindowUsedPercent = contextWindowUsedPercent,
            totalCost = totalCost
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

