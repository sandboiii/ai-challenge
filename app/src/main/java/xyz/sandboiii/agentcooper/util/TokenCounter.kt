package xyz.sandboiii.agentcooper.util

import android.util.Log
import com.aallam.ktoken.Encoding
import com.aallam.ktoken.Tokenizer
import xyz.sandboiii.agentcooper.data.remote.api.ChatMessageDto
import kotlinx.coroutines.runBlocking

object TokenCounter {
    private const val TAG = "TokenCounter"
    
    // Initialize tokenizer with CL100K_BASE encoding (used by GPT-4 and most OpenAI models)
    // On JVM, this uses LocalPbeLoader with FileSystem.RESOURCES by default
    // Tokenizer.of() is a suspend function, so we initialize it lazily
    // Note: libmbrainSDK warning can be ignored - ktoken will use fallback implementation
    private val tokenizer: Tokenizer? by lazy {
        try {
            runBlocking {
                Tokenizer.of(encoding = Encoding.CL100K_BASE)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize tokenizer, will use approximate counting", e)
            null
        }
    }
    
    /**
     * Count tokens for a list of chat messages.
     * Includes overhead for message formatting (role, content structure).
     * 
     * @param messages List of chat messages to count tokens for
     * @return Total token count including formatting overhead
     */
    suspend fun countTokens(messages: List<ChatMessageDto>): Int {
        val tokenizerInstance = tokenizer
        
        // If tokenizer failed to initialize, use approximate counting
        if (tokenizerInstance == null) {
            return approximateTokenCount(messages)
        }
        
        var totalTokens = 0
        
        try {
            for (message in messages) {
                // Count tokens for role (typically 1-2 tokens)
                val roleTokens = tokenizerInstance.encode(message.role).size
                
                // Count tokens for content
                val contentTokens = message.content?.let { tokenizerInstance.encode(it).size } ?: 0
                
                // Add overhead for message structure (typically 3-4 tokens per message)
                // This accounts for the JSON structure and formatting
                val messageOverhead = 4
                
                totalTokens += roleTokens + contentTokens + messageOverhead
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error counting tokens with tokenizer, using approximation", e)
            return approximateTokenCount(messages)
        }
        
        return totalTokens
    }
    
    /**
     * Count tokens for a single text string.
     * 
     * @param text Text to count tokens for
     * @return Token count
     */
    suspend fun countTokens(text: String): Int {
        val tokenizerInstance = tokenizer
        
        // If tokenizer failed to initialize, use approximate counting
        if (tokenizerInstance == null) {
            return approximateTokenCount(text)
        }
        
        return try {
            tokenizerInstance.encode(text).size
        } catch (e: Exception) {
            Log.w(TAG, "Error counting tokens with tokenizer, using approximation", e)
            approximateTokenCount(text)
        }
    }
    
    /**
     * Approximate token count when tokenizer is not available.
     * Uses a simple heuristic: ~4 characters per token for English/Russian text.
     * 
     * @param messages List of chat messages
     * @return Approximate token count
     */
    private fun approximateTokenCount(messages: List<ChatMessageDto>): Int {
        var totalChars = 0
        for (message in messages) {
            totalChars += message.role.length + (message.content?.length ?: 0)
            // Add overhead for message structure
            totalChars += 20 // Approximate overhead for JSON structure
        }
        // Approximate: ~4 characters per token
        return (totalChars / 4).coerceAtLeast(messages.size * 3)
    }
    
    /**
     * Approximate token count when tokenizer is not available.
     * Uses a simple heuristic: ~4 characters per token for English/Russian text.
     * 
     * @param text Text to count tokens for
     * @return Approximate token count
     */
    private fun approximateTokenCount(text: String): Int {
        // Approximate: ~4 characters per token
        return (text.length / 4).coerceAtLeast(1)
    }
}

