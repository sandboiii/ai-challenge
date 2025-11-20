package xyz.sandboiii.agentcooper.domain.usecase

import android.content.Context
import android.content.Intent
import xyz.sandboiii.agentcooper.data.service.ScheduledTaskForegroundService
import xyz.sandboiii.agentcooper.data.worker.ScheduledTaskWorkManager
import xyz.sandboiii.agentcooper.domain.repository.ScheduledTaskRepository
import javax.inject.Inject

class DeleteScheduledTaskUseCase @Inject constructor(
    private val taskRepository: ScheduledTaskRepository,
    private val workManager: ScheduledTaskWorkManager,
    private val context: Context
) {
    suspend operator fun invoke(taskId: String): Result<Unit> {
        // Get task to determine scheduling method
        val task = taskRepository.getTask(taskId)
        
        // Cancel scheduling
        if (task != null) {
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

        // Delete from repository
        return taskRepository.deleteTask(taskId)
    }
}
