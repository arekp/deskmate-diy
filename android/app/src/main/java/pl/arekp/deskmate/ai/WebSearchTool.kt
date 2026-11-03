package pl.arekp.deskmate.ai

import org.json.JSONArray
import org.json.JSONObject
import pl.arekp.deskmate.ai.llm.HttpJson
import pl.arekp.deskmate.ai.llm.ProviderException
import java.net.URLEncoder

/**
 * Wyszukiwanie w internecie.
 *  1. Gemini z wyszukiwarką Google (grounding) – osobne, krótkie zapytanie z kluczem Gemini użytkownika
 *     (działa niezależnie od tego, który model prowadzi rozmowę),
 *  2. gdy brak klucza Gemini albo błąd – Wikipedia (krótkie streszczenie hasła).
 */
class WebSearchTool(
    private val geminiKey: () -> String,
    private val geminiModel: () -> String,
) : AssistantTool {
    override val name = "web_search"
    override val description =
        "Wyszukuje aktualne informacje w internecie: wiadomości, wyniki sportowe, kursy, ceny, godziny otwarcia, " +
            "wydarzenia, fakty o osobach i miejscach, wszystko, czego możesz nie wiedzieć albo co mogło się zmienić. " +
            "Do pogody używaj get_weather."
    override val inputSchema = objectSchema(
        "query" to JSONObject().put("type", "string").put("description", "Zapytanie, np. „wynik meczu Legia wczoraj”"),
        required = listOf("query"),
    )

    override suspend fun run(input: JSONObject): ToolResult {
        val query = input.optString("query").trim()
        if (query.isBlank()) return ToolResult.text("Puste zapytanie.", isError = true)

        if (geminiKey().isNotBlank()) {
            try {
                return ToolResult.text(googleViaGemini(query))
            } catch (e: ProviderException) {
                // spróbuj Wikipedii niżej
                val wiki = runCatching { wikipedia(query) }.getOrNull()
                return ToolResult.text(
                    (wiki ?: "Wyszukiwarka niedostępna (${e.detail.ifBlank { e.kind.name }}).") +
                        if (wiki != null) " (Wyszukiwarka Google niedostępna: ${e.kind.name}; to dane z Wikipedii.)" else "",
                    isError = wiki == null,
                )
            }
        }
        val wiki = runCatching { wikipedia(query) }.getOrNull()
        return if (wiki != null) ToolResult.text("$wiki (Źródło: Wikipedia. Aktualne wiadomości wymagają klucza Gemini.)")
        else ToolResult.text("Nic nie znalazłem. Pełne wyszukiwanie wymaga klucza Gemini w ustawieniach.", isError = true)
    }

    private suspend fun googleViaGemini(query: String): String {
        val body = JSONObject()
            .put(
                "systemInstruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", SEARCH_PROMPT))),
            )
            .put(
                "contents",
                JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", query)))),
            )
            .put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
            .put("generationConfig", JSONObject().put("maxOutputTokens", 1024))
        val url = "https://generativelanguage.googleapis.com/v1beta/models/${geminiModel()}:generateContent"
        return parseGrounded(HttpJson.post(url, mapOf("x-goog-api-key" to geminiKey()), body))
    }

    private suspend fun wikipedia(query: String): String? {
        val headers = mapOf("User-Agent" to "DeskMateDIY/1.0 (https://github.com/arekp/deskmate-diy)")
        val q = URLEncoder.encode(query, "UTF-8")
        val search = HttpJson.get(
            "https://pl.wikipedia.org/w/api.php?action=query&list=search&srsearch=$q&srlimit=1&format=json&utf8=1",
            headers,
        )
        val title = search.optJSONObject("query")?.optJSONArray("search")?.optJSONObject(0)?.optString("title")
            ?.takeIf { it.isNotBlank() } ?: return null
        val summary = HttpJson.get(
            "https://pl.wikipedia.org/api/rest_v1/page/summary/" + URLEncoder.encode(title.replace(' ', '_'), "UTF-8"),
            headers,
        )
        return summary.optString("extract").takeIf { it.isNotBlank() }?.let { "Wikipedia – $title: ${it.take(900)}" }
    }

    companion object {
        private const val SEARCH_PROMPT =
            "Wyszukaj w Google i odpowiedz po polsku, zwięźle (do 5 zdań), konkretnymi faktami z datami. " +
                "Jeśli wyniki są niejednoznaczne – powiedz to."

        /** Tekst odpowiedzi + nazwy źródeł z groundingMetadata. */
        fun parseGrounded(json: JSONObject): String {
            val cand = json.optJSONArray("candidates")?.optJSONObject(0)
                ?: throw ProviderException(ProviderException.Kind.BAD_REQUEST, "brak wyników", detail = "brak wyników")
            val parts = cand.optJSONObject("content")?.optJSONArray("parts") ?: JSONArray()
            val text = (0 until parts.length()).mapNotNull { i ->
                parts.optJSONObject(i)?.takeIf { !it.optBoolean("thought") }?.optString("text")?.takeIf { it.isNotBlank() }
            }.joinToString(" ").trim()
            val chunks = cand.optJSONObject("groundingMetadata")?.optJSONArray("groundingChunks") ?: JSONArray()
            val sources = (0 until chunks.length()).mapNotNull { i ->
                chunks.optJSONObject(i)?.optJSONObject("web")?.optString("title")?.takeIf { it.isNotBlank() }
            }.distinct().take(4)
            return text.ifBlank { "Brak wyników." } + if (sources.isNotEmpty()) " (Źródła: ${sources.joinToString(", ")})" else ""
        }
    }
}
