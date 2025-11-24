package xyz.sandboiii.agentcooper.data.repository

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import xyz.sandboiii.agentcooper.data.remote.mcp.*
import xyz.sandboiii.agentcooper.domain.repository.McpRepository
import xyz.sandboiii.agentcooper.util.PreferencesManager
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class McpServerConfig(
    val id: String,
    val name: String,
    val url: String,
    val authorizationToken: String? = null
)

@Singleton
class McpRepositoryImpl @Inject constructor(
    private val preferencesManager: PreferencesManager
) : McpRepository {
    
    companion object {
        private const val TAG = "McpRepositoryImpl"
        private const val PREF_MCP_SERVERS = "mcp_servers"
    }
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = false
    }
    
    private val clients = mutableMapOf<String, McpClient>()
    private val _servers = MutableStateFlow<List<McpServerInfo>>(emptyList())
    
    init {
        // Load persisted servers on init
        CoroutineScope(Dispatchers.IO).launch {
            loadPersistedServers()
        }
    }
    
    override val servers: Flow<List<McpServerInfo>> = _servers.asStateFlow()
    
    override val allTools: Flow<List<McpTool>> = servers.map { serverList ->
        serverList.flatMap { it.tools }
    }
    
    private suspend fun loadPersistedServers() {
        try {
            val serversJson = preferencesManager.getMcpServers()
            if (serversJson.isNullOrBlank()) {
                return
            }
            
            val configs = json.decodeFromString<List<McpServerConfig>>(serversJson)
            val serverInfos = configs.map { config ->
                McpServerInfo(
                    id = config.id,
                    name = config.name,
                    url = config.url,
                    state = McpConnectionState.DISCONNECTED,
                    tools = emptyList(),
                    authorizationToken = config.authorizationToken
                )
            }
            _servers.value = serverInfos
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load persisted servers", e)
        }
    }
    
    private suspend fun saveServers() {
        try {
            val configs = _servers.value.map { server ->
                McpServerConfig(
                    id = server.id,
                    name = server.name,
                    url = server.url,
                    authorizationToken = server.authorizationToken
                )
            }
            val jsonString = json.encodeToString(configs)
            preferencesManager.setMcpServers(jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save servers", e)
        }
    }
    
    private fun updateServer(serverId: String, update: (McpServerInfo) -> McpServerInfo) {
        _servers.value = _servers.value.map { server ->
            if (server.id == serverId) {
                update(server)
            } else {
                server
            }
        }
    }
    
    override suspend fun connectServer(url: String, name: String?, authorizationToken: String?): Result<String> {
        return try {
            // Check if server already exists
            val existingServer = _servers.value.find { it.url == url }
            val serverId = existingServer?.id ?: UUID.randomUUID().toString()
            val serverName = name ?: existingServer?.name ?: "MCP Server"
            val token = authorizationToken?.takeIf { it.isNotBlank() } ?: existingServer?.authorizationToken
            
            // If server doesn't exist, add it
            if (existingServer == null) {
                val newServer = McpServerInfo(
                    id = serverId,
                    name = serverName,
                    url = url,
                    state = McpConnectionState.CONNECTING,
                    tools = emptyList(),
                    authorizationToken = token
                )
                _servers.value = _servers.value + newServer
                saveServers()
            } else {
                // Update existing server with new token if provided, or keep existing token
                updateServer(serverId) { 
                    it.copy(
                        state = McpConnectionState.CONNECTING,
                        authorizationToken = token ?: it.authorizationToken
                    )
                }
            }
            
            // Create or get client (recreate if token changed)
            val currentServer = _servers.value.find { it.id == serverId }
            val existingClient = clients[serverId]
            
            // If token changed, recreate client
            val finalClient = if (existingClient != null && currentServer?.authorizationToken != token) {
                existingClient.close()
                clients.remove(serverId)
                McpClient(url, "AgentCooper", "1.0.0", token).also { clients[serverId] = it }
            } else {
                clients.getOrPut(serverId) {
                    McpClient(url, "AgentCooper", "1.0.0", token)
                }
            }
            
            // Connect
            finalClient.connect()
            
            // Wait a bit for connection
            kotlinx.coroutines.delay(1000)
            
            // Check connection state
            val connectionState = finalClient.connectionState.value
            if (connectionState == McpConnectionState.CONNECTED) {
                // List tools
                val tools = try {
                    finalClient.listTools()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to list tools", e)
                    emptyList()
                }
                
                updateServer(serverId) {
                    it.copy(
                        state = McpConnectionState.CONNECTED,
                        tools = tools,
                        error = null,
                        authorizationToken = token ?: it.authorizationToken
                    )
                }
                
                Result.success(serverId)
            } else {
                val error = finalClient.error.value ?: "Connection failed"
                updateServer(serverId) {
                    it.copy(
                        state = McpConnectionState.ERROR,
                        error = error
                    )
                }
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect server", e)
            Result.failure(e)
        }
    }
    
    override suspend fun disconnectServer(serverId: String) {
        try {
            clients[serverId]?.disconnect()
            clients.remove(serverId)
            
            updateServer(serverId) {
                it.copy(
                    state = McpConnectionState.DISCONNECTED,
                    error = null
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to disconnect server", e)
        }
    }
    
    override suspend fun updateServer(serverId: String, url: String, name: String?, authorizationToken: String?): Result<Unit> {
        return try {
            val existingServer = _servers.value.find { it.id == serverId }
            if (existingServer == null) {
                return Result.failure(Exception("Server not found: $serverId"))
            }
            
            val urlChanged = existingServer.url != url
            val tokenChanged = existingServer.authorizationToken != authorizationToken
            
            // If URL or token changed, disconnect and recreate client
            if (urlChanged || tokenChanged) {
                clients[serverId]?.disconnect()
                clients.remove(serverId)
            }
            
            // Update server info
            val updatedName = name?.takeIf { it.isNotBlank() } ?: existingServer.name
            val updatedToken = authorizationToken?.takeIf { it.isNotBlank() } ?: existingServer.authorizationToken
            
            updateServer(serverId) {
                it.copy(
                    url = url,
                    name = updatedName,
                    authorizationToken = updatedToken,
                    state = if (urlChanged || tokenChanged) McpConnectionState.DISCONNECTED else it.state,
                    error = null
                )
            }
            
            // Save updated configuration
            saveServers()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update server", e)
            Result.failure(e)
        }
    }
    
    override suspend fun deleteServer(serverId: String) {
        try {
            // Disconnect first if connected
            clients[serverId]?.disconnect()
            clients.remove(serverId)
            
            // Remove from list
            _servers.value = _servers.value.filter { it.id != serverId }
            
            // Save updated list
            saveServers()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete server", e)
            throw e
        }
    }
    
    override suspend fun getServer(serverId: String): McpServerInfo? {
        return _servers.value.find { it.id == serverId }
    }
    
    override suspend fun getTool(toolName: String): McpTool? {
        return _servers.value
            .flatMap { it.tools }
            .find { it.name == toolName }
    }
    
    override suspend fun callTool(toolName: String, arguments: JsonElement?): Result<String> {
        return try {
            // Find the tool and its server
            val server = _servers.value.find { server ->
                server.tools.any { it.name == toolName }
            }
            
            if (server == null) {
                return Result.failure(Exception("Tool not found: $toolName"))
            }
            
            val client = clients[server.id]
            if (client == null || server.state != McpConnectionState.CONNECTED) {
                return Result.failure(Exception("Server not connected: ${server.name}"))
            }
            
            val result = client.callTool(toolName, arguments)
            
            // Convert result to string
            val resultText = result.content.joinToString("\n") { content ->
                when (content.type) {
                    "text" -> content.text ?: ""
                    else -> content.data ?: ""
                }
            }
            
            if (result.isError) {
                Result.failure(Exception(resultText))
            } else {
                Result.success(resultText)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to call tool: $toolName", e)
            Result.failure(e)
        }
    }
    
    override suspend fun refreshTools(serverId: String) {
        try {
            val client = clients[serverId]
            val server = _servers.value.find { it.id == serverId }
            
            if (client == null || server == null) {
                return
            }
            
            if (server.state != McpConnectionState.CONNECTED) {
                return
            }
            
            val tools = client.listTools()
            updateServer(serverId) {
                it.copy(tools = tools)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh tools", e)
        }
    }
}

