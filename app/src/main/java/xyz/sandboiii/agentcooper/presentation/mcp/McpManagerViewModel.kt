package xyz.sandboiii.agentcooper.presentation.mcp

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import xyz.sandboiii.agentcooper.domain.repository.McpRepository
import javax.inject.Inject

@HiltViewModel
class McpManagerViewModel @Inject constructor(
    private val mcpRepository: McpRepository
) : ViewModel() {
    
    companion object {
        private const val TAG = "McpManagerViewModel"
    }
    
    private val _state = MutableStateFlow<McpManagerState>(McpManagerState.Loading)
    val state: StateFlow<McpManagerState> = _state.asStateFlow()
    
    val serverUrl = MutableStateFlow("")
    val serverName = MutableStateFlow("")
    
    init {
        observeServers()
    }
    
    private fun observeServers() {
        viewModelScope.launch {
            mcpRepository.servers
                .map { servers ->
                    McpManagerState.Success(servers = servers)
                }
                .collect { newState ->
                    val currentState = _state.value
                    // Preserve error if it exists
                    if (currentState is McpManagerState.Success && currentState.error != null) {
                        _state.value = (newState as McpManagerState.Success).copy(error = currentState.error)
                    } else {
                        _state.value = newState
                    }
                }
        }
    }
    
    fun connectServer() {
        val url = serverUrl.value.trim()
        if (url.isEmpty()) {
            _state.value = McpManagerState.Success(
                servers = (_state.value as? McpManagerState.Success)?.servers ?: emptyList(),
                error = "URL cannot be empty"
            )
            return
        }
        
        viewModelScope.launch {
            try {
                val name = serverName.value.trim().takeIf { it.isNotEmpty() }
                val result = mcpRepository.connectServer(url, name)
                
                result.fold(
                    onSuccess = {
                        // Clear input fields on success
                        serverUrl.value = ""
                        serverName.value = ""
                        // Clear error
                        val currentState = _state.value
                        if (currentState is McpManagerState.Success) {
                            _state.value = currentState.copy(error = null)
                        }
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Failed to connect server", error)
                        val currentState = _state.value
                        if (currentState is McpManagerState.Success) {
                            _state.value = currentState.copy(error = error.message ?: "Failed to connect")
                        } else {
                            _state.value = McpManagerState.Error(error.message ?: "Failed to connect")
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Exception connecting server", e)
                val currentState = _state.value
                if (currentState is McpManagerState.Success) {
                    _state.value = currentState.copy(error = e.message ?: "Failed to connect")
                } else {
                    _state.value = McpManagerState.Error(e.message ?: "Failed to connect")
                }
            }
        }
    }
    
    fun disconnectServer(serverId: String) {
        viewModelScope.launch {
            try {
                mcpRepository.disconnectServer(serverId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to disconnect server", e)
                val currentState = _state.value
                if (currentState is McpManagerState.Success) {
                    _state.value = currentState.copy(error = e.message ?: "Failed to disconnect")
                }
            }
        }
    }
    
    fun deleteServer(serverId: String) {
        viewModelScope.launch {
            try {
                mcpRepository.deleteServer(serverId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete server", e)
                val currentState = _state.value
                if (currentState is McpManagerState.Success) {
                    _state.value = currentState.copy(error = e.message ?: "Failed to delete server")
                }
            }
        }
    }
    
    fun refreshTools(serverId: String) {
        viewModelScope.launch {
            try {
                val server = mcpRepository.getServer(serverId)
                if (server == null) {
                    Log.w(TAG, "Server not found: $serverId")
                    return@launch
                }
                
                // Check if server is connected
                if (server.state == xyz.sandboiii.agentcooper.data.remote.mcp.McpConnectionState.CONNECTED) {
                    // Server is connected, refresh tools
                    mcpRepository.refreshTools(serverId)
                } else {
                    // Server is not connected, try to reconnect
                    Log.d(TAG, "Server not connected, attempting to reconnect: ${server.url}")
                    val result = mcpRepository.connectServer(server.url, server.name)
                    
                    result.fold(
                        onSuccess = {
                            Log.d(TAG, "Successfully reconnected to server: $serverId")
                        },
                        onFailure = { error ->
                            Log.e(TAG, "Failed to reconnect server", error)
                            val currentState = _state.value
                            if (currentState is McpManagerState.Success) {
                                _state.value = currentState.copy(error = "Failed to reconnect: ${error.message ?: "Unknown error"}")
                            }
                        }
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh/reconnect", e)
                val currentState = _state.value
                if (currentState is McpManagerState.Success) {
                    _state.value = currentState.copy(error = e.message ?: "Failed to refresh/reconnect")
                }
            }
        }
    }
    
    fun clearError() {
        val currentState = _state.value
        if (currentState is McpManagerState.Success) {
            _state.value = currentState.copy(error = null)
        }
    }
}

