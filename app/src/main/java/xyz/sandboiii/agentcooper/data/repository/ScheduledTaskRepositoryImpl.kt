package xyz.sandboiii.agentcooper.data.repository

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import xyz.sandboiii.agentcooper.domain.model.ScheduledTask
import xyz.sandboiii.agentcooper.domain.repository.ScheduledTaskRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduledTaskRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : ScheduledTaskRepository {

    companion object {
        private const val TAG = "ScheduledTaskRepositoryImpl"
        private val SCHEDULED_TASKS = stringPreferencesKey("scheduled_tasks")
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = false
    }

    override val tasks: Flow<List<ScheduledTask>> = dataStore.data.map { preferences ->
        val tasksJson = preferences[SCHEDULED_TASKS]
        if (tasksJson.isNullOrBlank()) {
            emptyList()
        } else {
            try {
                json.decodeFromString<List<ScheduledTask>>(tasksJson)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decode scheduled tasks", e)
                emptyList()
            }
        }
    }

    override suspend fun createTask(task: ScheduledTask): Result<String> {
        return try {
            dataStore.edit { preferences ->
                val currentTasksJson = preferences[SCHEDULED_TASKS]
                val currentTasks = if (currentTasksJson.isNullOrBlank()) {
                    emptyList()
                } else {
                    try {
                        json.decodeFromString<List<ScheduledTask>>(currentTasksJson)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to decode existing tasks", e)
                        emptyList()
                    }
                }
                
                val updatedTasks = currentTasks + task
                preferences[SCHEDULED_TASKS] = json.encodeToString(updatedTasks)
            }
            Result.success(task.id)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create task", e)
            Result.failure(e)
        }
    }

    override suspend fun updateTask(task: ScheduledTask): Result<Unit> {
        return try {
            dataStore.edit { preferences ->
                val currentTasksJson = preferences[SCHEDULED_TASKS]
                val currentTasks = if (currentTasksJson.isNullOrBlank()) {
                    emptyList()
                } else {
                    try {
                        json.decodeFromString<List<ScheduledTask>>(currentTasksJson)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to decode existing tasks", e)
                        emptyList()
                    }
                }
                
                val updatedTasks = currentTasks.map { if (it.id == task.id) task else it }
                preferences[SCHEDULED_TASKS] = json.encodeToString(updatedTasks)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update task", e)
            Result.failure(e)
        }
    }

    override suspend fun deleteTask(taskId: String): Result<Unit> {
        return try {
            dataStore.edit { preferences ->
                val currentTasksJson = preferences[SCHEDULED_TASKS]
                val currentTasks = if (currentTasksJson.isNullOrBlank()) {
                    emptyList()
                } else {
                    try {
                        json.decodeFromString<List<ScheduledTask>>(currentTasksJson)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to decode existing tasks", e)
                        emptyList()
                    }
                }
                
                val updatedTasks = currentTasks.filter { it.id != taskId }
                preferences[SCHEDULED_TASKS] = json.encodeToString(updatedTasks)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete task", e)
            Result.failure(e)
        }
    }

    override suspend fun getTask(taskId: String): ScheduledTask? {
        return try {
            val tasksJson = dataStore.data.map { it[SCHEDULED_TASKS] }.first()
            if (tasksJson.isNullOrBlank()) {
                null
            } else {
                val tasks = json.decodeFromString<List<ScheduledTask>>(tasksJson)
                tasks.find { it.id == taskId }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get task", e)
            null
        }
    }

    override suspend fun enableTask(taskId: String): Result<Unit> {
        return try {
            val task = getTask(taskId)
            if (task != null) {
                updateTask(task.copy(enabled = true))
            } else {
                Result.failure(Exception("Task not found"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enable task", e)
            Result.failure(e)
        }
    }

    override suspend fun disableTask(taskId: String): Result<Unit> {
        return try {
            val task = getTask(taskId)
            if (task != null) {
                updateTask(task.copy(enabled = false))
            } else {
                Result.failure(Exception("Task not found"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to disable task", e)
            Result.failure(e)
        }
    }
}
