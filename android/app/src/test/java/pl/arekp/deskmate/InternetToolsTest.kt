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

class NewsSearchTest {
    @Test
    fun wikiQuery_dropsDatesAndFreshnessWords() {
        assertEquals("Trybunał Konstytucyjny Polska", WebSearchTool.wikiQuery("Trybunał Konstytucyjny Polska najnowsze informacje status listopad 2026"))
    }

    @Test
    fun parsesGoogleNewsRss() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0"><channel><title>wyniki</title>
              <item><title>Sejm przyjął ustawę o TK - Polsat News</title><pubDate>Tue, 03 Nov 2026 17:20:00 GMT</pubDate>
                <source url="https://www.polsatnews.pl">Polsat News</source></item>
              <item><title>Prezydent zawetował nowelizację - TVN24</title><pubDate>Tue, 03 Nov 2026 15:05:00 GMT</pubDate>
                <source url="https://tvn24.pl">TVN24</source></item>
            </channel></rss>
        """.trimIndent()
        val items = WebSearchTool.parseNewsRss(xml, 6)
        assertEquals(2, items.size)
        assertTrue(items[0], items[0].startsWith("Sejm przyjął ustawę o TK – Polsat News ("))
        assertTrue(items[1], items[1].startsWith("Prezydent zawetował nowelizację – TVN24"))
    }
}
