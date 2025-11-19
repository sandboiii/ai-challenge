package xyz.sandboiii.agentcooper.data.remote.dto

import kotlinx.serialization.Serializable
import xyz.sandboiii.agentcooper.data.remote.mcp.OpenAITool

@Serializable
data class OpenRouterRequest(
    val model: String,
    val messages: List<OpenRouterMessage>,
    val stream: Boolean = true,
    val temperature: Double? = null,
    val usage: UsageRequest? = null,
    val tools: List<OpenAITool>? = null,
    val tool_choice: String? = null // "auto", "none", or "required"
)

@Serializable
data class UsageRequest(
    val include: Boolean // No default value to ensure it's always serialized
)

@Serializable
data class OpenRouterMessage(
    val role: String,
    val content: String? = null,
    val tool_calls: List<ToolCall>? = null,
    val tool_call_id: String? = null
)

