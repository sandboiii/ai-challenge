package xyz.sandboiii.agentcooper.presentation.chat

sealed class ChatIntent {
    data class SendMessage(val content: String) : ChatIntent()
    data object LoadMessages : ChatIntent()
    data object ClearError : ChatIntent()
    data object RetryLastMessage : ChatIntent()
}

