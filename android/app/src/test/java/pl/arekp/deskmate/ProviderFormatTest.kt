package pl.arekp.deskmate

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.arekp.deskmate.ai.llm.ChatMessage
import pl.arekp.deskmate.ai.llm.ChatRequest
import pl.arekp.deskmate.ai.llm.ClaudeProvider
import pl.arekp.deskmate.ai.llm.GeminiProvider
import pl.arekp.deskmate.ai.llm.ImageData
import pl.arekp.deskmate.ai.llm.OpenRouterProvider
import pl.arekp.deskmate.ai.llm.Part
import pl.arekp.deskmate.ai.llm.Role
import pl.arekp.deskmate.ai.llm.ToolSpec

/** Sprawdza tłumaczenie wspólnego formatu rozmowy na formaty API i z powrotem. */
class ProviderFormatTest {

    private val expressSpec = ToolSpec(
        "express", "emocja",
        JSONObject().put("type", "object")
            .put("properties", JSONObject().put("emotion", JSONObject().put("type", "string")))
            .put("required", JSONArray().put("emotion")),
    )
    private val timeSpec = ToolSpec(
        "get_current_time", "czas",
        JSONObject().put("type", "object").put("properties", JSONObject()).put("required", JSONArray()),
    )

    /** Pełna tura z narzędziem: pytanie → wywołanie → wynik (z obrazem). */
    private val request = ChatRequest(
        system = "Jesteś robotem.",
        messages = listOf(
            ChatMessage.user("Cześć"),
            ChatMessage(
                Role.ASSISTANT,
                listOf(Part.Text("Już"), Part.ToolCall("call_1", "express", JSONObject().put("emotion", "happy"))),
            ),
            ChatMessage(
                Role.USER,
                listOf(Part.ToolResult("call_1", "express", "ok", images = listOf(ImageData("image/jpeg", "QUJD")))),
            ),
        ),
        tools = listOf(expressSpec, timeSpec),
        maxTokens = 400,
    )

    // --- Claude ---------------------------------------------------------------

    @Test
    fun claude_buildsMessagesWithToolUseAndToolResult() {
        val body = ClaudeProvider.buildBody(request, "claude-test")
        assertEquals("claude-test", body.getString("model"))
        assertEquals("Jesteś robotem.", body.getString("system"))
        val msgs = body.getJSONArray("messages")
        assertEquals(3, msgs.length())
        val toolUse = msgs.getJSONObject(1).getJSONArray("content").getJSONObject(1)
        assertEquals("tool_use", toolUse.getString("type"))
        assertEquals("call_1", toolUse.getString("id"))
        val toolResult = msgs.getJSONObject(2).getJSONArray("content").getJSONObject(0)
        assertEquals("tool_result", toolResult.getString("type"))
        assertEquals("call_1", toolResult.getString("tool_use_id"))
        assertEquals("image", toolResult.getJSONArray("content").getJSONObject(1).getString("type"))
        assertEquals(2, body.getJSONArray("tools").length())
    }

    @Test
    fun claude_parsesTextAndToolUse() {
        val json = JSONObject("""{"content":[{"type":"text","text":"Hej"},
            {"type":"tool_use","id":"t1","name":"express","input":{"emotion":"happy"}}],"stop_reason":"tool_use"}""")
        val r = ClaudeProvider.parse(json)
        assertEquals("Hej", r.text)
        assertEquals("express", r.toolCalls.single().name)
        assertEquals("happy", r.toolCalls.single().args.getString("emotion"))
    }

    // --- Gemini ---------------------------------------------------------------

    @Test
    fun gemini_usesUserModelRolesAndFunctionParts() {
        val body = GeminiProvider.buildBody(request)
        assertEquals(
            "Jesteś robotem.",
            body.getJSONObject("systemInstruction").getJSONArray("parts").getJSONObject(0).getString("text"),
        )
        val contents = body.getJSONArray("contents")
        assertEquals(listOf("user", "model", "user"), (0 until 3).map { contents.getJSONObject(it).getString("role") })
        val call = contents.getJSONObject(1).getJSONArray("parts").getJSONObject(1).getJSONObject("functionCall")
        assertEquals("express", call.getString("name"))
        val resultParts = contents.getJSONObject(2).getJSONArray("parts")
        val fnResp = resultParts.getJSONObject(0).getJSONObject("functionResponse")
        assertEquals("express", fnResp.getString("name"))
        assertEquals("ok", fnResp.getJSONObject("response").getString("result"))
        assertTrue(resultParts.getJSONObject(1).has("inlineData"))
    }

    @Test
    fun gemini_omitsEmptyParameters() {
        val decls = GeminiProvider.buildBody(request)
            .getJSONArray("tools").getJSONObject(0).getJSONArray("functionDeclarations")
        assertTrue(decls.getJSONObject(0).has("parameters")) // express ma parametry
        assertFalse(decls.getJSONObject(1).has("parameters")) // get_current_time – brak
    }

    @Test
    fun gemini_parsesFunctionCallAndKeepsRawPartForThoughtSignature() {
        val json = JSONObject("""{"candidates":[{"content":{"role":"model","parts":[
            {"text":"myślę","thought":true},
            {"functionCall":{"name":"express","args":{"emotion":"sad"}},"thoughtSignature":"sig123"}]}}]}""")
        val r = GeminiProvider.parse(json)
        assertEquals("", r.text) // "myśli" pominięte
        val call = r.toolCalls.single()
        assertEquals("sad", call.args.getString("emotion"))
        // Przy kolejnym zapytaniu do Gemini oryginalny fragment (z podpisem) wraca bez zmian
        val next = ChatRequest("s", listOf(ChatMessage(Role.ASSISTANT, listOf(call))), emptyList(), 100)
        val sent = GeminiProvider.buildBody(next).getJSONArray("contents").getJSONObject(0)
            .getJSONArray("parts").getJSONObject(0)
        assertEquals("sig123", sent.getString("thoughtSignature"))
    }

    // --- OpenRouter (format OpenAI) --------------------------------------------

    @Test
    fun openRouter_buildsToolCallsAndToolMessages() {
        val body = OpenRouterProvider.buildBody(request, "openrouter/free", withTools = true)
        val msgs = body.getJSONArray("messages")
        assertEquals("system", msgs.getJSONObject(0).getString("role"))
        val assistant = msgs.getJSONObject(2)
        val toolCall = assistant.getJSONArray("tool_calls").getJSONObject(0)
        assertEquals("call_1", toolCall.getString("id"))
        // argumenty jako tekst JSON
        assertEquals("happy", JSONObject(toolCall.getJSONObject("function").getString("arguments")).getString("emotion"))
        val tool = msgs.getJSONObject(3)
        assertEquals("tool", tool.getString("role"))
        assertEquals("call_1", tool.getString("tool_call_id"))
        // obraz z wyniku narzędzia idzie w osobnej wiadomości użytkownika
        val img = msgs.getJSONObject(4).getJSONArray("content").getJSONObject(0)
        assertEquals("image_url", img.getString("type"))
        assertTrue(img.getJSONObject("image_url").getString("url").startsWith("data:image/jpeg;base64,"))
        assertEquals(2, body.getJSONArray("tools").length())
    }

    @Test
    fun openRouter_withoutTools_flattensToolHistory() {
        val body = OpenRouterProvider.buildBody(request, "x:free", withTools = false)
        assertFalse(body.has("tools"))
        val msgs = body.getJSONArray("messages")
        for (i in 0 until msgs.length()) {
            assertFalse(msgs.getJSONObject(i).getString("role") == "tool")
            assertFalse(msgs.getJSONObject(i).has("tool_calls"))
        }
    }

    @Test
    fun openRouter_parsesToolCallsWithStringArguments() {
        val json = JSONObject("""{"choices":[{"message":{"role":"assistant","content":null,
            "tool_calls":[{"id":"c9","type":"function","function":{"name":"express","arguments":"{\"emotion\":\"curious\"}"}}]}}]}""")
        val r = OpenRouterProvider.parse(json)
        assertEquals("", r.text)
        assertEquals("c9", r.toolCalls.single().id)
        assertEquals("curious", r.toolCalls.single().args.getString("emotion"))
    }
}
