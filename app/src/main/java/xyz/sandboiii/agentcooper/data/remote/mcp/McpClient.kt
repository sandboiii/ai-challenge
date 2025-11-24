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
import kotlinx.serialization.json.buildJsonObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class McpClient(
    private val serverUrl: String,
    private val clientName: String = "AgentCooper",
    private val clientVersion: String = "1.0.0",
    private val authorizationToken: String? = null
) {
    companion object {
        private const val TAG = "McpClient"
    }
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true // Must be true to include jsonrpc: "2.0" in JSON-RPC requests
    }
    
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(json)
        }
        install(Logging) {
            level = LogLevel.ALL // Log all requests/responses to debug header issues
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
            
            // According to MCP spec: https://modelcontextprotocol.io/specification/2025-06-18/basic/transports#session-management
            // The server assigns the session ID during initialization, NOT the client.
            // The client should NOT send a session ID in the initial initialize request.
            // The server will return the session ID in the Mcp-Session-Id header of the InitializeResult response.
            // Only after receiving the session ID from the server should we include it in subsequent requests.
            
            // Clear any existing session ID to start fresh
            sessionId = null
            
            // Step 1: Initialize via POST as per spec (WITHOUT session ID header)
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
                        sessionId?.let { header("mcp-session-id", it) }
                        // header("MCP-Protocol-Version", protocolVersion)
                        authorizationToken?.let { header(HttpHeaders.Authorization, "Bearer $it") }
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
            // According to MCP spec, initialize should include clientInfo and protocolVersion
            // The Notion server's isInitializeRequest() checks for method === 'initialize'
            // According to MCP SDK's isInitializeRequest() function:
            // - params must be an object and not null
            // - params.capabilities must be an object and not null (can be empty {})
            // See: https://github.com/modelcontextprotocol/typescript-sdk/blob/main/src/types.ts
            val params = buildJsonObject {
                put("clientInfo", buildJsonObject {
                    put("name", clientName)
                    put("version", clientVersion)
                })
                // Include protocolVersion as per MCP spec
                put("protocolVersion", protocolVersion)
                // capabilities must be present as an object (not null) for isInitializeRequest() to pass
                put("capabilities", buildJsonObject {
                    // Empty capabilities object is valid
                })
            }
            
            val result = sendRequest<InitializeResult>(
                method = "initialize",
                params = params,
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
            // Notifications must NOT have an id field, and params must be an object (not null)
            val notification = JsonRpcNotification(
                method = "notifications/initialized",
                params = buildJsonObject {} // Empty object, not null
            )
            
            val response = client.post(serverUrl) {
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                header(HttpHeaders.Accept, "application/json, text/event-stream")
                header("MCP-Protocol-Version", protocolVersion)
                sessionId?.let { header("mcp-session-id", it) }
                authorizationToken?.let { header(HttpHeaders.Authorization, "Bearer $it") }
                setBody(json.encodeToString(JsonRpcNotification.serializer(), notification))
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
            // params must be an object (not null) according to MCP SDK validation
            val result = sendRequest<ToolsListResult>(
                method = "tools/list",
                params = buildJsonObject {} // Empty object, not null
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
        // MCP SDK requires params to be an object (not null) - use empty object if null
        val requestParams = params ?: buildJsonObject {}
        val request = JsonRpcRequest(
            id = id,
            method = method,
            params = requestParams
        )
        
        val deferred = CompletableDeferred<JsonRpcResponse>()
        pendingRequests[id] = deferred
        
        try {
            val requestBody = json.encodeToString(JsonRpcRequest.serializer(), request)
            Log.d(TAG, "Sending request: $requestBody")
            
            // Build headers list for logging
            val headersList = mutableListOf<String>()
            
            // Send request via POST as per MCP spec
            // According to Notion MCP server docs: https://github.com/makenotion/notion-mcp-server
            // Required headers: Authorization (Bearer token), Content-Type, mcp-session-id
            val response = client.post(serverUrl) {
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                headersList.add("Content-Type: application/json")
                
                header(HttpHeaders.Accept, "application/json, text/event-stream")
                headersList.add("Accept: application/json, text/event-stream")
                
                // According to MCP spec: https://modelcontextprotocol.io/specification/2025-06-18/basic/transports
                // Client MUST include MCP-Protocol-Version header on all requests
                header("MCP-Protocol-Version", protocolVersion)
                headersList.add("MCP-Protocol-Version: $protocolVersion")
                
                // According to MCP spec: https://modelcontextprotocol.io/specification/2025-06-18/basic/transports#session-management
                // Session ID is assigned by the SERVER during initialization, not the client.
                // For the initial initialize request, we should NOT send a session ID.
                // For subsequent requests (after initialization), we MUST include the session ID
                // that was returned by the server in the InitializeResult response.
                // Notion server checks req.headers['mcp-session-id'] (lowercase) - use lowercase to match
                sessionId?.let { session ->
                    // Include session ID for requests AFTER initialization
                    // Use lowercase 'mcp-session-id' to match Notion server's header check
                    header("mcp-session-id", session)
                    headersList.add("mcp-session-id: $session")
                    Log.d(TAG, "Including session ID in request header 'mcp-session-id': $session")
                } ?: run {
                    // No session ID is expected for the initial initialize request
                    // Notion server expects: !sessionId && isInitializeRequest(req.body)
                    if (isInitialization) {
                        Log.d(TAG, "Initial request - no session ID header (server will assign one)")
                    } else {
                        Log.w(TAG, "WARNING: No session ID available for non-initialization request!")
                    }
                }
                
                // Authorization header (if token provided)
                authorizationToken?.let { token ->
                    header(HttpHeaders.Authorization, "Bearer $token")
                    headersList.add("Authorization: Bearer ***")
                }
                
                setBody(requestBody)
            }
            
            Log.d(TAG, "Request headers sent: ${headersList.joinToString(", ")}")
            Log.d(TAG, "Response status: ${response.status.value} ${response.status.description}")
            Log.d(TAG, "Response headers: ${response.headers.entries()}")
            
            // Check for session ID in response (during initialization)
            // According to MCP spec, server assigns session ID and returns it in Mcp-Session-Id header
            // See: https://modelcontextprotocol.io/specification/2025-06-18/basic/transports#session-management
            // Notion server returns it in 'mcp-session-id' header (lowercase)
            if (isInitialization) {
                // Try header name variations (Notion server uses lowercase 'mcp-session-id')
                val receivedSessionId = response.headers["mcp-session-id"]
                    ?: response.headers["Mcp-Session-Id"]
                    ?: response.headers["MCP-Session-Id"]
                if (receivedSessionId != null) {
                    sessionId = receivedSessionId
                    Log.d(TAG, "Received session ID from server in InitializeResult: $sessionId")
                    Log.d(TAG, "Will include this session ID in all subsequent requests")
                } else {
                    Log.d(TAG, "Server did not return a session ID - session management not required")
                }
            }
            
            val contentType = response.headers[HttpHeaders.ContentType] ?: ""
            
            return when {
                // Single JSON response
                contentType.contains("application/json") -> {
                    if (response.status.value !in 200..299) {
                        // Try to read error response body
                        try {
                            val errorBody = response.body<String>()
                            Log.e(TAG, "Error response body: $errorBody")
                            throw Exception("HTTP ${response.status.value}: ${response.status.description}\nResponse: $errorBody")
                        } catch (e: Exception) {
                            if (e.message?.contains("HTTP") == true) {
                                throw e
                            }
                            Log.e(TAG, "Failed to read error response body", e)
                            throw Exception("HTTP ${response.status.value}: ${response.status.description}")
                        }
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
                    header("mcp-session-id", sid)
                    // header("MCP-Protocol-Version", protocolVersion)
                    authorizationToken?.let { header(HttpHeaders.Authorization, "Bearer $it") }
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

