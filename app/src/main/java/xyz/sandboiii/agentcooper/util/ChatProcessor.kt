package xyz.sandboiii.agentcooper.util

import android.util.Log
import xyz.sandboiii.agentcooper.data.repository.RAGRepository
import xyz.sandboiii.agentcooper.data.repository.RAGAugmentationResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Processor class responsible for message processing and RAG integration.
 * This class intercepts user queries and augments them with RAG context if enabled.
 */
@Singleton
class ChatProcessor @Inject constructor(
    private val ragRepository: RAGRepository,
    private val preferencesManager: PreferencesManager
) {
    
    companion object {
        private const val TAG = "ChatProcessor"
    }
    
    /**
     * Gets the final prompt to send to the LLM.
     * If RAG is enabled, augments the query with context from the vector store.
     * If RAG is disabled or augmentation fails, returns the original query.
     * 
     * @param rawQuery The original user query
     * @return The final prompt (augmented if RAG enabled, otherwise original query)
     */
    suspend fun getFinalPrompt(rawQuery: String): String {
        val result = getFinalPromptWithContext(rawQuery)
        return result.augmentedPrompt
    }
    
    /**
     * Gets the final prompt with RAG context information.
     * If RAG is enabled, augments the query with context from the vector store.
     * If RAG is disabled or augmentation fails, returns the original query with no context.
     * 
     * @param rawQuery The original user query
     * @return RAGAugmentationResult containing the augmented prompt and context chunks
     */
    suspend fun getFinalPromptWithContext(rawQuery: String): RAGAugmentationResult {
        // 1. Check RAG Toggle State from DataStore
        val isRagEnabled = try {
            preferencesManager.getRagEnabled()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read RAG enabled state, defaulting to false", e)
            false
        }
        
        if (isRagEnabled) {
            // 2. Call RAG Repository (which handles the fallback)
            Log.d(TAG, "RAG enabled, augmenting query: $rawQuery")
            return ragRepository.getAugmentedPrompt(rawQuery)
        } else {
            // 3. RAG disabled, return original query immediately
            Log.d(TAG, "RAG disabled, using original query")
            return RAGAugmentationResult(
                augmentedPrompt = rawQuery,
                contextChunks = null
            )
        }
    }
}

