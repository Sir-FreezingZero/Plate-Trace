package com.example.platescanner.scan

import android.content.Context
import android.graphics.Bitmap
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

/** What the reader saw in the latest frame, for the optional on-screen readout. */
class FrameInfo(
    val width: Int,
    val height: Int,
    val millis: Long,
    /** text, lowest character confidence, plate width in px */
    val reads: List<Triple<String, Float, Int>>,
)

/**
 * CameraX analyzer. For each frame (while recording):
 *
 * 1. **Find** plates in the frame with the plate detector.
 * 2. **Crop** each plate (with a small margin).
 * 3. **Read** the crop with the plate reader.
 * 4. **Check** the text against the plate-format rules from Settings.
 * 5. **Vote** across every read while the plate is in view ([PlateTracker]), then hand the
 *    agreed text, the clearest plate crop and a car shot centred on the plate to [onPlate].
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

    var agreeReads: Int
        get() = tracker.agreeReads
        set(value) { tracker.agreeReads = value }

    /** Optional: receives what was read in each frame (Settings → Show live readings). */
    @Volatile var onFrameInfo: ((FrameInfo) -> Unit)? = null

    /** Saves any plates still being voted on (call when recording stops). */
    fun flush() {
        tracker.flush(SystemClock.elapsedRealtime()).forEach { emit(it) }
    }

    private fun emit(r: PlateTracker.Result) =
        onPlate(PlateDetection(r.plate, r.rawText, r.crop, r.vehicle))

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
            val start = SystemClock.elapsedRealtime()
            val reads = findAndRead(frame)
            val now = SystemClock.elapsedRealtime()
            onFrameInfo?.invoke(
                FrameInfo(frame.width, frame.height, now - start, reads.map {
                    Triple(it.text, it.confidences.minOrNull() ?: 0f, it.box.width())
                })
            )
            // Every frame goes to the tracker, even with no reads, so it notices plates leaving.
            val scale = vehicleScale
            tracker.onFrame(now, reads) { box -> frame.cropVehicle(box, scale) }.forEach { emit(it) }
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
            .distinctBy { it.text }
            .map { PlateDetection(it.text, it.rawText, it.crop, bitmap.cropVehicle(it.box, vehicleScale)) }
    } catch (t: Throwable) {
        Log.e(TAG, "Still scan failed", t)
        emptyList()
    }

    /** Steps 1–4: detect, crop, read, check format. */
    private fun findAndRead(frame: Bitmap): List<PlateTracker.Read> {
        val models = engine ?: return emptyList()
        return models.detect(frame).mapNotNull { detection ->
            val crop = frame.cropAround(detection.box)
            val reading = models.read(crop)
            if (reading.text.length < 2 || reading.minConfidence < MIN_CHAR_CONFIDENCE) {
                return@mapNotNull null
            }
            val plate = PlateTextFilter.accept(PlateTextFilter.normalize(reading.text))
                ?: return@mapNotNull null
            // accept() may correct look-alike characters but never changes the length, so the
            // confidences still line up with the characters.
            val confidences = if (plate.length == reading.confidences.size) reading.confidences
            else FloatArray(plate.length) { reading.minConfidence }
            PlateTracker.Read(plate, reading.text, confidences, detection.box, crop)
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
