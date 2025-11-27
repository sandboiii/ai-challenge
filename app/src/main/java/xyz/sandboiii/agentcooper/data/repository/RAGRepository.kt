package xyz.sandboiii.agentcooper.data.repository

import android.util.Log
import xyz.sandboiii.agentcooper.data.remote.api.RAGApiService
import xyz.sandboiii.agentcooper.data.remote.api.AugmentRequest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data class representing the result of RAG augmentation
 */
data class RAGAugmentationResult(
    val augmentedPrompt: String,
    val contextChunks: List<String>? = null
)

/**
 * Repository for RAG (Retrieval-Augmented Generation) operations.
 * Handles query augmentation with fallback to original query if API fails.
 */
@Singleton
class RAGRepository @Inject constructor(
    private val ragApiService: RAGApiService
) {
    
    companion object {
        private const val TAG = "RAGRepository"
    }
    
    /**
     * Gets an augmented prompt for the given query.
     * If the RAG API call fails (network error, 5xx, or request exception),
     * returns the original query as a fallback to prevent chat session from breaking.
     * 
     * @param rawQuery The original user query
     * @param k Optional number of context chunks to retrieve (default: 3)
     * @return RAGAugmentationResult containing the augmented prompt and context chunks, or original query if augmentation fails
     */
    suspend fun getAugmentedPrompt(rawQuery: String, k: Int = 5): RAGAugmentationResult {
        return try {
            val request = AugmentRequest(query = rawQuery, k = k)
            val response = ragApiService.augmentQuery(request)
            Log.d(TAG, "Query augmented successfully. Original: ${response.original_query}, Context chunks: ${response.context_chunks.size}")
            
            // Build the augmented prompt from context chunks
            val augmentedPrompt = buildAugmentedPrompt(
                originalQuery = response.original_query,
                contextChunks = response.context_chunks
            )
            
            RAGAugmentationResult(
                augmentedPrompt = augmentedPrompt,
                contextChunks = response.context_chunks
            )
        } catch (e: Exception) {
            // Critical fallback: return original query to prevent chat session from breaking
            Log.w(TAG, "RAG augmentation failed, using original query as fallback", e)
            RAGAugmentationResult(
                augmentedPrompt = rawQuery,
                contextChunks = null
            )
        }
    }
    
    /**
     * Builds an augmented prompt from context chunks and original query.
     * Format: Context section with numbered chunks, followed by the question and instruction.
     * 
     * @param originalQuery The original user query
     * @param contextChunks List of context chunks retrieved from the vector store
     * @return Formatted prompt string with context and question
     */
    private fun buildAugmentedPrompt(originalQuery: String, contextChunks: List<String>): String {
        if (contextChunks.isEmpty()) {
            return originalQuery
        }
        
        val contextSection = buildString {
            append("Контекст:\n")
            contextChunks.forEachIndexed { index, chunk ->
                append("[Контекст ${index + 1}]: $chunk\n\n")
            }
        }
        
        return buildString {
            append(contextSection.trimEnd())
            append("\n\nВопрос: $originalQuery\n\n")
            append("Пожалуйста, ответь на вопрос на основе предоставленного контекста.")
        }
    }
    
    /**
     * Checks if the RAG API server is healthy and ready.
     * 
     * @return true if server is healthy, false otherwise
     */
    suspend fun checkHealth(): Boolean {
        return try {
            val health = ragApiService.checkHealth()
            health.status == "healthy" && health.vectorstore_loaded && health.embeddings_loaded
        } catch (e: Exception) {
            Log.w(TAG, "Health check failed", e)
            false
        }
    }
}

