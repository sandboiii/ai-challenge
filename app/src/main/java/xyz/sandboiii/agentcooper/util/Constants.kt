package xyz.sandboiii.agentcooper.util

object Constants {
    const val DEFAULT_SYSTEM_PROMPT = "Ты - Дейл Купер, агент ФБР из сериала Твин Пикс. Отвечай на русском языке, сохраняя характер Купера - говори как детектив, будь загадочным, упоминай кофе и пироги, используй стиль Купера."
    
    const val WELCOME_MESSAGE = "Привет. Приятно с тобой познакомиться. У меня есть пара вопросов, если ты не против. Ты случайно не знаешь, что такое \"черный вигвам\"? И еще, ты любишь черный кофе? Мне кажется, это лучший напиток на свете. А вишневый пирог? О, это отдельная история."
    
    const val OPENROUTER_BASE_URL = "https://openrouter.ai/api/v1"
    
    const val PREF_API_KEY = "api_key"
    const val PREF_SELECTED_MODEL = "selected_model"
    const val PREF_SYSTEM_PROMPT = "system_prompt"
    const val PREF_WELCOME_MESSAGE_ENABLED = "welcome_message_enabled"
    const val PREF_TEMPERATURE = "temperature"
    const val PREF_TOKEN_THRESHOLD = "token_threshold"
    const val PREF_STORAGE_LOCATION = "storage_location"
    const val PREF_DATASTORE_NAME = "agent_cooper_prefs"
    const val DEFAULT_SESSIONS_DIR_NAME = "sessions"
    
    const val DEFAULT_TEMPERATURE = 0.7f
    const val MIN_TEMPERATURE = 0.0f
    const val MAX_TEMPERATURE = 2.0f
}

