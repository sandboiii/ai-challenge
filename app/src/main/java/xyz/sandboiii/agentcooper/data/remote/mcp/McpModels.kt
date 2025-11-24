package xyz.sandboiii.agentcooper.data.remote.mcp

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// JSON-RPC 2.0 Models
@Serializable
data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val id: String? = null,
    val method: String,
    val params: JsonElement? = null
)

// JSON-RPC Notification (no id field, params must be object)
@Serializable
data class JsonRpcNotification(
    val jsonrpc: String = "2.0",
    val method: String,
    val params: JsonElement? = null
)

@Serializable
data class JsonRpcResponse(
    val jsonrpc: String = "2.0",
    val id: String? = null,
    val result: JsonElement? = null,
    val error: JsonRpcError? = null
)

@Serializable
data class JsonRpcError(
    val code: Int,
    val message: String,
    val data: JsonElement? = null
)

// MCP Initialize Models
@Serializable
data class InitializeParams(
    val protocolVersion: String = "2025-06-18",
    val capabilities: ClientCapabilities = ClientCapabilities(),
    val clientInfo: ClientInfo
)

@Serializable
data class ClientCapabilities(
    val roots: RootsCapability? = null,
    val sampling: JsonElement? = null
)

@Serializable
data class RootsCapability(
    val listChanged: Boolean = false
)

@Serializable
data class ClientInfo(
    val name: String,
    val version: String
)

@Serializable
data class InitializeResult(
    val protocolVersion: String,
    val capabilities: ServerCapabilities,
    val serverInfo: ServerInfo
)

@Serializable
data class ServerCapabilities(
    val tools: ToolsCapability? = null,
    val resources: JsonElement? = null,
    val prompts: JsonElement? = null,
    val sampling: JsonElement? = null
)

@Serializable
data class ToolsCapability(
    val listChanged: Boolean = false
)

@Serializable
data class ServerInfo(
    val name: String,
    val version: String
)

// MCP Tool Models
@Serializable
data class McpTool(
    val name: String,
    val description: String? = null,
    val inputSchema: JsonSchema
)

@Serializable
data class ToolsListResult(
    val tools: List<McpTool>
)

@Serializable
data class ToolCallParams(
    val name: String,
    val arguments: JsonElement? = null
)

@Serializable
data class ToolCallResult(
    val content: List<ToolCallContent>,
    val isError: Boolean = false
)

@Serializable
data class ToolCallContent(
    val type: String, // "text" or "image"
    val text: String? = null,
    val data: String? = null, // base64 encoded data for images
    val mimeType: String? = null
)

// JSON Schema Models (simplified for OpenAI compatibility)
@Serializable
data class JsonSchema(
    val type: String? = null,
    val properties: Map<String, JsonSchemaProperty>? = null,
    val required: List<String>? = null,
    val description: String? = null,
    val enum: List<String>? = null,
    val items: JsonElement? = null,
    val additionalProperties: JsonElement? = null
)

@Serializable
data class JsonSchemaProperty(
    val type: String? = null,
    val description: String? = null,
    val enum: List<String>? = null,
    val items: JsonElement? = null,
    val additionalProperties: JsonElement? = null
)

// Connection State Models
enum class McpConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

data class McpServerInfo(
    val id: String,
    val name: String,
    val url: String,
    val state: McpConnectionState,
    val error: String? = null,
    val tools: List<McpTool> = emptyList(),
    val authorizationToken: String? = null
)

// OpenAI-Compatible Tool Format
@Serializable
data class OpenAITool(
    val type: String = "function",
    val function: OpenAIFunction
)

@Serializable
data class OpenAIFunction(
    val name: String,
    val description: String? = null,
    val parameters: JsonElement // JSON Schema as JsonElement
)

// Converter function to convert MCP tool to OpenAI format
fun McpTool.toOpenAITool(): OpenAITool {
    // Convert MCP JSON Schema to OpenAI-compatible format
    val parameters = buildJsonObject {
        put("type", inputSchema.type ?: "object")
        inputSchema.description?.let { put("description", it) }
        
        inputSchema.properties?.let { props ->
            val propertiesObj = buildJsonObject {
                props.forEach { (key, prop) ->
                    val propObj = buildJsonObject {
                        prop.type?.let { put("type", it) }
                        prop.description?.let { put("description", it) }
                        prop.enum?.let { enumList ->
                            put("enum", JsonArray(enumList.map { JsonPrimitive(it) }))
                        }
                    }
                    put(key, propObj)
                }
            }
            put("properties", propertiesObj)
        }
        
        inputSchema.required?.let { requiredList ->
            put("required", JsonArray(requiredList.map { JsonPrimitive(it) }))
        }
    }
    
    return OpenAITool(
        function = OpenAIFunction(
            name = name,
            description = description,
            parameters = parameters
        )
    )
}

