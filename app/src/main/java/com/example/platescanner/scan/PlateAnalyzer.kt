package com.example.platescanner.scan

import android.graphics.Bitmap
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

/** A confirmed plate with a tight plate crop and a wider crop of the back of the vehicle. */
class PlateDetection(
    val plate: String,
    val rawText: String,
    val crop: Bitmap,
    val vehicle: Bitmap,
)

/**
 * CameraX analyzer: runs on-device OCR on each frame, picks out plate-like text, and when a
 * plate is confirmed crops it out of the frame and hands it to [onPlate].
 */
class PlateAnalyzer(
    private val onPlate: (PlateDetection) -> Unit,
) : ImageAnalysis.Analyzer {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val tracker = PlateTracker()

    /** Frames are only analysed while recording. */
    @Volatile var enabled: Boolean = false

    var cooldownMs: Long
        get() = tracker.cooldownMs
        set(value) { tracker.cooldownMs = value }

    // Run ML Kit callbacks on the thread that completes the task, so we can still use the
    // ImageProxy (it must be closed only after we've cropped from it).
    private val direct = Executor { it.run() }

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        if (!enabled) {
            imageProxy.close()
            return
        }
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
            onPlate(PlateDetection(c.plate, c.rawText, frame.cropAround(c.box), frame.cropVehicle(c.box)))
        }
    }

    /**
     * Scans a single still image (e.g. a photo picked from the gallery). Every plate found is
     * returned straight away; the multi-frame confirmation and cooldown don't apply.
     * [onResult] runs on a background thread.
     */
    fun scanStill(bitmap: Bitmap, onResult: (List<PlateDetection>) -> Unit) {
        recognizer.process(InputImage.fromBitmap(bitmap, 0)).addOnCompleteListener(direct) { task ->
            val detections = try {
                if (task.isSuccessful) {
                    PlateTextFilter.extract(task.result).map {
                        PlateDetection(
                            it.plate, it.rawText, bitmap.cropAround(it.box), bitmap.cropVehicle(it.box)
                        )
                    }
                } else {
                    Log.w(TAG, "Text recognition failed", task.exception)
                    emptyList()
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Still scan failed", t)
                emptyList()
            }
            onResult(detections)
        }
    }

    fun close() = recognizer.close()

    private companion object {
        const val TAG = "PlateAnalyzer"
    }
}

