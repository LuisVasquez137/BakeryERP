package com.bakery.erp.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.os.SystemClock
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bakery.erp.model.SelfieCheck
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.Executor
import java.util.concurrent.Executors

actual val isSelfieCameraAvailable: Boolean = true

@Composable
actual fun SelfieCamera(
    modifier: Modifier,
    onStatus: (SelfieCameraStatus) -> Unit,
    onCaptured: (jpeg: ByteArray, check: SelfieCheck) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnStatus by rememberUpdatedState(onStatus)
    val currentOnCaptured by rememberUpdatedState(onCaptured)

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (!granted) currentOnStatus(SelfieCameraStatus.NO_PERMISSION)
    }
    LaunchedEffect(Unit) {
        if (hasPermission) currentOnStatus(SelfieCameraStatus.STARTING)
        else permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (!hasPermission) {
        Box(modifier.background(Color.Black))
        return
    }

    // TextureView (COMPATIBLE) y no SurfaceView: un SurfaceView ignora el recorte circular
    // de Compose y se dibujaría cuadrado.
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    AndroidView(factory = { previewView }, modifier = modifier)

    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    LaunchedEffect(Unit) {
        provider = runCatching {
            withContext(Dispatchers.IO) { ProcessCameraProvider.getInstance(context).get() }
        }.getOrNull()
        if (provider == null) currentOnStatus(SelfieCameraStatus.NO_CAMERA)
    }
    val cameraProvider = provider ?: return

    DisposableEffect(lifecycleOwner, cameraProvider) {
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val analysisExecutor = Executors.newSingleThreadExecutor()
        val detector = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                // Ojos abiertos/cerrados: es lo que detecta el parpadeo.
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                // Cara chica = alguien al fondo; no cuenta. 0.15 y no más: con la tablet en
                // horizontal la cara de quien está parado frente a ella ocupa poco del ancho.
                .setMinFaceSize(0.15f)
                .build()
        )
        val tracker = SelfieTracker(
            detector = detector,
            callbackExecutor = analysisExecutor,
            mainExecutor = mainExecutor,
            onStatus = { currentOnStatus(it) },
            onCaptured = { jpeg, check -> currentOnCaptured(jpeg, check) }
        )

        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        // 640x480 alcanza para detectar la cara y para la foto (se guarda a 360 px).
        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(640, 480),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                        )
                    )
                    .build()
            )
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        analysis.setAnalyzer(analysisExecutor) { proxy -> tracker.analyze(proxy) }

        val hasFront = runCatching { cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) }
            .getOrDefault(false)
        val bound = hasFront && runCatching {
            cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis)
        }.isSuccess
        if (!bound) currentOnStatus(SelfieCameraStatus.NO_CAMERA)

        onDispose {
            tracker.stop()
            runCatching { cameraProvider.unbind(preview, analysis) }
            analysis.clearAnalyzer()
            detector.close()
            analysisExecutor.shutdown()
        }
    }
}

/**
 * Máquina de estados de la selfie. Corre entera en el hilo de análisis; solo los avisos a
 * la pantalla saltan al hilo principal.
 *
 * Parpadeo = ojos abiertos → cerrados → abiertos con la MISMA cara a la vista; si la cara
 * se pierde o aparece otra, empieza de cero. La foto que se guarda es de ojos abiertos.
 */
private class SelfieTracker(
    private val detector: FaceDetector,
    private val callbackExecutor: Executor,
    private val mainExecutor: Executor,
    private val onStatus: (SelfieCameraStatus) -> Unit,
    private val onCaptured: (ByteArray, SelfieCheck) -> Unit
) {
    @Volatile private var done = false
    private var lastStatus: SelfieCameraStatus? = null
    private var faceSince = 0L
    private var lastFaceAt = 0L
    private var sawOpen = false
    private var sawClosed = false
    private var bestFrame: Bitmap? = null
    private var bestFrameAt = 0L
    private var failures = 0

    fun stop() { done = true }

    @OptIn(ExperimentalGetImage::class)
    fun analyze(proxy: ImageProxy) {
        val media = proxy.image
        if (done || media == null) { proxy.close(); return }
        val rotation = proxy.imageInfo.rotationDegrees
        detector.process(InputImage.fromMediaImage(media, rotation))
            .addOnSuccessListener(callbackExecutor) { faces ->
                try { onFaces(faces, proxy, rotation) } finally { proxy.close() }
            }
            .addOnFailureListener(callbackExecutor) {
                try { onFailure(proxy, rotation) } finally { proxy.close() }
            }
    }

    private fun onFaces(faces: List<Face>, proxy: ImageProxy, rotation: Int) {
        if (done) return
        failures = 0
        val now = SystemClock.elapsedRealtime()
        when {
            faces.isEmpty() -> {
                // Un cuadro sin cara (movió la cabeza, mal enfoque) no reinicia la cuenta;
                // solo si la cara de verdad se fue.
                if (now - lastFaceAt > FACE_LOST_GRACE_MS) {
                    reset(); post(SelfieCameraStatus.LOOK_AT_CAMERA)
                }
                return
            }
            faces.size > 1 -> { reset(); post(SelfieCameraStatus.MULTIPLE_FACES); return }
        }
        val face = faces.first()
        lastFaceAt = now
        if (faceSince == 0L) faceSince = now
        post(SelfieCameraStatus.BLINK)

        val left = face.leftEyeOpenProbability
        val right = face.rightEyeOpenProbability
        val eyesOpen = left != null && right != null && left > EYES_OPEN && right > EYES_OPEN
        val eyesClosed = left != null && right != null && left < EYES_CLOSED && right < EYES_CLOSED

        if (eyesOpen) {
            if (sawOpen && sawClosed) {
                capture(proxy.croppedTo(face.boundingBox, rotation), SelfieCheck.LIVE)
                return
            }
            sawOpen = true
            // Guarda una foto de ojos abiertos por si no llega el parpadeo; no en cada cuadro.
            if (now - bestFrameAt > 300) {
                bestFrame = proxy.croppedTo(face.boundingBox, rotation)
                bestFrameAt = now
            }
        } else if (eyesClosed && sawOpen) {
            sawClosed = true
        }

        if (now - faceSince > NO_BLINK_TIMEOUT_MS) {
            capture(bestFrame ?: proxy.croppedTo(face.boundingBox, rotation), SelfieCheck.FACE)
        }
    }

    /** El detector no responde (p. ej. su modelo todavía se está descargando). */
    private fun onFailure(proxy: ImageProxy, rotation: Int) {
        if (done) return
        failures++
        if (failures >= MAX_DETECTOR_FAILURES) {
            capture(proxy.croppedTo(null, rotation), SelfieCheck.PHOTO)
        }
    }

    private fun reset() {
        faceSince = 0L
        lastFaceAt = 0L
        sawOpen = false
        sawClosed = false
        bestFrame = null
        bestFrameAt = 0L
    }

    private fun capture(bitmap: Bitmap, check: SelfieCheck) {
        done = true
        val jpeg = ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            out.toByteArray()
        }
        mainExecutor.execute {
            onStatus(SelfieCameraStatus.CAPTURED)
            onCaptured(jpeg, check)
        }
    }

    private fun post(status: SelfieCameraStatus) {
        if (status == lastStatus) return
        lastStatus = status
        mainExecutor.execute { onStatus(status) }
    }

    private companion object {
        // Umbrales holgados: con poca luz o lentes ML Kit rara vez da >0.6 / <0.3, y un
        // parpadeo dura ~150 ms (1-2 cuadros); con umbrales estrictos se perdía y la foto
        // esperaba el tiempo límite completo.
        const val EYES_OPEN = 0.5f
        const val EYES_CLOSED = 0.4f
        /** Sin parpadeo, la foto sale igual (FACE) a los 2.5 s de ver la cara. */
        const val NO_BLINK_TIMEOUT_MS = 2_500L
        const val FACE_LOST_GRACE_MS = 700L
        const val MAX_DETECTOR_FAILURES = 15
    }
}

/**
 * Cuadro derecho, recortado en cuadrado alrededor de la cara (o al centro si no hay cara)
 * y reducido a 360 px: ~25 KB en JPEG. La caja de ML Kit ya viene en coordenadas del
 * cuadro rotado, por eso se rota antes de recortar.
 */
private fun ImageProxy.croppedTo(box: Rect?, rotation: Int): Bitmap {
    val raw = toBitmap()
    val upright = if (rotation == 0) raw else Bitmap.createBitmap(
        raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(rotation.toFloat()) }, true
    )
    val w = upright.width
    val h = upright.height
    val maxSide = minOf(w, h)
    val side: Int
    val cx: Int
    val cy: Int
    if (box == null) {
        side = maxSide; cx = w / 2; cy = h / 2
    } else {
        side = (maxOf(box.width(), box.height()) * 1.8f).toInt().coerceIn(1, maxSide)
        cx = box.centerX(); cy = box.centerY()
    }
    val left = (cx - side / 2).coerceIn(0, w - side)
    val top = (cy - side / 2).coerceIn(0, h - side)
    val cropped = Bitmap.createBitmap(upright, left, top, side, side)
    return if (side > SELFIE_SIDE_PX) Bitmap.createScaledBitmap(cropped, SELFIE_SIDE_PX, SELFIE_SIDE_PX, true)
    else cropped
}

private const val SELFIE_SIDE_PX = 360
