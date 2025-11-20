package xyz.sandboiii.agentcooper.data.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import xyz.sandboiii.agentcooper.data.local.storage.SessionFileStorage
import xyz.sandboiii.agentcooper.data.remote.api.ChatApi
import xyz.sandboiii.agentcooper.data.remote.api.ChatMessageDto
import xyz.sandboiii.agentcooper.data.remote.api.MessageChunk
import xyz.sandboiii.agentcooper.data.remote.api.ToolCallInfo
import xyz.sandboiii.agentcooper.data.remote.mcp.toOpenAITool
import xyz.sandboiii.agentcooper.domain.model.ChatMessage
import xyz.sandboiii.agentcooper.domain.model.MessageRole
import xyz.sandboiii.agentcooper.domain.model.ToolCall
import xyz.sandboiii.agentcooper.domain.model.ToolResult
import xyz.sandboiii.agentcooper.domain.repository.McpRepository
import xyz.sandboiii.agentcooper.domain.repository.ScheduledTaskRepository
import xyz.sandboiii.agentcooper.domain.repository.SessionRepository
import xyz.sandboiii.agentcooper.util.NotificationManager
import xyz.sandboiii.agentcooper.util.PreferencesManager
import java.util.UUID

@HiltWorker
class ScheduledTaskWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val chatApi: ChatApi,
    private val taskRepository: ScheduledTaskRepository,
    private val mcpRepository: McpRepository,
    private val sessionRepository: SessionRepository,
    private val sessionFileStorage: SessionFileStorage,
    private val preferencesManager: PreferencesManager,
    private val notificationManager: NotificationManager
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "ScheduledTaskWorker"
        const val KEY_TASK_ID = "task_id"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = false
    }

    override suspend fun doWork(): Result {
        val taskId = inputData.getString(KEY_TASK_ID)
            ?: return Result.failure(workDataOf("error" to "Missing task ID"))

        return try {
            val task = taskRepository.getTask(taskId)
            if (task == null) {
                Log.e(TAG, "Task not found: $taskId")
                return Result.failure(workDataOf("error" to "Task not found"))
            }

            if (!task.enabled) {
                Log.d(TAG, "Task is disabled: $taskId")
                return Result.success()
            }

            Log.d(TAG, "Executing scheduled task: ${task.name}")

            // Build messages
            val messages = listOf(
                ChatMessageDto(role = "system", content = task.systemPrompt),
                ChatMessageDto(role = "user", content = task.userPrompt)
            )

            // Get MCP tools from selected servers (ensure they're connected)
            val mcpTools = if (task.mcpServerIds.isNotEmpty()) {
                try {
                    val servers = mcpRepository.servers.first()
                    val selectedServerIds = task.mcpServerIds.toSet()
                    
                    // Ensure selected servers are connected
                    val selectedServers = servers.filter { selectedServerIds.contains(it.id) }
                    for (server in selectedServers) {
                        if (server.state != xyz.sandboiii.agentcooper.data.remote.mcp.McpConnectionState.CONNECTED) {
                            Log.d(TAG, "Server ${server.name} is not connected, attempting to connect...")
                            val connectResult = mcpRepository.connectServer(server.url, server.name)
                            connectResult.fold(
                                onSuccess = { 
                                    Log.d(TAG, "Successfully connected to server: ${server.name}")
                                },
                                onFailure = { error ->
                                    Log.e(TAG, "Failed to connect to server ${server.name}: ${error.message}")
                                }
                            )
                        }
                    }
                    
                    // Get updated server list after connection attempts
                    val updatedServers = mcpRepository.servers.first()
                    // Get tools directly from selected servers that are now connected
                    val toolsFromSelectedServers = updatedServers
                        .filter { 
                            selectedServerIds.contains(it.id) && 
                            it.state == xyz.sandboiii.agentcooper.data.remote.mcp.McpConnectionState.CONNECTED
                        }
                        .flatMap { it.tools }
                    
                    Log.d(TAG, "Selected ${selectedServerIds.size} MCP servers, found ${toolsFromSelectedServers.size} tools from connected servers")
                    
                    if (toolsFromSelectedServers.isNotEmpty()) {
                        toolsFromSelectedServers.map { it.toOpenAITool() }
                    } else {
                        Log.d(TAG, "No tools found from selected MCP servers (servers may not be connected)")
                        null
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to get MCP tools", e)
                    null
                }
            } else {
                Log.d(TAG, "No MCP servers selected for this task")
                null
            }

            // Call AI API and handle tool calls (using streaming like normal chat)
            var accumulatedContent = ""
            var accumulatedToolCalls = mutableListOf<xyz.sandboiii.agentcooper.domain.model.ToolCall>()
            var toolResults = mutableListOf<ToolResult>()

            // First API call: potentially with tools
            chatApi.sendMessage(
                messages = messages,
                model = task.modelId,
                stream = true, // Use streaming to detect tool calls
                onMetadata = null,
                tools = mcpTools,
                onToolCalls = { toolCalls ->
                    Log.d(TAG, "Worker received tool calls: ${toolCalls.size}")
                    accumulatedToolCalls.addAll(toolCalls.map {
                        xyz.sandboiii.agentcooper.domain.model.ToolCall(it.id, it.name, it.arguments)
                    })
                }
            ).collect { chunk ->
                chunk.content?.let { content ->
                    accumulatedContent += content
                }
            }

            // Execute tool calls if any
            if (accumulatedToolCalls.isNotEmpty()) {
                Log.d(TAG, "Worker executing ${accumulatedToolCalls.size} tool calls")
                for (toolCall in accumulatedToolCalls) {
                    try {
                        val argumentsJson: JsonElement? = try {
                            json.parseToJsonElement(toolCall.arguments)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to parse tool arguments for ${toolCall.name}", e)
                            null
                        }

                        val result = mcpRepository.callTool(toolCall.name, argumentsJson)
                        result.fold(
                            onSuccess = { resultText ->
                                toolResults.add(ToolResult(toolCall.id, toolCall.name, resultText, false))
                            },
                            onFailure = { error ->
                                toolResults.add(ToolResult(toolCall.id, toolCall.name, "Error: ${error.message}", true))
                            }
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Exception during tool execution for ${toolCall.name}", e)
                        toolResults.add(ToolResult(toolCall.id, toolCall.name, "Error: ${e.message}", true))
                    }
                }

                // Send tool results back to the AI model for a final response
                if (toolResults.isNotEmpty()) {
                    Log.d(TAG, "Worker sending ${toolResults.size} tool results back to AI model")
                    val messagesWithToolResults = messages.toMutableList().apply {
                        add(ChatMessageDto(
                            role = "assistant",
                            content = "",
                            toolCalls = accumulatedToolCalls.map { ToolCallInfo(it.id, it.name, it.arguments) }
                        ))
                        toolResults.forEach { toolResult ->
                            add(ChatMessageDto(
                                role = "tool",
                                content = toolResult.result,
                                toolCallId = toolResult.toolCallId
                            ))
                        }
                    }

                    var finalContent = ""
                    chatApi.sendMessage(
                        messages = messagesWithToolResults,
                        model = task.modelId,
                        stream = true,
                        onMetadata = null,
                        tools = mcpTools,
                        onToolCalls = null
                    ).collect { chunk ->
                        chunk.content?.let { content ->
                            finalContent += content
                        }
                    }
                    accumulatedContent = finalContent
                }
            }

            // Create a new session for this task execution
            val session = sessionRepository.createSession(task.modelId)
            val sessionId = session.id
            val storageLocation = preferencesManager.getStorageLocation()

            // Set session title to sessionId
            sessionRepository.updateSessionTitle(sessionId, sessionId)

            // Add user prompt and assistant response to session
            val userMessage = ChatMessage(
                id = UUID.randomUUID().toString(),
                content = task.userPrompt,
                role = MessageRole.USER,
                timestamp = System.currentTimeMillis(),
                sessionId = sessionId
            )
            sessionFileStorage.addMessageToSession(session, userMessage, storageLocation)

            val assistantMessage = ChatMessage(
                id = UUID.randomUUID().toString(),
                content = accumulatedContent,
                role = MessageRole.ASSISTANT,
                timestamp = System.currentTimeMillis(),
                sessionId = sessionId,
                modelId = task.modelId,
                toolCalls = if (accumulatedToolCalls.isNotEmpty()) accumulatedToolCalls else null,
                toolResults = if (toolResults.isNotEmpty()) toolResults else null
            )
            sessionFileStorage.addMessageToSession(session, assistantMessage, storageLocation)

            // Update task with last run time
            val now = System.currentTimeMillis()
            val nextRunAt = now + (task.intervalMinutes * 60 * 1000)
            taskRepository.updateTask(
                task.copy(
                    lastRunAt = now,
                    nextRunAt = nextRunAt
                )
            )

            // Show notification (wait for complete response before showing)
            notificationManager.showTaskResultNotification(
                taskId = task.id,
                taskName = task.name,
                result = accumulatedContent,
                sessionId = sessionId,
                modelId = task.modelId
            )

            Log.d(TAG, "Task completed successfully: ${task.name}")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Task execution failed", e)
            
            // Show error notification
            val task = taskRepository.getTask(taskId)
            if (task != null) {
                notificationManager.showTaskErrorNotification(
                    taskId = task.id,
                    taskName = task.name,
                    error = e.message ?: "Unknown error"
                )
            }
            
            // Return success so WorkManager retries at next interval
            Result.success()
        }
    }
}

