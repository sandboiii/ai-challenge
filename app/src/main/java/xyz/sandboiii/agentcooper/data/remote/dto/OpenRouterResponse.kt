package xyz.sandboiii.agentcooper.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class OpenRouterResponse(
    val id: String? = null,
    val model: String? = null,
    val choices: List<Choice>? = null,
    val usage: Usage? = null, // Token usage information
    val error: Error? = null
)

@Serializable
data class Usage(
    val prompt_tokens: Int? = null,
    val completion_tokens: Int? = null,
    val total_tokens: Int? = null
)

@Serializable
data class Choice(
    val delta: Delta? = null,
    val message: Delta? = null,
    val finish_reason: String? = null
)

@Serializable
data class Delta(
    val role: String? = null,
    val content: String? = null
)

@Serializable
data class Error(
    val message: String,
    val type: String? = null,
    val code: Int? = null
)

