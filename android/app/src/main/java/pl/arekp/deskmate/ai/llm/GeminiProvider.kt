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

    /** Modele, które odrzuciły ustawienie myślenia (400) – dla nich wysyłamy zapytanie bez niego. */
    private val noThinkingConfig = mutableSetOf<String>()

    override suspend fun complete(request: ChatRequest): ChatResponse =
        GeminiModels.withFallback(model()) { name -> completeWith(name, request) }.first

    private suspend fun completeWith(name: String, request: ChatRequest): ChatResponse {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$name:generateContent"
        val headers = mapOf("x-goog-api-key" to apiKey())
        val thinking = if (name in noThinkingConfig) null else thinkingConfigFor(name)
        val json = try {
            HttpJson.post(url, headers, buildBody(request, thinking))
        } catch (e: ProviderException) {
            if (thinking == null || e.kind != ProviderException.Kind.BAD_REQUEST || GeminiModels.isModelProblem(e)) throw e
            noThinkingConfig += name
            HttpJson.post(url, headers, buildBody(request, null))
        }
        return parse(json)
    }

    companion object {
        const val ID = "gemini"

        /**
         * Jak mało „myśleć” przed odpowiedzią – robot ma odpowiadać szybko, a pytania są zwykle proste.
         * Gemini 2.5: thinkingBudget (0 = bez myślenia dla Flash, Pro wymaga min. 128).
         * Gemini 3.x i aliasy *-latest: thinkingLevel = "low".
         * Jeśli model odrzuci ustawienie (400), provider ponawia zapytanie bez niego.
         */
        fun thinkingConfigFor(model: String): JSONObject? {
            val m = model.lowercase()
            return when {
                "2.0" in m || "1.5" in m -> null // te modele nie myślą
                "2.5" in m && "pro" in m -> JSONObject().put("thinkingBudget", 128)
                "2.5" in m -> JSONObject().put("thinkingBudget", 0)
                else -> JSONObject().put("thinkingLevel", "low")
            }
        }

        fun buildBody(request: ChatRequest, thinking: JSONObject? = null): JSONObject {
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
                // Myśli (jeśli są) liczą się do limitu – zostawiamy zapas, żeby nie uciąć odpowiedzi
                .put(
                    "generationConfig",
                    JSONObject().put("maxOutputTokens", maxOf(request.maxTokens * 3, 1024))
                        .apply { if (thinking != null) put("thinkingConfig", thinking) },
                )
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
