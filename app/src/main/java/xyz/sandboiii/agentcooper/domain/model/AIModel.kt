package xyz.sandboiii.agentcooper.domain.model

data class AIModel(
    val id: String,
    val name: String,
    val provider: String,
    val description: String? = null
)

