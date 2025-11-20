package xyz.sandboiii.agentcooper.data.remote.api

import android.util.Log
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.serialization.json.Json
import xyz.sandboiii.agentcooper.data.remote.dto.*
import xyz.sandboiii.agentcooper.util.Constants
import xyz.sandboiii.agentcooper.util.PreferencesManager
import javax.inject.Inject

class OpenRouterApi @Inject constructor(
    private val preferencesManager: PreferencesManager
) : ChatApi {
    
    companion object {
        private const val TAG = "OpenRouterApi"
    }
    
    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = false
    }
    
    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(jsonParser)
        }
        install(Logging) {
            level = LogLevel.INFO
            logger = object : Logger {
                override fun log(message: String) {
                    Log.d(TAG, message)
                }
            }
        }
    }
    
    override suspend fun sendMessage(
        messages: List<ChatMessageDto>,
        model: String,
        stream: Boolean,
        onMetadata: ((MessageMetadata) -> Unit)?,
        tools: List<xyz.sandboiii.agentcooper.data.remote.mcp.OpenAITool>?,
        onToolCalls: ((List<ToolCallInfo>) -> Unit)?
    ): Flow<MessageChunk> = flow {
        val apiKey = preferencesManager.getApiKey() 
            ?: throw IllegalStateException("API key not set. Please configure your OpenRouter API key.")
        
        val temperature = preferencesManager.getTemperature()
        
        val requestMessages = messages.map { 
            OpenRouterMessage(
                role = it.role, 
                content = it.content,
                tool_calls = it.toolCalls?.map { toolCall ->
                    ToolCall(
                        id = toolCall.id,
                        type = "function",
                        function = ToolCallFunction(
                            name = toolCall.name,
                            arguments = toolCall.arguments
                        )
                    )
                },
                tool_call_id = it.toolCallId
            ) 
        }
        
        val request = OpenRouterRequest(
            model = model,
            messages = requestMessages,
            stream = stream,
            temperature = temperature.toDouble(),
            usage = UsageRequest(include = true), // Enable usage accounting to get cost from API
            tools = tools,
            tool_choice = if (tools != null && tools.isNotEmpty()) "auto" else null
        )
        
        try {
            val response = client.post("${Constants.OPENROUTER_BASE_URL}/chat/completions") {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                header("HTTP-Referer", "https://github.com/sandboiii/AgentCooper")
                header("X-Title", "Agent Cooper")
                setBody(request)
            }
            
            // Check response status first
            if (response.status.value == 404) {
                val errorBody = response.body<String>()
                Log.e(TAG, "404 response: $errorBody")
                try {
                    val errorResponse = jsonParser.decodeFromString<OpenRouterResponse>(errorBody)
                    errorResponse.error?.let { error ->
                        if (error.message.contains("data policy", ignoreCase = true) || 
                            error.message.contains("privacy", ignoreCase = true)) {
                            throw IllegalStateException("This model is not compatible with your privacy settings. Please configure your privacy settings at https://openrouter.ai/settings/privacy or choose a different model.")
                        }
                        throw RuntimeException("API error: ${error.message}")
                    }
                } catch (e: IllegalStateException) {
                    throw e
                } catch (e: Exception) {
                    // If error parsing fails, check raw body
                    if (errorBody.contains("data policy", ignoreCase = true) || 
                        errorBody.contains("privacy", ignoreCase = true)) {
                        throw IllegalStateException("This model is not compatible with your privacy settings. Please configure your privacy settings at https://openrouter.ai/settings/privacy or choose a different model.")
                    }
                    throw RuntimeException("API error: $errorBody")
                }
            }
            
            if (stream) {
                // Handle SSE streaming
                Log.d(TAG, "Starting SSE stream processing")
                val body = response.body<String>()
                Log.d(TAG, "Response body length: ${body.length}")
                Log.d(TAG, "Response preview: ${body.take(1000)}")
                
                var hasReceivedData = false
                var lastResponseModel: String? = null
                var lastUsage: Usage? = null
                var accumulatedToolCalls = mutableListOf<ToolCallInfo>()
                var finishReason: String? = null
                
                // First, try to parse as SSE format (lines starting with "data: ")
                val lines = body.lines()
                var foundSSEFormat = false
                
                for (line in lines) {
                    if (line.startsWith("data: ")) {
                        foundSSEFormat = true
                        val data = line.removePrefix("data: ").trim()
                        if (data == "[DONE]") {
                            Log.d(TAG, "Received [DONE] signal")
                            // Always invoke metadata callback if we have any information, even if usage is null
                            // This ensures the callback is called so cost calculation can proceed
                            onMetadata?.invoke(MessageMetadata(
                                modelId = lastResponseModel,
                                promptTokens = lastUsage?.prompt_tokens,
                                completionTokens = lastUsage?.completion_tokens,
                                totalTokens = lastUsage?.total_tokens,
                                cost = lastUsage?.cost
                            ))
                            Log.d(TAG, "Invoked metadata callback: model=$lastResponseModel, usage=$lastUsage, cost=${lastUsage?.cost}")
                            
                            // Emit any accumulated tool calls at the end
                            if (accumulatedToolCalls.isNotEmpty()) {
                                Log.d(TAG, "Final tool calls emission: ${accumulatedToolCalls.size} tool calls")
                                onToolCalls?.invoke(accumulatedToolCalls)
                            }
                            break
                        }
                        
                        if (data.isNotEmpty()) {
                            try {
                                val jsonResponse = jsonParser.decodeFromString<OpenRouterResponse>(data)
                                
                                // Capture model and usage from response (always capture, even if no content)
                                if (jsonResponse.model != null) {
                                    lastResponseModel = jsonResponse.model
                                    Log.d(TAG, "Captured model: ${jsonResponse.model}")
                                }
                                if (jsonResponse.usage != null) {
                                    lastUsage = jsonResponse.usage
                                    Log.d(TAG, "Captured usage: prompt=${jsonResponse.usage.prompt_tokens}, completion=${jsonResponse.usage.completion_tokens}, total=${jsonResponse.usage.total_tokens}, cost=${jsonResponse.usage.cost}")
                                }
                                
                                val choice = jsonResponse.choices?.firstOrNull()
                                finishReason = choice?.finish_reason
                                
                                // Check for tool_calls in delta (streaming)
                                val deltaToolCalls = choice?.delta?.tool_calls
                                if (deltaToolCalls != null && deltaToolCalls.isNotEmpty()) {
                                    hasReceivedData = true
                                    val toolCalls = deltaToolCalls.mapNotNull { toolCall ->
                                        toolCall.id?.let { id ->
                                            ToolCallInfo(
                                                id = id,
                                                name = toolCall.function.name,
                                                arguments = toolCall.function.arguments
                                            )
                                        }
                                    }
                                    accumulatedToolCalls.addAll(toolCalls)
                                    Log.d(TAG, "Detected tool calls in delta: ${toolCalls.size}, total accumulated: ${accumulatedToolCalls.size}")
                                    emit(MessageChunk(toolCalls = toolCalls))
                                    // Immediately invoke callback for streaming tool calls
                                    onToolCalls?.invoke(toolCalls)
                                }
                                
                                // Check for tool_calls in message (complete response)
                                val messageToolCalls = choice?.message?.tool_calls
                                if (messageToolCalls != null && messageToolCalls.isNotEmpty()) {
                                    hasReceivedData = true
                                    val toolCalls = messageToolCalls.mapNotNull { toolCall ->
                                        toolCall.id?.let { id ->
                                            ToolCallInfo(
                                                id = id,
                                                name = toolCall.function.name,
                                                arguments = toolCall.function.arguments
                                            )
                                        }
                                    }
                                    accumulatedToolCalls.addAll(toolCalls)
                                    Log.d(TAG, "Detected tool calls in message: ${toolCalls.size}, total accumulated: ${accumulatedToolCalls.size}")
                                    emit(MessageChunk(toolCalls = toolCalls, finishReason = finishReason))
                                    // Immediately invoke callback for complete message tool calls
                                    onToolCalls?.invoke(toolCalls)
                                }
                                
                                // Try delta content (for streaming chunks)
                                val deltaContent = choice?.delta?.content
                                if (!deltaContent.isNullOrEmpty()) {
                                    hasReceivedData = true
                                    Log.d(TAG, "Emitting delta chunk (length: ${deltaContent.length}): ${deltaContent.take(50)}...")
                                    emit(MessageChunk(content = deltaContent))
                                } else {
                                    // Try message field (for complete responses in SSE)
                                    val messageContent = choice?.message?.content
                                    if (!messageContent.isNullOrEmpty()) {
                                        Log.d(TAG, "Emitting complete message from SSE (length: ${messageContent.length}): ${messageContent.take(50)}...")
                                        emit(MessageChunk(content = messageContent, finishReason = finishReason))
                                        hasReceivedData = true
                                    } else if (jsonResponse.usage != null) {
                                        // If there's usage info but no content, this might be a final metadata-only message
                                        Log.d(TAG, "Received metadata-only message with usage info")
                                    }
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to parse SSE JSON line: $data", e)
                                Log.w(TAG, "Error: ${e.message}")
                            }
                        }
                    } else if (line.startsWith("error:")) {
                        val errorData = line.removePrefix("error:").trim()
                        Log.e(TAG, "Server error: $errorData")
                        throw RuntimeException("Server error: $errorData")
                    }
                }
                
                // If we didn't get metadata before [DONE], try to extract it from the last valid response
                if (lastResponseModel == null && lastUsage == null && foundSSEFormat) {
                    // Parse the last data line before [DONE] again to get usage
                    val lastDataLine = lines.lastOrNull { it.startsWith("data: ") && !it.contains("[DONE]") }
                    lastDataLine?.let { line ->
                        val data = line.removePrefix("data: ").trim()
                        if (data.isNotEmpty()) {
                            try {
                                val jsonResponse = jsonParser.decodeFromString<OpenRouterResponse>(data)
                                if (jsonResponse.model != null) lastResponseModel = jsonResponse.model
                                if (jsonResponse.usage != null) lastUsage = jsonResponse.usage
                                onMetadata?.invoke(MessageMetadata(
                                    modelId = lastResponseModel,
                                    promptTokens = lastUsage?.prompt_tokens,
                                    completionTokens = lastUsage?.completion_tokens,
                                    totalTokens = lastUsage?.total_tokens,
                                    cost = lastUsage?.cost
                                ))
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to extract metadata from last line", e)
                            }
                        }
                    }
                }
                
                // If not SSE format, try parsing as single JSON response
                if (!foundSSEFormat && !hasReceivedData) {
                    Log.d(TAG, "Not SSE format, trying to parse as single JSON response")
                    try {
                        val jsonResponse = jsonParser.decodeFromString<OpenRouterResponse>(body.trim())
                        
                        // Check for error first
                        jsonResponse.error?.let { error ->
                            Log.e(TAG, "API error: ${error.message}")
                            if (error.message.contains("data policy", ignoreCase = true) || 
                                error.message.contains("privacy", ignoreCase = true)) {
                                throw IllegalStateException("This model is not compatible with your privacy settings. Please configure your privacy settings at https://openrouter.ai/settings/privacy or choose a different model.")
                            }
                            throw RuntimeException("API error: ${error.message}")
                        }
                        
                        // Extract metadata
                        if (jsonResponse.model != null || jsonResponse.usage != null) {
                            onMetadata?.invoke(MessageMetadata(
                                modelId = jsonResponse.model,
                                promptTokens = jsonResponse.usage?.prompt_tokens,
                                completionTokens = jsonResponse.usage?.completion_tokens,
                                totalTokens = jsonResponse.usage?.total_tokens,
                                cost = jsonResponse.usage?.cost
                            ))
                        }
                        
                        val choice = jsonResponse.choices?.firstOrNull()
                        val finishReason = choice?.finish_reason
                        
                        // Check for tool_calls in message
                        val messageToolCalls = choice?.message?.tool_calls
                        if (messageToolCalls != null && messageToolCalls.isNotEmpty()) {
                            val toolCalls = messageToolCalls.mapNotNull { toolCall ->
                                toolCall.id?.let { id ->
                                    ToolCallInfo(
                                        id = id,
                                        name = toolCall.function.name,
                                        arguments = toolCall.function.arguments
                                    )
                                }
                            }
                            Log.d(TAG, "Detected tool calls in non-streaming response: ${toolCalls.size}")
                            emit(MessageChunk(toolCalls = toolCalls, finishReason = finishReason))
                            onToolCalls?.invoke(toolCalls)
                            hasReceivedData = true
                        }
                        
                        // Try message field first (complete response)
                        val messageContent = choice?.message?.content
                        if (!messageContent.isNullOrEmpty()) {
                            Log.d(TAG, "Emitting complete message from JSON (length: ${messageContent.length}): ${messageContent.take(50)}...")
                            emit(MessageChunk(content = messageContent, finishReason = finishReason))
                            hasReceivedData = true
                        } else {
                            // Try delta as fallback
                            val deltaContent = choice?.delta?.content
                            if (!deltaContent.isNullOrEmpty()) {
                                Log.d(TAG, "Emitting delta from JSON (length: ${deltaContent.length}): ${deltaContent.take(50)}...")
                                emit(MessageChunk(content = deltaContent, finishReason = finishReason))
                                hasReceivedData = true
                            } else {
                                Log.w(TAG, "No content found in response. Choices: ${jsonResponse.choices}")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse body as JSON", e)
                        Log.e(TAG, "Error: ${e.message}")
                        Log.e(TAG, "Body sample: ${body.take(500)}")
                        throw RuntimeException("Failed to parse response: ${e.message}", e)
                    }
                }
                
                if (!hasReceivedData) {
                    Log.e(TAG, "No data received! Full response: ${body.take(3000)}")
                } else {
                    Log.d(TAG, "Stream processing completed successfully")
                }
            } else {
                // Handle non-streaming response
                val jsonResponse = response.body<OpenRouterResponse>()
                
                // Check for errors first
                jsonResponse.error?.let { error ->
                    Log.e(TAG, "API error: ${error.message}")
                    if (error.message.contains("data policy", ignoreCase = true) || 
                        error.message.contains("privacy", ignoreCase = true)) {
                        throw IllegalStateException("This model is not compatible with your privacy settings. Please configure your privacy settings at https://openrouter.ai/settings/privacy or choose a different model.")
                    }
                    throw RuntimeException("API error: ${error.message}")
                }
                
                // Extract metadata from non-streaming response
                if (jsonResponse.model != null || jsonResponse.usage != null) {
                    onMetadata?.invoke(MessageMetadata(
                        modelId = jsonResponse.model,
                        promptTokens = jsonResponse.usage?.prompt_tokens,
                        completionTokens = jsonResponse.usage?.completion_tokens,
                        totalTokens = jsonResponse.usage?.total_tokens,
                        cost = jsonResponse.usage?.cost
                    ))
                    Log.d(TAG, "Non-streaming metadata: cost=${jsonResponse.usage?.cost}")
                }
                
                val choice = jsonResponse.choices?.firstOrNull()
                val finishReason = choice?.finish_reason
                
                Log.d(TAG, "Non-streaming response - finish_reason: $finishReason, has message: ${choice?.message != null}")
                
                // Check for tool_calls
                val messageToolCalls = choice?.message?.tool_calls
                Log.d(TAG, "Non-streaming response - tool_calls: ${messageToolCalls?.size ?: 0}")
                if (messageToolCalls != null && messageToolCalls.isNotEmpty()) {
                    val toolCalls = messageToolCalls.mapNotNull { toolCall ->
                        toolCall.id?.let { id ->
                            ToolCallInfo(
                                id = id,
                                name = toolCall.function.name,
                                arguments = toolCall.function.arguments
                            )
                        }
                    }
                    Log.d(TAG, "Detected tool calls in non-streaming response: ${toolCalls.size}")
                    emit(MessageChunk(toolCalls = toolCalls, finishReason = finishReason))
                    onToolCalls?.invoke(toolCalls)
                } else if (finishReason == "tool_calls") {
                    Log.w(TAG, "Finish reason is tool_calls but no tool_calls found in message")
                }
                
                choice?.message?.content?.let { content ->
                    Log.d(TAG, "Non-streaming response - content length: ${content.length}")
                    emit(MessageChunk(content = content, finishReason = finishReason))
                } ?: run {
                    Log.w(TAG, "No content in non-streaming response (finish_reason: $finishReason)")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send message", e)
            throw RuntimeException("Failed to send message: ${e.message}", e)
        }
    }
    
    override suspend fun generateTitle(
        userMessage: String,
        aiResponse: String,
        model: String
    ): String {
        val apiKey = preferencesManager.getApiKey()
            ?: throw IllegalStateException("API key not set. Please configure your OpenRouter API key.")

        val temperature = preferencesManager.getTemperature()

        // Create a prompt asking the AI to generate a short title
        val titlePrompt = """
            Based on this conversation, generate a short, descriptive title (2-6 words maximum). 
            Only return the title, nothing else.
            
            User: $userMessage
            Assistant: $aiResponse
            
            Title:
        """.trimIndent()

        val requestMessages = listOf(
            OpenRouterMessage(role = "user", content = titlePrompt)
        )

        val request = OpenRouterRequest(
            model = model,
            messages = requestMessages,
            stream = false, // Non-streaming for title generation
            temperature = temperature.toDouble()
        )

        return try {
            Log.d(TAG, "Generating title for conversation")
            val response = client.post("${Constants.OPENROUTER_BASE_URL}/chat/completions") {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                header("HTTP-Referer", "https://github.com/sandboiii/AgentCooper")
                header("X-Title", "Agent Cooper")
                setBody(request)
            }

            val jsonResponse = response.body<OpenRouterResponse>()
            val title = jsonResponse.choices?.firstOrNull()?.message?.content
                ?.trim()
                ?.replace(Regex("[\"']"), "") // Remove quotes if present
                ?.take(50) // Limit to 50 characters
                ?: throw RuntimeException("No title generated")

            Log.d(TAG, "Generated title: $title")
            title
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate title", e)
            // Fallback to generating title from user message
            generateTitleFromMessage(userMessage)
        }
    }

    /**
     * Fallback method to generate a simple title from the user message
     * if API title generation fails.
     */
    private fun generateTitleFromMessage(message: String): String {
        val cleaned = message.trim().replace(Regex("\\s+"), " ")
        val words = cleaned.split(" ")
        val maxWords = 6
        val maxLength = 50

        val title = if (words.size <= maxWords) {
            cleaned
        } else {
            words.take(maxWords).joinToString(" ")
        }

        return if (title.length > maxLength) {
            title.take(maxLength - 3) + "..."
        } else {
            title
        }
    }

    override suspend fun getModels(): List<ModelDto> {
        val apiKey = preferencesManager.getApiKey() 
            ?: throw IllegalStateException("API key not set. Please configure your OpenRouter API key.")
        
        return try {
            Log.d(TAG, "Fetching models from OpenRouter API")
            val response = client.get("${Constants.OPENROUTER_BASE_URL}/models") {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
            }
            
            val responseBody = response.body<String>()
            Log.d(TAG, "API Response length: ${responseBody.length} characters")
            Log.d(TAG, "API Response preview: ${responseBody.take(500)}")
            
            val modelsResponse = jsonParser.decodeFromString<OpenRouterModelsResponse>(responseBody)
            Log.d(TAG, "Parsed ${modelsResponse.data.size} models")
            
            // OpenRouter API automatically filters models based on your privacy settings
            // when you're authenticated with an API key. The /models endpoint respects
            // your privacy configuration at https://openrouter.ai/settings/privacy
            // All models returned here should be compatible with your privacy settings.
            val privacyFilteredModels = modelsResponse.data
            
            // Filter to only exclude image and audio generation models
            // Keep all text models, including those that might be good for coding or general chat
            val chatOnlyModels = privacyFilteredModels.filter { modelData ->
                val modality = modelData.architecture?.modality?.lowercase()
                
                // Only exclude image/audio models based on modality
                // Include everything else (text models, null modality, etc.)
                when (modality) {
                    "image", "audio" -> false
                    else -> true // Include text, null, or any other modality
                }
            }
            
            Log.d(TAG, "Retrieved ${privacyFilteredModels.size} models (filtered by OpenRouter based on privacy settings)")
            Log.d(TAG, "Filtered to ${chatOnlyModels.size} chat-only models (excluded image/audio models)")
            
            chatOnlyModels.map { modelData ->
                val providerName = when {
                    !modelData.top_provider?.name.isNullOrBlank() -> modelData.top_provider!!.name!!
                    !modelData.top_provider?.id.isNullOrBlank() -> modelData.top_provider!!.id!!
                    else -> "unknown"
                }
                
                val pricingInfo = modelData.pricing?.let {
                    PricingInfo(
                        prompt = it.prompt,
                        completion = it.completion
                    )
                }
                
                ModelDto(
                    id = modelData.id,
                    name = modelData.name ?: modelData.id,
                    provider = providerName,
                    description = modelData.description,
                    pricing = pricingInfo,
                    contextLength = modelData.context_length,
                    contextLengthDisplay = modelData.context_length_display
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch models", e)
            Log.e(TAG, "Error message: ${e.message}")
            Log.e(TAG, "Error cause: ${e.cause?.message}")
            Log.e(TAG, "Error stack trace: ${e.stackTraceToString()}")
            throw RuntimeException("Failed to fetch models: ${e.message}", e)
        }
    }
}

