package pl.arekp.deskmate

import android.content.Context
import pl.arekp.deskmate.ai.llm.LlmRouter

/**
 * Ustawienia wpisywane w aplikacji (przytrzymaj palec na ekranie z oczami).
 * Gdy pole jest puste, używana jest wartość z local.properties (BuildConfig) – wygodne przy budowaniu samemu.
 */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("deskmate", Context.MODE_PRIVATE)

    // --- Router modeli ---
    var routerMode: LlmRouter.Mode
        get() = runCatching { LlmRouter.Mode.valueOf(read(KEY_MODE, "")) }.getOrDefault(LlmRouter.Mode.AUTO)
        set(v) = write(KEY_MODE, v.name)

    // --- Gemini (darmowy pakiet, klucz z Google AI Studio) ---
    var geminiKey: String
        get() = read(KEY_GEMINI, BuildConfig.GEMINI_API_KEY)
        set(v) = write(KEY_GEMINI, v)

    var geminiModel: String
        get() = read(KEY_GEMINI_MODEL, DEFAULT_GEMINI_MODEL)
        set(v) = write(KEY_GEMINI_MODEL, v)

    // --- OpenRouter (darmowe modele) ---
    var openRouterKey: String
        get() = read(KEY_OPENROUTER, BuildConfig.OPENROUTER_API_KEY)
        set(v) = write(KEY_OPENROUTER, v)

    var openRouterModel: String
        get() = read(KEY_OPENROUTER_MODEL, DEFAULT_OPENROUTER_MODEL)
        set(v) = write(KEY_OPENROUTER_MODEL, v)

    // --- Claude (płatne API, console.anthropic.com) ---
    var claudeKey: String
        get() = read(KEY_CLAUDE, BuildConfig.ANTHROPIC_API_KEY)
        set(v) = write(KEY_CLAUDE, v)

    var claudeModel: String
        get() = read(KEY_CLAUDE_MODEL, BuildConfig.ANTHROPIC_MODEL)
        set(v) = write(KEY_CLAUDE_MODEL, v)

    /** Miasto użytkownika – domyślne dla pytań o pogodę. */
    var homeCity: String
        get() = read(KEY_HOME_CITY, "")
        set(v) = write(KEY_HOME_CITY, v)

    // --- Logi na GitHub (prywatne repozytorium – czyta je Claude, żeby poprawiać błędy) ---
    var logRepo: String
        get() = read(KEY_LOG_REPO, DEFAULT_LOG_REPO)
        set(v) = write(KEY_LOG_REPO, v)

    var logToken: String
        get() = read(KEY_LOG_TOKEN, "")
        set(v) = write(KEY_LOG_TOKEN, v)

    /** Wysyłaj logi automatycznie: po błędach (najwyżej co 10 min) i przy starcie po awarii. */
    var logAutoUpload: Boolean
        get() = prefs.getBoolean(KEY_LOG_AUTO, true)
        set(v) = prefs.edit().putBoolean(KEY_LOG_AUTO, v).apply()

    // --- Pomocnik ekranu PC ---
    var screenHelperUrl: String
        get() = read(KEY_SCREEN_URL, BuildConfig.SCREEN_HELPER_URL)
        set(v) = write(KEY_SCREEN_URL, v)

    var screenHelperToken: String
        get() = read(KEY_SCREEN_TOKEN, BuildConfig.SCREEN_HELPER_TOKEN)
        set(v) = write(KEY_SCREEN_TOKEN, v)

    // --- Mowa i diagnostyka ---
    /** Usługa rozpoznawania mowy: "" = domyślna systemowa, [STT_ON_DEVICE] albo komponent "pakiet/klasa". */
    var sttService: String
        get() = read(KEY_STT, "")
        set(v) = write(KEY_STT, v)

    /** Silnik syntezy mowy (nazwa pakietu), "" = domyślny systemowy. */
    var ttsEngine: String
        get() = read(KEY_TTS, "")
        set(v) = write(KEY_TTS, v)

    /** Przerywanie robota głosem (mikrofon nasłuchuje, gdy robot mówi). */
    var bargeInVoice: Boolean
        get() = prefs.getBoolean(KEY_BARGE_IN2, false)
        set(v) = prefs.edit().putBoolean(KEY_BARGE_IN2, v).apply()

    /** Okienko z podglądem kamery („co widzę”) w prawym górnym rogu. */
    var showCameraPreview: Boolean
        get() = prefs.getBoolean(KEY_CAMERA_PIP, true)
        set(v) = prefs.edit().putBoolean(KEY_CAMERA_PIP, v).apply()

    /** Tryb debug: informacje techniczne w pasku na dole ekranu. */
    var debugMode: Boolean
        get() = prefs.getBoolean(KEY_DEBUG, false)
        set(v) = prefs.edit().putBoolean(KEY_DEBUG, v).apply()

    private fun read(key: String, fallback: String): String =
        prefs.getString(key, null)?.takeIf { it.isNotBlank() } ?: fallback

    private fun write(key: String, value: String) {
        prefs.edit().putString(key, value.trim()).apply()
    }

    companion object {
        // aliasy *-latest bywały bez darmowego pakietu (429 przy pierwszym zapytaniu); GeminiModels i tak próbuje darmowych
        const val DEFAULT_GEMINI_MODEL = "gemini-3.8-flash"
        const val DEFAULT_OPENROUTER_MODEL = "openrouter/free"
        const val STT_ON_DEVICE = "on_device"
        const val DEFAULT_LOG_REPO = "arekp/deskmate-logs"

        private const val KEY_MODE = "routerMode"
        private const val KEY_GEMINI = "geminiKey"
        private const val KEY_GEMINI_MODEL = "geminiModel"
        private const val KEY_OPENROUTER = "openRouterKey"
        private const val KEY_OPENROUTER_MODEL = "openRouterModel"
        private const val KEY_CLAUDE = "apiKey" // zgodność z wersją 0.1
        private const val KEY_CLAUDE_MODEL = "model"
        private const val KEY_SCREEN_URL = "screenUrl"
        private const val KEY_SCREEN_TOKEN = "screenToken"
        private const val KEY_HOME_CITY = "homeCity"
        private const val KEY_LOG_REPO = "logRepo"
        private const val KEY_LOG_TOKEN = "logToken"
        private const val KEY_LOG_AUTO = "logAuto"
        private const val KEY_STT = "sttService"
        private const val KEY_TTS = "ttsEngine"
        private const val KEY_DEBUG = "debugMode"
        private const val KEY_CAMERA_PIP = "cameraPip"
        // nowy klucz: poprzednia wersja miała domyślnie włączone i przerywała sama sobie
        private const val KEY_BARGE_IN2 = "bargeInVoice2"
    }
}
