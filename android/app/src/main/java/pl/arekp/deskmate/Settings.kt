package pl.arekp.deskmate

import android.content.Context

/**
 * Ustawienia wpisywane w aplikacji (przytrzymaj palec na ekranie z oczami).
 * Gdy pole jest puste, używana jest wartość z local.properties (BuildConfig) – wygodne przy budowaniu samemu.
 */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("deskmate", Context.MODE_PRIVATE)

    var apiKey: String
        get() = read(KEY_API, BuildConfig.ANTHROPIC_API_KEY)
        set(v) = write(KEY_API, v)

    var model: String
        get() = read(KEY_MODEL, BuildConfig.ANTHROPIC_MODEL)
        set(v) = write(KEY_MODEL, v)

    var screenHelperUrl: String
        get() = read(KEY_SCREEN_URL, BuildConfig.SCREEN_HELPER_URL)
        set(v) = write(KEY_SCREEN_URL, v)

    var screenHelperToken: String
        get() = read(KEY_SCREEN_TOKEN, BuildConfig.SCREEN_HELPER_TOKEN)
        set(v) = write(KEY_SCREEN_TOKEN, v)

    private fun read(key: String, fallback: String): String =
        prefs.getString(key, null)?.takeIf { it.isNotBlank() } ?: fallback

    private fun write(key: String, value: String) {
        prefs.edit().putString(key, value.trim()).apply()
    }

    private companion object {
        const val KEY_API = "apiKey"
        const val KEY_MODEL = "model"
        const val KEY_SCREEN_URL = "screenUrl"
        const val KEY_SCREEN_TOKEN = "screenToken"
    }
}
