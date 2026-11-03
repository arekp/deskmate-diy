package pl.arekp.deskmate

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.arekp.deskmate.ai.WeatherTool
import pl.arekp.deskmate.ai.WebSearchTool
import pl.arekp.deskmate.ai.llm.ProviderException

class InternetToolsTest {

    @Test
    fun weather_describesCurrentAndDaily() {
        val json = JSONObject(
            """
            {"current":{"temperature_2m":7.6,"apparent_temperature":4.2,"relative_humidity_2m":81,"precipitation":0.0,
                        "weather_code":3,"wind_speed_10m":14.4},
             "daily":{"time":["2026-11-03","2026-11-04"],"weather_code":[61,0],
                      "temperature_2m_max":[9.1,11.0],"temperature_2m_min":[3.2,2.5],
                      "precipitation_probability_max":[80,5]}}
            """.trimIndent(),
        )
        val d = WeatherTool.describe("Kraków, Polska", json)
        assertTrue(d, d.startsWith("Pogoda: Kraków, Polska. Teraz: pochmurno, 8°C (odczuwalna 4°C), wiatr 14 km/h, wilgotność 81%."))
        assertTrue(d, d.contains("Dziś: słaby deszcz, 3…9°C, szansa opadu 80%."))
        assertTrue(d, d.contains("Jutro: bezchmurnie, 3…11°C, szansa opadu 5%."))
    }

    @Test
    fun wmoCodes() {
        assertEquals("burza", WeatherTool.wmo(95))
        assertEquals("mgła", WeatherTool.wmo(45))
        assertTrue(WeatherTool.wmo(1234).contains("1234"))
    }

    @Test
    fun grounded_textWithSources_skipsThoughts() {
        val json = JSONObject(
            """
            {"candidates":[{"content":{"parts":[{"text":"myślę…","thought":true},{"text":"Legia wygrała 2:1."}]},
              "groundingMetadata":{"groundingChunks":[{"web":{"title":"sport.pl"}},{"web":{"title":"sport.pl"}},{"web":{"title":"tvp.pl"}}]}}]}
            """.trimIndent(),
        )
        assertEquals("Legia wygrała 2:1. (Źródła: sport.pl, tvp.pl)", WebSearchTool.parseGrounded(json))
    }

    @Test
    fun errorDetail_isShortServerMessage() {
        val e = ProviderException.fromHttp(400, """{"error":{"code":400,"message":"API key not valid. Please pass a valid API key.","status":"INVALID_ARGUMENT"}}""", null)
        assertEquals("API key not valid. Please pass a valid API key.", e.detail)
        val or = ProviderException.fromHttp(429, """{"error":{"message":"Rate limit exceeded: free-models-per-day"}}""", null)
        assertEquals("Rate limit exceeded: free-models-per-day", or.detail)
        assertEquals("Bad Gateway", ProviderException.fromHttp(502, "Bad Gateway", null).detail)
    }
}
