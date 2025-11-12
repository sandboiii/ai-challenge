package xyz.sandboiii.agentcooper.domain.model

import xyz.sandboiii.agentcooper.data.remote.api.PricingInfo

data class AIModel(
    val id: String,
    val name: String,
    val provider: String,
    val description: String? = null,
    val pricing: PricingInfo? = null,
    val contextLength: Int? = null, // Context window size (numeric)
    val contextLengthDisplay: String? = null // Context window size (formatted string, e.g., "8K", "128K")
)

