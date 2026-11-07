package pl.arekp.deskmate.vision

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors
import kotlin.math.abs

/**
 * Pozycja twarzy w kadrze przedniej kamery.
 * x, y w zakresie -1..1 (0 = środek kadru; x>0 = prawa strona obrazu, y>0 = góra).
 */
data class FaceObservation(
    val present: Boolean,
    val x: Float = 0f,
    val y: Float = 0f,
    /** Szerokość twarzy jako ułamek szerokości kadru – im bliżej, tym większa. */
    val size: Float = 0f,
    val smiling: Boolean = false,
    /** Patrzy mniej więcej w stronę kamery. */
    val attentive: Boolean = false,
    /** Ramka twarzy w kadrze (0..1, obraz wyprostowany, NIE odbity lustrzanie) – do podglądu kamery. */
    val box: NormBox? = null,
)

/** Prostokąt we współrzędnych 0..1 kadru. */
data class NormBox(val left: Float, val top: Float, val right: Float, val bottom: Float)

/** Etykieta tego, co widać w kadrze (już po polsku, jeśli znamy tłumaczenie). */
data class SceneLabel(val name: String, val confidence: Float)

/** Otoczenie widziane przez kamerę: ogólne etykiety i poza (ręka w górze). */
data class SceneObservation(
    val labels: List<SceneLabel> = emptyList(),
    val personPose: Boolean = false,
    val handRaised: Boolean = false,
)

/**
 * Wzrok robota – wszystko lokalnie na telefonie (ML Kit), obraz z kamery nie opuszcza urządzenia:
 *  - twarz (każda klatka): kierunek, uśmiech, czy patrzy w kamerę,
 *  - etykiety otoczenia (co [LABEL_EVERY_MS]): "kubek", "laptop", "roślina"…,
 *  - poza ciała (co [POSE_EVERY_MS]): podniesiona ręka i machanie ([waves]),
 *  - tekst w kadrze (OCR) tylko na żądanie: [readText].
 */
class FaceTracker(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
) {
    private val _faces = MutableStateFlow(FaceObservation(present = false))
    val faces: StateFlow<FaceObservation> = _faces.asStateFlow()

    private val _scene = MutableStateFlow(SceneObservation())
    val scene: StateFlow<SceneObservation> = _scene.asStateFlow()

    private val _waves = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Zdarzenie: ktoś macha do robota. */
    val waves: SharedFlow<Unit> = _waves.asSharedFlow()

    private val executor = Executors.newSingleThreadExecutor()
    /** Wyniki ML Kit obsługujemy poza wątkiem głównym; po zamknięciu nic się już nie wykona (bez wyjątków). */
    private val results = java.util.concurrent.Executor { r ->
        if (!executor.isShutdown) runCatching { executor.execute(r) }
    }
    private val labeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder().setConfidenceThreshold(LABEL_MIN_CONFIDENCE).build(),
    )
    private val poseDetector = PoseDetection.getClient(
        PoseDetectorOptions.Builder().setDetectorMode(PoseDetectorOptions.STREAM_MODE).build(),
    )
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val waveDetector = WaveDetector()
    private var lastLabelAt = 0L
    private var lastPoseAt = 0L
    @Volatile private var textRequest: CompletableDeferred<String>? = null

    /** Odczytuje tekst widoczny w kadrze (OCR, alfabet łaciński). Pusty napis, gdy nic nie ma. */
    suspend fun readText(): String {
        val request = CompletableDeferred<String>()
        textRequest = request
        return withTimeoutOrNull(4000) { request.await() } ?: ""
    }
    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.1f)
            .enableTracking()
            .build(),
    )

    private val preview = Preview.Builder().build()

    /** Podłącza okienko podglądu kamery (albo odłącza: null). Wołać z wątku głównego. */
    fun setPreviewSurface(provider: Preview.SurfaceProvider?) {
        preview.setSurfaceProvider(provider)
    }

    fun start() {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(executor, ::analyze)
            try {
                provider.unbindAll()
                // Podgląd (okienko w rogu) korzysta z tej samej kamery – podłączany/odłączany przez setPreviewSurface()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis)
            } catch (e: Exception) {
                pl.arekp.deskmate.DiagLog.e(TAG, "Nie udało się uruchomić kamery", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /** Wstrzymanie (np. na czas ustawień): kamera wyłączona, ML Kit gotowy do [start] ponownie. */
    fun pause() {
        runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
        _faces.value = FaceObservation(present = false)
    }

    fun stop() {
        runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
        detector.close()
        labeler.close()
        poseDetector.close()
        textRecognizer.close()
        executor.shutdown()
    }

    @OptIn(ExperimentalGetImage::class)
    private fun analyze(proxy: ImageProxy) {
        val media = proxy.image ?: run { proxy.close(); return }
        val rotation = proxy.imageInfo.rotationDegrees
        val input = InputImage.fromMediaImage(media, rotation)
        // Współrzędne ML Kit są w obrazie już obróconym do pionu
        val w = (if (rotation % 180 == 0) proxy.width else proxy.height).toFloat()
        val h = (if (rotation % 180 == 0) proxy.height else proxy.width).toFloat()

        val now = SystemClock.uptimeMillis()
        val tasks = mutableListOf<Task<*>>()

        tasks += detector.process(input)
            .addOnSuccessListener(results) { faces ->
                val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                _faces.value = if (face == null) {
                    FaceObservation(present = false)
                } else {
                    val box = face.boundingBox
                    FaceObservation(
                        present = true,
                        x = (box.exactCenterX() / w * 2f - 1f).coerceIn(-1f, 1f),
                        y = -(box.exactCenterY() / h * 2f - 1f).coerceIn(-1f, 1f),
                        size = box.width() / w,
                        smiling = (face.smilingProbability ?: 0f) > 0.7f,
                        attentive = abs(face.headEulerAngleY) < 20f && abs(face.headEulerAngleX) < 20f,
                        box = NormBox(box.left / w, box.top / h, box.right / w, box.bottom / h),
                    )
                }
            }

        if (now - lastLabelAt >= LABEL_EVERY_MS) {
            lastLabelAt = now
            tasks += labeler.process(input).addOnSuccessListener(results) { labels ->
                val list = labels.sortedByDescending { it.confidence }.take(MAX_LABELS)
                    .map { SceneLabel(LabelsPl.translate(it.text), it.confidence) }
                _scene.value = _scene.value.copy(labels = list)
            }
        }

        if (now - lastPoseAt >= POSE_EVERY_MS) {
            lastPoseAt = now
            tasks += poseDetector.process(input).addOnSuccessListener(results) { pose ->
                fun arm(wrist: Int, shoulder: Int): WaveDetector.Arm? {
                    val wr = pose.getPoseLandmark(wrist) ?: return null
                    val sh = pose.getPoseLandmark(shoulder) ?: return null
                    val visible = wr.inFrameLikelihood > POSE_MIN_LIKELIHOOD && sh.inFrameLikelihood > POSE_MIN_LIKELIHOOD
                    return WaveDetector.Arm(wr.position.x / w, wr.position.y / h, sh.position.y / h, visible)
                }
                val arms = listOfNotNull(
                    arm(PoseLandmark.LEFT_WRIST, PoseLandmark.LEFT_SHOULDER),
                    arm(PoseLandmark.RIGHT_WRIST, PoseLandmark.RIGHT_SHOULDER),
                )
                if (waveDetector.update(now, arms) == WaveDetector.Event.WAVE) _waves.tryEmit(Unit)
                _scene.value = _scene.value.copy(
                    personPose = pose.allPoseLandmarks.isNotEmpty(),
                    handRaised = waveDetector.handRaised,
                )
            }
        }

        textRequest?.let { request ->
            textRequest = null
            tasks += textRecognizer.process(input)
                .addOnSuccessListener(results) { request.complete(it.text.trim()) }
                .addOnFailureListener(results) { request.complete("") }
        }

        Tasks.whenAllComplete(tasks).addOnCompleteListener(results) { proxy.close() }
    }

    private companion object {
        const val TAG = "FaceTracker"
        const val LABEL_EVERY_MS = 3000L
        const val POSE_EVERY_MS = 250L
        const val LABEL_MIN_CONFIDENCE = 0.6f
        const val POSE_MIN_LIKELIHOOD = 0.5f
        const val MAX_LABELS = 6
    }
}
