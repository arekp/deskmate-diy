package pl.arekp.deskmate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.arekp.deskmate.ai.llm.ProviderException
import pl.arekp.deskmate.mcp.HomeIntent

class QuotaAndHomeTest {
    private val perMinute = """
        {"error":{"code":429,"message":"You exceeded your current quota, please check your plan and billing details.","status":"RESOURCE_EXHAUSTED",
         "details":[{"@type":"type.googleapis.com/google.rpc.QuotaFailure","violations":[{"quotaMetric":"generativelanguage.googleapis.com/generate_content_free_tier_requests",
           "quotaId":"GenerateRequestsPerMinutePerProjectPerModel-FreeTier","quotaValue":"15"}]},
          {"@type":"type.googleapis.com/google.rpc.RetryInfo","retryDelay":"37s"}]}}
    """.trimIndent()

    @Test
    fun geminiPerMinuteQuota_isShortAndHasRetryDelay() {
        val e = ProviderException.fromHttp(429, perMinute, null)
        assertEquals(37_000L, e.retryAfterMs)
        assertEquals("GenerateRequestsPerMinutePerProjectPerModel-FreeTier", e.quotaId)
        assertFalse(ProviderException.isDailyQuota(e))
    }

    @Test
    fun geminiDailyQuota_isDetected() {
        val daily = perMinute.replace("GenerateRequestsPerMinutePerProjectPerModel", "GenerateRequestsPerDayPerProjectPerModel")
        assertTrue(ProviderException.isDailyQuota(ProviderException.fromHttp(429, daily, null)))
    }

    @Test
    fun homeIntent() {
        assertTrue(HomeIntent.matches("Włącz światło w salonie"))
        assertTrue(HomeIntent.matches("jaka jest temperatura w sypialni"))
        assertTrue(HomeIntent.matches("zgaś wszystko"))
        assertFalse(HomeIntent.matches("jaka jest pogoda w Warszawie"))
        assertFalse(HomeIntent.matches("opowiedz dowcip"))
    }
}
