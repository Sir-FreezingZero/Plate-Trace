package com.example.platescanner.scan

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executor

/** A confirmed plate with its cropped image. */
class PlateDetection(val plate: String, val rawText: String, val crop: Bitmap)

/**
 * CameraX analyzer: runs on-device OCR on each frame, picks out plate-like text, and when a
 * plate is confirmed crops it out of the frame and hands it to [onPlate].
 */
class PlateAnalyzer(
    private val onPlate: (PlateDetection) -> Unit,
) : ImageAnalysis.Analyzer {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val tracker = PlateTracker()

    // Run ML Kit callbacks on the thread that completes the task, so we can still use the
    // ImageProxy (it must be closed only after we've cropped from it).
    private val direct = Executor { it.run() }

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        val rotation = imageProxy.imageInfo.rotationDegrees
        val input = InputImage.fromMediaImage(mediaImage, rotation)

        recognizer.process(input).addOnCompleteListener(direct) { task ->
            try {
                if (task.isSuccessful) handle(PlateTextFilter.extract(task.result), imageProxy, rotation)
                else Log.w(TAG, "Text recognition failed", task.exception)
            } catch (t: Throwable) {
                Log.e(TAG, "Plate handling failed", t)
            } finally {
                imageProxy.close()
            }
        }
    }

    private fun handle(candidates: List<PlateCandidate>, imageProxy: ImageProxy, rotation: Int) {
        if (candidates.isEmpty()) return
        val now = SystemClock.elapsedRealtime()
        val confirmed = candidates.filter { tracker.onRead(it.plate, now) }
        if (confirmed.isEmpty()) return

        // ML Kit boxes are in upright coordinates, so rotate the frame to match before cropping.
        val frame = imageProxy.toBitmap().rotated(rotation)
        for (c in confirmed) {
            onPlate(PlateDetection(c.plate, c.rawText, frame.cropAround(c.box)))
        }
    }

    fun close() = recognizer.close()

    private companion object {
        const val TAG = "PlateAnalyzer"
    }
}

private fun Bitmap.rotated(degrees: Int): Bitmap {
    if (degrees % 360 == 0) return this
    val m = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(this, 0, 0, width, height, m, true)
}

/**
 * Crops the text box plus a margin, so the saved image shows the whole plate rather than
 * just the characters.
 */
private fun Bitmap.cropAround(box: Rect, padX: Float = 0.18f, padY: Float = 0.7f): Bitmap {
    val dx = (box.width() * padX).toInt()
    val dy = (box.height() * padY).toInt()
    val left = (box.left - dx).coerceIn(0, width - 1)
    val top = (box.top - dy).coerceIn(0, height - 1)
    val right = (box.right + dx).coerceIn(left + 1, width)
    val bottom = (box.bottom + dy).coerceIn(top + 1, height)
    return Bitmap.createBitmap(this, left, top, right - left, bottom - top)
}
