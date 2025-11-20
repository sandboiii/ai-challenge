package xyz.sandboiii.agentcooper.data.worker

import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import xyz.sandboiii.agentcooper.domain.model.ScheduledTask
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduledTaskWorkManager @Inject constructor(
    private val workManager: WorkManager
) {
    companion object {
        private const val TAG = "ScheduledTaskWorkManager"
        private const val MIN_WORKMANAGER_INTERVAL_MINUTES = 15L
    }

    fun scheduleTask(task: ScheduledTask): Result<Unit> {
        return try {
            if (task.intervalMinutes < MIN_WORKMANAGER_INTERVAL_MINUTES) {
                Log.w(TAG, "Task interval ${task.intervalMinutes} minutes is less than WorkManager minimum ${MIN_WORKMANAGER_INTERVAL_MINUTES} minutes. Task will be handled by foreground service.")
                return Result.success(Unit)
            }

            val workRequest = PeriodicWorkRequestBuilder<ScheduledTaskWorker>(
                task.intervalMinutes,
                TimeUnit.MINUTES
            )
                .setInputData(
                    workDataOf(ScheduledTaskWorker.KEY_TASK_ID to task.id)
                )
                .build()

            val workName = "scheduled_task_${task.id}"
            workManager.enqueueUniquePeriodicWork(
                workName,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )

            Log.d(TAG, "Scheduled task: ${task.name} with interval ${task.intervalMinutes} minutes")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule task", e)
            Result.failure(e)
        }
    }

    fun cancelTask(taskId: String): Result<Unit> {
        return try {
            val workName = "scheduled_task_$taskId"
            workManager.cancelUniqueWork(workName)
            Log.d(TAG, "Cancelled task: $taskId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel task", e)
            Result.failure(e)
        }
    }

    suspend fun rescheduleAllTasks(tasks: List<ScheduledTask>): Result<Unit> {
        return try {
            tasks.forEach { task ->
                if (task.enabled && task.intervalMinutes >= MIN_WORKMANAGER_INTERVAL_MINUTES) {
                    scheduleTask(task)
                }
            }
            Log.d(TAG, "Rescheduled ${tasks.size} tasks")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reschedule tasks", e)
            Result.failure(e)
        }
    }
}
