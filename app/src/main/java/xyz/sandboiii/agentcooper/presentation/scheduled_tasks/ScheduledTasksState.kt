package xyz.sandboiii.agentcooper.presentation.scheduled_tasks

import xyz.sandboiii.agentcooper.domain.model.ScheduledTask

sealed class ScheduledTasksState {
    object Loading : ScheduledTasksState()
    data class Success(val tasks: List<ScheduledTask>, val error: String? = null) : ScheduledTasksState()
    data class Error(val message: String) : ScheduledTasksState()
}
