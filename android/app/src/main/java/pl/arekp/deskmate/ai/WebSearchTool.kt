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
 *  2. bez klucza Gemini albo przy błędzie – BEZ KLUCZA: nagłówki z Google News (RSS, aktualne wiadomości)
 *     + streszczenie hasła z Wikipedii (wiedza ogólna).
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

        var googleError: String? = null
        if (geminiKey().isNotBlank()) {
            try {
                return ToolResult.text(googleViaGemini(query))
            } catch (e: ProviderException) {
                googleError = e.detail.ifBlank { e.kind.name }
                pl.arekp.deskmate.DiagLog.w("WebSearch", "Google przez Gemini (${geminiModel()}) nie zadziałał: ${e.message}")
            }
        }
        // Bez klucza (albo gdy Google przez Gemini zawiódł): wiadomości z Google News + Wikipedia
        val news = runCatching { googleNews(query) }.getOrNull().orEmpty()
        val wiki = runCatching { wikipedia(query) }.getOrNull()
        if (news.isEmpty() && wiki == null) {
            return ToolResult.text("Nic nie znalazłem${googleError?.let { " (Google przez Gemini: $it)" } ?: ""}.", isError = true)
        }
        return ToolResult.text(
            buildString {
                if (news.isNotEmpty()) {
                    append("Najnowsze nagłówki (Google News): ")
                    append(news.joinToString(" | "))
                    append(". ")
                }
                if (wiki != null) append(wiki)
                append(" Streść najważniejsze fakty z nagłówków; nie wymyślaj szczegółów, których w nich nie ma.")
            }.trim(),
        )
    }

    /** Nagłówki z Google News RSS (bez klucza): „tytuł – źródło (data)”. */
    private suspend fun googleNews(query: String): List<String> {
        val q = URLEncoder.encode(query, "UTF-8")
        val xml = HttpJson.getText(
            "https://news.google.com/rss/search?q=$q&hl=pl&gl=PL&ceid=PL:pl",
            mapOf("User-Agent" to "Mozilla/5.0 (Android) DeskMateDIY/1.0"),
        )
        return parseNewsRss(xml, MAX_NEWS)
    }

    /** Test wyszukiwarki Google przez Gemini (przycisk w ustawieniach). */
    suspend fun testGoogle(): String = try {
        val t0 = System.currentTimeMillis()
        val r = googleViaGemini("Jaka jest dzisiejsza data? Odpowiedz krótko.")
        "✅ działa (${System.currentTimeMillis() - t0} ms) – „${r.take(60)}”"
    } catch (e: ProviderException) {
        "❌ ${e.detail.ifBlank { e.kind.name }} [${e.message?.take(120)}]"
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
        return pl.arekp.deskmate.ai.llm.GeminiModels.withFallback(geminiModel()) { model ->
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
            parseGrounded(HttpJson.post(url, mapOf("x-goog-api-key" to geminiKey()), body))
        }.first
    }

    private suspend fun wikipedia(query: String): String? {
        val headers = mapOf("User-Agent" to "DeskMateDIY/1.0 (https://github.com/arekp/deskmate-diy)")
        val q = URLEncoder.encode(wikiQuery(query).ifBlank { query }, "UTF-8")
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
        private const val MAX_NEWS = 6

        /**
         * Słowa o „aktualności” i daty psują wyszukiwanie w Wikipedii (np. „TK status listopad 2026” → hasło „Polska”),
         * więc je usuwamy – zostaje temat.
         */
        fun wikiQuery(query: String): String {
            val stop = setOf(
                "aktualny", "aktualne", "aktualna", "aktualnie", "najnowsze", "najnowszy", "najnowsza", "status", "stan",
                "dzisiaj", "dziś", "dzisiejszy", "dzisiejsze", "wczoraj", "jutro", "teraz", "obecnie", "wiadomości", "informacje",
                "news", "newsy", "najważniejsze", "streszczenie", "dzień", "dnia", "na", "w", "o", "z", "i",
                "styczeń", "luty", "marzec", "kwiecień", "maj", "czerwiec", "lipiec", "sierpień", "wrzesień",
                "październik", "listopad", "grudzień", "stycznia", "lutego", "marca", "kwietnia", "maja", "czerwca",
                "lipca", "sierpnia", "września", "października", "listopada", "grudnia",
            )
            return query.split(Regex("\\s+"))
                .filter { w -> w.lowercase() !in stop && !Regex("\\d{1,4}").matches(w) }
                .joinToString(" ")
        }

        /** Parsuje RSS (Google News): tytuł, źródło i data każdej pozycji. */
        fun parseNewsRss(xml: String, max: Int): List<String> {
            val doc = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false }
                .newDocumentBuilder().parse(org.xml.sax.InputSource(java.io.StringReader(xml)))
            val items = doc.getElementsByTagName("item")
            val out = mutableListOf<String>()
            for (i in 0 until minOf(items.length, max)) {
                val item = items.item(i) as? org.w3c.dom.Element ?: continue
                fun tag(name: String) = item.getElementsByTagName(name).item(0)?.textContent?.trim().orEmpty()
                val source = tag("source")
                // Google News dokleja „ - źródło” do tytułu
                val title = tag("title").removeSuffix(" - $source").trim()
                val date = runCatching {
                    java.time.ZonedDateTime.parse(tag("pubDate"), java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)
                        .withZoneSameInstant(java.time.ZoneId.systemDefault())
                        .format(java.time.format.DateTimeFormatter.ofPattern("d.MM HH:mm"))
                }.getOrDefault("")
                if (title.isNotBlank()) out += listOf(title, source.ifBlank { null }, date.ifBlank { null })
                    .let { (t, s, d) -> t + (s?.let { " – $it" } ?: "") + (d?.let { " ($it)" } ?: "") }
            }
            return out
        }

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
