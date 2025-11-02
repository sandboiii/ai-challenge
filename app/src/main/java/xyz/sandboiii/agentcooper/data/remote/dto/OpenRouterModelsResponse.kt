package xyz.sandboiii.agentcooper.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class OpenRouterModelsResponse(
    val data: List<ModelData>
)

@Serializable
data class ModelData(
    val id: String,
    val name: String? = null,
    val description: String? = null,
    val context_length: Int? = null,
    val architecture: Architecture? = null,
    val top_provider: Provider? = null
)

@Serializable
data class Architecture(
    val modality: String? = null,
    val tokenizer: String? = null
)

@Serializable
data class Provider(
    val id: String,
    val name: String
)

