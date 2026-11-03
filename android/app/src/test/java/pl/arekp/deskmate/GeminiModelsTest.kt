package pl.arekp.deskmate

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import pl.arekp.deskmate.ai.llm.GeminiModels
import pl.arekp.deskmate.ai.llm.ProviderException
import pl.arekp.deskmate.ai.llm.ProviderException.Kind

class GeminiModelsTest {
    @Test
    fun quotaOnAlias_fallsBackToFreeTierModel() = runBlocking {
        val tried = mutableListOf<String>()
        val (r, model) = GeminiModels.withFallback("gemini-test-latest") { m ->
            tried += m
            if (m == "gemini-test-latest") throw ProviderException.fromHttp(429, """{"error":{"message":"You exceeded your current quota"}}""", null)
            "ok:$m"
        }
        assertEquals("ok:${GeminiModels.FREE_TIER.first()}", r)
        assertEquals(GeminiModels.FREE_TIER.first(), model)
        assertEquals(listOf("gemini-test-latest", GeminiModels.FREE_TIER.first()), tried)
    }

    @Test
    fun badKey_isNotRetriedOnOtherModels() = runBlocking {
        var calls = 0
        try {
            GeminiModels.withFallback("gemini-x") { calls++; throw ProviderException(Kind.AUTH, "HTTP 403") }
            fail("powinien rzucić")
        } catch (e: ProviderException) {
            assertEquals(Kind.AUTH, e.kind)
        }
        assertEquals(1, calls)
    }

    @Test
    fun notFoundModel_triesNext() {
        assert(GeminiModels.isModelProblem(ProviderException.fromHttp(404, """{"error":{"message":"models/x is not found"}}""", null)))
    }
}

class GeminiAccountCooldownTest {
    @Test
    fun allModelsWithoutQuota_pauseGeminiForAWhile() = kotlinx.coroutines.runBlocking {
        var now = 1_000_000L
        GeminiModels.clock = { now }
        var calls = 0
        val quota = { ProviderException.fromHttp(429, """{"error":{"message":"You exceeded your current quota"}}""", null) }
        runCatching { GeminiModels.withFallback("gemini-acc") { calls++; throw quota() } }
        val first = calls
        runCatching { GeminiModels.withFallback("gemini-acc") { calls++; throw quota() } }
        assertEquals("w przerwie nie pytamy ponownie", first, calls)
        now += GeminiModels.ACCOUNT_COOLDOWN_MS + 1
        runCatching { GeminiModels.withFallback("gemini-acc") { calls++; "ok" } }
        assertEquals(first + 1, calls)
        GeminiModels.clock = { System.currentTimeMillis() }
    }
}
