package xyz.sandboiii.agentcooper.domain.model

import xyz.sandboiii.agentcooper.data.remote.api.PricingInfo

data class AIModel(
    val id: String,
    val name: String,
    val provider: String,
    val description: String? = null,
    val pricing: PricingInfo? = null
)

