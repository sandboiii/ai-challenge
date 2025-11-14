package xyz.sandboiii.agentcooper.util

import com.aallam.ktoken.Encoding
import com.aallam.ktoken.Tokenizer
import xyz.sandboiii.agentcooper.data.remote.api.ChatMessageDto
import kotlinx.coroutines.runBlocking

object TokenCounter {
    // Initialize tokenizer with CL100K_BASE encoding (used by GPT-4 and most OpenAI models)
    // On JVM, this uses LocalPbeLoader with FileSystem.RESOURCES by default
    // Tokenizer.of() is a suspend function, so we initialize it lazily
    private val tokenizer: Tokenizer by lazy {
        runBlocking {
            Tokenizer.of(encoding = Encoding.CL100K_BASE)
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
        var totalTokens = 0
        
        // Ensure tokenizer is initialized
        val tokenizerInstance = tokenizer
        
        for (message in messages) {
            // Count tokens for role (typically 1-2 tokens)
            val roleTokens = tokenizerInstance.encode(message.role).size
            
            // Count tokens for content
            val contentTokens = tokenizerInstance.encode(message.content).size
            
            // Add overhead for message structure (typically 3-4 tokens per message)
            // This accounts for the JSON structure and formatting
            val messageOverhead = 4
            
            totalTokens += roleTokens + contentTokens + messageOverhead
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
        // Ensure tokenizer is initialized
        val tokenizerInstance = tokenizer
        return tokenizerInstance.encode(text).size
    }
}

