package com.example.platescanner.ui

import android.os.SystemClock
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.platescanner.CaptureMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.concurrent.Executor

/**
 * Full-screen back-camera preview, feeding plates to [analyzer] in one of two ways:
 *
 * - [CaptureMode.VIDEO]: frames from the video stream (1080p), as fast as they can be processed.
 * - [CaptureMode.PHOTO]: a full-resolution still photo every [photoIntervalMs] while
 *   [recording]. Sharper and higher resolution on many phones, but fewer images per second.
 *
 * Every use case is 16:9 so they all show the same area as the preview, which keeps the
 * live-readings boxes lined up.
 */
@Composable
fun CameraPreview(
    analyzer: ImageAnalysis.Analyzer,
    analysisExecutor: Executor,
    mode: CaptureMode,
    photoIntervalMs: Long,
    recording: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    DisposableEffect(lifecycleOwner, mode) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var disposed = false

        providerFuture.addListener({
            if (disposed) return@addListener
            val cameraProvider = providerFuture.get().also { provider = it }

            val preview = Preview.Builder()
                .setResolutionSelector(selector16x9())
                .build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }

            val second: UseCase = when (mode) {
                CaptureMode.VIDEO -> ImageAnalysis.Builder()
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                            .setResolutionStrategy(
                                ResolutionStrategy(
                                    Size(1920, 1080),
                                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER,
                                )
                            )
                            .build()
                    )
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    // RGBA frames convert to a bitmap with a plain copy (YUV needs a conversion),
                    // which takes time off every frame.
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                    .also { it.setAnalyzer(analysisExecutor, analyzer) }

                CaptureMode.PHOTO -> ImageCapture.Builder()
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                            .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                            .build()
                    )
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setFlashMode(ImageCapture.FLASH_MODE_OFF)
                    .build()
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, second,
                )
                imageCapture = second as? ImageCapture
            } catch (e: Exception) {
                Log.e("CameraPreview", "Camera bind failed", e)
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            disposed = true
            imageCapture = null
            provider?.unbindAll()
        }
    }

    // Photo mode: take a picture, process it, wait out the rest of the interval, repeat.
    LaunchedEffect(imageCapture, recording, photoIntervalMs) {
        val capture = imageCapture ?: return@LaunchedEffect
        if (!recording) return@LaunchedEffect
        while (isActive) {
            val started = SystemClock.elapsedRealtime()
            val done = CompletableDeferred<Unit>()
            try {
                capture.takePicture(analysisExecutor, object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        try {
                            analyzer.analyze(image) // closes the image
                        } finally {
                            done.complete(Unit)
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.w("CameraPreview", "Photo capture failed", exception)
                        done.complete(Unit)
                    }
                })
            } catch (e: Exception) {
                Log.w("CameraPreview", "takePicture failed", e)
                done.complete(Unit)
            }
            done.await()
            val elapsed = SystemClock.elapsedRealtime() - started
            delay((photoIntervalMs - elapsed).coerceAtLeast(50))
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

private fun selector16x9() = ResolutionSelector.Builder()
    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
    .build()
