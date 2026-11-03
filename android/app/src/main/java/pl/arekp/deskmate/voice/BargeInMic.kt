package pl.arekp.deskmate.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Lekki nasłuch mikrofonu (tylko głośność): gdy robot mówi – do przerywania głosem,
 * a w spoczynku – żeby wiedzieć, że użytkownik zaczął mówić (wtedy dopiero startuje rozpoznawanie mowy).
 * Źródło VOICE_COMMUNICATION + systemowe wycinanie echa (AEC) i szumu, jeśli telefon je ma.
 * Dźwięk nie jest nigdzie zapisywany ani wysyłany – liczy się tylko głośność.
 *
 * [start] przed mówieniem, [stop] zaraz po – mikrofon musi być wolny, zanim zacznie słuchać SpeechRecognizer.
 */
class BargeInMic(
    private val onSpeech: () -> Unit,
    private val onInfo: (key: String, value: String) -> Unit = { _, _ -> },
    private val marginDb: Float = BargeInVad.DEFAULT_MARGIN_DB,
) {
    @Volatile private var running = false
    private var thread: Thread? = null

    @SuppressLint("MissingPermission") // RECORD_AUDIO sprawdzane w MainActivity przed startem
    fun start() {
        if (running) return
        val rate = 16_000
        val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val record = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION, rate,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, rate / 5),
            ).takeIf { it.state == AudioRecord.STATE_INITIALIZED }
        }.getOrNull() ?: run {
            onInfo("Przerywanie", "mikrofon niedostępny")
            return
        }
        val aec = if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(record.audioSessionId)?.apply { enabled = true } else null
        val ns = if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(record.audioSessionId)?.apply { enabled = true } else null
        val effects = "AEC ${if (aec != null) "tak" else "nie"}"

        running = true
        thread = Thread({
            val vad = BargeInVad(marginDb)
            val frame = ShortArray(rate / 50) // 20 ms
            var lastInfo = 0L
            var lastFire = 0L
            try {
                record.startRecording()
                while (running) {
                    val n = record.read(frame, 0, frame.size)
                    if (n <= 0) continue
                    var sum = 0.0
                    for (i in 0 until n) sum += frame[i].toDouble() * frame[i]
                    val db = (20 * log10(sqrt(sum / n) / 32768.0 + 1e-9)).toFloat()
                    val now = SystemClock.uptimeMillis()
                    // Zgłoszenie najwyżej co 2 s (nasłuch w spoczynku działa dłużej niż jedna wypowiedź)
                    if (vad.update(now, db) && now - lastFire > REARM_MS) {
                        lastFire = now
                        onSpeech()
                    }
                    if (now - lastInfo > 500) {
                        lastInfo = now
                        onInfo("Przerywanie", String.format(Locale.ROOT, "mikrofon %.0f dB, próg %.0f dB, %s", db, vad.thresholdDb, effects))
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Nasłuch przerwań", e)
            } finally {
                running = false
                runCatching { record.stop() }
                aec?.release()
                ns?.release()
                record.release()
            }
        }, "barge-in").apply { start() }
    }

    val isRunning: Boolean get() = running

    /** Zatrzymanie bez czekania (np. przy zamykaniu aplikacji). */
    fun requestStop() {
        running = false
    }

    /** Zatrzymuje nasłuch i zwalnia mikrofon (czeka na wątek poza wątkiem głównym). */
    suspend fun stop() {
        running = false
        val t = thread ?: return
        thread = null
        withContext(Dispatchers.IO) { t.join(400) }
    }

    private companion object {
        const val TAG = "BargeInMic"
        const val REARM_MS = 2_000L
    }
}
