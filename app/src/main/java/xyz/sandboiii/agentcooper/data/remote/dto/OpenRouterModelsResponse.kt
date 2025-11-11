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
    val top_provider: Provider? = null,
    val pricing: Pricing? = null,
    val providers: List<ProviderInfo>? = null, // Hugging Face nests pricing in providers array
    val moderation: Boolean? = null,
    val context_length_display: String? = null,
    val supports_streaming: Boolean? = null,
    val supports_system_prompt: Boolean? = null,
    val supports_functions: Boolean? = null,
    val supports_native_functions: Boolean? = null,
    val data_policy: String? = null // Privacy/data policy indicator
)

@Serializable
data class Architecture(
    val modality: String? = null,
    val tokenizer: String? = null
)

@Serializable
data class Provider(
    val id: String? = null,
    val name: String? = null
)

@Serializable
data class Pricing(
    val prompt: String? = null,
    val completion: String? = null,
    // Alternative field names that Hugging Face might use
    val input: Double? = null, // Hugging Face uses numbers, not strings
    val output: Double? = null, // Hugging Face uses numbers, not strings
    val prompt_price: String? = null,
    val completion_price: String? = null,
    val input_price: String? = null,
    val output_price: String? = null
)

@Serializable
data class ProviderInfo(
    val provider: String? = null,
    val status: String? = null,
    val context_length: Int? = null,
    val pricing: Pricing? = null,
    val is_model_author: Boolean? = null
)

