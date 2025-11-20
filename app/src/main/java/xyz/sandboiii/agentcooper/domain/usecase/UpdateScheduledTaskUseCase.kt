package xyz.sandboiii.agentcooper.domain.usecase

import android.content.Context
import android.content.Intent
import xyz.sandboiii.agentcooper.data.service.ScheduledTaskForegroundService
import xyz.sandboiii.agentcooper.data.worker.ScheduledTaskWorkManager
import xyz.sandboiii.agentcooper.domain.model.ScheduledTask
import xyz.sandboiii.agentcooper.domain.repository.ScheduledTaskRepository
import javax.inject.Inject

class UpdateScheduledTaskUseCase @Inject constructor(
    private val taskRepository: ScheduledTaskRepository,
    private val workManager: ScheduledTaskWorkManager,
    private val context: Context
) {
    suspend operator fun invoke(task: ScheduledTask): Result<Unit> {
        // Get old task to check if we need to reschedule
        val oldTask = taskRepository.getTask(task.id)
        
        // Update task in repository
        val updateResult = taskRepository.updateTask(task)
        if (updateResult.isFailure) {
            return updateResult
        }

        // Cancel old scheduling
        if (oldTask != null) {
            if (oldTask.intervalMinutes < 15) {
                val intent = Intent(context, ScheduledTaskForegroundService::class.java).apply {
                    action = "STOP_TASK"
                    putExtra("task_id", task.id)
                }
                context.startService(intent)
            } else {
                workManager.cancelTask(task.id)
            }
        }

        // Schedule with new settings if enabled
        if (task.enabled) {
            if (task.intervalMinutes < 15) {
                val intent = Intent(context, ScheduledTaskForegroundService::class.java).apply {
                    action = "START_TASK"
                    putExtra("task_id", task.id)
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } else {
                workManager.scheduleTask(task)
            }
        }

        return Result.success(Unit)
    }
}
