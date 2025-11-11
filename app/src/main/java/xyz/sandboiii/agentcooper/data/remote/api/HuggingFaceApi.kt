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

class HuggingFaceApi @Inject constructor(
    private val preferencesManager: PreferencesManager
) : ChatApi {
    
    companion object {
        private const val TAG = "HuggingFaceApi"
        
        /**
         * Formats numeric pricing value to string format.
         * Hugging Face provides prices as numbers (e.g., 1.2 means $1.2 per million tokens)
         */
        private fun formatPrice(price: Double): String {
            // Format as price per million tokens
            // Remove trailing zeros if it's a whole number
            return if (price % 1.0 == 0.0) {
                price.toInt().toString()
            } else {
                String.format("%.2f", price).trimEnd('0').trimEnd('.')
            }
        }
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
            ?: throw IllegalStateException("API key not set. Please configure your Hugging Face API key.")
        
        val temperature = preferencesManager.getTemperature()
        
        val requestMessages = messages.map { 
            OpenRouterMessage(role = it.role, content = it.content) 
        }
        
        val request = OpenRouterRequest(
            model = model,
            messages = requestMessages,
            stream = stream,
            temperature = temperature.toDouble()
        )
        
        try {
            val response = client.post("${Constants.HUGGINGFACE_BASE_URL}/chat/completions") {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                setBody(request)
            }
            
            // Check response status first
            if (response.status.value == 404) {
                val errorBody = response.body<String>()
                Log.e(TAG, "404 response: $errorBody")
                try {
                    val errorResponse = jsonParser.decodeFromString<OpenRouterResponse>(errorBody)
                    errorResponse.error?.let { error ->
                        throw RuntimeException("API error: ${error.message}")
                    }
                } catch (e: Exception) {
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
    
    override suspend fun sendMessageWithUsage(
        messages: List<ChatMessageDto>,
        model: String
    ): MessageResponse {
        val apiKey = preferencesManager.getApiKey() 
            ?: throw IllegalStateException("API key not set. Please configure your Hugging Face API key.")
        
        val temperature = preferencesManager.getTemperature()
        
        val requestMessages = messages.map { 
            OpenRouterMessage(role = it.role, content = it.content) 
        }
        
        val request = OpenRouterRequest(
            model = model,
            messages = requestMessages,
            stream = false, // Non-streaming to get usage
            temperature = temperature.toDouble()
        )
        
        try {
            val response = client.post("${Constants.HUGGINGFACE_BASE_URL}/chat/completions") {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                setBody(request)
            }
            
            val jsonResponse = response.body<OpenRouterResponse>()
            
            // Check for errors first
            jsonResponse.error?.let { error ->
                Log.e(TAG, "API error: ${error.message}")
                throw RuntimeException("API error: ${error.message}")
            }
            
            val content = jsonResponse.choices?.firstOrNull()?.message?.content ?: ""
            val usage = jsonResponse.usage
            
            return MessageResponse(
                content = content,
                promptTokens = usage?.prompt_tokens,
                completionTokens = usage?.completion_tokens
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send message with usage", e)
            throw RuntimeException("Failed to send message: ${e.message}", e)
        }
    }
    
    override suspend fun generateTitle(
        userMessage: String,
        aiResponse: String,
        model: String
    ): String {
        val apiKey = preferencesManager.getApiKey()
            ?: throw IllegalStateException("API key not set. Please configure your Hugging Face API key.")

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
            val response = client.post("${Constants.HUGGINGFACE_BASE_URL}/chat/completions") {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                header(HttpHeaders.ContentType, ContentType.Application.Json)
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
            ?: throw IllegalStateException("API key not set. Please configure your Hugging Face API key.")
        
        return try {
            Log.d(TAG, "Fetching models from Hugging Face API")
            val response = client.get("${Constants.HUGGINGFACE_BASE_URL}/models") {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
            }
            
            val responseBody = response.body<String>()
            Log.d(TAG, "API Response length: ${responseBody.length} characters")
            Log.d(TAG, "API Response preview: ${responseBody.take(3000)}")
            
            // Check if response contains pricing-related keywords
            val hasPricingKeyword = responseBody.contains("pricing", ignoreCase = true) ||
                    responseBody.contains("price", ignoreCase = true) ||
                    responseBody.contains("cost", ignoreCase = true) ||
                    responseBody.contains("prompt", ignoreCase = true) ||
                    responseBody.contains("completion", ignoreCase = true)
            Log.d(TAG, "Response contains pricing-related keywords: $hasPricingKeyword")
            
            // Try to find pricing in the raw response
            if (hasPricingKeyword) {
                val pricingMatch = Regex("(?i)\"pricing\"\\s*:\\s*\\{([^}]+)\\}").find(responseBody)
                if (pricingMatch != null) {
                    Log.d(TAG, "Found pricing object in response: ${pricingMatch.value}")
                } else {
                    Log.d(TAG, "No pricing object found in response despite keywords")
                }
            }
            
            // Hugging Face uses OpenAI-compatible format: { "data": [...] }
            val modelsResponse = jsonParser.decodeFromString<OpenRouterModelsResponse>(responseBody)
            Log.d(TAG, "Parsed ${modelsResponse.data.size} models")
            
            // Log first model structure to understand the format
            if (modelsResponse.data.isNotEmpty()) {
                val firstModel = modelsResponse.data.first()
                Log.d(TAG, "First model structure - id: ${firstModel.id}, name: ${firstModel.name}")
                Log.d(TAG, "First model pricing: ${firstModel.pricing}")
                Log.d(TAG, "First model pricing prompt: ${firstModel.pricing?.prompt}, completion: ${firstModel.pricing?.completion}")
            }
            
            // Filter to only exclude image and audio generation models
            // Keep all text models, including those that might be good for coding or general chat
            val chatOnlyModels = modelsResponse.data.filter { modelData ->
                val modality = modelData.architecture?.modality?.lowercase()
                
                // Only exclude image/audio models based on modality
                // Include everything else (text models, null modality, etc.)
                when (modality) {
                    "image", "audio" -> false
                    else -> true // Include text, null, or any other modality
                }
            }
            
            Log.d(TAG, "Retrieved ${modelsResponse.data.size} models from Hugging Face")
            Log.d(TAG, "Filtered to ${chatOnlyModels.size} chat-only models (excluded image/audio models)")
            
            chatOnlyModels.map { modelData ->
                val providerName = when {
                    !modelData.top_provider?.name.isNullOrBlank() -> modelData.top_provider!!.name!!
                    !modelData.top_provider?.id.isNullOrBlank() -> modelData.top_provider!!.id!!
                    else -> "Hugging Face"
                }
                
                // Extract pricing information from providers array (Hugging Face format)
                val pricingInfo = if (modelData.providers != null && modelData.providers.isNotEmpty()) {
                    // Try to find pricing from providers array
                    // Prefer the provider that matches top_provider, or use the first one with pricing
                    val providerWithPricing = modelData.providers.firstOrNull { it.pricing != null }
                        ?: modelData.providers.firstOrNull()
                    
                    providerWithPricing?.pricing?.let { pricing ->
                        Log.d(TAG, "Model ${modelData.id} - pricing from provider ${providerWithPricing.provider}: input=${pricing.input}, output=${pricing.output}")
                        
                        // Convert numeric pricing to string format (e.g., 1.2 -> "1.2" or "$1.2/1M tokens")
                        val promptPrice = pricing.prompt 
                            ?: pricing.prompt_price 
                            ?: pricing.input?.let { formatPrice(it) }
                            ?: pricing.input_price
                        
                        val completionPrice = pricing.completion 
                            ?: pricing.completion_price 
                            ?: pricing.output?.let { formatPrice(it) }
                            ?: pricing.output_price
                        
                        if (promptPrice != null || completionPrice != null) {
                            PricingInfo(
                                prompt = promptPrice,
                                completion = completionPrice
                            )
                        } else {
                            Log.d(TAG, "Model ${modelData.id} - provider pricing exists but no price fields found")
                            null
                        }
                    } ?: run {
                        Log.d(TAG, "Model ${modelData.id} - providers exist but no pricing found")
                        null
                    }
                } else if (modelData.pricing != null) {
                    // Fallback to direct pricing object (OpenRouter format)
                    val pricing = modelData.pricing
                    Log.d(TAG, "Model ${modelData.id} - direct pricing object: prompt=${pricing.prompt}, completion=${pricing.completion}, input=${pricing.input}, output=${pricing.output}")
                    
                    val promptPrice = pricing.prompt 
                        ?: pricing.prompt_price 
                        ?: pricing.input?.let { formatPrice(it) }
                        ?: pricing.input_price
                    
                    val completionPrice = pricing.completion 
                        ?: pricing.completion_price 
                        ?: pricing.output?.let { formatPrice(it) }
                        ?: pricing.output_price
                    
                    if (promptPrice != null || completionPrice != null) {
                        PricingInfo(
                            prompt = promptPrice,
                            completion = completionPrice
                        )
                    } else {
                        Log.d(TAG, "Model ${modelData.id} - pricing object exists but no price fields found")
                        null
                    }
                } else {
                    Log.d(TAG, "Model ${modelData.id} - no pricing found (no providers or pricing object)")
                    null
                }
                
                Log.d(TAG, "Model: ${modelData.id}, Name: ${modelData.name}, Provider: $providerName, Pricing: ${pricingInfo?.displayText ?: "null"}")
                
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

