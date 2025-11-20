package xyz.sandboiii.agentcooper.domain.usecase

import android.content.Context
import android.content.Intent
import xyz.sandboiii.agentcooper.data.service.ScheduledTaskForegroundService
import xyz.sandboiii.agentcooper.data.worker.ScheduledTaskWorkManager
import xyz.sandboiii.agentcooper.domain.model.ScheduledTask
import xyz.sandboiii.agentcooper.domain.repository.ScheduledTaskRepository
import javax.inject.Inject

class CreateScheduledTaskUseCase @Inject constructor(
    private val taskRepository: ScheduledTaskRepository,
    private val workManager: ScheduledTaskWorkManager,
    private val context: Context
) {
    suspend operator fun invoke(task: ScheduledTask): Result<String> {
        // Create task in repository
        val createResult = taskRepository.createTask(task)
        if (createResult.isFailure) {
            return createResult
        }

        // Schedule task
        if (task.enabled) {
            if (task.intervalMinutes < 15) {
                // Use foreground service for short intervals
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
                // Use WorkManager for long intervals
                workManager.scheduleTask(task)
            }
        }

        return Result.success(task.id)
    }
}
