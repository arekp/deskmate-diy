package pl.arekp.deskmate.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import pl.arekp.deskmate.Config
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

/**
 * Rozpoznawanie mowy (systemowe, Google) i synteza mowy (TTS).
 * Metody wywoływać z wątku głównego.
 */
class Speech(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    fun init() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        }
        tts = TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) tts?.language = Locale.forLanguageTag(Config.SPEECH_LANG)
        }
    }

    val canListen: Boolean get() = recognizer != null

    /** Słucha jednej wypowiedzi. Zwraca tekst albo null (cisza, błąd). */
    suspend fun listenOnce(): String? {
        val rec = recognizer ?: return null
        return suspendCancellableCoroutine { cont ->
            var finished = false
            fun finish(result: String?) {
                if (finished) return
                finished = true
                if (cont.isActive) cont.resume(result)
            }

            rec.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    finish(list?.firstOrNull())
                }

                override fun onError(error: Int) = finish(null)
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Config.SPEECH_LANG)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
            }
            rec.startListening(intent)
            cont.invokeOnCancellation { rec.cancel() }
        }
    }

    /** Mówi tekst i czeka na koniec wypowiedzi. */
    suspend fun say(text: String) {
        val engine = tts
        if (engine == null || !ttsReady || text.isBlank()) return
        _speaking.value = true
        try {
            suspendCancellableCoroutine { cont ->
                val id = UUID.randomUUID().toString()
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        if (utteranceId == id && cont.isActive) cont.resume(Unit)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        if (utteranceId == id && cont.isActive) cont.resume(Unit)
                    }
                })
                engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
                cont.invokeOnCancellation { engine.stop() }
            }
        } finally {
            _speaking.value = false
        }
    }

    fun shutdown() {
        recognizer?.destroy()
        tts?.shutdown()
    }
}
