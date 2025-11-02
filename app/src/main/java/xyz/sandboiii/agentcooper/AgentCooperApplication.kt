package xyz.sandboiii.agentcooper

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
                val preferencesManager = EntryPointAccessors.fromApplication(
                    applicationContext,
                    PreferencesManagerEntryPoint::class.java
                ).preferencesManager()
                preferencesManager.initializeDefaults()
            } catch (e: Exception) {
                // If initialization fails, getApiKey() will handle it lazily
            }
        }
    }
    
    @dagger.hilt.EntryPoint
    @dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
    interface PreferencesManagerEntryPoint {
        fun preferencesManager(): PreferencesManager
    }
}

