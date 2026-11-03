package pl.arekp.deskmate

import android.os.Build
import android.util.Base64
import org.json.JSONObject
import pl.arekp.deskmate.ai.llm.HttpJson
import pl.arekp.deskmate.ai.llm.ProviderException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Wysyła raport diagnostyczny do PRYWATNEGO repozytorium GitHub (domyślnie arekp/deskmate-logs),
 * skąd Claude może go odczytać i poprawiać błędy – bez ręcznego przesyłania plików.
 *
 * Plik: `logs/<model-telefonu>/<data_godzina>.txt` (GitHub Contents API).
 * Token: „fine-grained” z dostępem TYLKO do tego repozytorium, uprawnienie Contents: Read and write.
 * Do publicznego repozytorium nie wysyła (logi zawierają m.in. treść rozmów).
 */
class LogUploader(private val settings: Settings) {

    sealed class Result(val message: String) {
        class Ok(val path: String) : Result("Wysłano: $path")
        class Failed(message: String) : Result(message)
    }

    fun isConfigured() = settings.logToken.isNotBlank() && settings.logRepo.contains('/')

    /**
     * Tryb deweloperski: nadpisuje jeden plik `logs/<model>/live.txt` (Claude czyta go na bieżąco).
     * Zwraca wynik jak [upload].
     */
    suspend fun uploadLive(report: String): Result = upload(report, "na żywo", live = true)

    suspend fun upload(report: String, reason: String, live: Boolean = false): Result {
        if (!isConfigured()) return Result.Failed("Brak tokenu GitHub albo nazwy repozytorium (ustawienia → Logi).")
        val repo = settings.logRepo.trim()
        val headers = mapOf(
            "Authorization" to "Bearer ${settings.logToken.trim()}",
            "Accept" to "application/vnd.github+json",
            "X-GitHub-Api-Version" to "2022-11-28",
            "User-Agent" to "DeskMateDIY",
        )
        return try {
            val info = HttpJson.get("https://api.github.com/repos/$repo", headers)
            if (!info.optBoolean("private", false)) {
                return Result.Failed("Repozytorium $repo jest publiczne – ze względów prywatności logi wysyłam tylko do prywatnego.")
            }
            val device = Build.MODEL.replace(Regex("[^A-Za-z0-9_-]"), "_")
            val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
            val path = if (live) "logs/$device/live.txt" else "logs/$device/$stamp.txt"
            val url = "https://api.github.com/repos/$repo/contents/$path"
            val body = JSONObject()
                .put("message", "Logi DeskMate ($reason) – $device $stamp")
                .put("content", Base64.encodeToString(report.toByteArray(), Base64.NO_WRAP))
            if (live) {
                // nadpisanie istniejącego pliku wymaga jego „sha”
                runCatching { HttpJson.get(url, headers).optString("sha") }.getOrNull()
                    ?.takeIf { it.isNotBlank() }?.let { body.put("sha", it) }
            }
            HttpJson.put(url, headers, body)
            if (!live) DiagLog.i("Logi", "Wysłano na GitHub: $repo/$path ($reason)")
            Result.Ok(path)
        } catch (e: ProviderException) {
            val why = when (e.kind) {
                ProviderException.Kind.AUTH -> "zły token albo brak uprawnienia Contents: write"
                ProviderException.Kind.BAD_REQUEST -> "repozytorium nie istnieje albo token go nie widzi"
                else -> e.detail.ifBlank { e.kind.name }
            }
            DiagLog.w("Logi", "Wysyłka na GitHub nieudana: $why")
            Result.Failed("Nie wysłano: $why")
        }
    }
}
