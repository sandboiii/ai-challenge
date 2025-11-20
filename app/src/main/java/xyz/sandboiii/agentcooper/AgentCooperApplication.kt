package xyz.sandboiii.agentcooper

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.EntryPointAccessors
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import xyz.sandboiii.agentcooper.data.service.ScheduledTaskForegroundService
import xyz.sandboiii.agentcooper.data.worker.ScheduledTaskWorkManager
import xyz.sandboiii.agentcooper.domain.repository.ScheduledTaskRepository
import xyz.sandboiii.agentcooper.util.PreferencesManager

@HiltAndroidApp
class AgentCooperApplication : Application() {
    
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize default API key if not set
        // Use EntryPoint to access Hilt dependencies before Activity creation
        applicationScope.launch {
            try {
                val entryPoint = EntryPointAccessors.fromApplication(
                    applicationContext,
                    AppEntryPoint::class.java
                )
                entryPoint.preferencesManager().initializeDefaults()
                
                // Reschedule all enabled tasks after app start/reboot
                val tasks = entryPoint.taskRepository().tasks.first()
                val enabledTasks = tasks.filter { it.enabled }
                
                // Schedule WorkManager tasks (>= 15 minutes)
                entryPoint.workManager().rescheduleAllTasks(enabledTasks)
                
                // Start foreground service for short-interval tasks (< 15 minutes)
                val shortIntervalTasks = enabledTasks.filter { it.intervalMinutes < 15 }
                if (shortIntervalTasks.isNotEmpty()) {
                    shortIntervalTasks.forEach { task ->
                        val intent = Intent(applicationContext, ScheduledTaskForegroundService::class.java).apply {
                            action = "START_TASK"
                            putExtra("task_id", task.id)
                        }
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            startForegroundService(intent)
                        } else {
                            startService(intent)
                        }
                    }
                }
            } catch (e: Exception) {
                // If initialization fails, getApiKey() will handle it lazily
            }
        }
    }
    
    @dagger.hilt.EntryPoint
    @dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
    interface AppEntryPoint {
        fun preferencesManager(): PreferencesManager
        fun taskRepository(): ScheduledTaskRepository
        fun workManager(): ScheduledTaskWorkManager
    }
}

