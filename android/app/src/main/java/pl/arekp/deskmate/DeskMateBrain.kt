package pl.arekp.deskmate

import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
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
import pl.arekp.deskmate.ai.llm.GeminiModels
import pl.arekp.deskmate.ai.llm.LlmRouter
import pl.arekp.deskmate.ai.llm.OpenRouterProvider
import pl.arekp.deskmate.ai.Expression
import pl.arekp.deskmate.ai.ReasoningFilter
import pl.arekp.deskmate.ai.SpeechText
import pl.arekp.deskmate.ai.LookTool
import pl.arekp.deskmate.ai.toSpec
import pl.arekp.deskmate.ai.WeatherTool
import pl.arekp.deskmate.ai.WebSearchTool
import pl.arekp.deskmate.ai.llm.ChatMessage
import pl.arekp.deskmate.ai.llm.ChatRequest
import pl.arekp.deskmate.ai.llm.ProviderException
import pl.arekp.deskmate.ai.ScreenTool
import pl.arekp.deskmate.ble.RobotLink
import pl.arekp.deskmate.ui.Activity
import pl.arekp.deskmate.ui.Emotion
import pl.arekp.deskmate.ui.SpeechBeat
import pl.arekp.deskmate.ui.UiState
import androidx.camera.core.Preview
import pl.arekp.deskmate.vision.FaceObservation
import pl.arekp.deskmate.vision.SceneObservation
import pl.arekp.deskmate.vision.FaceTracker
import pl.arekp.deskmate.vision.HeadController
import pl.arekp.deskmate.mcp.HomeIntent
import pl.arekp.deskmate.mcp.McpManager
import pl.arekp.deskmate.mcp.McpServerConfig
import pl.arekp.deskmate.voice.BargeInMic
import pl.arekp.deskmate.voice.ListenPolicy
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
            it.copy(showCamera = settings.showCameraPreview, ticker = it.ticker.copy(debug = settings.debugMode).withTech("Wzrok", "ML Kit na urządzeniu: twarz, etykiety, poza, OCR"))
        },
    )
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    /** Dla okienka podglądu kamery. */
    val face: StateFlow<FaceObservation> get() = faces.faces
    val scene: StateFlow<SceneObservation> get() = faces.scene
    fun attachPreview(provider: Preview.SurfaceProvider?) = faces.setPreviewSurface(provider)

    private val _gaze = MutableStateFlow(Offset.Zero)
    /** Kierunek spojrzenia oczu (−1..1) – osobno od [ui], bo zmienia się z każdą klatką. */
    val gaze: StateFlow<Offset> = _gaze.asStateFlow()

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
    /** Kiedy słuchać: dotyk, przerwanie, jedno dopytanie po odpowiedzi albo głos, gdy patrzysz na robota. */
    private val policy = ListenPolicy()
    /** Mikrofon w spoczynku – tylko pomiar głośności, żeby wiedzieć, że zaczynasz mówić. */
    private val idleMic = BargeInMic(
        onSpeech = {
            policy.onVoice(SystemClock.uptimeMillis())
            _ui.update { it.copy(listening = true) } // natychmiastowa reakcja, zanim ruszy rozpoznawanie
        },
        onInfo = { k, v -> tech(if (k == "Przerywanie") "Nasłuch" else k, v) },
    )
    private var speakStartedAt = 0L
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
    /** Serwery MCP użytkownika (np. Home Assistant) – narzędzia dołączane do agenta. */
    val mcp = McpManager(scope, { McpServerConfig.listFromJson(settings.mcpServersJson) }, onEvent = { event(it) })
    private val builtInTools = buildTools()
    /** Bieżąca wypowiedź i „pamięć” rozmowy o domu – narzędzia MCP tylko wtedy (mniej tokenów i limitów). */
    @Volatile private var currentQuestion = ""
    @Volatile private var homeTurnsLeft = 0
    private fun mcpToolsNow(): List<pl.arekp.deskmate.ai.AssistantTool> =
        if (HomeIntent.matches(currentQuestion) || homeTurnsLeft > 0) mcp.tools() else emptyList()

    private val assistant = Assistant(router, { builtInTools + mcpToolsNow() }) { label ->
        if (label != aiStatus && aiStatus.isNotBlank()) event("Model zmieniony na $label")
        aiStatus = label
        refreshStatus()
    }

    private var faceSince = 0L
    private var lastFaceAt = SystemClock.uptimeMillis()
    private var asleep = false
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
        conversationJob = scope.launch { conversationLoop() }
        scope.launch { sleepWatcher() }
        mcp.refresh()
        scope.launch { autoUploadWatcher() }
    }

    private var conversationJob: Job? = null
    /** Robot wstrzymany (ustawienia otwarte): nie słucha, nie mówi, kamera wyłączona. */
    @Volatile var paused = false
        private set

    /** Wejście w ustawienia: oczy zamknięte, koniec słuchania i mówienia, kamera wyłączona, głowa w pozycji wyjściowej. */
    fun pause() {
        if (paused || !started) return
        paused = true
        conversationJob?.cancel() // przerywa słuchanie i mówienie w toku
        conversationJob = null
        speech.stopSpeaking()
        idleMic.requestStop()
        faces.pause()
        robot.send("H")
        _ui.update { it.copy(listening = false, caption = "") }
        setActivity(Activity.SLEEPING)
        DiagLog.i("App", "Ustawienia otwarte – robot wstrzymany")
    }

    /** Wyjście z ustawień: kamera i rozmowa wracają. */
    fun resume() {
        if (!paused) return
        paused = false
        faces.start()
        setActivity(Activity.IDLE)
        setEmotion(Emotion.NEUTRAL)
        conversationJob = scope.launch { conversationLoop() }
        DiagLog.i("App", "Ustawienia zamknięte – robot wznowiony")
    }

    fun stop() {
        idleMic.requestStop()
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

    private fun event(text: String) {
        DiagLog.i("Zdarzenie", text)
        _ui.update { it.copy(ticker = it.ticker.withEvent(text)) }
    }

    /** Informacja techniczna – widoczna w pasku tylko w trybie debug. */
    private fun tech(key: String, value: String?) {
        // do dziennika tylko rzadkie i ważne (bez poziomów mikrofonu co 0,5 s)
        if (key in LOGGED_TECH && _ui.value.ticker.tech[key] != value) DiagLog.i("Tech", "$key: $value")
        _ui.update { it.copy(ticker = it.ticker.withTech(key, value)) }
    }

    private fun setDebug(on: Boolean) {
        settings.debugMode = on
        _ui.update { it.copy(ticker = it.ticker.copy(debug = on)) }
    }

    // --- Ustawienia (okno po przytrzymaniu palca) --------------------------------

    /** Test połączenia z modelami (przycisk w ustawieniach) – dokładny wynik albo błąd każdego z nich. */
    suspend fun testProviders(): List<String> {
        val all = router.all()
        if (all.none { it.isConfigured() }) return listOf("Brak kluczy – wpisz klucz Gemini, OpenRouter albo Claude.")
        val inMode = router.order().map { it.id }.toSet()
        val tools = builtInTools + mcp.tools()
        val toolSpecs = tools.map { it.toSpec() }
        val mcpCount = mcp.tools().size
        val lines = mutableListOf<String>()
        lines += "Tryb: ${settings.routerMode.label} → w rozmowie: ${router.order().joinToString(" → ") { it.label }.ifBlank { "nic" }}"
        if (settings.routerMode != LlmRouter.Mode.AUTO) lines += "⚠️ Tryb inny niż Automatycznie – część modeli nie jest używana w rozmowie."
        lines += "Narzędzia: ${tools.size} (w tym MCP: $mcpCount). " + mcp.statuses.joinToString("; ") { (if (it.ok) "✅ " else "❌ ") + it.text }.ifBlank { "Brak serwerów MCP (dodaj i Zapisz)." }
        for (p in all) {
            if (!p.isConfigured()) { lines += "• ${p.label}: brak klucza"; continue }
            val used = if (p.id in inMode) "" else " (nieużywany w tym trybie)"
            fun fail(e: Exception) = if (e is ProviderException) "❌ ${router.describe(e)} [${e.message?.take(140)}]" else "❌ ${e.message?.take(140)}"
            val t0 = SystemClock.uptimeMillis()
            val basic = try {
                val r = p.complete(ChatRequest("Odpowiadasz jednym słowem.", listOf(ChatMessage.user("Napisz: OK")), emptyList(), 50))
                "✅ ${SystemClock.uptimeMillis() - t0} ms „${r.text.take(20)}”"
            } catch (e: Exception) { fail(e) }
            lines += "• ${p.label}$used: $basic"
            // każdy model osobno tylko przy problemie – test nie powinien sam wyczerpywać limitu na minutę
            if (p is GeminiProvider && (!basic.startsWith("✅") || SystemClock.uptimeMillis() - t0 > 4_000)) {
                // Każdy model Gemini osobno – żeby było widać, który ma limit, który jest przeciążony, a który wycofany
                for (m in GeminiModels.candidates(settings.geminiModel)) {
                    val tm = SystemClock.uptimeMillis()
                    val r = try {
                        p.completeWith(m, ChatRequest("Odpowiadasz jednym słowem.", listOf(ChatMessage.user("Napisz: OK")), emptyList(), 50))
                        "✅ ${SystemClock.uptimeMillis() - tm} ms"
                    } catch (e: Exception) { fail(e) }
                    lines += "   model $m: $r"
                }
            }
            if (!basic.startsWith("✅")) continue
            // Drugi test: z pełną listą narzędzi (wbudowane + MCP) – czy model je przyjmuje
            val t1 = SystemClock.uptimeMillis()
            val withTools = try {
                val r = p.complete(
                    ChatRequest("Odpowiadasz jednym słowem. Nie używaj narzędzi.", listOf(ChatMessage.user("Napisz: OK")), toolSpecs, 50),
                )
                val dropped = (p as? OpenRouterProvider)?.lastDroppedTools
                if (dropped != null) "⚠️ model NIE przyjął narzędzi – odpowiada bez nich (MCP nie zadziała): $dropped"
                else "✅ przyjmuje ${toolSpecs.size} narzędzi (${SystemClock.uptimeMillis() - t1} ms)" + if (r.toolCalls.isNotEmpty()) " – od razu wywołał ${r.toolCalls.first().name}" else ""
            } catch (e: Exception) { fail(e) }
            lines += "   z narzędziami: $withTools"
        }
        lines += if (settings.geminiKey.isBlank()) "• Wyszukiwarka Google: brak klucza Gemini (zapas: Google News + Wikipedia)"
        else "• Wyszukiwarka Google (przez Gemini): " + WebSearchTool({ settings.geminiKey }, { settings.geminiModel }).testGoogle()
        lines += "Pogoda, Google News i Wikipedia nie wymagają klucza."
        DiagLog.i("Test", lines.joinToString("\n"))
        return lines
    }

    /** Raport do wysłania (ustawienia → Logi): wersja, telefon, ustawienia bez kluczy + dziennik. */
    fun diagnosticReport(): String = LogShare.report(activity, settings, profile, router.order().map { it.label })

    private val logUploader = LogUploader(settings)
    private var lastAutoUploadAt = 0L
    private var errorsAtLastUpload = 0

    /** Przycisk „Wyślij logi na GitHub” w ustawieniach. */
    suspend fun uploadLogs(): String = logUploader.upload(diagnosticReport(), "ręcznie").message

    /** Automatyczna wysyłka: po awarii poprzedniego uruchomienia i po nowych błędach (najwyżej co 10 min). */
    private suspend fun autoUploadWatcher() {
        if (settings.logAutoUpload && logUploader.isConfigured() && DiagLog.previousRunCrashed()) {
            logUploader.upload(diagnosticReport(), "awaria poprzedniego uruchomienia")
        }
        var liveSize = -1
        while (scope.isActive) {
            delay(60_000)
            val now = SystemClock.uptimeMillis()
            // Tryb debug = tryb deweloperski: log na żywo co minutę (tylko gdy coś przybyło)
            if (settings.debugMode && logUploader.isConfigured()) {
                val report = diagnosticReport()
                if (report.length != liveSize) {
                    val r = logUploader.uploadLive(report)
                    if (r is LogUploader.Result.Ok) liveSize = report.length
                    tech("Logi", if (r is LogUploader.Result.Ok) "na żywo OK" else r.message)
                }
            }
            if (!settings.debugMode && DiagLog.errorCount > errorsAtLastUpload && now - lastAutoUploadAt > 10 * 60_000L &&
                settings.logAutoUpload && logUploader.isConfigured()
            ) {
                lastAutoUploadAt = now
                errorsAtLastUpload = DiagLog.errorCount
                logUploader.upload(diagnosticReport(), "automatycznie po błędach")
            }
        }
    }

    fun sttOptions(): List<SpeechOption> = speech.recognizers()
    fun ttsOptions(): List<SpeechOption> = speech.ttsEngines()

    /** Wywoływane po zapisaniu ustawień: odświeża pasek i – gdy trzeba – usługi mowy. */
    fun onSettingsSaved(speechChanged: Boolean) {
        _ui.update { it.copy(ticker = it.ticker.copy(debug = settings.debugMode), showCamera = settings.showCameraPreview) }
        mcp.refresh()
        if (speechChanged) {
            speech.reload()
            event("Zmieniono ustawienia mowy")
        }
        refreshStatus()
    }

    // Mina i gest przychodzą jako znacznik w odpowiedzi ([happy nod]), a data/godzina jest w prompcie –
    // dzięki temu zwykła odpowiedź to jedno zapytanie do AI, bez rund narzędzi `express` / `get_current_time`.
    private fun buildTools(): List<AssistantTool> = listOfNotNull(
        LookTool({ faces.faces.value }, { faces.scene.value }, { faces.readText() }),
        WeatherTool { settings.homeCity },
        WebSearchTool({ settings.geminiKey }, { settings.geminiModel }),
        ScreenTool(settings),
        // TODO: GmailTool, CalendarTool, SlackTool… – patrz docs/roadmapa.md
    )

    // --- Wzrok ---------------------------------------------------------------

    private fun onFace(f: FaceObservation) {
        val now = SystemClock.uptimeMillis()
        if (f.present) {
            lastFaceAt = now
            if (faceSince == 0L) faceSince = now
            if (asleep) wakeUp()
            head.onFace(f)
            _gaze.value = Offset(f.x * 0.6f, f.y * 0.6f)
        } else {
            faceSince = 0L
            _gaze.value = Offset.Zero
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

    private fun faceEngaged(now: Long): Boolean {
        val f = faces.faces.value
        return f.present && f.attentive && faceSince != 0L && now - faceSince > Config.ENGAGE_AFTER_MS
    }

    private suspend fun conversationLoop() {
        while (scope.isActive) {
            val now = SystemClock.uptimeMillis()
            val engaged = faceEngaged(now)
            val someoneHere = now - lastFaceAt < 3_000
            val trigger = if (speech.canListen && !speech.speaking.value) policy.next(now, someoneHere, engaged, asleep) else null

            if (trigger == null) {
                // W spoczynku: mikrofon tylko mierzy głośność, gdy ktoś patrzy na robota
                val wantMic = policy.wantsVoiceDetection(engaged, asleep) && !speech.speaking.value
                if (wantMic && !idleMic.isRunning) idleMic.start()
                if (!wantMic && idleMic.isRunning) {
                    idleMic.stop()
                    _ui.update { it.copy(listening = false) }
                }
                delay(100)
                continue
            }

            idleMic.stop() // mikrofon musi być wolny dla rozpoznawania mowy
            policy.onListened()
            converse(trigger)
        }
    }

    /** Jedna wymiana: słuchanie → (polecenie lokalne | model AI) → odpowiedź. */
    private suspend fun converse(trigger: ListenPolicy.Trigger) {
        tech("Słucham, bo", trigger.label)
        setEmotion(Emotion.LISTENING)
        setActivity(Activity.LISTENING)
        _ui.update { it.copy(listening = true) }
        val heard = speech.listenOnce(
            onPartial = { part -> _ui.update { it.copy(caption = "„$part…”") } },
            onSpeechEnd = {
                setEmotion(Emotion.THINKING)
                setActivity(Activity.THINKING)
            },
        )
        _ui.update { it.copy(listening = false) }
        setActivity(Activity.IDLE)

        DiagLog.i("Rozmowa", "Słuchanie (${trigger.label}) → ${if (heard.isNullOrBlank()) "cisza/brak" else "„$heard”"}")
        if (heard.isNullOrBlank()) {
            // Cisza – NIE słuchamy od nowa; czekamy na nowy sygnał (głos, dotyk)
            setEmotion(Emotion.NEUTRAL)
            if (trigger == ListenPolicy.Trigger.VOICE || trigger == ListenPolicy.Trigger.TAP) {
                _ui.update { it.copy(caption = "") }
            }
            return
        }

        _ui.update { it.copy(caption = "„$heard”", ticker = it.ticker.copy(heard = heard)) }

        // Polecenia i pytania obsługiwane lokalnie, bez modelu AI (od razu)
        if (VoiceCommands.isStop(heard)) {
            event("Zatrzymano")
            answerWasCut = false
            setEmotion(Emotion.NEUTRAL)
            return
        }
        VoiceCommands.debugToggle(heard)?.let { on ->
            setDebug(on)
            event(if (on) "Tryb debug włączony" else "Tryb debug wyłączony")
            setEmotion(Emotion.HAPPY, holdMs = 1500)
            speakInterruptible(if (on) "Tryb debug włączony." else "Tryb debug wyłączony.")
            return
        }
        VoiceCommands.localAnswer(heard)?.let { local ->
            tech("LLM czas", "0 ms (odpowiedź lokalna)")
            setEmotion(Emotion.HAPPY, holdMs = 2000)
            _ui.update { it.copy(caption = local) }
            answerWasCut = speakInterruptible(local)
            return
        }

        setEmotion(Emotion.THINKING)
        setActivity(Activity.THINKING)
        val askedAt = SystemClock.uptimeMillis()
        val question = if (answerWasCut) "(Przerwałem Ci poprzednią odpowiedź.) $heard" else heard
        answerWasCut = false
        currentQuestion = heard
        val mcpCallsBefore = pl.arekp.deskmate.mcp.calls.get()
        val raw = assistant.ask(question)
        // użył narzędzi domu → kolejne 2 wypowiedzi też z nimi („a teraz w kuchni”)
        homeTurnsLeft = if (pl.arekp.deskmate.mcp.calls.get() > mcpCallsBefore) 2 else (homeTurnsLeft - 1).coerceAtLeast(0)
        tech("LLM czas", "${SystemClock.uptimeMillis() - askedAt} ms")
        DiagLog.i("Rozmowa", "Odpowiedź (${SystemClock.uptimeMillis() - askedAt} ms): ${raw.take(400)}")
        val expr = Expression.parse(ReasoningFilter.clean(raw))
        val answer = SpeechText.completeSentences(expr.text).ifBlank { "Hmm." }
        if (expr.emotion != null) setEmotion(expr.emotion, holdMs = 4000)
        else if (_ui.value.emotion == Emotion.THINKING) setEmotion(Emotion.NEUTRAL)
        expr.gesture?.let {
            gesture(it)
            event("Gest: ${GESTURE_NAMES[it] ?: it}")
        }
        _ui.update { it.copy(caption = answer) }
        answerWasCut = speakInterruptible(answer)
    }

    // --- Dotyk, emocje, gesty --------------------------------------------------

    /** Ktoś macha – przywitaj się (najwyżej raz na [Config.WAVE_GREET_COOLDOWN_MS]). */
    private fun onWave() {
        // Podczas mówienia machanie = przerwanie, ale dopiero po 1 s (żeby gest sprzed odpowiedzi nie uciął jej na starcie)
        if (interruptRequest != null) {
            if (SystemClock.uptimeMillis() - speakStartedAt > Config.WAVE_INTERRUPT_AFTER_MS) requestInterrupt("machanie")
            return
        }
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
            policy.onTap()
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
        speakStartedAt = SystemClock.uptimeMillis()
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
        if (interrupted) policy.onInterrupted() else policy.onAnswered(SystemClock.uptimeMillis())
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
        val LOGGED_TECH = setOf("STT", "TTS", "STT czas", "TTS czas", "LLM czas", "Słucham, bo", "Wzrok")
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
