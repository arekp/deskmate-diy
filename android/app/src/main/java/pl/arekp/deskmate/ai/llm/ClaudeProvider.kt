package pl.arekp.deskmate.ai.llm

import org.json.JSONArray
import org.json.JSONObject

/**
 * Claude przez Messages API (Anthropic). Płatne – klucz z console.anthropic.com
 * (subskrypcja Claude Pro nie obejmuje API).
 */
class ClaudeProvider(
    private val apiKey: () -> String,
    private val model: () -> String,
) : LlmProvider {
    override val id = ID
    override val label: String get() = "Claude (${model()})"
    override val isFree = false
    override fun isConfigured() = apiKey().isNotBlank()

    override suspend fun complete(request: ChatRequest): ChatResponse {
        val json = HttpJson.post(
            URL,
            mapOf("x-api-key" to apiKey(), "anthropic-version" to API_VERSION),
            buildBody(request, model()),
        )
        return parse(json)
    }

    companion object {
        const val ID = "claude"
        private const val URL = "https://api.anthropic.com/v1/messages"
        private const val API_VERSION = "2023-06-01"

        fun buildBody(request: ChatRequest, model: String): JSONObject {
            val messages = JSONArray()
            for (m in request.messages) {
                val content = JSONArray()
                for (p in m.parts) when (p) {
                    is Part.Text -> content.put(JSONObject().put("type", "text").put("text", p.text))
                    is Part.Image -> content.put(imageBlock(p.image))
                    is Part.ToolCall -> content.put(
                        JSONObject().put("type", "tool_use").put("id", p.id).put("name", p.name).put("input", p.args),
                    )
                    is Part.ToolResult -> {
                        val inner = JSONArray().put(JSONObject().put("type", "text").put("text", p.text))
                        p.images.forEach { inner.put(imageBlock(it)) }
                        content.put(
                            JSONObject().put("type", "tool_result").put("tool_use_id", p.callId).put("content", inner)
                                .apply { if (p.isError) put("is_error", true) },
                        )
                    }
                }
                messages.put(JSONObject().put("role", if (m.role == Role.USER) "user" else "assistant").put("content", content))
            }
            val tools = JSONArray()
            request.tools.forEach {
                tools.put(JSONObject().put("name", it.name).put("description", it.description).put("input_schema", it.inputSchema))
            }
            return JSONObject()
                .put("model", model)
                .put("max_tokens", request.maxTokens)
                .put("system", request.system)
                .put("messages", messages)
                .apply { if (tools.length() > 0) put("tools", tools) }
        }

        fun parse(json: JSONObject): ChatResponse {
            val parts = mutableListOf<Part>()
            val content = json.optJSONArray("content") ?: JSONArray()
            for (i in 0 until content.length()) {
                val b = content.getJSONObject(i)
                when (b.optString("type")) {
                    "text" -> parts += Part.Text(b.optString("text"))
                    "tool_use" -> parts += Part.ToolCall(
                        id = b.getString("id"),
                        name = b.getString("name"),
                        args = b.optJSONObject("input") ?: JSONObject(),
                    )
                }
            }
            return ChatResponse(parts)
        }

        private fun imageBlock(img: ImageData) = JSONObject()
            .put("type", "image")
            .put("source", JSONObject().put("type", "base64").put("media_type", img.mediaType).put("data", img.base64))
    }
}
