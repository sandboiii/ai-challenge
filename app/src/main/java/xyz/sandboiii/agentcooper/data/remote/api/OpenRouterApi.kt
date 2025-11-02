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
        stream: Boolean
    ): Flow<String> = flow {
        val apiKey = preferencesManager.getApiKey() 
            ?: throw IllegalStateException("API key not set. Please configure your OpenRouter API key.")
        
        val requestMessages = messages.map { 
            OpenRouterMessage(role = it.role, content = it.content) 
        }
        
        val request = OpenRouterRequest(
            model = model,
            messages = requestMessages,
            stream = stream
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
                
                // First, try to parse as SSE format (lines starting with "data: ")
                val lines = body.lines()
                var foundSSEFormat = false
                
                for (line in lines) {
                    if (line.startsWith("data: ")) {
                        foundSSEFormat = true
                        val data = line.removePrefix("data: ").trim()
                        if (data == "[DONE]") {
                            Log.d(TAG, "Received [DONE] signal")
                            break
                        }
                        
                        if (data.isNotEmpty()) {
                            try {
                                val jsonResponse = jsonParser.decodeFromString<OpenRouterResponse>(data)
                                
                                // Try delta first (for streaming chunks)
                                val deltaContent = jsonResponse.choices?.firstOrNull()?.delta?.content
                                if (!deltaContent.isNullOrEmpty()) {
                                    hasReceivedData = true
                                    Log.d(TAG, "Emitting delta chunk (length: ${deltaContent.length}): ${deltaContent.take(50)}...")
                                    emit(deltaContent)
                                } else {
                                    // Try message field (for complete responses in SSE)
                                    val messageContent = jsonResponse.choices?.firstOrNull()?.message?.content
                                    if (!messageContent.isNullOrEmpty()) {
                                        Log.d(TAG, "Emitting complete message from SSE (length: ${messageContent.length}): ${messageContent.take(50)}...")
                                        emit(messageContent)
                                        hasReceivedData = true
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
                        
                        // Try message field first (complete response)
                        val messageContent = jsonResponse.choices?.firstOrNull()?.message?.content
                        if (!messageContent.isNullOrEmpty()) {
                            Log.d(TAG, "Emitting complete message from JSON (length: ${messageContent.length}): ${messageContent.take(50)}...")
                            emit(messageContent)
                            hasReceivedData = true
                        } else {
                            // Try delta as fallback
                            val deltaContent = jsonResponse.choices?.firstOrNull()?.delta?.content
                            if (!deltaContent.isNullOrEmpty()) {
                                Log.d(TAG, "Emitting delta from JSON (length: ${deltaContent.length}): ${deltaContent.take(50)}...")
                                emit(deltaContent)
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
                
                jsonResponse.choices?.firstOrNull()?.message?.content?.let { content ->
                    emit(content)
                } ?: run {
                    Log.w(TAG, "No content in non-streaming response")
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
            stream = false // Non-streaming for title generation
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
            val filteredModels = modelsResponse.data
            
            Log.d(TAG, "Retrieved ${filteredModels.size} models (filtered by OpenRouter based on privacy settings)")
            
            filteredModels.map { modelData ->
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
                
                Log.d(TAG, "Model: ${modelData.id}, Name: ${modelData.name}, Provider: $providerName, Pricing: ${pricingInfo?.displayText ?: "unknown"}, Data Policy: ${modelData.data_policy}")
                
                ModelDto(
                    id = modelData.id,
                    name = modelData.name ?: modelData.id,
                    provider = providerName,
                    description = modelData.description,
                    pricing = pricingInfo
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

