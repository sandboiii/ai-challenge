package xyz.sandboiii.agentcooper.util

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
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
        const val DEFAULT_API_KEY = "INSERT_YOUR_KEY"
        private val SYSTEM_PROMPT = stringPreferencesKey(Constants.PREF_SYSTEM_PROMPT)
        private val WELCOME_MESSAGE_ENABLED = booleanPreferencesKey(Constants.PREF_WELCOME_MESSAGE_ENABLED)
        private val TEMPERATURE = floatPreferencesKey(Constants.PREF_TEMPERATURE)
        private val TOKEN_THRESHOLD = intPreferencesKey(Constants.PREF_TOKEN_THRESHOLD)
        private val STORAGE_LOCATION = stringPreferencesKey(Constants.PREF_STORAGE_LOCATION)
    }
    
    val apiKey: Flow<String?> = dataStore.data.map { it[API_KEY] }
    val selectedModel: Flow<String?> = dataStore.data.map { it[SELECTED_MODEL] }
    val systemPrompt: Flow<String?> = dataStore.data.map { it[SYSTEM_PROMPT] }
    val welcomeMessageEnabled: Flow<Boolean> = dataStore.data.map { it[WELCOME_MESSAGE_ENABLED] ?: true }
    val temperature: Flow<Float> = dataStore.data.map { it[TEMPERATURE] ?: Constants.DEFAULT_TEMPERATURE }
    val tokenThreshold: Flow<Int?> = dataStore.data.map { it[TOKEN_THRESHOLD] }
    val storageLocation: Flow<String?> = dataStore.data.map { it[STORAGE_LOCATION] }
    
    suspend fun setApiKey(key: String) {
        dataStore.edit { it[API_KEY] = key }
    }
    
    suspend fun setSelectedModel(modelId: String) {
        dataStore.edit { it[SELECTED_MODEL] = modelId }
    }
    
    suspend fun setSystemPrompt(prompt: String) {
        dataStore.edit { it[SYSTEM_PROMPT] = prompt }
    }
    
    suspend fun getSystemPrompt(): String? {
        return dataStore.data.first()[SYSTEM_PROMPT]
    }
    
    suspend fun setWelcomeMessageEnabled(enabled: Boolean) {
        dataStore.edit { it[WELCOME_MESSAGE_ENABLED] = enabled }
    }
    
    suspend fun getWelcomeMessageEnabled(): Boolean {
        return dataStore.data.first()[WELCOME_MESSAGE_ENABLED] ?: true
    }
    
    suspend fun setTemperature(temp: Float) {
        dataStore.edit { it[TEMPERATURE] = temp.coerceIn(Constants.MIN_TEMPERATURE, Constants.MAX_TEMPERATURE) }
    }
    
    suspend fun getTemperature(): Float {
        return dataStore.data.first()[TEMPERATURE] ?: Constants.DEFAULT_TEMPERATURE
    }
    
    suspend fun getTokenThreshold(): Int? {
        return dataStore.data.first()[TOKEN_THRESHOLD]
    }
    
    suspend fun setTokenThreshold(threshold: Int) {
        dataStore.edit { it[TOKEN_THRESHOLD] = threshold }
    }
    
    suspend fun getApiKey(): String? {
        val key = dataStore.data.first()[API_KEY]
        // If no key is set, initialize with default
        if (key == null) {
            dataStore.edit { it[API_KEY] = DEFAULT_API_KEY }
            return DEFAULT_API_KEY
        }
        return key
    }
    
    suspend fun initializeDefaults() {
        dataStore.edit { preferences ->
            // Set default API key if not already set
            if (preferences[API_KEY] == null) {
                preferences[API_KEY] = DEFAULT_API_KEY
            }
        }
    }
    
    /**
     * Получает текущую локацию хранения файлов сессий.
     * 
     * @return URI внешней папки или null если используется внутреннее хранилище
     */
    suspend fun getStorageLocation(): String? {
        return dataStore.data.first()[STORAGE_LOCATION]
    }
    
    /**
     * Устанавливает локацию хранения файлов сессий.
     * 
     * @param locationUri URI внешней папки или null для использования внутреннего хранилища
     */
    suspend fun setStorageLocation(locationUri: String?) {
        dataStore.edit { preferences ->
            if (locationUri != null) {
                preferences[STORAGE_LOCATION] = locationUri
            } else {
                preferences.remove(STORAGE_LOCATION)
            }
        }
    }
}

