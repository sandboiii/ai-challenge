package xyz.sandboiii.agentcooper.di

import android.content.Context
import androidx.work.WorkManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import xyz.sandboiii.agentcooper.data.worker.ScheduledTaskWorkManager
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object WorkerModule {
    
    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager {
        return WorkManager.getInstance(context)
    }
    
    @Provides
    @Singleton
    fun provideScheduledTaskWorkManager(
        workManager: WorkManager
    ): ScheduledTaskWorkManager {
        return ScheduledTaskWorkManager(workManager)
    }
}
