package pl.arekp.deskmate

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import pl.arekp.deskmate.ai.llm.ChatRequest
import pl.arekp.deskmate.ai.llm.ChatResponse
import pl.arekp.deskmate.ai.llm.ClaudeProvider
import pl.arekp.deskmate.ai.llm.GeminiProvider
import pl.arekp.deskmate.ai.llm.LlmProvider
import pl.arekp.deskmate.ai.llm.LlmRouter
import pl.arekp.deskmate.ai.llm.LlmRouter.Mode
import pl.arekp.deskmate.ai.llm.OpenRouterProvider
import pl.arekp.deskmate.ai.llm.Part
import pl.arekp.deskmate.ai.llm.ProviderException
import pl.arekp.deskmate.ai.llm.ProviderException.Kind

class LlmRouterTest {

    private class Fake(
        override val id: String,
        override val isFree: Boolean,
        var configured: Boolean = true,
    ) : LlmProvider {
        override val label: String get() = id
        val failures = ArrayDeque<ProviderException>()
        var calls = 0
        override fun isConfigured() = configured
        override suspend fun complete(request: ChatRequest): ChatResponse {
            calls++
            failures.removeFirstOrNull()?.let { throw it }
            return ChatResponse(listOf(Part.Text("odpowiedź od $id")))
        }
    }

    private val gemini = Fake(GeminiProvider.ID, isFree = true)
    private val openrouter = Fake(OpenRouterProvider.ID, isFree = true)
    private val claude = Fake(ClaudeProvider.ID, isFree = false)
    private var mode = Mode.AUTO
    private var now = 0L
    private val router = LlmRouter(listOf(gemini, openrouter, claude), { mode }, { now })
    private val request = ChatRequest("system", emptyList(), emptyList(), 100)

    private fun ask() = runBlocking { router.complete(request).provider.id }

    @Test
    fun auto_prefersFreeProviders() {
        assertEquals(listOf("gemini", "openrouter", "claude"), router.order().map { it.id })
        assertEquals("gemini", ask())
    }

    @Test
    fun quality_prefersClaude() {
        mode = Mode.QUALITY
        assertEquals("claude", ask())
    }

    @Test
    fun freeOnly_neverUsesPaid() {
        mode = Mode.FREE_ONLY
        gemini.failures += ProviderException(Kind.RATE_LIMIT, "429")
        openrouter.failures += ProviderException(Kind.RATE_LIMIT, "429")
        try {
            ask()
            fail("powinien rzucić AllProvidersFailed")
        } catch (e: LlmRouter.AllProvidersFailed) {
            assertEquals(setOf("gemini", "openrouter"), e.errors.keys)
        }
        assertEquals(0, claude.calls)
    }

    @Test
    fun rateLimit_fallsBackAndCoolsDown() {
        gemini.failures += ProviderException(Kind.RATE_LIMIT, "429", retryAfterMs = 10_000)
        assertEquals("openrouter", ask())
        // Gemini na ławce kar – kolejne zapytanie nawet go nie próbuje
        assertEquals("openrouter", ask())
        assertEquals(1, gemini.calls)
        // Po upływie Retry-After wraca do Gemini
        now += 10_001
        assertEquals("gemini", ask())
    }

    @Test
    fun rateLimitWithoutRetryAfter_usesDefaultCooldown() {
        gemini.failures += ProviderException(Kind.RATE_LIMIT, "429")
        ask()
        now += LlmRouter.RATE_LIMIT_COOLDOWN_MS - 1
        assertEquals("openrouter", ask())
        now += 2
        assertEquals("gemini", ask())
    }

    @Test
    fun badRequest_triesNextWithoutPenalty() {
        gemini.failures += ProviderException(Kind.BAD_REQUEST, "400")
        assertEquals("openrouter", ask())
        assertEquals("gemini", ask())
    }

    @Test
    fun authError_longCooldown() {
        gemini.failures += ProviderException(Kind.AUTH, "401")
        ask()
        now += 5 * 60_000
        assertEquals("openrouter", ask())
        now += LlmRouter.AUTH_COOLDOWN_MS
        assertEquals("gemini", ask())
    }

    @Test
    fun allFree_fail_thenPaidClaudeInAuto() {
        gemini.failures += ProviderException(Kind.QUOTA, "402")
        openrouter.failures += ProviderException(Kind.SERVER, "503")
        assertEquals("claude", ask())
    }

    @Test
    fun unconfiguredProvidersAreSkipped() {
        gemini.configured = false
        assertEquals("openrouter", ask())
        assertEquals(0, gemini.calls)
        assertTrue(router.isAnyConfigured())
    }

    @Test
    fun nothingConfigured() {
        listOf(gemini, openrouter, claude).forEach { it.configured = false }
        assertFalse(router.isAnyConfigured())
    }

    @Test
    fun whenEveryoneIsCoolingDown_stillTries() {
        mode = Mode.GEMINI
        gemini.failures += ProviderException(Kind.SERVER, "500")
        runCatching { ask() }
        assertEquals("gemini", ask()) // jedyny dostępny – próbuje mimo kary
    }

    @Test
    fun httpCodesAreClassified() {
        assertEquals(Kind.RATE_LIMIT, ProviderException.fromHttp(429, "", "7").kind)
        assertEquals(7_000L, ProviderException.fromHttp(429, "", "7").retryAfterMs)
        assertEquals(Kind.QUOTA, ProviderException.fromHttp(402, "", null).kind)
        assertEquals(Kind.AUTH, ProviderException.fromHttp(401, "", null).kind)
        assertEquals(Kind.SERVER, ProviderException.fromHttp(529, "", null).kind)
        assertEquals(Kind.BAD_REQUEST, ProviderException.fromHttp(400, "", null).kind)
    }
}
