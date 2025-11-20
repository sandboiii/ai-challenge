package xyz.sandboiii.agentcooper.domain.usecase

import android.content.Context
import android.content.Intent
import xyz.sandboiii.agentcooper.data.service.ScheduledTaskForegroundService
import xyz.sandboiii.agentcooper.data.worker.ScheduledTaskWorkManager
import xyz.sandboiii.agentcooper.domain.repository.ScheduledTaskRepository
import javax.inject.Inject

class ToggleScheduledTaskUseCase @Inject constructor(
    private val taskRepository: ScheduledTaskRepository,
    private val workManager: ScheduledTaskWorkManager,
    private val context: Context
) {
    suspend operator fun invoke(taskId: String, enabled: Boolean): Result<Unit> {
        val task = taskRepository.getTask(taskId)
            ?: return Result.failure(Exception("Task not found"))

        // Update enabled status
        val updateResult = if (enabled) {
            taskRepository.enableTask(taskId)
        } else {
            taskRepository.disableTask(taskId)
        }

        if (updateResult.isFailure) {
            return updateResult
        }

        // Schedule or cancel based on enabled status
            if (enabled) {
                val updatedTask = task.copy(enabled = true)
                if (updatedTask.intervalMinutes < 15) {
                    val intent = Intent(context, ScheduledTaskForegroundService::class.java).apply {
                        action = "START_TASK"
                        putExtra("task_id", taskId)
                    }
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        context.startForegroundService(intent)
                    } else {
                        context.startService(intent)
                    }
            } else {
                workManager.scheduleTask(updatedTask)
            }
        } else {
            if (task.intervalMinutes < 15) {
                val intent = Intent(context, ScheduledTaskForegroundService::class.java).apply {
                    action = "STOP_TASK"
                    putExtra("task_id", taskId)
                }
                context.startService(intent)
            } else {
                workManager.cancelTask(taskId)
            }
        }

        return Result.success(Unit)
    }
}
