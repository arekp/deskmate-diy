package pl.arekp.deskmate

import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors

/**
 * Dziennik diagnostyczny do pliku – żeby dało się go wysłać (np. do aplikacji Claude) i poprawiać błędy.
 *
 * - zapis w tle do `files/logs/deskmate.log`, rotacja po [MAX_BYTES] (poprzedni plik: deskmate.log.1),
 * - KLUCZE API SĄ WYCINANE przed zapisem: znane wartości z ustawień + wzorce (AIza…, sk-or-…, sk-ant-…, Bearer …),
 * - awarie aplikacji (nieobsłużone wyjątki) zapisują pełny ślad stosu.
 * Bez [init] (np. w testach JVM) działa jako no-op poza [redact].
 */
object DiagLog {
    private const val MAX_BYTES = 1_000_000L
    private val TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS")
    private val writer = Executors.newSingleThreadExecutor { r -> Thread(r, "diaglog").apply { isDaemon = true } }

    @Volatile private var file: File? = null
    @Volatile private var secrets: () -> List<String> = { emptyList() }

    fun init(dir: File, knownSecrets: () -> List<String>) {
        val logs = File(dir, "logs").apply { mkdirs() }
        file = File(logs, "deskmate.log")
        secrets = knownSecrets
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            writeNow("E", "CRASH", "Awaria w wątku ${t.name}: ${e}\n${stackTrace(e)}")
            previous?.uncaughtException(t, e)
        }
        i("App", "=== start aplikacji ===")
    }

    fun i(tag: String, msg: String) = log("I", tag, msg)
    fun w(tag: String, msg: String, e: Throwable? = null) = log("W", tag, msg + (e?.let { " | $it" } ?: ""))
    fun e(tag: String, msg: String, e: Throwable? = null) {
        errorCount++
        log("E", tag, msg + (e?.let { " | $it\n${stackTrace(it)}" } ?: ""))
    }

    /** Liczba błędów (E) od startu – do automatycznej wysyłki logów. */
    @Volatile var errorCount = 0
        private set

    /** Czy poprzednie uruchomienie zakończyło się awarią (wpis CRASH po ostatnim starcie). */
    fun previousRunCrashed(): Boolean {
        val text = contents()
        val lastStart = text.lastIndexOf("=== start aplikacji ===")
        val beforeThisStart = if (lastStart > 0) text.substring(0, lastStart) else ""
        val prevStart = beforeThisStart.lastIndexOf("=== start aplikacji ===")
        return beforeThisStart.substring(maxOf(prevStart, 0)).contains("E/CRASH")
    }

    private fun log(level: String, tag: String, msg: String) {
        runCatching {
            when (level) {
                "E" -> Log.e("DeskMate/$tag", msg)
                "W" -> Log.w("DeskMate/$tag", msg)
                else -> Log.i("DeskMate/$tag", msg)
            }
        }
        if (file == null) return
        val stamp = LocalDateTime.now()
        runCatching { writer.execute { writeLine(stamp, level, tag, msg) } }
    }

    private fun writeNow(level: String, tag: String, msg: String) = writeLine(LocalDateTime.now(), level, tag, msg)

    @Synchronized
    private fun writeLine(stamp: LocalDateTime, level: String, tag: String, msg: String) {
        val f = file ?: return
        runCatching {
            if (f.length() > MAX_BYTES) {
                val old = File(f.parentFile, f.name + ".1")
                old.delete()
                f.renameTo(old)
            }
            f.appendText("${stamp.format(TIME)} $level/$tag: ${redact(msg, secrets())}\n")
        }
    }

    /** Cała treść dziennika (starszy plik + bieżący). */
    fun contents(): String {
        val f = file ?: return ""
        val old = File(f.parentFile, f.name + ".1")
        return buildString {
            if (old.exists()) append(old.readText())
            if (f.exists()) append(f.readText())
        }
    }

    fun clear() {
        val f = file ?: return
        runCatching { writer.execute { f.delete(); File(f.parentFile, f.name + ".1").delete() } }
    }

    private val KEY_PATTERNS = listOf(
        Regex("AIza[0-9A-Za-z_\\-]{20,}"),         // Google / Gemini
        Regex("sk-or-[0-9A-Za-z_\\-]{10,}"),       // OpenRouter
        Regex("sk-ant-[0-9A-Za-z_\\-]{10,}"),      // Anthropic
        Regex("(github_pat_|ghp_|gho_)[0-9A-Za-z_]{20,}"), // GitHub
        Regex("(?i)(bearer\\s+)[^\\s\"']+"),         // nagłówki
        Regex("(?i)(x-goog-api-key|x-api-key|x-token)([\"'=:\\s]+)[^\\s\"',}]+"),
    )

    /** Usuwa klucze i tokeny z tekstu. */
    fun redact(text: String, known: List<String> = emptyList()): String {
        var out = text
        known.filter { it.length >= 8 }.forEach { out = out.replace(it, "***") }
        KEY_PATTERNS.forEachIndexed { i, re ->
            out = when (i) {
                4 -> re.replace(out) { it.groupValues[1] + "***" }
                5 -> re.replace(out) { it.groupValues[1] + it.groupValues[2] + "***" }
                else -> re.replace(out, "***")
            }
        }
        return out
    }

    private fun stackTrace(e: Throwable): String = StringWriter().also { e.printStackTrace(PrintWriter(it)) }.toString()
}
