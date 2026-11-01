package pl.arekp.deskmate

import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import pl.arekp.deskmate.ai.AssistantTool
import pl.arekp.deskmate.ai.ClaudeClient
import pl.arekp.deskmate.ai.ExpressTool
import pl.arekp.deskmate.ai.ScreenTool
import pl.arekp.deskmate.ai.TimeTool
import pl.arekp.deskmate.ble.RobotLink
import pl.arekp.deskmate.ui.Emotion
import pl.arekp.deskmate.ui.UiState
import pl.arekp.deskmate.vision.FaceObservation
import pl.arekp.deskmate.vision.FaceTracker
import pl.arekp.deskmate.vision.HeadController
import pl.arekp.deskmate.voice.Speech

/**
 * "Mózg" robota: łączy kamerę, głos, model językowy, oczy na ekranie i ruch głowy.
 *
 * Zachowanie:
 *  - widzi twarz -> obraca głowę w jej stronę, oczy patrzą na użytkownika,
 *  - patrzysz w kamerę przez chwilę -> zaczyna słuchać (bez słowa-klucza),
 *  - po wypowiedzi pyta model, mówi odpowiedź, okazuje emocje i gestykuluje,
 *  - nikogo długo nie ma -> zasypia; wraca twarz -> budzi się,
 *  - pogłaskanie (czujnik dotyku) -> radość.
 */
class DeskMateBrain(private val activity: ComponentActivity) {

    val profile: DeviceProfile = DeviceProfile.detect()

    private val _ui = MutableStateFlow(UiState(eyeScale = profile.eyeScale))
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private val scope = activity.lifecycleScope
    private val robot = RobotLink(activity, scope)
    private val head = HeadController(profile) { robot.send(it) }
    private val faces = FaceTracker(activity, activity)
    private val speech = Speech(activity)
    private val claude = ClaudeClient(buildTools())

    private var faceSince = 0L
    private var lastFaceAt = SystemClock.uptimeMillis()
    private var asleep = false
    private var emptyListens = 0
    private var resetEmotionJob: Job? = null
    private var started = false

    fun start() {
        if (started) return
        started = true
        speech.init()
        robot.start()
        faces.start()

        scope.launch { faces.faces.collect { onFace(it) } }
        scope.launch { robot.events.collect { if (it.startsWith("TOUCH")) onTouch() } }
        scope.launch {
            robot.state.collect { s -> _ui.update { it.copy(status = "${profile.label} · ${s.label}") } }
        }
        scope.launch { conversationLoop() }
        scope.launch { sleepWatcher() }
    }

    fun stop() {
        robot.stop()
        faces.stop()
        speech.shutdown()
    }

    private fun buildTools(): List<AssistantTool> = listOfNotNull(
        ExpressTool { emotion, gesture ->
            setEmotion(emotion, holdMs = 4000)
            gesture?.let { gesture(it) }
        },
        TimeTool(),
        ScreenTool.fromConfig(),
        // TODO: GmailTool, CalendarTool, SlackTool… – patrz docs/roadmapa.md
    )

    // --- Wzrok ---------------------------------------------------------------

    private fun onFace(f: FaceObservation) {
        val now = SystemClock.uptimeMillis()
        if (f.present) {
            lastFaceAt = now
            if (faceSince == 0L) {
                faceSince = now
                emptyListens = 0
            }
            if (asleep) wakeUp()
            head.onFace(f)
            _ui.update { it.copy(gazeX = f.x * 0.6f, gazeY = f.y * 0.6f) }
        } else {
            faceSince = 0L
            _ui.update { it.copy(gazeX = 0f, gazeY = 0f) }
        }
    }

    private suspend fun sleepWatcher() {
        while (scope.isActive) {
            delay(2000)
            if (!asleep && SystemClock.uptimeMillis() - lastFaceAt > Config.SLEEP_AFTER_MS) {
                asleep = true
                head.reset()
                gesture("sleep", pauseMs = Long.MAX_VALUE / 4)
                setEmotion(Emotion.SLEEPY)
            }
        }
    }

    private fun wakeUp() {
        asleep = false
        head.pauseFor(0)
        gesture("wake")
        setEmotion(Emotion.HAPPY, holdMs = 1500)
    }

    // --- Rozmowa -------------------------------------------------------------

    private suspend fun conversationLoop() {
        while (scope.isActive) {
            val f = faces.faces.value
            val engaged = !asleep && f.present && f.attentive &&
                faceSince != 0L && SystemClock.uptimeMillis() - faceSince > Config.ENGAGE_AFTER_MS
            if (!engaged || !speech.canListen || speech.speaking.value) {
                delay(300)
                continue
            }

            setEmotion(Emotion.LISTENING)
            _ui.update { it.copy(listening = true) }
            val heard = speech.listenOnce()
            _ui.update { it.copy(listening = false) }

            if (heard.isNullOrBlank()) {
                // Cisza – nie nasłuchuj bez przerwy, wydłużaj przerwy aż twarz zniknie i wróci
                emptyListens++
                setEmotion(Emotion.NEUTRAL)
                delay((1000L * emptyListens).coerceAtMost(10_000L))
                continue
            }
            emptyListens = 0

            _ui.update { it.copy(caption = "„$heard”") }
            setEmotion(Emotion.THINKING)
            val answer = claude.ask(heard)
            if (_ui.value.emotion == Emotion.THINKING) setEmotion(Emotion.NEUTRAL)
            _ui.update { it.copy(caption = answer) }
            speech.say(answer)
            delay(400)
        }
    }

    // --- Dotyk, emocje, gesty --------------------------------------------------

    private fun onTouch() {
        if (asleep) wakeUp()
        setEmotion(Emotion.HAPPY, holdMs = 2500)
    }

    private fun gesture(name: String, pauseMs: Long = Config.GESTURE_PAUSE_MS) {
        head.pauseFor(pauseMs)
        robot.send("G $name")
    }

    private fun setEmotion(emotion: Emotion, holdMs: Long = 0) {
        resetEmotionJob?.cancel()
        _ui.update { it.copy(emotion = emotion) }
        if (holdMs > 0) {
            resetEmotionJob = scope.launch {
                delay(holdMs)
                if (!asleep) _ui.update { it.copy(emotion = Emotion.NEUTRAL) }
            }
        }
    }
}
