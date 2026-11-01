package pl.arekp.deskmate.ai.llm

import org.json.JSONArray
import org.json.JSONObject

/**
 * Google Gemini przez Gemini API (generateContent). Darmowy pakiet – klucz z Google AI Studio.
 * Uwaga: w darmowym pakiecie Google może wykorzystywać dane do ulepszania swoich produktów.
 */
class GeminiProvider(
    private val apiKey: () -> String,
    private val model: () -> String,
    override val isFree: Boolean = true,
) : LlmProvider {
    override val id = ID
    override val label: String get() = "Gemini (${model()})"
    override fun isConfigured() = apiKey().isNotBlank()

    override suspend fun complete(request: ChatRequest): ChatResponse {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/${model()}:generateContent"
        val json = HttpJson.post(url, mapOf("x-goog-api-key" to apiKey()), buildBody(request))
        return parse(json)
    }

    companion object {
        const val ID = "gemini"

        fun buildBody(request: ChatRequest): JSONObject {
            val contents = JSONArray()
            for (m in request.messages) {
                val parts = JSONArray()
                for (p in m.parts) when (p) {
                    is Part.Text -> parts.put(JSONObject().put("text", p.text))
                    is Part.Image -> parts.put(inlineData(p.image))
                    is Part.ToolCall ->
                        // Odsyłamy oryginał (z "thoughtSignature"), jeśli pochodzi od Gemini
                        parts.put(
                            if (p.rawProvider == ID && p.raw != null) p.raw
                            else JSONObject().put("functionCall", JSONObject().put("name", p.name).put("args", p.args)),
                        )
                    is Part.ToolResult -> {
                        val response = JSONObject().put(if (p.isError) "error" else "result", p.text)
                        parts.put(JSONObject().put("functionResponse", JSONObject().put("name", p.name).put("response", response)))
                        p.images.forEach { parts.put(inlineData(it)) }
                    }
                }
                contents.put(JSONObject().put("role", if (m.role == Role.USER) "user" else "model").put("parts", parts))
            }

            val declarations = JSONArray()
            request.tools.forEach { t ->
                val decl = JSONObject().put("name", t.name).put("description", t.description)
                // Gemini nie akceptuje obiektu bez właściwości – wtedy pomijamy parametry
                val props = t.inputSchema.optJSONObject("properties")
                if (props != null && props.length() > 0) decl.put("parameters", t.inputSchema)
                declarations.put(decl)
            }

            return JSONObject()
                .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", request.system))))
                .put("contents", contents)
                // Modele Gemini "myślą", a myśli liczą się do limitu – dajemy zapas, żeby nie uciąć odpowiedzi
                .put("generationConfig", JSONObject().put("maxOutputTokens", maxOf(request.maxTokens * 4, 2048)))
                .apply {
                    if (declarations.length() > 0) {
                        put("tools", JSONArray().put(JSONObject().put("functionDeclarations", declarations)))
                    }
                }
        }

        fun parse(json: JSONObject): ChatResponse {
            val candidate = json.optJSONArray("candidates")?.optJSONObject(0)
                ?: throw ProviderException(
                    ProviderException.Kind.BAD_REQUEST,
                    "Brak odpowiedzi (np. zablokowana): ${json.optJSONObject("promptFeedback")}",
                )
            val parts = mutableListOf<Part>()
            val raw = candidate.optJSONObject("content")?.optJSONArray("parts") ?: JSONArray()
            var counter = 0
            for (i in 0 until raw.length()) {
                val p = raw.getJSONObject(i)
                if (p.optBoolean("thought", false)) continue // wewnętrzne "myśli" modelu
                val call = p.optJSONObject("functionCall")
                when {
                    call != null -> parts += Part.ToolCall(
                        id = call.optString("id").ifBlank { "gemini-${call.optString("name")}-${counter++}" },
                        name = call.getString("name"),
                        args = call.optJSONObject("args") ?: JSONObject(),
                        raw = p,
                        rawProvider = ID,
                    )
                    p.has("text") -> parts += Part.Text(p.getString("text"))
                }
            }
            return ChatResponse(parts)
        }

        private fun inlineData(img: ImageData) =
            JSONObject().put("inlineData", JSONObject().put("mimeType", img.mediaType).put("data", img.base64))
    }
}
