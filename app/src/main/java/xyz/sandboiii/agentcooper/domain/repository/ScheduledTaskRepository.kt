package xyz.sandboiii.agentcooper.domain.repository

import kotlinx.coroutines.flow.Flow
import xyz.sandboiii.agentcooper.domain.model.ScheduledTask

interface ScheduledTaskRepository {
    val tasks: Flow<List<ScheduledTask>>
    
    suspend fun createTask(task: ScheduledTask): Result<String>
    suspend fun updateTask(task: ScheduledTask): Result<Unit>
    suspend fun deleteTask(taskId: String): Result<Unit>
    suspend fun getTask(taskId: String): ScheduledTask?
    suspend fun enableTask(taskId: String): Result<Unit>
    suspend fun disableTask(taskId: String): Result<Unit>
}
