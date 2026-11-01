package pl.arekp.deskmate.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import pl.arekp.deskmate.Config
import pl.arekp.deskmate.Settings
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Klient Messages API (Anthropic) z obsługą narzędzi.
 * Pętla: pytanie -> model -> (narzędzia -> model)* -> tekst do powiedzenia.
 */
class ClaudeClient(
    private val tools: List<AssistantTool>,
    private val settings: Settings,
) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val history = mutableListOf<JSONObject>()

    suspend fun ask(userText: String): String = withContext(Dispatchers.IO) {
        if (settings.apiKey.isBlank()) {
            return@withContext "Nie mam jeszcze klucza API. Przytrzymaj palec na ekranie i wpisz go w ustawieniach."
        }
        history.add(JSONObject().put("role", "user").put("content", userText))

        try {
            repeat(Config.MAX_TOOL_ROUNDS) {
                val response = post(buildRequest())
                val content = response.getJSONArray("content")
                history.add(JSONObject().put("role", "assistant").put("content", content))

                if (response.optString("stop_reason") != "tool_use") {
                    trimHistory()
                    return@withContext extractText(content)
                }
                history.add(JSONObject().put("role", "user").put("content", runTools(content)))
            }
            "Za dużo kroków – spróbuj zapytać prościej."
        } catch (e: Exception) {
            Log.e(TAG, "Błąd zapytania", e)
            rollbackToLastUserText()
            "Mam problem z połączeniem. Spróbuj za chwilę."
        }
    }

    fun reset() = history.clear()

    private suspend fun runTools(content: JSONArray): JSONArray {
        val results = JSONArray()
        for (i in 0 until content.length()) {
            val block = content.getJSONObject(i)
            if (block.optString("type") != "tool_use") continue
            val tool = tools.firstOrNull { it.name == block.optString("name") }
            val result = if (tool == null) {
                ToolResult.text("Nieznane narzędzie", isError = true)
            } else {
                runCatching { tool.run(block.optJSONObject("input") ?: JSONObject()) }
                    .getOrElse { ToolResult.text("Błąd narzędzia: ${it.message}", isError = true) }
            }
            results.put(
                JSONObject()
                    .put("type", "tool_result")
                    .put("tool_use_id", block.getString("id"))
                    .put("content", result.content)
                    .apply { if (result.isError) put("is_error", true) },
            )
        }
        return results
    }

    private fun buildRequest(): JSONObject {
        val toolDefs = JSONArray()
        tools.forEach {
            toolDefs.put(
                JSONObject()
                    .put("name", it.name)
                    .put("description", it.description)
                    .put("input_schema", it.inputSchema),
            )
        }
        return JSONObject()
            .put("model", settings.model)
            .put("max_tokens", Config.MAX_TOKENS)
            .put("system", Config.SYSTEM_PROMPT)
            .put("tools", toolDefs)
            .put("messages", JSONArray(history))
    }

    private fun post(body: JSONObject): JSONObject {
        val request = Request.Builder()
            .url(API_URL)
            .header("x-api-key", settings.apiKey)
            .header("anthropic-version", API_VERSION)
            .post(body.toString().toRequestBody(JSON))
            .build()
        http.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}: ${text.take(300)}")
            return JSONObject(text)
        }
    }

    private fun extractText(content: JSONArray): String = buildString {
        for (i in 0 until content.length()) {
            val block = content.getJSONObject(i)
            if (block.optString("type") == "text") append(block.optString("text")).append(' ')
        }
    }.trim()

    /** Historia musi zaczynać się od zwykłej wiadomości użytkownika (nie od tool_result). */
    private fun trimHistory() {
        while (history.size > Config.HISTORY_LIMIT) {
            history.removeAt(0)
            while (history.isNotEmpty() && !isPlainUserMessage(history.first())) history.removeAt(0)
        }
    }

    /** Po błędzie usuwamy niedokończoną turę, żeby historia była poprawna. */
    private fun rollbackToLastUserText() {
        val idx = history.indexOfLast { isPlainUserMessage(it) }
        if (idx >= 0) while (history.size > idx) history.removeAt(history.size - 1)
    }

    private fun isPlainUserMessage(m: JSONObject) =
        m.optString("role") == "user" && m.opt("content") is String

    private companion object {
        const val TAG = "ClaudeClient"
        const val API_URL = "https://api.anthropic.com/v1/messages"
        const val API_VERSION = "2023-06-01"
        val JSON = "application/json".toMediaType()
    }
}
