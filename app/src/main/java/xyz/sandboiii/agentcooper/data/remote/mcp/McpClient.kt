package xyz.sandboiii.agentcooper.data.remote.mcp

import android.util.Log
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class McpClient(
    private val serverUrl: String,
    private val clientName: String = "AgentCooper",
    private val clientVersion: String = "1.0.0"
) {
    companion object {
        private const val TAG = "McpClient"
    }
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = false
    }
    
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(json)
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
            requestTimeout = 0 // No timeout for SSE connections
        }
    }
    
    private val requestIdCounter = AtomicInteger(1)
    private val pendingRequests = ConcurrentHashMap<String, CompletableDeferred<JsonRpcResponse>>()
    private var sseStreamJob: Job? = null
    
    private val _connectionState = MutableStateFlow(McpConnectionState.DISCONNECTED)
    val connectionState: StateFlow<McpConnectionState> = _connectionState.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    private var isInitialized = false
    private var sessionId: String? = null
    private val protocolVersion = "2025-06-18"
    
    suspend fun connect() {
        if (_connectionState.value == McpConnectionState.CONNECTED) {
            Log.d(TAG, "Already connected")
            return
        }
        
        _connectionState.value = McpConnectionState.CONNECTING
        _error.value = null
        
        try {
            Log.d(TAG, "Connecting to MCP server: $serverUrl")
            
            // Step 1: Initialize via POST as per spec
            initialize()
            
            // Step 2: Send InitializedNotification
            sendInitializedNotification()
            
            // Step 3: Optionally open SSE stream for server-to-client messages
            startSSEStream()
            
            _connectionState.value = McpConnectionState.CONNECTED
            Log.d(TAG, "Connected and initialized MCP server")
        } catch (e: Exception) {
            Log.e(TAG, "Connection error", e)
            _connectionState.value = McpConnectionState.ERROR
            _error.value = e.message ?: "Connection failed"
            isInitialized = false
            throw e
        }
    }
    
    private suspend fun startSSEStream() {
        // Optionally open SSE stream for listening to server messages
        // This is not required for basic operation, but allows server-to-client notifications
        try {
            sseStreamJob = CoroutineScope(Dispatchers.IO).launch {
                try {
                    client.prepareGet(serverUrl) {
                        header(HttpHeaders.Accept, "text/event-stream")
                        header(HttpHeaders.CacheControl, "no-cache")
                        sessionId?.let { header("Mcp-Session-Id", it) }
                        header("MCP-Protocol-Version", protocolVersion)
                    }.execute { httpResponse ->
                        if (httpResponse.status.value == 405) {
                            // Server doesn't support GET/SSE stream, that's okay
                            Log.d(TAG, "Server doesn't support SSE stream (405), continuing without it")
                            return@execute
                        }
                        
                        if (httpResponse.status.value !in 200..299) {
                            Log.w(TAG, "SSE stream failed: HTTP ${httpResponse.status.value}")
                            return@execute
                        }
                        
                        val contentType = httpResponse.headers[HttpHeaders.ContentType]
                        if (contentType?.contains("text/event-stream") != true) {
                            Log.w(TAG, "SSE stream returned non-SSE content type: $contentType")
                            return@execute
                        }
                        
                        Log.d(TAG, "SSE stream opened for server messages")
                        
                        // Start reading SSE stream
                        val channel: ByteReadChannel = httpResponse.body()
                        val buffer = StringBuilder()
                        
                        while (isActive) {
                            val line = channel.readUTF8Line()
                            if (line == null) {
                                delay(100)
                                continue
                            }
                            
                            if (line.isEmpty()) {
                                // Empty line indicates end of event
                                if (buffer.isNotEmpty()) {
                                    processSSEEvent(buffer.toString())
                                    buffer.clear()
                                }
                            } else if (line.startsWith("data: ")) {
                                buffer.append(line.removePrefix("data: "))
                            } else if (line.startsWith("event: ")) {
                                val eventType = line.removePrefix("event: ")
                                Log.d(TAG, "SSE event: $eventType")
                            } else if (line.startsWith("id: ")) {
                                // Handle event ID for resumability
                                val eventId = line.removePrefix("id: ")
                                Log.d(TAG, "SSE event ID: $eventId")
                            }
                        }
                    }
                } catch (e: Exception) {
                    // SSE stream errors are non-fatal
                    Log.w(TAG, "SSE stream error (non-fatal)", e)
                }
            }
        } catch (e: Exception) {
            // SSE stream is optional, so we continue even if it fails
            Log.w(TAG, "Failed to start SSE stream (non-fatal)", e)
        }
    }
    
    private suspend fun processSSEEvent(data: String) {
        try {
            val response = json.decodeFromString<JsonRpcResponse>(data)
            Log.d(TAG, "Received JSON-RPC response: id=${response.id}")
            
            response.id?.let { id ->
                pendingRequests[id]?.complete(response)
                pendingRequests.remove(id)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse SSE event", e)
        }
    }
    
    private suspend fun initialize() {
        if (isInitialized) {
            Log.d(TAG, "Already initialized")
            return
        }
        
        try {
            val params = InitializeParams(
                clientInfo = ClientInfo(
                    name = clientName,
                    version = clientVersion
                )
            )
            
            val result = sendRequest<InitializeResult>(
                method = "initialize",
                params = json.encodeToJsonElement(params),
                isInitialization = true
            )
            
            if (result != null) {
                isInitialized = true
                Log.d(TAG, "Initialized MCP server: ${result.serverInfo.name} v${result.serverInfo.version}")
            } else {
                throw Exception("Initialize failed: no response")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Initialize failed", e)
            _error.value = "Initialize failed: ${e.message}"
            throw e
        }
    }
    
    private suspend fun sendInitializedNotification() {
        try {
            val notification = JsonRpcRequest(
                id = null, // Notifications have no id
                method = "notifications/initialized",
                params = null
            )
            
            val response = client.post(serverUrl) {
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                header(HttpHeaders.Accept, "application/json, text/event-stream")
                sessionId?.let { header("Mcp-Session-Id", it) }
                header("MCP-Protocol-Version", protocolVersion)
                setBody(json.encodeToString(JsonRpcRequest.serializer(), notification))
            }
            
            // For notifications, server should return 202 Accepted
            if (response.status.value == 202 || response.status.value in 200..299) {
                Log.d(TAG, "Initialized notification sent successfully")
            } else {
                Log.w(TAG, "Unexpected status for initialized notification: ${response.status.value}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send initialized notification", e)
            // Non-fatal, continue
        }
    }
    
    suspend fun listTools(): List<McpTool> {
        ensureConnected()
        
        try {
            val result = sendRequest<ToolsListResult>(
                method = "tools/list",
                params = null
            )
            
            return result?.tools ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list tools", e)
            throw e
        }
    }
    
    suspend fun callTool(name: String, arguments: JsonElement? = null): ToolCallResult {
        ensureConnected()
        
        try {
            val params = ToolCallParams(
                name = name,
                arguments = arguments
            )
            
            val result = sendRequest<ToolCallResult>(
                method = "tools/call",
                params = json.encodeToJsonElement(params)
            )
            
            return result ?: ToolCallResult(
                content = listOf(ToolCallContent(
                    type = "text",
                    text = "Tool call failed: no response"
                )),
                isError = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to call tool: $name", e)
            return ToolCallResult(
                content = listOf(ToolCallContent(
                    type = "text",
                    text = "Tool call failed: ${e.message}"
                )),
                isError = true
            )
        }
    }
    
    private inline suspend fun <reified T> sendRequest(
        method: String,
        params: JsonElement?,
        isInitialization: Boolean = false
    ): T? {
        val id = requestIdCounter.getAndIncrement().toString()
        val request = JsonRpcRequest(
            id = id,
            method = method,
            params = params
        )
        
        val deferred = CompletableDeferred<JsonRpcResponse>()
        pendingRequests[id] = deferred
        
        try {
            // Send request via POST as per MCP spec
            val response = client.post(serverUrl) {
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                header(HttpHeaders.Accept, "application/json, text/event-stream")
                sessionId?.let { header("Mcp-Session-Id", it) }
                header("MCP-Protocol-Version", protocolVersion)
                setBody(json.encodeToString(JsonRpcRequest.serializer(), request))
            }
            
            // Check for session ID in response (during initialization)
            if (isInitialization) {
                val receivedSessionId = response.headers["Mcp-Session-Id"]
                if (receivedSessionId != null) {
                    sessionId = receivedSessionId
                    Log.d(TAG, "Received session ID: $sessionId")
                }
            }
            
            val contentType = response.headers[HttpHeaders.ContentType] ?: ""
            
            return when {
                // Single JSON response
                contentType.contains("application/json") -> {
                    if (response.status.value !in 200..299) {
                        throw Exception("HTTP ${response.status.value}: ${response.status.description}")
                    }
                    
                    val jsonRpcResponse = response.body<JsonRpcResponse>()
                    val error = jsonRpcResponse.error
                    
                    if (error != null) {
                        throw Exception("JSON-RPC error: ${error.message}")
                    }
                    
                    jsonRpcResponse.result?.let { result ->
                        json.decodeFromJsonElement(result)
                    }
                }
                
                // SSE stream response
                contentType.contains("text/event-stream") -> {
                    if (response.status.value !in 200..299) {
                        throw Exception("HTTP ${response.status.value}: ${response.status.description}")
                    }
                    
                    // Read SSE stream to get the response
                    val channel: ByteReadChannel = response.body()
                    val buffer = StringBuilder()
                    var foundResponse = false
                    var jsonRpcResponse: JsonRpcResponse? = null
                    
                    while (!foundResponse) {
                        val line = channel.readUTF8Line()
                        if (line == null) {
                            delay(10)
                            continue
                        }
                        
                        if (line.isEmpty()) {
                            // Empty line indicates end of event
                            if (buffer.isNotEmpty()) {
                                try {
                                    val eventData = buffer.toString()
                                    val parsed = json.decodeFromString<JsonRpcResponse>(eventData)
                                    
                                    // Check if this is the response we're waiting for
                                    if (parsed.id == id) {
                                        jsonRpcResponse = parsed
                                        foundResponse = true
                                    } else {
                                        // Process other messages
                                        processSSEEvent(eventData)
                                    }
                                } catch (e: Exception) {
                                    Log.w(TAG, "Failed to parse SSE event", e)
                                }
                                buffer.clear()
                            }
                        } else if (line.startsWith("data: ")) {
                            buffer.append(line.removePrefix("data: "))
                        } else if (line.startsWith("event: ")) {
                            val eventType = line.removePrefix("event: ")
                            Log.d(TAG, "SSE event type: $eventType")
                        }
                    }
                    
                    val error = jsonRpcResponse?.error
                    if (error != null) {
                        throw Exception("JSON-RPC error: ${error.message}")
                    }
                    
                    jsonRpcResponse?.result?.let { result ->
                        json.decodeFromJsonElement(result)
                    }
                }
                
                else -> {
                    throw Exception("Unexpected content type: $contentType")
                }
            }
        } catch (e: Exception) {
            pendingRequests.remove(id)
            deferred.completeExceptionally(e)
            throw e
        } finally {
            pendingRequests.remove(id)
        }
    }
    
    private suspend fun ensureConnected() {
        if (_connectionState.value != McpConnectionState.CONNECTED) {
            throw IllegalStateException("Not connected to MCP server")
        }
        if (!isInitialized) {
            throw IllegalStateException("MCP server not initialized")
        }
    }
    
    suspend fun disconnect() {
        // Optionally send DELETE to terminate session
        sessionId?.let { sid ->
            try {
                client.delete(serverUrl) {
                    header("Mcp-Session-Id", sid)
                    header("MCP-Protocol-Version", protocolVersion)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to send session termination", e)
                // Non-fatal
            }
        }
        
        sseStreamJob?.cancel()
        sseStreamJob = null
        pendingRequests.clear()
        isInitialized = false
        sessionId = null
        _connectionState.value = McpConnectionState.DISCONNECTED
        _error.value = null
        Log.d(TAG, "Disconnected from MCP server")
    }
    
    fun close() {
        runBlocking {
            disconnect()
        }
    }
}

