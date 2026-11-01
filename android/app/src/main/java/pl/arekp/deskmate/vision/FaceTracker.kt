package pl.arekp.deskmate.vision

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
)

/**
 * Wykrywa twarz lokalnie na telefonie (ML Kit) – obraz z kamery nie opuszcza urządzenia.
 */
class FaceTracker(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
) {
    private val _faces = MutableStateFlow(FaceObservation(present = false))
    val faces: StateFlow<FaceObservation> = _faces.asStateFlow()

    private val executor = Executors.newSingleThreadExecutor()
    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.1f)
            .enableTracking()
            .build(),
    )

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
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
            } catch (e: Exception) {
                Log.e(TAG, "Nie udało się uruchomić kamery", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun stop() {
        runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
        detector.close()
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

        detector.process(input)
            .addOnSuccessListener { faces ->
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
                    )
                }
            }
            .addOnCompleteListener { proxy.close() }
    }

    private companion object {
        const val TAG = "FaceTracker"
    }
}
