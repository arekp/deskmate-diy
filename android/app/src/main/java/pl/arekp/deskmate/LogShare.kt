package pl.arekp.deskmate

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Raport diagnostyczny = nagłówek (wersja, telefon, ustawienia BEZ kluczy) + dziennik [DiagLog].
 * Do wysłania np. do aplikacji Claude („Udostępnij”) albo zapisania w Pobranych i dołączenia do rozmowy.
 */
object LogShare {

    fun report(context: Context, settings: Settings, profile: DeviceProfile, routerOrder: List<String>): String {
        val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        val header = buildString {
            appendLine("# DeskMate DIY – raport diagnostyczny ($now)")
            appendLine("Aplikacja: ${BuildConfig.VERSION_NAME}, build ${BuildConfig.BUILD_NUMBER}, commit ${BuildConfig.GIT_SHA}")
            appendLine("Telefon: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE}), Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
            appendLine("Profil: ${profile.label}")
            appendLine("Router: ${settings.routerMode.label}; kolejność: ${routerOrder.joinToString(" → ")}")
            appendLine(
                "Klucze: Gemini ${has(settings.geminiKey)}, OpenRouter ${has(settings.openRouterKey)}, Claude ${has(settings.claudeKey)}" +
                    " (same klucze nie są zapisywane)",
            )
            appendLine("Modele: Gemini=${settings.geminiModel}, OpenRouter=${settings.openRouterModel}, Claude=${settings.claudeModel}")
            appendLine(
                "Mowa: STT=${settings.sttService.ifBlank { "domyślne" }}, TTS=${settings.ttsEngine.ifBlank { "domyślny" }}, " +
                    "przerywanie głosem=${settings.bargeInVoice}, debug=${settings.debugMode}, miasto=${settings.homeCity.ifBlank { "-" }}",
            )
            appendLine("----")
        }
        return DiagLog.redact(header + DiagLog.contents().ifBlank { "(dziennik pusty)\n" }, secretsOf(settings))
    }

    fun secretsOf(settings: Settings): List<String> =
        listOf(settings.geminiKey, settings.openRouterKey, settings.claudeKey, settings.screenHelperToken, settings.logToken)

    /** Otwiera systemowe „Udostępnij” z plikiem .txt (np. do aplikacji Claude, Gmaila, Dysku). */
    fun share(context: Context, text: String) {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "deskmate-log-${stamp()}.txt").apply { writeText(text) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.logs", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "DeskMate – logi diagnostyczne")
            putExtra(Intent.EXTRA_TEXT, "Logi DeskMate DIY w załączniku – przeanalizuj błędy.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Wyślij logi").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Zapisuje raport w Pobranych (Android 10+). Zwraca nazwę pliku albo null. */
    fun saveToDownloads(context: Context, text: String): String? {
        if (Build.VERSION.SDK_INT < 29) return null
        val name = "deskmate-log-${stamp()}.txt"
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } ?: return null
        return name
    }

    private fun has(key: String) = if (key.isNotBlank()) "jest" else "brak"
    private fun stamp() = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
}
