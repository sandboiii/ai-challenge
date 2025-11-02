package xyz.sandboiii.agentcooper.data.remote.api

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
import kotlinx.serialization.json.Json
import xyz.sandboiii.agentcooper.data.remote.dto.*
import xyz.sandboiii.agentcooper.util.Constants
import xyz.sandboiii.agentcooper.util.PreferencesManager
import javax.inject.Inject

class OpenRouterApi @Inject constructor(
    private val preferencesManager: PreferencesManager
) : ChatApi {
    
    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        install(Logging) {
            level = LogLevel.INFO
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
            
            if (stream) {
                // Handle SSE streaming
                val body = response.body<String>()
                val lines = body.lines()
                
                for (line in lines) {
                    if (line.startsWith("data: ")) {
                        val data = line.removePrefix("data: ").trim()
                        if (data == "[DONE]") {
                            break
                        }
                        
                        if (data.isNotEmpty()) {
                            try {
                                val jsonResponse = Json.decodeFromString<OpenRouterResponse>(data)
                                jsonResponse.choices?.firstOrNull()?.delta?.content?.let { content ->
                                    emit(content)
                                }
                            } catch (e: Exception) {
                                // Skip invalid JSON lines
                            }
                        }
                    }
                }
            } else {
                // Handle non-streaming response
                val jsonResponse = response.body<OpenRouterResponse>()
                jsonResponse.choices?.firstOrNull()?.message?.content?.let { content ->
                    emit(content)
                }
            }
        } catch (e: Exception) {
            throw RuntimeException("Failed to send message: ${e.message}", e)
        }
    }
    
    override suspend fun getModels(): List<ModelDto> {
        val apiKey = preferencesManager.getApiKey() 
            ?: throw IllegalStateException("API key not set. Please configure your OpenRouter API key.")
        
        return try {
            val response = client.get("${Constants.OPENROUTER_BASE_URL}/models") {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
            }
            
            val modelsResponse = response.body<OpenRouterModelsResponse>()
            modelsResponse.data.map { modelData ->
                ModelDto(
                    id = modelData.id,
                    name = modelData.name ?: modelData.id,
                    provider = modelData.top_provider?.name ?: modelData.top_provider?.id ?: "unknown",
                    description = modelData.description
                )
            }
        } catch (e: Exception) {
            throw RuntimeException("Failed to fetch models: ${e.message}", e)
        }
    }
}

