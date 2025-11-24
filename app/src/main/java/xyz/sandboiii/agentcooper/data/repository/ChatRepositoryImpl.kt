package xyz.sandboiii.agentcooper.data.repository

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import java.util.UUID
import xyz.sandboiii.agentcooper.data.local.storage.SessionFileStorage
import xyz.sandboiii.agentcooper.data.remote.api.ChatApi
import xyz.sandboiii.agentcooper.data.remote.api.ChatMessageDto
import xyz.sandboiii.agentcooper.domain.model.ChatMessage
import xyz.sandboiii.agentcooper.domain.model.MessageRole
import xyz.sandboiii.agentcooper.domain.repository.ChatRepository
import xyz.sandboiii.agentcooper.domain.repository.McpRepository
import xyz.sandboiii.agentcooper.domain.repository.SessionRepository
import xyz.sandboiii.agentcooper.util.Constants
import xyz.sandboiii.agentcooper.util.PreferencesManager
import xyz.sandboiii.agentcooper.util.TokenCounter
import xyz.sandboiii.agentcooper.data.remote.mcp.toOpenAITool
import xyz.sandboiii.agentcooper.data.remote.dto.ToolCall
import xyz.sandboiii.agentcooper.data.remote.api.ToolCallInfo
import kotlinx.serialization.json.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import javax.inject.Inject

class ChatRepositoryImpl @Inject constructor(
    private val chatApi: ChatApi,
    private val sessionFileStorage: SessionFileStorage,
    private val sessionRepository: SessionRepository,
    private val preferencesManager: PreferencesManager,
    private val mcpRepository: McpRepository
) : ChatRepository {
    
    companion object {
        private const val TAG = "ChatRepositoryImpl"
    }
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = false
    }
    
    private val _isSummarizing = MutableStateFlow(false)
    override val isSummarizing: StateFlow<Boolean> = _isSummarizing.asStateFlow()
    
    @kotlinx.coroutines.ExperimentalCoroutinesApi
    override fun getMessages(sessionId: String): Flow<List<ChatMessage>> {
        // Подписываемся на события изменений файлов для конкретной сессии
        // Загружаем сообщения при старте и при каждом изменении файла
        Log.d(TAG, "getMessages called for session: $sessionId")
        return sessionFileStorage.changeEvents
            .filter { 
                val matches = it == sessionId
                Log.d(TAG, "Change event received: $it, matches session $sessionId: $matches")
                matches
            }
            .onStart { 
                // Эмитим событие для загрузки начального состояния
                Log.d(TAG, "onStart: emitting initial load for session: $sessionId")
                emit(sessionId)
            }
            .flatMapLatest { 
                flow {
                    val messages = loadMessagesFromFile(sessionId)
                    Log.d(TAG, "Loading messages for session $sessionId: ${messages.size} messages")
                    emit(messages)
                }
            }
            .distinctUntilChanged()
    }
    
    /**
     * Загружает сообщения из файла сессии.
     */
    private suspend fun loadMessagesFromFile(sessionId: String): List<ChatMessage> {
        return try {
            val storageLocation = preferencesManager.getStorageLocation()
            val sessionFile = sessionFileStorage.readSessionFile(sessionId, storageLocation)
            sessionFile?.messages?.map { it.toDomain() } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error loading messages for session: $sessionId", e)
            emptyList()
        }
    }
    
    override suspend fun sendMessage(
        sessionId: String,
        content: String,
        modelId: String
    ): Flow<String> = flow {
        val storageLocation = preferencesManager.getStorageLocation()
        
        // Get conversation history before adding new message
        val existingMessages = loadMessagesFromFile(sessionId)
        
        // Check if this is the first user message (to generate title)
        val isFirstUserMessage = existingMessages.none { it.role == MessageRole.USER }
        
        // Get system prompt
        val systemPrompt = preferencesManager.getSystemPrompt() ?: Constants.DEFAULT_SYSTEM_PROMPT
        
        // Build initial message context for token counting
        // IMPORTANT: Count ALL tokens including summary content, matching what will be sent to the API
        // Include the new user message content for accurate token counting
        val initialMessages = buildMessageContext(
            messages = existingMessages,
            systemPrompt = systemPrompt,
            newUserMessageContent = content
        )
        
        // Log token counting info
        val userMessageCount = initialMessages.count { it.role == "user" }
        val assistantMessageCount = initialMessages.count { it.role == "assistant" }
        val summaryCount = initialMessages.count { it.role == "system" && it.content?.startsWith("Краткое содержание") == true }
        Log.d(TAG, "Token counting: 1 system prompt, ${summaryCount} summary (if present), ${userMessageCount} user messages, ${assistantMessageCount} assistant messages")
        
        // Check token count and trigger summarization if needed (BEFORE saving user message)
        // This counts ALL input tokens: system prompt + summary (if present) + all user messages + all assistant messages
        // The token count matches exactly what will be sent to the API
        val tokenCount = TokenCounter.countTokens(initialMessages)
        val threshold = preferencesManager.getTokenThreshold()
        
        // Get model context length as fallback threshold
        val modelContextLength = try {
            val models = chatApi.getModels()
            models.find { it.id == modelId }?.contextLength
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get model context length", e)
            null
        }
        
        val effectiveThreshold = threshold ?: modelContextLength
        
        Log.d(TAG, "Token count: $tokenCount, Threshold: $effectiveThreshold")
        
        // Get session for updating
        val session = sessionRepository.getSessionById(sessionId)
            ?: throw IllegalStateException("Session not found: $sessionId")
        
        // If threshold is set and token count exceeds it, perform summarization BEFORE saving user message
        if (effectiveThreshold != null && tokenCount > effectiveThreshold) {
            Log.d(TAG, "Token count ($tokenCount) exceeds threshold ($effectiveThreshold), triggering summarization")
            _isSummarizing.value = true // Update summarization state
            try {
                // Summarize existing messages (excluding the new user message which hasn't been saved yet)
                // The new user message will be saved after summarization
                summarizeMessages(sessionId, modelId, existingMessages, storageLocation)
            } finally {
                // Save user message BEFORE clearing summarization state to ensure it's in file when ViewModel reloads
                val userMessage = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    content = content,
                    role = MessageRole.USER,
                    timestamp = System.currentTimeMillis(),
                    sessionId = sessionId
                )
                sessionFileStorage.addMessageToSession(session, userMessage, storageLocation)
                _isSummarizing.value = false // Update summarization state (after user message is saved)
            }
        } else {
            val userMessage = ChatMessage(
                id = UUID.randomUUID().toString(),
                content = content,
                role = MessageRole.USER,
                timestamp = System.currentTimeMillis(),
                sessionId = sessionId
            )
            sessionFileStorage.addMessageToSession(session, userMessage, storageLocation)
        }
        
        // Re-fetch messages after potential summarization
        val messagesAfterSummarization = loadMessagesFromFile(sessionId)
        
        // Build final message context using the same function
        // The current user message is already included in messagesAfterSummarization since we saved it above
        val messages = buildMessageContext(
            messages = messagesAfterSummarization,
            systemPrompt = systemPrompt,
            newUserMessageContent = null // User message is already in messagesAfterSummarization
        )
        
        // Log final message context - each message on a separate line
        Log.d(TAG, "Final message context (${messages.size} messages):")
        messages.forEachIndexed { index, message ->
            Log.d(TAG, "  [$index] ${message.role}: ${message.content}")
        }
        
        // Create assistant message entity
        val assistantMessageId = UUID.randomUUID().toString()
        var accumulatedContent = ""
        var accumulatedToolCalls = mutableListOf<xyz.sandboiii.agentcooper.domain.model.ToolCall>()
        var toolResults = mutableListOf<xyz.sandboiii.agentcooper.domain.model.ToolResult>()

        // Track response time and metadata
        val startTime = System.currentTimeMillis()
        var responseModelId: String? = null
        var promptTokens: Int? = null
        var completionTokens: Int? = null
        var totalTokens: Int? = null
        var apiCost: Double? = null // Cost from API usage accounting

        // Get available tools from MCP
        val mcpTools = try {
            mcpRepository.allTools.first()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get MCP tools", e)
            emptyList()
        }

        val openAITools = if (mcpTools.isNotEmpty()) {
            mcpTools.map { it.toOpenAITool() }
        } else {
            null
        }

        Log.d(TAG, "Available MCP tools: ${mcpTools.size}")

        // Stream response from API with metadata callback and tool calling support
        try {
            chatApi.sendMessage(
                messages, 
                modelId, 
                stream = true,
                onMetadata = { metadata ->
                    Log.d(TAG, "Metadata callback invoked: model=${metadata.modelId}, prompt=${metadata.promptTokens}, completion=${metadata.completionTokens}, total=${metadata.totalTokens}, cost=${metadata.cost}")
                    responseModelId = metadata.modelId ?: modelId
                    promptTokens = metadata.promptTokens
                    completionTokens = metadata.completionTokens
                    totalTokens = metadata.totalTokens
                    apiCost = metadata.cost
                    Log.d(TAG, "Updated token variables: prompt=$promptTokens, completion=$completionTokens, total=$totalTokens, apiCost=$apiCost")
                },
                tools = openAITools,
                onToolCalls = null // Don't use callback - we collect from chunks to avoid duplicates
            ).collect { chunk ->
                // Handle content chunks
                chunk.content?.let { content ->
                    accumulatedContent += content
                    emit(content)
                }
                
                // Handle tool calls
                chunk.toolCalls?.let { toolCalls ->
                    val domainToolCalls = toolCalls.map { toolCall ->
                        xyz.sandboiii.agentcooper.domain.model.ToolCall(
                            id = toolCall.id,
                            name = toolCall.name,
                            arguments = toolCall.arguments
                        )
                    }
                    accumulatedToolCalls.addAll(domainToolCalls)
                    Log.d(TAG, "Accumulated ${accumulatedToolCalls.size} tool calls")
                }
            }
            
            // Execute tool calls iteratively - AI can make multiple rounds of tool calls
            var currentMessages = messages.toMutableList()
            var roundNumber = 1
            val maxRounds = 5 // Prevent infinite loops
            
            while (accumulatedToolCalls.isNotEmpty() && roundNumber <= maxRounds) {
                Log.d(TAG, "Tool calling round $roundNumber: Executing ${accumulatedToolCalls.size} tool calls")
                
                // Execute each tool call via MCP
                val currentRoundToolResults = mutableListOf<xyz.sandboiii.agentcooper.domain.model.ToolResult>()
                
                for (toolCall in accumulatedToolCalls) {
                    try {
                        Log.d(TAG, "Executing tool: ${toolCall.name} with args: ${toolCall.arguments}")
                        
                        // Parse arguments JSON
                        val argumentsJson = try {
                            json.parseToJsonElement(toolCall.arguments)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to parse tool arguments", e)
                            val errorResult = xyz.sandboiii.agentcooper.domain.model.ToolResult(
                                toolCallId = toolCall.id,
                                toolName = toolCall.name,
                                result = "Error: Failed to parse arguments: ${e.message}",
                                isError = true
                            )
                            currentRoundToolResults.add(errorResult)
                            toolResults.add(errorResult)
                            continue
                        }
                        
                        // Call tool via MCP repository
                        val result = mcpRepository.callTool(toolCall.name, argumentsJson)
                        
                        result.fold(
                            onSuccess = { resultText ->
                                Log.d(TAG, "Tool ${toolCall.name} executed successfully")
                                val successResult = xyz.sandboiii.agentcooper.domain.model.ToolResult(
                                    toolCallId = toolCall.id,
                                    toolName = toolCall.name,
                                    result = resultText,
                                    isError = false
                                )
                                currentRoundToolResults.add(successResult)
                                toolResults.add(successResult)
                            },
                            onFailure = { error ->
                                Log.e(TAG, "Tool ${toolCall.name} execution failed", error)
                                val errorResult = xyz.sandboiii.agentcooper.domain.model.ToolResult(
                                    toolCallId = toolCall.id,
                                    toolName = toolCall.name,
                                    result = "Error: ${error.message}",
                                    isError = true
                                )
                                currentRoundToolResults.add(errorResult)
                                toolResults.add(errorResult)
                            }
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Exception executing tool ${toolCall.name}", e)
                        val errorResult = xyz.sandboiii.agentcooper.domain.model.ToolResult(
                            toolCallId = toolCall.id,
                            toolName = toolCall.name,
                            result = "Error: ${e.message}",
                            isError = true
                        )
                        currentRoundToolResults.add(errorResult)
                        toolResults.add(errorResult)
                    }
                }
                
                // Send tool results back to OpenRouter and get response (which may contain more tool calls)
                if (currentRoundToolResults.isNotEmpty()) {
                    Log.d(TAG, "Round $roundNumber: Sending ${currentRoundToolResults.size} tool results back to OpenRouter")
                    
                    // Build messages with tool results for this round
                    val messagesWithToolResults = currentMessages.toMutableList().apply {
                        // Add assistant message with tool calls from this round
                        val toolCallsForMessage = accumulatedToolCalls.map { toolCall ->
                            ToolCallInfo(
                                id = toolCall.id,
                                name = toolCall.name,
                                arguments = toolCall.arguments
                            )
                        }
                        add(ChatMessageDto(
                            role = "assistant",
                            content = "", // Empty content when tool calls are present
                            toolCallId = null,
                            toolCalls = toolCallsForMessage
                        ))
                        
                        // Add tool result messages for this round
                        currentRoundToolResults.forEach { toolResult ->
                            add(ChatMessageDto(
                                role = "tool",
                                content = toolResult.result,
                                toolCallId = toolResult.toolCallId,
                                toolCalls = null
                            ))
                        }
                    }
                    
                    // Clear accumulated tool calls for next round
                    accumulatedToolCalls.clear()
                    var roundContent = ""
                    
                    // Get response from OpenRouter (may contain more tool calls)
                    chatApi.sendMessage(
                        messagesWithToolResults,
                        modelId,
                        stream = true,
                        onMetadata = { metadata ->
                            responseModelId = metadata.modelId ?: modelId
                            promptTokens = (promptTokens ?: 0) + (metadata.promptTokens ?: 0)
                            completionTokens = (completionTokens ?: 0) + (metadata.completionTokens ?: 0)
                            totalTokens = (totalTokens ?: 0) + (metadata.totalTokens ?: 0)
                            apiCost = (apiCost ?: 0.0) + (metadata.cost ?: 0.0)
                        },
                        tools = openAITools,
                        onToolCalls = null // Don't use callback - we collect from chunks to avoid duplicates
                    ).collect { chunk ->
                        // Handle content chunks
                        chunk.content?.let { content ->
                            roundContent += content
                            emit(content)
                        }
                        
                        // Handle new tool calls in this round
                        chunk.toolCalls?.let { newToolCalls ->
                            val domainToolCalls = newToolCalls.map { toolCall ->
                                xyz.sandboiii.agentcooper.domain.model.ToolCall(
                                    id = toolCall.id,
                                    name = toolCall.name,
                                    arguments = toolCall.arguments
                                )
                            }
                            accumulatedToolCalls.addAll(domainToolCalls)
                            Log.d(TAG, "Round $roundNumber: Added ${domainToolCalls.size} tool calls from chunk")
                        }
                    }
                    
                    accumulatedContent += roundContent
                    currentMessages = messagesWithToolResults
                    roundNumber++
                    
                    // If no new tool calls were received, break the loop
                    if (accumulatedToolCalls.isEmpty()) {
                        Log.d(TAG, "No more tool calls received, ending tool calling loop")
                        break
                    }
                } else {
                    break
                }
            }
            
            if (roundNumber > maxRounds) {
                Log.w(TAG, "Tool calling stopped after $maxRounds rounds to prevent infinite loop")
                emit("\n\n[Note: Tool calling stopped after $maxRounds rounds to prevent infinite loop]")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error streaming message", e)
            throw e
        }
        
        val responseTimeMs = System.currentTimeMillis() - startTime
        
        // Use accumulated content as message
        val messageContent = accumulatedContent
        
        // Log final token values before cost calculation
        Log.d(TAG, "Before cost calculation - Final token values: prompt=$promptTokens, completion=$completionTokens, total=$totalTokens, modelId=${responseModelId ?: modelId}, apiCost=$apiCost")
        
        // Calculate cost and context window usage
        var totalCost: Double? = null
        var contextWindowUsedPercent: Double? = null
        
        // Use API cost if available, otherwise fallback to local calculation
        if (apiCost != null) {
            totalCost = apiCost
            Log.d(TAG, "Using cost from API usage accounting: $totalCost")
        } else {
            Log.d(TAG, "API cost not available, falling back to local calculation")
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
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to calculate cost locally", e)
            }
        }
        
        // Calculate context window usage percentage (separate from cost calculation)
        try {
            val models = chatApi.getModels()
            val lookupModelId = responseModelId ?: modelId
            val model = models.find { it.id == lookupModelId }
            
            model?.let { modelInfo ->
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
            Log.w(TAG, "Failed to calculate context window usage", e)
        }
        
            // Save assistant message
            val assistantMessage = ChatMessage(
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
                totalCost = totalCost,
                toolCalls = if (accumulatedToolCalls.isNotEmpty()) accumulatedToolCalls else null,
                toolResults = if (toolResults.isNotEmpty()) toolResults else null
            )
        sessionFileStorage.addMessageToSession(session, assistantMessage, storageLocation)
        
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
        val storageLocation = preferencesManager.getStorageLocation()
        sessionFileStorage.deleteSessionMessages(sessionId, storageLocation)
    }
    
    override suspend fun deleteAllMessages() {
        val storageLocation = preferencesManager.getStorageLocation()
        val allSessions = sessionFileStorage.getAllSessionFiles(storageLocation)
        
        allSessions.forEach { sessionFile ->
            sessionFileStorage.deleteSessionMessages(sessionFile.session.id, storageLocation)
        }
    }
    
    /**
     * Builds message context for API calls.
     * Includes: system prompt + summary (if any) + messages after last summary + new user message (if provided).
     * 
     * @param messages List of existing messages from file
     * @param systemPrompt System prompt to include
     * @param newUserMessageContent Optional new user message content to append (for token counting before saving)
     * @return List of ChatMessageDto ready to send to API
     */
    private fun buildMessageContext(
        messages: List<ChatMessage>,
        systemPrompt: String,
        newUserMessageContent: String? = null
    ): List<ChatMessageDto> {
        return mutableListOf<ChatMessageDto>().apply {
            // Find the last summary message (if any)
            val lastSummaryIndex = messages.indexOfLast { it.role == MessageRole.SUMMARY }
            
            if (lastSummaryIndex >= 0) {
                // Include the summary content as a system message
                val summaryMessage = messages[lastSummaryIndex]
                summaryMessage.summarizationContent?.let { summaryContent ->
                    add(ChatMessageDto(
                        role = "system",
                        content = "$systemPrompt\n\nКраткое содержание предыдущего разговора: $summaryContent"
                    ))
                }
                
                // Only include messages after the last summary (excluding welcome and summary messages)
                messages.subList(lastSummaryIndex + 1, messages.size).forEach { msg ->
                    if (!msg.id.startsWith("welcome-") && msg.role != MessageRole.SUMMARY) {
                        add(ChatMessageDto(
                            role = when (msg.role) {
                                MessageRole.USER -> "user"
                                MessageRole.ASSISTANT -> "assistant"
                                MessageRole.SYSTEM -> "system"
                                else -> "user" // fallback
                            },
                            content = msg.content
                        ))
                    }
                }
            } else {
                // Add system prompt as first message
                add(ChatMessageDto(role = "system", content = systemPrompt))

                // No summary exists, include all messages (except welcome and summaries)
                messages.forEach { msg ->
                    if (!msg.id.startsWith("welcome-") && msg.role != MessageRole.SUMMARY) {
                        add(ChatMessageDto(
                            role = when (msg.role) {
                                MessageRole.USER -> "user"
                                MessageRole.ASSISTANT -> "assistant"
                                MessageRole.SYSTEM -> "system"
                                else -> "user" // fallback
                            },
                            content = msg.content
                        ))
                    }
                }
            }
            
            // Add new user message if provided (for token counting before saving)
            newUserMessageContent?.let { content ->
                add(ChatMessageDto(role = "user", content = content))
            }
        }
    }
    
    /**
     * Summarizes old messages in the conversation to reduce token count.
     * Finds messages before the last summary (or all messages if no summary exists),
     * creates a summary, and inserts it as a SUMMARY role message.
     */
    private suspend fun summarizeMessages(
        sessionId: String,
        modelId: String,
        existingMessages: List<ChatMessage>,
        storageLocation: String?
    ) {
        try {
            // Find the last summary message index
            val lastSummaryIndex = existingMessages.indexOfLast { it.role == MessageRole.SUMMARY }
            
            // Determine which messages to summarize
            val messagesToSummarize = if (lastSummaryIndex >= 0) {
                // Summarize messages since the last summary (including all messages up to current point)
                // This includes the last summary as well
                existingMessages.subList(lastSummaryIndex, existingMessages.size)
            } else {
                // The summary will include everything up to the current point
                if (existingMessages.isNotEmpty()) {
                    existingMessages
                } else {
                    emptyList() // No messages to summarize
                }
            }
            
            if (messagesToSummarize.isEmpty()) {
                Log.d(TAG, "No messages to summarize")
                return
            }
            
            Log.d(TAG, "Summarizing ${messagesToSummarize.size} messages")
            
            // Build summarization prompt
            val conversationText = messagesToSummarize.joinToString("\n") { msg ->
                val roleText = when (msg.role) {
                    MessageRole.USER -> "Пользователь"
                    MessageRole.ASSISTANT -> "Ассистент"
                    MessageRole.SYSTEM -> "Система"
                    MessageRole.SUMMARY -> "Краткое содержание прошлых разговоров"
                }
                "$roleText: ${msg.content}"
            }
            
            val summarizationPrompt = "Кратко суммируй следующий разговор, сохраняя ключевую информацию и контекст:\n\n$conversationText"
            
            // Call API for summarization (non-streaming)
            val summaryMessages = listOf(
                ChatMessageDto(role = "system", content = "Вы полезный ассистент, который создаёт краткие сводки разговоров сохраняя в том числе содержание прошлых разговоров."),
                ChatMessageDto(role = "user", content = summarizationPrompt)
            )
            
            var summaryContent = ""
            chatApi.sendMessage(
                messages = summaryMessages,
                model = modelId,
                stream = false,
                onMetadata = null
            ).collect { chunk ->
                summaryContent += chunk
            }
            
            if (summaryContent.isBlank()) {
                Log.w(TAG, "Summary content is empty, skipping summarization")
                return
            }
            
            // Get session for updating
            val session = sessionRepository.getSessionById(sessionId)
                ?: throw IllegalStateException("Session not found: $sessionId")
            
            // Create summary message
            val summaryMessage = ChatMessage(
                id = UUID.randomUUID().toString(),
                content = "Chat history summarized",
                role = MessageRole.SUMMARY,
                timestamp = System.currentTimeMillis(),
                sessionId = sessionId,
                modelId = modelId,
                summarizationContent = summaryContent.trim()
            )
            
            // Insert summary message
            sessionFileStorage.addMessageToSession(session, summaryMessage, storageLocation)
            
            Log.d(TAG, "Summarization completed: ${summaryContent.length} characters")
        } catch (e: Exception) {
            Log.e(TAG, "Error during summarization", e)
            // Don't throw - allow the conversation to continue even if summarization fails
        }
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
    
    override suspend fun sendMessageWithToolCallUpdates(
        sessionId: String,
        content: String,
        modelId: String,
        onToolCallsUpdate: (List<xyz.sandboiii.agentcooper.domain.model.ToolCall>) -> Unit,
        onToolResultsUpdate: (List<xyz.sandboiii.agentcooper.domain.model.ToolResult>) -> Unit
    ): Flow<String> = flow {
        val storageLocation = preferencesManager.getStorageLocation()
        
        // Get conversation history before adding new message
        val existingMessages = loadMessagesFromFile(sessionId)
        
        // Check if this is the first user message (to generate title)
        val isFirstUserMessage = existingMessages.none { it.role == MessageRole.USER }
        
        // Get system prompt
        val systemPrompt = preferencesManager.getSystemPrompt() ?: Constants.DEFAULT_SYSTEM_PROMPT
        
        // Build initial message context for token counting
        val initialMessages = buildMessageContext(
            messages = existingMessages,
            systemPrompt = systemPrompt,
            newUserMessageContent = content
        )
        
        // Log token counting info
        val userMessageCount = initialMessages.count { it.role == "user" }
        val assistantMessageCount = initialMessages.count { it.role == "assistant" }
        val summaryCount = initialMessages.count { it.role == "system" && it.content?.startsWith("Краткое содержание") == true }
        Log.d(TAG, "Token counting: 1 system prompt, ${summaryCount} summary (if present), ${userMessageCount} user messages, ${assistantMessageCount} assistant messages")
        
        // Check token count and trigger summarization if needed
        val tokenCount = TokenCounter.countTokens(initialMessages)
        val threshold = preferencesManager.getTokenThreshold()
        
        // Get model context length as fallback threshold
        val modelContextLength = try {
            val models = chatApi.getModels()
            models.find { it.id == modelId }?.contextLength
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get model context length", e)
            null
        }
        
        val effectiveThreshold = threshold ?: modelContextLength
        
        Log.d(TAG, "Token count: $tokenCount, Threshold: $effectiveThreshold")
        
        // Get session for updating
        val session = sessionRepository.getSessionById(sessionId)
            ?: throw IllegalStateException("Session not found: $sessionId")
        
        // If threshold is set and token count exceeds it, perform summarization BEFORE saving user message
        if (effectiveThreshold != null && tokenCount > effectiveThreshold) {
            Log.d(TAG, "Token count ($tokenCount) exceeds threshold ($effectiveThreshold), triggering summarization")
            _isSummarizing.value = true
            try {
                summarizeMessages(sessionId, modelId, existingMessages, storageLocation)
            } finally {
                val userMessage = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    content = content,
                    role = MessageRole.USER,
                    timestamp = System.currentTimeMillis(),
                    sessionId = sessionId
                )
                sessionFileStorage.addMessageToSession(session, userMessage, storageLocation)
                _isSummarizing.value = false
            }
        } else {
            // Save user message immediately if no summarization needed
            val userMessage = ChatMessage(
                id = UUID.randomUUID().toString(),
                content = content,
                role = MessageRole.USER,
                timestamp = System.currentTimeMillis(),
                sessionId = sessionId
            )
            sessionFileStorage.addMessageToSession(session, userMessage, storageLocation)
        }
        
        // Reload messages after potential summarization and user message addition
        val updatedMessages = loadMessagesFromFile(sessionId)
        val messages = buildMessageContext(
            messages = updatedMessages,
            systemPrompt = systemPrompt
        )
        
        // Initialize variables for streaming with tool call tracking
        var accumulatedContent = ""
        var accumulatedToolCalls = mutableListOf<xyz.sandboiii.agentcooper.domain.model.ToolCall>()
        var toolResults = mutableListOf<xyz.sandboiii.agentcooper.domain.model.ToolResult>()

        // Track response time and metadata
        val startTime = System.currentTimeMillis()
        var responseModelId: String? = null
        var promptTokens: Int? = null
        var completionTokens: Int? = null
        var totalTokens: Int? = null
        var apiCost: Double? = null

        // Get available tools from MCP
        val mcpTools = try {
            mcpRepository.allTools.first()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get MCP tools", e)
            emptyList()
        }

        val openAITools = if (mcpTools.isNotEmpty()) {
            mcpTools.map { it.toOpenAITool() }
        } else {
            null
        }

        Log.d(TAG, "Available MCP tools: ${mcpTools.size}")

        // Stream response from API with metadata callback and tool calling support
        try {
            chatApi.sendMessage(
                messages, 
                modelId, 
                stream = true,
                onMetadata = { metadata ->
                    Log.d(TAG, "Metadata callback invoked: model=${metadata.modelId}, prompt=${metadata.promptTokens}, completion=${metadata.completionTokens}, total=${metadata.totalTokens}, cost=${metadata.cost}")
                    responseModelId = metadata.modelId ?: modelId
                    promptTokens = metadata.promptTokens
                    completionTokens = metadata.completionTokens
                    totalTokens = metadata.totalTokens
                    apiCost = metadata.cost
                },
                tools = openAITools,
                onToolCalls = null // Don't use callback - we collect from chunks to avoid duplicates
            ).collect { chunk ->
                // Handle content chunks
                chunk.content?.let { content ->
                    accumulatedContent += content
                    emit(content)
                }
                
                // Handle tool calls and notify ViewModel immediately
                chunk.toolCalls?.let { toolCalls ->
                    val domainToolCalls = toolCalls.map { toolCall ->
                        xyz.sandboiii.agentcooper.domain.model.ToolCall(
                            id = toolCall.id,
                            name = toolCall.name,
                            arguments = toolCall.arguments
                        )
                    }
                    accumulatedToolCalls.addAll(domainToolCalls)
                    Log.d(TAG, "Streaming: Accumulated ${accumulatedToolCalls.size} tool calls")
                    // Notify ViewModel of new tool calls immediately
                    onToolCallsUpdate(accumulatedToolCalls.toList())
                }
            }
            
            // Execute tool calls iteratively - AI can make multiple rounds of tool calls
            var currentMessages = messages.toMutableList()
            var roundNumber = 1
            val maxRounds = 5
            
            while (accumulatedToolCalls.isNotEmpty() && roundNumber <= maxRounds) {
                Log.d(TAG, "Tool calling round $roundNumber: Executing ${accumulatedToolCalls.size} tool calls")
                
                val currentRoundToolResults = mutableListOf<xyz.sandboiii.agentcooper.domain.model.ToolResult>()
                
                for (toolCall in accumulatedToolCalls) {
                    try {
                        Log.d(TAG, "Executing tool: ${toolCall.name} with args: ${toolCall.arguments}")
                        
                        val argumentsJson = try {
                            json.parseToJsonElement(toolCall.arguments)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to parse tool arguments", e)
                            val errorResult = xyz.sandboiii.agentcooper.domain.model.ToolResult(
                                toolCallId = toolCall.id,
                                toolName = toolCall.name,
                                result = "Error: Failed to parse arguments: ${e.message}",
                                isError = true
                            )
                            currentRoundToolResults.add(errorResult)
                            toolResults.add(errorResult)
                            continue
                        }
                        
                        val result = mcpRepository.callTool(toolCall.name, argumentsJson)
                        
                        result.fold(
                            onSuccess = { resultText ->
                                Log.d(TAG, "Tool ${toolCall.name} executed successfully")
                                val successResult = xyz.sandboiii.agentcooper.domain.model.ToolResult(
                                    toolCallId = toolCall.id,
                                    toolName = toolCall.name,
                                    result = resultText,
                                    isError = false
                                )
                                currentRoundToolResults.add(successResult)
                                toolResults.add(successResult)
                            },
                            onFailure = { error ->
                                Log.e(TAG, "Tool ${toolCall.name} execution failed", error)
                                val errorResult = xyz.sandboiii.agentcooper.domain.model.ToolResult(
                                    toolCallId = toolCall.id,
                                    toolName = toolCall.name,
                                    result = "Error: ${error.message}",
                                    isError = true
                                )
                                currentRoundToolResults.add(errorResult)
                                toolResults.add(errorResult)
                            }
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Exception executing tool ${toolCall.name}", e)
                        val errorResult = xyz.sandboiii.agentcooper.domain.model.ToolResult(
                            toolCallId = toolCall.id,
                            toolName = toolCall.name,
                            result = "Error: ${e.message}",
                            isError = true
                        )
                        currentRoundToolResults.add(errorResult)
                        toolResults.add(errorResult)
                    }
                }
                
                // Notify ViewModel of tool results immediately
                onToolResultsUpdate(toolResults.toList())
                
                // Send tool results back to OpenRouter and get response
                if (currentRoundToolResults.isNotEmpty()) {
                    Log.d(TAG, "Round $roundNumber: Sending ${currentRoundToolResults.size} tool results back to OpenRouter")
                    
                    val messagesWithToolResults = currentMessages.toMutableList().apply {
                        val toolCallsForMessage = accumulatedToolCalls.map { toolCall ->
                            ToolCallInfo(
                                id = toolCall.id,
                                name = toolCall.name,
                                arguments = toolCall.arguments
                            )
                        }
                        add(ChatMessageDto(
                            role = "assistant",
                            content = "",
                            toolCallId = null,
                            toolCalls = toolCallsForMessage
                        ))
                        
                        currentRoundToolResults.forEach { toolResult ->
                            add(ChatMessageDto(
                                role = "tool",
                                content = toolResult.result,
                                toolCallId = toolResult.toolCallId,
                                toolCalls = null
                            ))
                        }
                    }
                    
                    accumulatedToolCalls.clear()
                    var roundContent = ""
                    
                    chatApi.sendMessage(
                        messagesWithToolResults,
                        modelId,
                        stream = true,
                        onMetadata = { metadata ->
                            responseModelId = metadata.modelId ?: modelId
                            promptTokens = (promptTokens ?: 0) + (metadata.promptTokens ?: 0)
                            completionTokens = (completionTokens ?: 0) + (metadata.completionTokens ?: 0)
                            totalTokens = (totalTokens ?: 0) + (metadata.totalTokens ?: 0)
                            apiCost = (apiCost ?: 0.0) + (metadata.cost ?: 0.0)
                        },
                        tools = openAITools,
                        onToolCalls = null // Don't use callback - we collect from chunks to avoid duplicates
                    ).collect { chunk ->
                        chunk.content?.let { content ->
                            roundContent += content
                            emit(content)
                        }
                        
                        chunk.toolCalls?.let { newToolCalls ->
                            val domainToolCalls = newToolCalls.map { toolCall ->
                                xyz.sandboiii.agentcooper.domain.model.ToolCall(
                                    id = toolCall.id,
                                    name = toolCall.name,
                                    arguments = toolCall.arguments
                                )
                            }
                            accumulatedToolCalls.addAll(domainToolCalls)
                            Log.d(TAG, "Round $roundNumber: Added ${domainToolCalls.size} tool calls from chunk")
                            // Notify ViewModel of new tool calls immediately
                            onToolCallsUpdate(accumulatedToolCalls.toList())
                        }
                    }
                    
                    accumulatedContent += roundContent
                    currentMessages = messagesWithToolResults
                    roundNumber++
                    
                    if (accumulatedToolCalls.isEmpty()) {
                        Log.d(TAG, "No more tool calls received, ending tool calling loop")
                        break
                    }
                } else {
                    break
                }
            }
            
            if (roundNumber > maxRounds) {
                Log.w(TAG, "Tool calling stopped after $maxRounds rounds to prevent infinite loop")
                emit("\n\n[Note: Tool calling stopped after $maxRounds rounds to prevent infinite loop]")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error streaming message", e)
            throw e
        }
        
        val responseTimeMs = System.currentTimeMillis() - startTime
        val messageContent = accumulatedContent
        
        Log.d(TAG, "Before cost calculation - Final token values: prompt=$promptTokens, completion=$completionTokens, total=$totalTokens, modelId=${responseModelId ?: modelId}, apiCost=$apiCost")
        
        // Calculate cost and context window usage
        var totalCost: Double? = null
        var contextWindowUsedPercent: Double? = null
        
        // Use API cost if available, otherwise fallback to local calculation
        if (apiCost != null) {
            totalCost = apiCost
            Log.d(TAG, "Using cost from API usage accounting: $totalCost")
        } else {
            Log.d(TAG, "API cost not available, falling back to local calculation")
            try {
                val models = chatApi.getModels()
                val lookupModelId = responseModelId ?: modelId
                val model = models.find { it.id == lookupModelId }
                
                Log.d(TAG, "Looking up model for cost calculation: $lookupModelId")
                Log.d(TAG, "Token usage: prompt=$promptTokens, completion=$completionTokens, total=$totalTokens")
                
                if (model == null) {
                    Log.w(TAG, "Model not found in models list: $lookupModelId")
                }
                
                model?.let { modelInfo ->
                    Log.d(TAG, "Found model: ${modelInfo.name}, pricing: ${modelInfo.pricing?.displayText ?: "null"}")
                    
                    // Calculate cost if model is paid
                    modelInfo.pricing?.let { pricing ->
                        Log.d(TAG, "Pricing details: prompt=${pricing.prompt}, completion=${pricing.completion}, isFree=${pricing.isFree}")
                        
                        val promptPricePerToken = pricing.prompt?.toDoubleOrNull() ?: 0.0
                        val completionPricePerToken = pricing.completion?.toDoubleOrNull() ?: 0.0
                        
                        val isActuallyPaid = promptPricePerToken > 0.0 || completionPricePerToken > 0.0
                        
                        if (isActuallyPaid && promptTokens != null && completionTokens != null) {
                            val promptCost = promptTokens!! * promptPricePerToken
                            val completionCost = completionTokens!! * completionPricePerToken
                            totalCost = promptCost + completionCost
                            
                            Log.d(TAG, "Cost calculation: promptCost=$promptCost, completionCost=$completionCost, totalCost=$totalCost")
                        } else {
                            Log.d(TAG, "Model appears to be free or missing token counts")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to calculate cost", e)
            }
        }
        
        // Calculate context window usage percentage
        try {
            val models = chatApi.getModels()
            val lookupModelId = responseModelId ?: modelId
            val model = models.find { it.id == lookupModelId }
            
            model?.contextLength?.let { contextLength ->
                totalTokens?.let { tokens ->
                    contextWindowUsedPercent = (tokens.toDouble() / contextLength.toDouble()) * 100.0
                    Log.d(TAG, "Context window usage: $tokens / $contextLength = ${contextWindowUsedPercent}%")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to calculate context window usage", e)
        }
        
        Log.d(TAG, "Cost calculation complete - totalCost=$totalCost, contextWindowUsedPercent=$contextWindowUsedPercent")
        
        // Save assistant message with all accumulated data
        val assistantMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            content = messageContent,
            role = MessageRole.ASSISTANT,
            timestamp = System.currentTimeMillis(),
            sessionId = sessionId,
            modelId = responseModelId ?: modelId,
            responseTimeMs = responseTimeMs,
            promptTokens = promptTokens,
            completionTokens = completionTokens,
            totalCost = totalCost,
            contextWindowUsedPercent = contextWindowUsedPercent,
            toolCalls = if (accumulatedToolCalls.isNotEmpty()) accumulatedToolCalls else null,
            toolResults = if (toolResults.isNotEmpty()) toolResults else null
        )
        
        sessionFileStorage.addMessageToSession(session, assistantMessage, storageLocation)
        
        // Generate title if this is the first user message
        if (isFirstUserMessage) {
            try {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val titlePrompt = "Generate a short, descriptive title (max 50 characters) for a conversation that starts with: \"$content\". Respond with only the title, no quotes or extra text."
                        
                        val titleMessages = listOf(
                            ChatMessageDto(role = "system", content = "You are a helpful assistant that generates concise titles for conversations."),
                            ChatMessageDto(role = "user", content = titlePrompt)
                        )
                        
                        var generatedTitle = ""
                        chatApi.sendMessage(
                            messages = titleMessages,
                            model = modelId,
                            stream = false,
                            onMetadata = null,
                            tools = null,
                            onToolCalls = null
                        ).collect { chunk ->
                            chunk.content?.let { generatedTitle += it }
                        }
                        
                        if (generatedTitle.isNotBlank()) {
                            val cleanTitle = generatedTitle.trim()
                                .removePrefix("\"").removeSuffix("\"")
                                .take(50)
                            
                            sessionRepository.updateSessionTitle(sessionId, cleanTitle)
                            Log.d(TAG, "Generated session title: $cleanTitle")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to generate session title", e)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to generate session title", e)
            }
        }
    }
}
