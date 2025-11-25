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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import xyz.sandboiii.agentcooper.util.Constants
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data class for RAG API health check response
 */
@Serializable
data class HealthResponse(
    val status: String,
    val vectorstore_loaded: Boolean,
    val embeddings_loaded: Boolean
)

/**
 * Data class for RAG API augment request
 */
@Serializable
data class AugmentRequest(
    val query: String,
    val k: Int = 3
)

/**
 * Data class for RAG API augment response
 */
@Serializable
data class AugmentResponse(
    val original_query: String,
    val context_chunks: List<String>,
    val suggested_prompt: String
)

/**
 * Data class for RAG API error response
 */
@Serializable
data class ErrorResponse(
    val detail: String
)

/**
 * Service class for interacting with the RAG API using Ktor Client
 */
@Singleton
class RAGApiService @Inject constructor() {
    
    companion object {
        private const val TAG = "RAGApiService"
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
        engine {
            connectTimeout = 30_000
            socketTimeout = 30_000
        }
    }
    
    /**
     * Checks the health status of the RAG API server
     * @return HealthResponse containing server status information
     * @throws Exception if the request fails
     */
    suspend fun checkHealth(): HealthResponse {
        return try {
            val response = client.get("${Constants.RAG_BASE_URL}/health") {
                contentType(ContentType.Application.Json)
            }
            response.body<HealthResponse>()
        } catch (e: Exception) {
            Log.e(TAG, "Health check failed", e)
            throw e
        }
    }
    
    /**
     * Augments a user query with relevant context from the vector store
     * @param request AugmentRequest containing the query and optional k parameter
     * @return AugmentResponse containing the original query, context chunks, and suggested prompt
     * @throws Exception if the request fails (network error, 5xx, or serialization issue)
     */
    suspend fun augmentQuery(request: AugmentRequest): AugmentResponse {
        return try {
            val response = client.post("${Constants.RAG_BASE_URL}/api/v1/augment") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            
            when (response.status.value) {
                in 200..299 -> {
                    response.body<AugmentResponse>()
                }
                503 -> {
                    val errorBody = try {
                        response.body<ErrorResponse>()
                    } catch (e: Exception) {
                        ErrorResponse("Vector store or embeddings not loaded")
                    }
                    Log.w(TAG, "Service unavailable: ${errorBody.detail}")
                    throw Exception("Service unavailable: ${errorBody.detail}")
                }
                in 500..599 -> {
                    val errorBody = try {
                        response.body<ErrorResponse>()
                    } catch (e: Exception) {
                        ErrorResponse("Internal server error")
                    }
                    Log.e(TAG, "Server error: ${errorBody.detail}")
                    throw Exception("Server error: ${errorBody.detail}")
                }
                else -> {
                    val errorMessage = "Unexpected status code: ${response.status.value}"
                    Log.e(TAG, errorMessage)
                    throw Exception(errorMessage)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to augment query: ${request.query}", e)
            throw e
        }
    }
}

