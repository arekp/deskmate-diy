package pl.arekp.deskmate.ai

import android.util.Log
import pl.arekp.deskmate.Config
import pl.arekp.deskmate.ai.llm.ChatMessage
import pl.arekp.deskmate.ai.llm.ChatRequest
import pl.arekp.deskmate.ai.llm.LlmRouter
import pl.arekp.deskmate.ai.llm.Part
import pl.arekp.deskmate.ai.llm.Role

/**
 * Prowadzi rozmowę: historia + pętla narzędzi. Model wybiera [LlmRouter],
 * więc kolejne kroki mogą obsłużyć różni dostawcy – historia jest w formacie wspólnym.
 */
class Assistant(
    private val router: LlmRouter,
    private val tools: List<AssistantTool>,
    private val onProviderUsed: (String) -> Unit = {},
) {
    private val history = mutableListOf<ChatMessage>()

    suspend fun ask(userText: String): String {
        if (!router.isAnyConfigured()) {
            return "Nie mam jeszcze klucza do żadnego modelu. Przytrzymaj palec na ekranie i wpisz klucz Gemini, OpenRouter albo Claude."
        }
        history += ChatMessage.user(userText)

        try {
            repeat(Config.MAX_TOOL_ROUNDS) {
                val routed = router.complete(
                    ChatRequest(Config.SYSTEM_PROMPT, history.toList(), tools.map { it.toSpec() }, Config.MAX_TOKENS),
                )
                onProviderUsed(routed.provider.label)
                val response = routed.response
                history += ChatMessage(Role.ASSISTANT, response.parts.ifEmpty { listOf(Part.Text("…")) })

                if (response.toolCalls.isEmpty()) {
                    trimHistory()
                    return response.text.ifBlank { "Hmm, nie wiem, co powiedzieć." }
                }
                history += ChatMessage(Role.USER, response.toolCalls.map { runTool(it) })
            }
            trimHistory()
            return "Za dużo kroków – spróbuj zapytać prościej."
        } catch (e: LlmRouter.AllProvidersFailed) {
            Log.w(TAG, e.message ?: "brak odpowiedzi")
            rollbackLastTurn()
            return "Żaden model teraz nie odpowiada – sprawdź internet albo limity. Spróbuj za chwilę."
        } catch (e: Exception) {
            Log.e(TAG, "Błąd rozmowy", e)
            rollbackLastTurn()
            return "Coś poszło nie tak. Spróbuj jeszcze raz."
        }
    }

    fun reset() = history.clear()

    private suspend fun runTool(call: Part.ToolCall): Part.ToolResult {
        val tool = tools.firstOrNull { it.name == call.name }
            ?: return Part.ToolResult(call.id, call.name, "Nieznane narzędzie", isError = true)
        val result = runCatching { tool.run(call.args) }
            .getOrElse { ToolResult.text("Błąd narzędzia: ${it.message}", isError = true) }
        return Part.ToolResult(call.id, call.name, result.text, result.images, result.isError)
    }

    /** Historia musi zaczynać się od zwykłej wiadomości użytkownika. */
    private fun trimHistory() {
        while (history.size > Config.HISTORY_LIMIT) {
            history.removeAt(0)
            while (history.isNotEmpty() && !history.first().isPlainUserText) history.removeAt(0)
        }
    }

    private fun rollbackLastTurn() {
        val idx = history.indexOfLast { it.isPlainUserText }
        if (idx >= 0) while (history.size > idx) history.removeAt(history.size - 1)
    }

    private companion object {
        const val TAG = "Assistant"
    }
}
