package xyz.sandboiii.agentcooper.domain.repository

import kotlinx.coroutines.flow.Flow
import xyz.sandboiii.agentcooper.data.remote.mcp.McpConnectionState
import xyz.sandboiii.agentcooper.data.remote.mcp.McpServerInfo
import xyz.sandboiii.agentcooper.data.remote.mcp.McpTool

interface McpRepository {
    val servers: Flow<List<McpServerInfo>>
    val allTools: Flow<List<McpTool>>
    
    suspend fun connectServer(url: String, name: String? = null, authorizationToken: String? = null): Result<String> // Returns server ID
    suspend fun updateServer(serverId: String, url: String, name: String?, authorizationToken: String?): Result<Unit>
    suspend fun disconnectServer(serverId: String)
    suspend fun deleteServer(serverId: String)
    suspend fun getServer(serverId: String): McpServerInfo?
    suspend fun getTool(toolName: String): McpTool?
    suspend fun callTool(toolName: String, arguments: kotlinx.serialization.json.JsonElement?): Result<String> // Returns result as string
    suspend fun refreshTools(serverId: String)
}

