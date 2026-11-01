package pl.arekp.deskmate.ai.llm

import org.json.JSONArray
import org.json.JSONObject

/**
 * OpenRouter (format zgodny z OpenAI Chat Completions).
 * Domyślnie "openrouter/free" – OpenRouter sam wybiera dostępny darmowy model.
 * Limity darmowych modeli: ok. 20 zapytań/min, 50/dzień (1000/dzień po jednorazowym doładowaniu 10 USD).
 */
class OpenRouterProvider(
    private val apiKey: () -> String,
    private val model: () -> String,
) : LlmProvider {
    override val id = ID
    override val label: String get() = "OpenRouter (${model()})"
    override val isFree: Boolean get() = model().endsWith(":free") || model() == "openrouter/free"
    override fun isConfigured() = apiKey().isNotBlank()

    override suspend fun complete(request: ChatRequest): ChatResponse {
        val headers = mapOf(
            "Authorization" to "Bearer ${apiKey()}",
            "HTTP-Referer" to "https://github.com/arekp/deskmate-diy",
            "X-Title" to "DeskMate DIY",
        )
        return try {
            parse(HttpJson.post(URL, headers, buildBody(request, model(), withTools = true)))
        } catch (e: ProviderException) {
            // Część darmowych modeli nie obsługuje narzędzi – wtedy rozmawiamy bez nich
            if (e.kind == ProviderException.Kind.BAD_REQUEST && request.tools.isNotEmpty()) {
                parse(HttpJson.post(URL, headers, buildBody(request, model(), withTools = false)))
            } else {
                throw e
            }
        }
    }

    companion object {
        const val ID = "openrouter"
        private const val URL = "https://openrouter.ai/api/v1/chat/completions"

        fun buildBody(request: ChatRequest, model: String, withTools: Boolean): JSONObject {
            val messages = JSONArray().put(JSONObject().put("role", "system").put("content", request.system))
            for (m in request.messages) {
                if (m.role == Role.ASSISTANT) {
                    val text = m.parts.filterIsInstance<Part.Text>().joinToString(" ") { it.text }
                    val calls = m.parts.filterIsInstance<Part.ToolCall>()
                    val msg = JSONObject().put("role", "assistant").put("content", text.ifBlank { JSONObject.NULL })
                    if (withTools && calls.isNotEmpty()) {
                        val arr = JSONArray()
                        calls.forEach {
                            arr.put(
                                JSONObject().put("id", it.id).put("type", "function").put(
                                    "function",
                                    JSONObject().put("name", it.name).put("arguments", it.args.toString()),
                                ),
                            )
                        }
                        msg.put("tool_calls", arr)
                    }
                    if (text.isNotBlank() || (withTools && calls.isNotEmpty())) messages.put(msg)
                } else {
                    // Najpierw wyniki narzędzi (muszą następować tuż po tool_calls), potem treść użytkownika
                    val pendingImages = mutableListOf<ImageData>()
                    for (r in m.parts.filterIsInstance<Part.ToolResult>()) {
                        if (withTools) {
                            messages.put(JSONObject().put("role", "tool").put("tool_call_id", r.callId).put("content", r.text))
                        } else {
                            messages.put(JSONObject().put("role", "user").put("content", "[wynik ${r.name}] ${r.text}"))
                        }
                        pendingImages += r.images
                    }
                    val content = JSONArray()
                    m.parts.forEach { p ->
                        when (p) {
                            is Part.Text -> content.put(JSONObject().put("type", "text").put("text", p.text))
                            is Part.Image -> content.put(imageUrl(p.image))
                            else -> Unit
                        }
                    }
                    pendingImages.forEach { content.put(imageUrl(it)) }
                    if (content.length() > 0) messages.put(JSONObject().put("role", "user").put("content", content))
                }
            }

            val body = JSONObject()
                .put("model", model)
                .put("max_tokens", request.maxTokens)
                .put("messages", messages)
            if (withTools && request.tools.isNotEmpty()) {
                val tools = JSONArray()
                request.tools.forEach {
                    tools.put(
                        JSONObject().put("type", "function").put(
                            "function",
                            JSONObject().put("name", it.name).put("description", it.description).put("parameters", it.inputSchema),
                        ),
                    )
                }
                body.put("tools", tools)
            }
            return body
        }

        fun parse(json: JSONObject): ChatResponse {
            json.optJSONObject("error")?.let {
                throw ProviderException(ProviderException.Kind.SERVER, "OpenRouter: ${it.optString("message")}")
            }
            val message = json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
                ?: throw ProviderException(ProviderException.Kind.SERVER, "Brak odpowiedzi w choices")
            val parts = mutableListOf<Part>()
            val text = message.optString("content", "").takeUnless { message.isNull("content") }.orEmpty()
            if (text.isNotBlank()) parts += Part.Text(text)
            val calls = message.optJSONArray("tool_calls") ?: JSONArray()
            for (i in 0 until calls.length()) {
                val c = calls.getJSONObject(i)
                val fn = c.getJSONObject("function")
                val args = fn.optString("arguments").let { s ->
                    if (s.isBlank()) JSONObject() else runCatching { JSONObject(s) }.getOrDefault(JSONObject())
                }
                parts += Part.ToolCall(id = c.optString("id").ifBlank { "call_$i" }, name = fn.getString("name"), args = args)
            }
            return ChatResponse(parts)
        }

        private fun imageUrl(img: ImageData) = JSONObject()
            .put("type", "image_url")
            .put("image_url", JSONObject().put("url", "data:${img.mediaType};base64,${img.base64}"))
    }
}
