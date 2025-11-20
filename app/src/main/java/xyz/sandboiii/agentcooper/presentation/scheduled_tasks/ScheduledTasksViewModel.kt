package xyz.sandboiii.agentcooper.presentation.scheduled_tasks

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import xyz.sandboiii.agentcooper.data.remote.api.ChatApi
import xyz.sandboiii.agentcooper.data.remote.api.ModelDto
import xyz.sandboiii.agentcooper.data.remote.mcp.McpServerInfo
import xyz.sandboiii.agentcooper.domain.model.ScheduledTask
import xyz.sandboiii.agentcooper.domain.repository.McpRepository
import xyz.sandboiii.agentcooper.domain.repository.ScheduledTaskRepository
import xyz.sandboiii.agentcooper.domain.usecase.CreateScheduledTaskUseCase
import xyz.sandboiii.agentcooper.domain.usecase.DeleteScheduledTaskUseCase
import xyz.sandboiii.agentcooper.domain.usecase.ToggleScheduledTaskUseCase
import xyz.sandboiii.agentcooper.domain.usecase.UpdateScheduledTaskUseCase
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ScheduledTasksViewModel @Inject constructor(
    private val createTaskUseCase: CreateScheduledTaskUseCase,
    private val updateTaskUseCase: UpdateScheduledTaskUseCase,
    private val deleteTaskUseCase: DeleteScheduledTaskUseCase,
    private val toggleTaskUseCase: ToggleScheduledTaskUseCase,
    private val taskRepository: ScheduledTaskRepository,
    private val mcpRepository: McpRepository,
    private val chatApi: ChatApi
) : ViewModel() {

    companion object {
        private const val TAG = "ScheduledTasksViewModel"
    }

    private val _state = MutableStateFlow<ScheduledTasksState>(ScheduledTasksState.Loading)
    val state: StateFlow<ScheduledTasksState> = _state.asStateFlow()

    // Form state
    val taskName = MutableStateFlow("")
    val systemPrompt = MutableStateFlow("")
    val userPrompt = MutableStateFlow("")
    val modelId = MutableStateFlow<String?>(null)
    val intervalMinutes = MutableStateFlow("15")
    val enabled = MutableStateFlow(true)

    // Editing state
    val editingTaskId = MutableStateFlow<String?>(null)

    // Models list
    private val _models = MutableStateFlow<List<ModelDto>>(emptyList())
    val models: StateFlow<List<ModelDto>> = _models.asStateFlow()

    // MCP servers
    val mcpServers: StateFlow<List<McpServerInfo>> = mcpRepository.servers.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    // Selected MCP server IDs
    val selectedMcpServerIds = MutableStateFlow<Set<String>>(emptySet())

    init {
        loadModels()
        observeTasks()
    }

    private fun observeTasks() {
        viewModelScope.launch {
            taskRepository.tasks
                .map { tasks ->
                    ScheduledTasksState.Success(tasks = tasks)
                }
                .collect { newState ->
                    val currentState = _state.value
                    // Preserve error if it exists
                    if (currentState is ScheduledTasksState.Success && currentState.error != null) {
                        _state.value = (newState as ScheduledTasksState.Success).copy(error = currentState.error)
                    } else {
                        _state.value = newState
                    }
                }
        }
    }

    fun loadModels() {
        viewModelScope.launch {
            try {
                _models.value = chatApi.getModels()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load models", e)
            }
        }
    }

    fun createTask() {
        val name = taskName.value.trim()
        val sysPrompt = systemPrompt.value.trim()
        val usrPrompt = userPrompt.value.trim()
        val model = modelId.value
        val interval = intervalMinutes.value.toLongOrNull()

        if (name.isEmpty()) {
            setError("Task name cannot be empty")
            return
        }
        if (sysPrompt.isEmpty()) {
            setError("System prompt cannot be empty")
            return
        }
        if (usrPrompt.isEmpty()) {
            setError("User prompt cannot be empty")
            return
        }
        if (model == null) {
            setError("Please select a model")
            return
        }
        if (interval == null || interval < 1) {
            setError("Interval must be at least 1 minute")
            return
        }

        viewModelScope.launch {
            try {
                val task = ScheduledTask(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    systemPrompt = sysPrompt,
                    userPrompt = usrPrompt,
                    modelId = model,
                    intervalMinutes = interval,
                    enabled = enabled.value,
                    createdAt = System.currentTimeMillis(),
                    mcpServerIds = selectedMcpServerIds.value.toList()
                )

                val result = createTaskUseCase(task)
                result.fold(
                    onSuccess = {
                        clearForm()
                        clearError()
                    },
                    onFailure = { error ->
                        setError(error.message ?: "Failed to create task")
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Exception creating task", e)
                setError(e.message ?: "Failed to create task")
            }
        }
    }

    fun updateTask(taskId: String) {
        val name = taskName.value.trim()
        val sysPrompt = systemPrompt.value.trim()
        val usrPrompt = userPrompt.value.trim()
        val model = modelId.value
        val interval = intervalMinutes.value.toLongOrNull()

        if (name.isEmpty()) {
            setError("Task name cannot be empty")
            return
        }
        if (sysPrompt.isEmpty()) {
            setError("System prompt cannot be empty")
            return
        }
        if (usrPrompt.isEmpty()) {
            setError("User prompt cannot be empty")
            return
        }
        if (model == null) {
            setError("Please select a model")
            return
        }
        if (interval == null || interval < 1) {
            setError("Interval must be at least 1 minute")
            return
        }

        viewModelScope.launch {
            try {
                val currentState = _state.value
                if (currentState !is ScheduledTasksState.Success) {
                    setError("Cannot update task: invalid state")
                    return@launch
                }

                val existingTask = currentState.tasks.find { it.id == taskId }
                    ?: run {
                        setError("Task not found")
                        return@launch
                    }

                val task = existingTask.copy(
                    name = name,
                    systemPrompt = sysPrompt,
                    userPrompt = usrPrompt,
                    modelId = model,
                    intervalMinutes = interval,
                    enabled = enabled.value,
                    mcpServerIds = selectedMcpServerIds.value.toList()
                )

                val result = updateTaskUseCase(task)
                result.fold(
                    onSuccess = {
                        clearForm()
                        clearError()
                    },
                    onFailure = { error ->
                        setError(error.message ?: "Failed to update task")
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Exception updating task", e)
                setError(e.message ?: "Failed to update task")
            }
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            try {
                val result = deleteTaskUseCase(taskId)
                result.fold(
                    onSuccess = { clearError() },
                    onFailure = { error ->
                        setError(error.message ?: "Failed to delete task")
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Exception deleting task", e)
                setError(e.message ?: "Failed to delete task")
            }
        }
    }

    fun toggleTask(taskId: String, enabled: Boolean) {
        viewModelScope.launch {
            try {
                val result = toggleTaskUseCase(taskId, enabled)
                result.fold(
                    onSuccess = { clearError() },
                    onFailure = { error ->
                        setError(error.message ?: "Failed to toggle task")
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Exception toggling task", e)
                setError(e.message ?: "Failed to toggle task")
            }
        }
    }

    fun startEditing(task: ScheduledTask) {
        editingTaskId.value = task.id
        taskName.value = task.name
        systemPrompt.value = task.systemPrompt
        userPrompt.value = task.userPrompt
        modelId.value = task.modelId
        intervalMinutes.value = task.intervalMinutes.toString()
        enabled.value = task.enabled
        selectedMcpServerIds.value = task.mcpServerIds.toSet()
    }

    fun cancelEditing() {
        editingTaskId.value = null
        clearForm()
    }

    private fun clearForm() {
        taskName.value = ""
        systemPrompt.value = ""
        userPrompt.value = ""
        modelId.value = null
        intervalMinutes.value = "15"
        enabled.value = true
        editingTaskId.value = null
        selectedMcpServerIds.value = emptySet()
    }

    private fun setError(message: String) {
        val currentState = _state.value
        if (currentState is ScheduledTasksState.Success) {
            _state.value = currentState.copy(error = message)
        } else {
            _state.value = ScheduledTasksState.Error(message)
        }
    }

    fun clearError() {
        val currentState = _state.value
        if (currentState is ScheduledTasksState.Success) {
            _state.value = currentState.copy(error = null)
        } else if (currentState is ScheduledTasksState.Error) {
            _state.value = ScheduledTasksState.Loading
        }
    }
}
