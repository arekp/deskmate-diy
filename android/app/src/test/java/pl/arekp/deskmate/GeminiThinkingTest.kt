package pl.arekp.deskmate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import pl.arekp.deskmate.ai.llm.ChatMessage
import pl.arekp.deskmate.ai.llm.ChatRequest
import pl.arekp.deskmate.ai.llm.GeminiProvider

class GeminiThinkingTest {
    @Test
    fun thinkingIsKeptLowPerModelFamily() {
        assertEquals("low", GeminiProvider.thinkingConfigFor("gemini-flash-latest")!!.getString("thinkingLevel"))
        assertEquals("low", GeminiProvider.thinkingConfigFor("gemini-3.7-flash")!!.getString("thinkingLevel"))
        assertEquals(0, GeminiProvider.thinkingConfigFor("gemini-2.5-flash")!!.getInt("thinkingBudget"))
        assertEquals(128, GeminiProvider.thinkingConfigFor("gemini-2.5-pro")!!.getInt("thinkingBudget"))
        assertNull(GeminiProvider.thinkingConfigFor("gemini-2.0-flash"))
    }

    @Test
    fun bodyCarriesThinkingConfigOnlyWhenGiven() {
        val req = ChatRequest("sys", listOf(ChatMessage.user("hej")), emptyList(), 400)
        val withCfg = GeminiProvider.buildBody(req, GeminiProvider.thinkingConfigFor("gemini-flash-latest"))
        assertEquals("low", withCfg.getJSONObject("generationConfig").getJSONObject("thinkingConfig").getString("thinkingLevel"))
        assertFalse(GeminiProvider.buildBody(req).getJSONObject("generationConfig").has("thinkingConfig"))
    }
}
