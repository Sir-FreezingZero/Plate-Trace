package com.example.platescanner.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy

/** A confirmed plate with a tight plate crop and a wider crop of the back of the vehicle. */
class PlateDetection(
    val plate: String,
    val rawText: String,
    val crop: Bitmap,
    val vehicle: Bitmap,
)

/** One plate found and read in a single frame. */
private class PlateRead(val plate: String, val rawText: String, val box: Rect, val crop: Bitmap)

/**
 * CameraX analyzer. For each frame (while recording):
 *
 * 1. **Find** plates in the frame with the plate detector.
 * 2. **Crop** each plate (with a small margin).
 * 3. **Read** the crop with the plate reader.
 * 4. **Check** the text against the plate-format rules from Settings.
 * 5. **Confirm** with [PlateTracker] (repeat reads, out-of-view delay), then hand the plate
 *    crop and a car shot centred on the plate to [onPlate].
 *
 * Runs synchronously on the analysis thread; with STRATEGY_KEEP_ONLY_LATEST, frames that
 * arrive while one is being processed are simply dropped.
 */
class PlateAnalyzer(
    context: Context,
    private val onPlate: (PlateDetection) -> Unit,
) : ImageAnalysis.Analyzer {

    private val appContext = context.applicationContext

    /** Models are loaded on first use (on a background thread), not at startup. */
    private val engineLazy = lazy {
        try {
            PlateEngine(appContext)
        } catch (t: Throwable) {
            Log.e(TAG, "Could not load plate models", t)
            null
        }
    }
    private val engine: PlateEngine? get() = engineLazy.value

    private val tracker = PlateTracker()

    /** Car shot width as a multiple of the plate's width (Settings). */
    @Volatile var vehicleScale: Float = 4.5f

    /** Frames are only analysed while recording. */
    @Volatile var enabled: Boolean = false

    var cooldownMs: Long
        get() = tracker.cooldownMs
        set(value) { tracker.cooldownMs = value }

    override fun analyze(imageProxy: ImageProxy) {
        if (!enabled) {
            imageProxy.close()
            return
        }
        val frame = try {
            imageProxy.toBitmap().rotated(imageProxy.imageInfo.rotationDegrees)
        } catch (t: Throwable) {
            Log.e(TAG, "Frame conversion failed", t)
            null
        } finally {
            imageProxy.close() // free the camera buffer before the (slower) model work
        }
        if (frame == null) return

        try {
            val reads = findAndRead(frame)
            if (reads.isEmpty()) return
            val now = SystemClock.elapsedRealtime()
            // The tracker may return an earlier spelling of the same plate to save under.
            reads.mapNotNull { r -> tracker.onRead(r.plate, now)?.let { r to it } }
                .distinctBy { it.second }
                .forEach { (r, plate) ->
                    onPlate(PlateDetection(plate, r.rawText, r.crop, frame.cropVehicle(r.box, vehicleScale)))
                }
        } catch (t: Throwable) {
            Log.e(TAG, "Plate processing failed", t)
        }
    }

    /**
     * Scans a single still image (e.g. a photo from the gallery). Every plate found is returned
     * straight away; the repeat-read confirmation and cooldown don't apply. Blocking: call off
     * the main thread.
     */
    fun scanStill(bitmap: Bitmap): List<PlateDetection> = try {
        findAndRead(bitmap)
            .distinctBy { it.plate }
            .map { PlateDetection(it.plate, it.rawText, it.crop, bitmap.cropVehicle(it.box, vehicleScale)) }
    } catch (t: Throwable) {
        Log.e(TAG, "Still scan failed", t)
        emptyList()
    }

    /** Steps 1–4: detect, crop, read, check format. */
    private fun findAndRead(frame: Bitmap): List<PlateRead> {
        val models = engine ?: return emptyList()
        return models.detect(frame).mapNotNull { detection ->
            val crop = frame.cropAround(detection.box)
            val reading = models.read(crop)
            if (reading.text.length < 2 || reading.minConfidence < MIN_CHAR_CONFIDENCE) {
                return@mapNotNull null
            }
            val plate = PlateTextFilter.accept(PlateTextFilter.normalize(reading.text))
                ?: return@mapNotNull null
            PlateRead(plate, reading.text, detection.box, crop)
        }
    }

    fun close() {
        if (engineLazy.isInitialized()) engineLazy.value?.close()
    }

    private companion object {
        const val TAG = "PlateAnalyzer"

        /** Reads where any character is less certain than this are ignored. */
        const val MIN_CHAR_CONFIDENCE = 0.4f
    }
}
