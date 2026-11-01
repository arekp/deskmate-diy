package pl.arekp.deskmate.ai

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import pl.arekp.deskmate.Settings
import pl.arekp.deskmate.ai.llm.ImageData
import pl.arekp.deskmate.ui.Emotion
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Pozwala modelowi okazywać emocje (oczy) i gestykulować (serwa). */
class ExpressTool(private val onExpress: (Emotion, String?) -> Unit) : AssistantTool {
    override val name = "express"
    override val description =
        "Pokaż emocję na twarzy robota i opcjonalnie wykonaj gest głową. " +
            "Używaj naturalnie, np. kiwnij przy zgodzie, pokaż radość przy dobrej wiadomości."
    override val inputSchema = objectSchema(
        "emotion" to enumProp(
            "Emocja widoczna w oczach",
            listOf("neutral", "happy", "sad", "curious", "thinking", "sleepy", "surprised"),
        ),
        "gesture" to enumProp(
            "Gest głową",
            listOf("none", "nod", "shake", "happy", "sad", "curious"),
        ),
        required = listOf("emotion"),
    )

    override suspend fun run(input: JSONObject): ToolResult {
        val gesture = input.optString("gesture", "none").takeIf { it != "none" && it.isNotBlank() }
        onExpress(Emotion.parse(input.optString("emotion")), gesture)
        return ToolResult.text("ok")
    }
}

class TimeTool : AssistantTool {
    override val name = "get_current_time"
    override val description = "Zwraca aktualną datę, dzień tygodnia i godzinę u użytkownika."
    override val inputSchema = objectSchema()

    override suspend fun run(input: JSONObject): ToolResult {
        val now = ZonedDateTime.now()
        val fmt = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy, HH:mm", Locale.forLanguageTag("pl-PL"))
        return ToolResult.text("${now.format(fmt)} (${now.zone})")
    }
}

/**
 * "Spójrz na mój ekran" – pobiera zrzut ekranu z komputera (desktop/screen_helper.py).
 * Działa tylko na wyraźną prośbę użytkownika; zrzut trafia do modelu i nie jest zapisywany.
 */
class ScreenTool(private val settings: Settings) : AssistantTool {
    override val name = "look_at_screen"
    override val description =
        "Pobiera aktualny zrzut ekranu komputera użytkownika. Używaj TYLKO, gdy użytkownik " +
            "wyraźnie prosi, żebyś spojrzał na jego ekran / pomógł z tym, co ma otwarte."
    override val inputSchema = objectSchema()

    private val http = OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS).build()

    override suspend fun run(input: JSONObject): ToolResult = withContext(Dispatchers.IO) {
        val baseUrl = settings.screenHelperUrl
        if (baseUrl.isBlank()) {
            return@withContext ToolResult.text(
                "Pomocnik ekranu nie jest skonfigurowany (ustawienia aplikacji + desktop/screen_helper.py).",
                isError = true,
            )
        }
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/screenshot")
            .header("X-Token", settings.screenHelperToken)
            .build()
        http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext ToolResult.text("Pomocnik ekranu zwrócił ${resp.code}", true)
            val bytes = resp.body?.bytes() ?: return@withContext ToolResult.text("Pusty zrzut", true)
            ToolResult(
                text = "Zrzut ekranu użytkownika.",
                images = listOf(ImageData("image/jpeg", Base64.encodeToString(bytes, Base64.NO_WRAP))),
            )
        }
    }
}
