package xyz.sandboiii.agentcooper.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ScheduledTask(
    val id: String,
    val name: String,
    val systemPrompt: String,
    val userPrompt: String,
    val modelId: String,
    val intervalMinutes: Long, // Minimum 1 minute for testing, 15 minutes for WorkManager
    val enabled: Boolean,
    val createdAt: Long,
    val lastRunAt: Long? = null,
    val nextRunAt: Long? = null,
    val mcpServerIds: List<String> = emptyList(), // List of MCP server IDs to use for this task
    val sessionId: String? = null // Chat session ID to store task results (created automatically if null)
)
