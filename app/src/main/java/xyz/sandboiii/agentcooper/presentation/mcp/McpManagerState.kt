package xyz.sandboiii.agentcooper.presentation.mcp

import xyz.sandboiii.agentcooper.data.remote.mcp.McpServerInfo

sealed class McpManagerState {
    data object Loading : McpManagerState()
    data class Success(
        val servers: List<McpServerInfo>,
        val error: String? = null
    ) : McpManagerState()
    data class Error(
        val message: String
    ) : McpManagerState()
}




