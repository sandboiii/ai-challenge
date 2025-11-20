package xyz.sandboiii.agentcooper.data.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import xyz.sandboiii.agentcooper.data.local.storage.SessionFileStorage
import xyz.sandboiii.agentcooper.data.remote.api.ChatApi
import xyz.sandboiii.agentcooper.data.remote.api.ChatMessageDto
import xyz.sandboiii.agentcooper.data.remote.api.ToolCallInfo
import xyz.sandboiii.agentcooper.data.remote.mcp.toOpenAITool
import xyz.sandboiii.agentcooper.domain.model.ChatMessage
import xyz.sandboiii.agentcooper.domain.model.MessageRole
import xyz.sandboiii.agentcooper.domain.model.ScheduledTask
import xyz.sandboiii.agentcooper.domain.model.ToolCall
import xyz.sandboiii.agentcooper.domain.model.ToolResult
import xyz.sandboiii.agentcooper.domain.repository.McpRepository
import xyz.sandboiii.agentcooper.domain.repository.ScheduledTaskRepository
import xyz.sandboiii.agentcooper.domain.repository.SessionRepository
import xyz.sandboiii.agentcooper.util.PreferencesManager
import java.util.UUID
import xyz.sandboiii.agentcooper.presentation.MainActivity
import xyz.sandboiii.agentcooper.util.NotificationManager as AppNotificationManager
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@AndroidEntryPoint
class ScheduledTaskForegroundService : Service() {

    companion object {
        private const val TAG = "ScheduledTaskForegroundService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "scheduled_tasks_foreground"
        private const val CHANNEL_NAME = "Scheduled Tasks Service"
        private const val MIN_WORKMANAGER_INTERVAL_MINUTES = 15L
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = false
    }

    @Inject
    lateinit var chatApi: ChatApi

    @Inject
    lateinit var taskRepository: ScheduledTaskRepository

    @Inject
    lateinit var mcpRepository: McpRepository

    @Inject
    lateinit var sessionRepository: SessionRepository

    @Inject
    lateinit var sessionFileStorage: SessionFileStorage

    @Inject
    lateinit var preferencesManager: PreferencesManager

    @Inject
    lateinit var notificationManager: AppNotificationManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val activeJobs = ConcurrentHashMap<String, Job>()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START_TASK" -> {
                val taskId = intent.getStringExtra("task_id")
                if (taskId != null) {
                    startTask(taskId)
                }
            }
            "STOP_TASK" -> {
                val taskId = intent.getStringExtra("task_id")
                if (taskId != null) {
                    stopTask(taskId)
                }
            }
            "STOP_ALL" -> {
                stopAllTasks()
                stopSelf()
            }
        }

        startForeground(NOTIFICATION_ID, createForegroundNotification())
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                android.app.NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background service for scheduled AI tasks"
            }
            val systemNotificationManager = getSystemService(android.app.NotificationManager::class.java)
            systemNotificationManager.createNotificationChannel(channel)
        }
    }

    private fun createForegroundNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Scheduled AI Tasks")
            .setContentText("Running scheduled tasks")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun startTask(taskId: String) {
        if (activeJobs.containsKey(taskId)) {
            Log.d(TAG, "Task already running: $taskId")
            return
        }

        val job = serviceScope.launch {
            while (isActive) {
                try {
                    val task = taskRepository.getTask(taskId)
                    if (task == null || !task.enabled) {
                        Log.d(TAG, "Task not found or disabled: $taskId")
                        break
                    }

                    if (task.intervalMinutes >= MIN_WORKMANAGER_INTERVAL_MINUTES) {
                        Log.d(TAG, "Task interval is >= 15 minutes, should use WorkManager: $taskId")
                        break
                    }

                    // Execute task
                    executeTask(task)

                    // Wait for next interval
                    delay(task.intervalMinutes * 60 * 1000)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Error in task loop: $taskId", e)
                    // Wait for interval before retrying on error (or 1 minute minimum)
                    val task = taskRepository.getTask(taskId)
                    val delayMs = if (task != null) {
                        (task.intervalMinutes * 60 * 1000).coerceAtLeast(60000)
                    } else {
                        60000L
                    }
                    delay(delayMs)
                }
            }
            activeJobs.remove(taskId)
            checkAndStopService()
        }

        activeJobs[taskId] = job
        Log.d(TAG, "Started task: $taskId")
    }

    private suspend fun executeTask(task: ScheduledTask) {
        try {
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
                    Log.d(TAG, "Service received tool calls: ${toolCalls.size}")
                    accumulatedToolCalls.addAll(toolCalls.map {
                        xyz.sandboiii.agentcooper.domain.model.ToolCall(it.id, it.name, it.arguments)
                    })
                }
            ).collect { chunk ->
                chunk.content?.let { content ->
                    accumulatedContent += content
                }
            }

            // Execute tool calls iteratively - AI can make multiple rounds of tool calls
            var currentMessages = messages.toMutableList()
            var roundNumber = 1
            val maxRounds = 5 // Prevent infinite loops
            
            while (accumulatedToolCalls.isNotEmpty() && roundNumber <= maxRounds) {
                Log.d(TAG, "Service tool calling round $roundNumber: Executing ${accumulatedToolCalls.size} tool calls")
                
                // Execute each tool call via MCP
                val currentRoundToolResults = mutableListOf<ToolResult>()
                
                for (toolCall in accumulatedToolCalls) {
                    try {
                        val argumentsJson: JsonElement? = try {
                            json.parseToJsonElement(toolCall.arguments)
                        } catch (e: Exception) {
                            Log.e(TAG, "Service: Failed to parse tool arguments for ${toolCall.name}", e)
                            val errorResult = ToolResult(toolCall.id, toolCall.name, "Error: Failed to parse arguments: ${e.message}", true)
                            currentRoundToolResults.add(errorResult)
                            toolResults.add(errorResult)
                            continue
                        }

                        val result = mcpRepository.callTool(toolCall.name, argumentsJson)
                        result.fold(
                            onSuccess = { resultText ->
                                val successResult = ToolResult(toolCall.id, toolCall.name, resultText, false)
                                currentRoundToolResults.add(successResult)
                                toolResults.add(successResult)
                            },
                            onFailure = { error ->
                                val errorResult = ToolResult(toolCall.id, toolCall.name, "Error: ${error.message}", true)
                                currentRoundToolResults.add(errorResult)
                                toolResults.add(errorResult)
                            }
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Service: Exception during tool execution for ${toolCall.name}", e)
                        val errorResult = ToolResult(toolCall.id, toolCall.name, "Error: ${e.message}", true)
                        currentRoundToolResults.add(errorResult)
                        toolResults.add(errorResult)
                    }
                }

                // Send tool results back to AI model and get response (which may contain more tool calls)
                if (currentRoundToolResults.isNotEmpty()) {
                    Log.d(TAG, "Service round $roundNumber: Sending ${currentRoundToolResults.size} tool results back to AI model")
                    val messagesWithToolResults = currentMessages.toMutableList().apply {
                        add(ChatMessageDto(
                            role = "assistant",
                            content = "",
                            toolCalls = accumulatedToolCalls.map { ToolCallInfo(it.id, it.name, it.arguments) }
                        ))
                        currentRoundToolResults.forEach { toolResult ->
                            add(ChatMessageDto(
                                role = "tool",
                                content = toolResult.result,
                                toolCallId = toolResult.toolCallId
                            ))
                        }
                    }

                    // Clear accumulated tool calls for next round
                    accumulatedToolCalls.clear()
                    var roundContent = ""
                    
                    chatApi.sendMessage(
                        messages = messagesWithToolResults,
                        model = task.modelId,
                        stream = true,
                        onMetadata = null,
                        tools = mcpTools,
                        onToolCalls = { newToolCalls ->
                            Log.d(TAG, "Service round $roundNumber: Received ${newToolCalls.size} new tool calls")
                            accumulatedToolCalls.addAll(newToolCalls.map {
                                xyz.sandboiii.agentcooper.domain.model.ToolCall(it.id, it.name, it.arguments)
                            })
                        }
                    ).collect { chunk ->
                        chunk.content?.let { content ->
                            roundContent += content
                        }
                        
                        // Handle new tool calls in this round
                        chunk.toolCalls?.let { newToolCalls ->
                            val domainToolCalls = newToolCalls.map { toolCall ->
                                xyz.sandboiii.agentcooper.domain.model.ToolCall(
                                    id = toolCall.id,
                                    name = toolCall.name,
                                    arguments = toolCall.arguments
                                )
                            }
                            accumulatedToolCalls.addAll(domainToolCalls)
                            Log.d(TAG, "Service round $roundNumber: Added ${domainToolCalls.size} tool calls from chunk")
                        }
                    }
                    
                    accumulatedContent += roundContent
                    currentMessages = messagesWithToolResults
                    roundNumber++
                    
                    // If no new tool calls were received, break the loop
                    if (accumulatedToolCalls.isEmpty()) {
                        Log.d(TAG, "Service: No more tool calls received, ending tool calling loop")
                        break
                    }
                } else {
                    break
                }
            }
            
            if (roundNumber > maxRounds) {
                Log.w(TAG, "Service: Tool calling stopped after $maxRounds rounds to prevent infinite loop")
                accumulatedContent += "\n\n[Note: Tool calling stopped after $maxRounds rounds to prevent infinite loop]"
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
        } catch (e: Exception) {
            Log.e(TAG, "Task execution failed: ${task.name}", e)
            notificationManager.showTaskErrorNotification(
                taskId = task.id,
                taskName = task.name,
                error = e.message ?: "Unknown error"
            )
        }
    }

    private fun stopTask(taskId: String) {
        activeJobs[taskId]?.cancel()
        activeJobs.remove(taskId)
        Log.d(TAG, "Stopped task: $taskId")
        checkAndStopService()
    }

    private fun stopAllTasks() {
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        Log.d(TAG, "Stopped all tasks")
    }

    private fun checkAndStopService() {
        if (activeJobs.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        activeJobs.clear()
    }
}
