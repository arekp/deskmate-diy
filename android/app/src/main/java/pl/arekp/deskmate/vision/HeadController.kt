package pl.arekp.deskmate.vision

import android.os.SystemClock
import pl.arekp.deskmate.Config
import pl.arekp.deskmate.DeviceProfile
import java.util.Locale
import kotlin.math.abs

/**
 * Zamienia pozycję twarzy w kadrze na komendy ruchu głowy ("L pan tilt ms").
 *
 * Kamera jest w telefonie, który porusza się razem z głową, więc to pętla sprzężenia zwrotnego:
 * każda klatka mówi, o ile stopni twarz jest obok środka, a my przesuwamy głowę o część tej odchyłki.
 */
class HeadController(
    private val profile: DeviceProfile,
    private val clock: () -> Long = { SystemClock.uptimeMillis() },
    private val send: (String) -> Unit,
) {
    var pan = 0f
        private set
    var tilt = 0f
        private set

    private var lastSentAt = 0L
    private var lastPan = Float.NaN
    private var lastTilt = Float.NaN
    private var pausedUntil = 0L

    /** Wstrzymaj śledzenie (np. na czas gestu, żeby go nie przerwać). */
    fun pauseFor(ms: Long) {
        pausedUntil = clock() + ms
    }

    fun onFace(face: FaceObservation) {
        if (!face.present) return
        val now = clock()
        if (now < pausedUntil) return

        val panSign = if (profile.invertPan) -1f else 1f
        val tiltSign = if (profile.invertTilt) -1f else 1f
        val errPan = face.x * profile.cameraHfovDeg / 2f * panSign
        val errTilt = face.y * profile.cameraVfovDeg / 2f * tiltSign

        if (abs(errPan) > Config.TRACK_DEADZONE_DEG) {
            pan = (pan + errPan * Config.TRACK_GAIN).coerceIn(Config.PAN_MIN, Config.PAN_MAX)
        }
        if (abs(errTilt) > Config.TRACK_DEADZONE_DEG) {
            tilt = (tilt + errTilt * Config.TRACK_GAIN).coerceIn(Config.TILT_MIN, Config.TILT_MAX)
        }

        val changed = lastPan.isNaN() || abs(pan - lastPan) > 0.8f || abs(tilt - lastTilt) > 0.8f
        if (changed && now - lastSentAt >= Config.LOOK_INTERVAL_MS) {
            send(String.format(Locale.US, "L %.1f %.1f %d", pan, tilt, 180))
            lastSentAt = now
            lastPan = pan
            lastTilt = tilt
        }
    }

    fun reset() {
        pan = 0f
        tilt = 0f
        lastPan = Float.NaN
        lastTilt = Float.NaN
    }
}
