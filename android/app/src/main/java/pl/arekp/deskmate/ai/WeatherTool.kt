package pl.arekp.deskmate.ai

import org.json.JSONObject
import pl.arekp.deskmate.ai.llm.HttpJson
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Pogoda z Open-Meteo (open-meteo.com) – darmowe, bez klucza.
 * Najpierw geokodowanie nazwy miasta, potem prognoza: teraz + kilka dni.
 */
class WeatherTool(private val homeCity: () -> String) : AssistantTool {
    override val name = "get_weather"
    override val description =
        "Aktualna pogoda i prognoza (do 7 dni) dla miasta. Bez podanego miasta – miasto użytkownika z ustawień. " +
            "Używaj zawsze, gdy pytają o pogodę, temperaturę, deszcz, wiatr, czy wziąć parasol itp."
    override val inputSchema = objectSchema(
        "city" to JSONObject().put("type", "string").put("description", "Nazwa miasta, np. „Kraków” (opcjonalnie)"),
        "days" to JSONObject().put("type", "integer").put("description", "Ile dni prognozy, 1–7 (domyślnie 2)"),
    )

    override suspend fun run(input: JSONObject): ToolResult {
        val city = input.optString("city").trim().ifBlank { homeCity().trim() }
        if (city.isBlank()) return ToolResult.text("Nie wiem, o które miasto chodzi – zapytaj użytkownika.", isError = true)
        val days = input.optInt("days", 2).coerceIn(1, 7)

        val q = URLEncoder.encode(city, "UTF-8")
        val geo = HttpJson.get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=1&language=pl&format=json")
        val place = geo.optJSONArray("results")?.optJSONObject(0)
            ?: return ToolResult.text("Nie znalazłem miejscowości „$city”.", isError = true)
        val lat = place.getDouble("latitude")
        val lon = place.getDouble("longitude")
        val url = String.format(
            Locale.ROOT,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
                "&current=temperature_2m,apparent_temperature,relative_humidity_2m,precipitation,weather_code,wind_speed_10m" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,precipitation_sum" +
                "&timezone=auto&forecast_days=%d&wind_speed_unit=kmh",
            lat, lon, days,
        )
        val placeName = listOfNotNull(place.optString("name").ifBlank { null }, place.optString("country").ifBlank { null })
            .joinToString(", ")
        return ToolResult.text(describe(placeName, HttpJson.get(url)))
    }

    companion object {
        /** Zamienia odpowiedź Open-Meteo na krótki opis po polsku (dla modelu). */
        fun describe(place: String, f: JSONObject): String = buildString {
            append("Pogoda: $place. ")
            f.optJSONObject("current")?.let { c ->
                append("Teraz: ${wmo(c.optInt("weather_code"))}, ${c.optDouble("temperature_2m").roundToInt()}°C")
                if (c.has("apparent_temperature")) append(" (odczuwalna ${c.optDouble("apparent_temperature").roundToInt()}°C)")
                if (c.has("wind_speed_10m")) append(", wiatr ${c.optDouble("wind_speed_10m").roundToInt()} km/h")
                if (c.has("relative_humidity_2m")) append(", wilgotność ${c.optInt("relative_humidity_2m")}%")
                if (c.optDouble("precipitation", 0.0) > 0) append(", opad ${c.optDouble("precipitation")} mm")
                append(". ")
            }
            f.optJSONObject("daily")?.let { d ->
                val dates = d.optJSONArray("time") ?: return@let
                for (i in 0 until dates.length()) {
                    val label = when (i) { 0 -> "Dziś"; 1 -> "Jutro"; else -> dates.optString(i) }
                    append("$label: ${wmo(d.optJSONArray("weather_code")?.optInt(i) ?: 0)}, ")
                    append("${d.optJSONArray("temperature_2m_min")?.optDouble(i)?.roundToInt()}…")
                    append("${d.optJSONArray("temperature_2m_max")?.optDouble(i)?.roundToInt()}°C")
                    d.optJSONArray("precipitation_probability_max")?.optInt(i, -1)?.takeIf { it >= 0 }
                        ?.let { append(", szansa opadu $it%") }
                    append(". ")
                }
            }
        }.trim()

        /** Kody pogody WMO (używane przez Open-Meteo) po polsku. */
        fun wmo(code: Int): String = when (code) {
            0 -> "bezchmurnie"
            1 -> "przeważnie słonecznie"
            2 -> "częściowe zachmurzenie"
            3 -> "pochmurno"
            45, 48 -> "mgła"
            51, 53, 55 -> "mżawka"
            56, 57 -> "marznąca mżawka"
            61 -> "słaby deszcz"
            63 -> "deszcz"
            65 -> "ulewny deszcz"
            66, 67 -> "marznący deszcz"
            71 -> "słaby śnieg"
            73 -> "śnieg"
            75 -> "intensywny śnieg"
            77 -> "ziarna śniegu"
            80, 81 -> "przelotny deszcz"
            82 -> "gwałtowne ulewy"
            85, 86 -> "przelotny śnieg"
            95 -> "burza"
            96, 99 -> "burza z gradem"
            else -> "pogoda nieznana (kod $code)"
        }
    }
}
