package xyz.sandboiii.agentcooper.util

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesManager @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        private val API_KEY = stringPreferencesKey(Constants.PREF_API_KEY)
        private val SELECTED_MODEL = stringPreferencesKey(Constants.PREF_SELECTED_MODEL)
    }
    
    val apiKey: Flow<String?> = dataStore.data.map { it[API_KEY] }
    val selectedModel: Flow<String?> = dataStore.data.map { it[SELECTED_MODEL] }
    
    suspend fun setApiKey(key: String) {
        dataStore.edit { it[API_KEY] = key }
    }
    
    suspend fun setSelectedModel(modelId: String) {
        dataStore.edit { it[SELECTED_MODEL] = modelId }
    }
    
    suspend fun getApiKey(): String? {
        return dataStore.data.first()[API_KEY]
    }
}

