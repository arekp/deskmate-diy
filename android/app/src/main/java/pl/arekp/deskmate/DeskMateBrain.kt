package pl.arekp.deskmate

import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import pl.arekp.deskmate.ai.AssistantTool
import pl.arekp.deskmate.ai.Assistant
import pl.arekp.deskmate.ai.llm.ClaudeProvider
import pl.arekp.deskmate.ai.llm.GeminiProvider
import pl.arekp.deskmate.ai.llm.LlmRouter
import pl.arekp.deskmate.ai.llm.OpenRouterProvider
import pl.arekp.deskmate.ai.ExpressTool
import pl.arekp.deskmate.ai.LookTool
import pl.arekp.deskmate.ai.ScreenTool
import pl.arekp.deskmate.ai.TimeTool
import pl.arekp.deskmate.ble.RobotLink
import pl.arekp.deskmate.ui.Activity
import pl.arekp.deskmate.ui.Emotion
import pl.arekp.deskmate.ui.SpeechBeat
import pl.arekp.deskmate.ui.UiState
import pl.arekp.deskmate.vision.FaceObservation
import pl.arekp.deskmate.vision.FaceTracker
import pl.arekp.deskmate.vision.HeadController
import pl.arekp.deskmate.voice.BargeInMic
import pl.arekp.deskmate.voice.Speech
import pl.arekp.deskmate.voice.SpeechOption
import pl.arekp.deskmate.voice.VoiceCommands

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
    val settings = Settings(activity)

    private val _ui = MutableStateFlow(
        UiState(eyeScale = profile.eyeScale).let {
            it.copy(ticker = it.ticker.copy(debug = settings.debugMode).withTech("Wzrok", "ML Kit na urządzeniu: twarz, etykiety, poza, OCR"))
        },
    )
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private val scope = activity.lifecycleScope
    private val robot = RobotLink(activity, scope)
    private val head = HeadController(profile) { robot.send(it) }
    private val faces = FaceTracker(activity, activity)
    private var beatSeq = 0L
    private val speech = Speech(
        activity, settings,
        onInfo = { key, value -> tech(key, value) },
        onWord = { syl -> _ui.update { it.copy(speechBeat = SpeechBeat(++beatSeq, syl)) } },
    )
    private var lastWaveAt = 0L

    /** Prośba o przerwanie bieżącej wypowiedzi (dotyk, ręka, głos) – aktywna tylko podczas mówienia. */
    @Volatile private var interruptRequest: CompletableDeferred<String>? = null
    /** Następne słuchanie bez czekania na spojrzenie (po przerwaniu albo dotknięciu). */
    @Volatile private var forceListen = false
    /** Poprzednia odpowiedź została przerwana – powiemy o tym modelowi. */
    private var answerWasCut = false
    /** Router modeli: Gemini (darmowy) → OpenRouter (darmowe) → Claude (płatny), wg trybu w ustawieniach. */
    val router = LlmRouter(
        providers = listOf(
            GeminiProvider({ settings.geminiKey }, { settings.geminiModel }),
            OpenRouterProvider({ settings.openRouterKey }, { settings.openRouterModel }),
            ClaudeProvider({ settings.claudeKey }, { settings.claudeModel }),
        ),
        mode = { settings.routerMode },
        onEvent = { event(it) },
    )
    private var robotStatus = ""
    private var aiStatus = ""
    private val assistant = Assistant(router, buildTools()) { label ->
        if (label != aiStatus && aiStatus.isNotBlank()) event("Model zmieniony na $label")
        aiStatus = label
        refreshStatus()
    }

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
        scope.launch { faces.waves.collect { onWave() } }
        scope.launch { speech.speaking.collect { s -> _ui.update { it.copy(speaking = s) } } }
        scope.launch {
            faces.scene.collect { sc ->
                tech("Widzę", sc.labels.joinToString(", ") { "${it.name} ${(it.confidence * 100).toInt()}%" }.ifBlank { "—" })
                tech("Poza", if (sc.handRaised) "ręka w górze" else if (sc.personPose) "sylwetka" else "")
                if (sc.handRaised) requestInterrupt("ręka w górze")
            }
        }
        scope.launch { robot.events.collect { if (it.startsWith("TOUCH")) onTouch() } }
        scope.launch {
            var wasConnected = false
            robot.state.collect { s ->
                val connected = s == RobotLink.State.CONNECTED
                if (connected && !wasConnected) event("Połączono z robotem")
                if (!connected && wasConnected) event("Utracono połączenie z robotem")
                wasConnected = connected
                robotStatus = s.label
                refreshStatus()
            }
        }
        if (!router.isAnyConfigured()) {
            _ui.update { it.copy(caption = "Przytrzymaj palec na ekranie, żeby wpisać klucz Gemini / OpenRouter / Claude") }
        }
        scope.launch { conversationLoop() }
        scope.launch { sleepWatcher() }
    }

    fun stop() {
        robot.stop()
        faces.stop()
        speech.shutdown()
    }

    private fun refreshStatus() {
        val ai = aiStatus.ifBlank { "AI: ${settings.routerMode.label}" }
        _ui.update {
            it.copy(
                status = "${profile.label} · $robotStatus · $ai",
                ticker = it.ticker.copy(model = aiStatus, mode = settings.routerMode.label, robot = robotStatus),
            )
        }
    }

    // --- Pasek informacji ------------------------------------------------------

    private fun setActivity(a: Activity) = _ui.update { it.copy(ticker = it.ticker.copy(activity = a)) }

    private fun event(text: String) = _ui.update { it.copy(ticker = it.ticker.withEvent(text)) }

    /** Informacja techniczna – widoczna w pasku tylko w trybie debug. */
    private fun tech(key: String, value: String?) = _ui.update { it.copy(ticker = it.ticker.withTech(key, value)) }

    private fun setDebug(on: Boolean) {
        settings.debugMode = on
        _ui.update { it.copy(ticker = it.ticker.copy(debug = on)) }
    }

    // --- Ustawienia (okno po przytrzymaniu palca) --------------------------------

    fun sttOptions(): List<SpeechOption> = speech.recognizers()
    fun ttsOptions(): List<SpeechOption> = speech.ttsEngines()

    /** Wywoływane po zapisaniu ustawień: odświeża pasek i – gdy trzeba – usługi mowy. */
    fun onSettingsSaved(speechChanged: Boolean) {
        _ui.update { it.copy(ticker = it.ticker.copy(debug = settings.debugMode)) }
        if (speechChanged) {
            speech.reload()
            event("Zmieniono ustawienia mowy")
        }
        refreshStatus()
    }

    private fun buildTools(): List<AssistantTool> = listOfNotNull(
        ExpressTool { emotion, gesture ->
            setEmotion(emotion, holdMs = 4000)
            gesture?.let {
                gesture(it)
                event("Gest: ${GESTURE_NAMES[it] ?: it}")
            }
        },
        TimeTool(),
        LookTool({ faces.faces.value }, { faces.scene.value }, { faces.readText() }),
        ScreenTool(settings),
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
                setActivity(Activity.SLEEPING)
                event("Nikogo nie ma – zasypiam")
                head.reset()
                gesture("sleep", pauseMs = Long.MAX_VALUE / 4)
                setEmotion(Emotion.SLEEPY)
            }
        }
    }

    private fun wakeUp() {
        asleep = false
        setActivity(Activity.IDLE)
        event("Widzę Cię – budzę się")
        head.pauseFor(0)
        gesture("wake")
        setEmotion(Emotion.HAPPY, holdMs = 1500)
    }

    // --- Rozmowa -------------------------------------------------------------

    private suspend fun conversationLoop() {
        while (scope.isActive) {
            val f = faces.faces.value
            val engaged = forceListen || (!asleep && f.present && f.attentive &&
                faceSince != 0L && SystemClock.uptimeMillis() - faceSince > Config.ENGAGE_AFTER_MS)
            if (!engaged || !speech.canListen || speech.speaking.value) {
                delay(300)
                continue
            }

            forceListen = false
            setEmotion(Emotion.LISTENING)
            setActivity(Activity.LISTENING)
            _ui.update { it.copy(listening = true) }
            val heard = speech.listenOnce()
            _ui.update { it.copy(listening = false) }
            setActivity(Activity.IDLE)

            if (heard.isNullOrBlank()) {
                // Cisza – nie nasłuchuj bez przerwy, wydłużaj przerwy aż twarz zniknie i wróci
                emptyListens++
                setEmotion(Emotion.NEUTRAL)
                delay((1000L * emptyListens).coerceAtMost(10_000L))
                continue
            }
            emptyListens = 0

            _ui.update { it.copy(caption = "„$heard”", ticker = it.ticker.copy(heard = heard)) }

            // Polecenia obsługiwane lokalnie, bez modelu AI
            if (VoiceCommands.isStop(heard)) {
                event("Zatrzymano")
                answerWasCut = false
                setEmotion(Emotion.NEUTRAL)
                delay(400)
                continue
            }
            val debugCmd = VoiceCommands.debugToggle(heard)
            if (debugCmd != null) {
                val on = debugCmd
                setDebug(on)
                event(if (on) "Tryb debug włączony" else "Tryb debug wyłączony")
                setActivity(Activity.SPEAKING)
                speech.say(if (on) "Tryb debug włączony." else "Tryb debug wyłączony.")
                setActivity(Activity.IDLE)
                delay(400)
                continue
            }

            setEmotion(Emotion.THINKING)
            setActivity(Activity.THINKING)
            val askedAt = SystemClock.uptimeMillis()
            val question = if (answerWasCut) "(Przerwałem Ci poprzednią odpowiedź.) $heard" else heard
            answerWasCut = false
            val answer = assistant.ask(question)
            tech("LLM czas", "${SystemClock.uptimeMillis() - askedAt} ms")
            if (_ui.value.emotion == Emotion.THINKING) setEmotion(Emotion.NEUTRAL)
            _ui.update { it.copy(caption = answer) }
            answerWasCut = speakInterruptible(answer)
            if (!answerWasCut) delay(400)
        }
    }

    // --- Dotyk, emocje, gesty --------------------------------------------------

    /** Ktoś macha – przywitaj się (najwyżej raz na [Config.WAVE_GREET_COOLDOWN_MS]). */
    private fun onWave() {
        if (requestInterrupt("machanie")) return
        event("Widzę machanie")
        val now = SystemClock.uptimeMillis()
        lastFaceAt = now
        if (asleep) wakeUp()
        val act = _ui.value.ticker.activity
        if (act != Activity.IDLE || now - lastWaveAt < Config.WAVE_GREET_COOLDOWN_MS) return
        lastWaveAt = now
        setEmotion(Emotion.EXCITED, holdMs = 2500)
        gesture("happy")
        scope.launch {
            setActivity(Activity.SPEAKING)
            speech.say(GREETINGS.random())
            if (_ui.value.ticker.activity == Activity.SPEAKING) setActivity(Activity.IDLE)
        }
    }

    /** Dotknięcie ekranu: przerwij mówienie albo zacznij słuchać od razu. */
    fun onTap() {
        if (requestInterrupt("dotyk")) return
        if (_ui.value.ticker.activity == Activity.IDLE || asleep) {
            if (asleep) wakeUp()
            forceListen = true
            event("Dotyk – słucham")
        }
    }

    /** Zgłasza przerwanie; true, jeśli robot właśnie mówił. */
    private fun requestInterrupt(reason: String): Boolean {
        val req = interruptRequest ?: return false
        return req.complete(reason)
    }

    /**
     * Mówi [text], ale da się mu przerwać (dotyk, ręka, a w ustawieniach też głos).
     * Zwraca true, gdy przerwano – wtedy od razu słuchamy nowego pytania.
     */
    private suspend fun speakInterruptible(text: String): Boolean = coroutineScope {
        val req = CompletableDeferred<String>()
        interruptRequest = req
        val mic = if (settings.bargeInVoice) {
            BargeInMic(onSpeech = { requestInterrupt("głos") }, onInfo = { k, v -> tech(k, v) }).also { it.start() }
        } else null
        val watcher = launch {
            val reason = req.await()
            event("Przerwano ($reason) – słucham")
            speech.stopSpeaking()
        }
        setActivity(Activity.SPEAKING)
        speech.say(text)
        interruptRequest = null
        mic?.stop()
        watcher.cancel()
        setActivity(Activity.IDLE)
        val interrupted = req.isCompleted
        if (interrupted) forceListen = true
        interrupted
    }

    private fun onTouch() {
        event("Ktoś mnie pogłaskał")
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

    private companion object {
        val GREETINGS = listOf("Cześć!", "Hej, hej!", "O, cześć! Miło Cię widzieć.", "Witaj!")
        val GESTURE_NAMES = mapOf(
            "nod" to "kiwam głową",
            "shake" to "kręcę głową",
            "happy" to "radość",
            "sad" to "smutek",
            "curious" to "ciekawość",
        )
    }
}
