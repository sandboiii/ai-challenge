package xyz.sandboiii.agentcooper.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import xyz.sandboiii.agentcooper.data.remote.api.ChatApi
import xyz.sandboiii.agentcooper.data.remote.api.OpenRouterApi
import xyz.sandboiii.agentcooper.util.Constants
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = Constants.PREF_DATASTORE_NAME)

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    
    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return context.dataStore
    }
    
    @Provides
    @Singleton
    fun provideChatApi(
        preferencesManager: xyz.sandboiii.agentcooper.util.PreferencesManager
    ): ChatApi {
        return OpenRouterApi(preferencesManager)
    }
}

