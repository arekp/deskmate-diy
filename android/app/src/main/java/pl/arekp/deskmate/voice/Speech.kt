package pl.arekp.deskmate.voice

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings as AndroidSettings
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import pl.arekp.deskmate.Config
import pl.arekp.deskmate.Settings
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

/** Pozycja na liście wyboru w ustawieniach: identyfikator do zapisania + czytelna nazwa. */
data class SpeechOption(val id: String, val label: String)

/**
 * Rozpoznawanie mowy (STT) i synteza mowy (TTS) – usługi systemowe Androida.
 * Którą usługę/silnik użyć, mówią ustawienia (puste = domyślne w telefonie).
 * Informacje techniczne (jaki silnik, głos, czasy) idą do [onInfo] → pasek w trybie debug.
 * Metody wywoływać z wątku głównego.
 */
class Speech(
    private val context: Context,
    private val settings: Settings,
    private val onInfo: (key: String, value: String) -> Unit = { _, _ -> },
    /** TTS zaczyna kolejne słowo – liczba sylab (do ruchu ust). Nie każdy silnik to zgłasza. */
    private val onWord: (syllables: Int) -> Unit = {},
) {

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    fun init() {
        initRecognizer()
        initTts()
    }

    /** Po zmianie ustawień mowy – tworzy usługi od nowa. */
    fun reload() {
        recognizer?.destroy()
        recognizer = null
        tts?.shutdown()
        tts = null
        ttsReady = false
        init()
    }

    val canListen: Boolean get() = recognizer != null

    // --- STT -------------------------------------------------------------------

    private fun initRecognizer() {
        val choice = settings.sttService
        recognizer = when {
            choice == Settings.STT_ON_DEVICE && Build.VERSION.SDK_INT >= 31 &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context) ->
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            choice.contains('/') -> ComponentName.unflattenFromString(choice)
                ?.let { SpeechRecognizer.createSpeechRecognizer(context, it) }
            SpeechRecognizer.isRecognitionAvailable(context) -> SpeechRecognizer.createSpeechRecognizer(context)
            else -> null
        }
        onInfo("STT", sttLabel())
    }

    private fun sttLabel(): String {
        val choice = settings.sttService
        if (recognizer == null) return "niedostępne"
        if (choice == Settings.STT_ON_DEVICE) {
            return if (Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context))
                "na urządzeniu (Android)" else "na urządzeniu niedostępne → domyślne"
        }
        val component = if (choice.contains('/')) choice
        else AndroidSettings.Secure.getString(context.contentResolver, "voice_recognition_service").orEmpty()
        val name = ComponentName.unflattenFromString(component)?.let { serviceLabel(it) } ?: "systemowe"
        return if (choice.isBlank()) "$name (domyślne)" else name
    }

    private fun serviceLabel(c: ComponentName): String = runCatching {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        pm.getServiceInfo(c, 0).loadLabel(pm).toString().ifBlank { c.packageName }
    }.getOrDefault(c.packageName)

    /** Usługi rozpoznawania mowy zainstalowane w telefonie (do ustawień). */
    fun recognizers(): List<SpeechOption> {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val services = pm.queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE), 0).map {
            val c = ComponentName(it.serviceInfo.packageName, it.serviceInfo.name)
            SpeechOption(c.flattenToString(), it.loadLabel(pm).toString().ifBlank { c.packageName })
        }
        val onDevice = if (Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context))
            listOf(SpeechOption(Settings.STT_ON_DEVICE, "Na urządzeniu (offline)")) else emptyList()
        return listOf(SpeechOption("", "Domyślne w telefonie")) + onDevice + services
    }

    /**
     * Słucha jednej wypowiedzi. Zwraca tekst albo null (cisza, błąd, limit czasu).
     * [onPartial] – słowa rozpoznane w trakcie mówienia (podgląd na ekranie),
     * [onSpeechEnd] – użytkownik skończył mówić (można od razu pokazać „myślę”).
     */
    suspend fun listenOnce(
        onPartial: (String) -> Unit = {},
        onSpeechEnd: () -> Unit = {},
    ): String? {
        val rec = recognizer ?: return null
        val started = SystemClock.uptimeMillis()
        var speechEnd = 0L
        var busy = false
        val result = withTimeoutOrNull(LISTEN_TIMEOUT_MS) { suspendCancellableCoroutine<String?> { cont ->
            var finished = false
            fun finish(result: String?) {
                if (finished) return
                finished = true
                if (cont.isActive) cont.resume(result)
            }

            rec.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val now = SystemClock.uptimeMillis()
                    val wait = if (speechEnd > 0) " (rozpoznanie ${now - speechEnd} ms)" else ""
                    onInfo("STT czas", "${now - started} ms$wait")
                    finish(list?.firstOrNull())
                }

                override fun onError(error: Int) {
                    onInfo("STT czas", "błąd: ${errorText(error)}")
                    busy = error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || error == SpeechRecognizer.ERROR_CLIENT
                    finish(null)
                }

                override fun onEndOfSpeech() {
                    speechEnd = SystemClock.uptimeMillis()
                    onSpeechEnd()
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                        ?.takeIf { it.isNotBlank() }?.let(onPartial)
                }

                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Config.SPEECH_LANG)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
            }
            rec.startListening(intent)
            cont.invokeOnCancellation { rec.cancel() }
        } }
        if (result == null && SystemClock.uptimeMillis() - started >= LISTEN_TIMEOUT_MS) onInfo("STT czas", "limit czasu")
        // Zawieszona albo zajęta usługa – utwórz od nowa, zamiast utknąć
        if (busy || (result == null && SystemClock.uptimeMillis() - started >= LISTEN_TIMEOUT_MS)) {
            recognizer?.destroy()
            initRecognizer()
        }
        return result
    }

    // --- TTS -------------------------------------------------------------------

    private fun initTts() {
        val engine = settings.ttsEngine
        val listener = TextToSpeech.OnInitListener { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            val t = tts
            if (!ttsReady || t == null) {
                onInfo("TTS", "błąd uruchomienia silnika")
                return@OnInitListener
            }
            val lang = Locale.forLanguageTag(Config.SPEECH_LANG)
            val res = t.setLanguage(lang)
            onInfo("TTS", ttsLabel(t, missingLanguage = res < TextToSpeech.LANG_AVAILABLE))
        }
        tts = if (engine.isBlank()) TextToSpeech(context, listener) else TextToSpeech(context, listener, engine)
    }

    private fun ttsLabel(t: TextToSpeech, missingLanguage: Boolean): String {
        val pkg = settings.ttsEngine.ifBlank { t.defaultEngine.orEmpty() }
        val name = t.engines.firstOrNull { it.name == pkg }?.label ?: pkg.ifBlank { "systemowy" }
        val suffix = if (settings.ttsEngine.isBlank()) " (domyślny)" else ""
        if (missingLanguage) return "$name$suffix · BRAK głosu pl-PL"
        val voice = runCatching { t.voice }.getOrNull()
        val where = when {
            voice == null -> ""
            voice.isNetworkConnectionRequired -> " · w chmurze"
            else -> " · lokalnie"
        }
        return "$name$suffix · głos ${voice?.name ?: "?"}$where"
    }

    /** Silniki syntezy mowy zainstalowane w telefonie (do ustawień). */
    fun ttsEngines(): List<SpeechOption> =
        listOf(SpeechOption("", "Domyślny w telefonie")) +
            (tts?.engines.orEmpty()).map { SpeechOption(it.name, it.label) }

    /** Przerywa bieżącą wypowiedź (say() zwróci wtedy false). */
    fun stopSpeaking() {
        tts?.stop()
    }

    /** Mówi tekst i czeka na koniec wypowiedzi. Zwraca false, gdy przerwano ([stopSpeaking]). */
    suspend fun say(text: String): Boolean {
        val engine = tts
        if (engine == null || !ttsReady || text.isBlank()) return true
        _speaking.value = true
        val queued = SystemClock.uptimeMillis()
        var startedAt = 0L
        return try {
            // Limit czasu na wypadek silnika, który nie zgłosi końca (ok. 150 ms na znak, min. 8 s)
            withTimeoutOrNull(maxOf(8_000L, text.length * 150L)) { suspendCancellableCoroutine<Boolean> { cont ->
                val id = UUID.randomUUID().toString()
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        if (utteranceId == id) startedAt = SystemClock.uptimeMillis()
                    }

                    override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                        if (utteranceId != id || start < 0 || end > text.length || start >= end) return
                        val syllables = Syllables.count(text.substring(start, end))
                        if (syllables > 0) onWord(syllables)
                    }

                    override fun onDone(utteranceId: String?) {
                        if (utteranceId != id) return
                        val now = SystemClock.uptimeMillis()
                        val begin = if (startedAt > 0) startedAt else queued
                        val first = if (startedAt > 0) "start ${startedAt - queued} ms, " else ""
                        onInfo("TTS czas", "${first}mówienie %.1f s".format(Locale.ROOT, (now - begin) / 1000.0))
                        if (cont.isActive) cont.resume(true)
                    }

                    override fun onStop(utteranceId: String?, interrupted: Boolean) {
                        if (utteranceId != id) return
                        onInfo("TTS czas", "przerwano po %.1f s".format(Locale.ROOT, (SystemClock.uptimeMillis() - queued) / 1000.0))
                        if (cont.isActive) cont.resume(false)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        if (utteranceId != id) return
                        onInfo("TTS czas", "błąd syntezy")
                        if (cont.isActive) cont.resume(true)
                    }
                })
                engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
                cont.invokeOnCancellation { engine.stop() }
            } } ?: true
        } finally {
            _speaking.value = false
        }
    }

    fun shutdown() {
        recognizer?.destroy()
        tts?.shutdown()
    }

    private companion object {
        const val LISTEN_TIMEOUT_MS = 15_000L
    }

    private fun errorText(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_NO_MATCH -> "nic nie rozpoznano"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "cisza"
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "sieć"
        SpeechRecognizer.ERROR_AUDIO -> "mikrofon"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "brak uprawnień"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "usługa zajęta"
        SpeechRecognizer.ERROR_SERVER -> "serwer"
        SpeechRecognizer.ERROR_CLIENT -> "klient"
        else -> "kod $code"
    }
}
