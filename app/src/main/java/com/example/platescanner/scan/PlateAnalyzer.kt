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

/** One detected plate in a frame, for the optional live view. */
data class LiveBox(
    /** In upright frame pixels. */
    val box: Rect,
    /** What was read, or null if nothing readable. */
    val text: String?,
    val confidence: Float,
    /** True if the reading passed the confidence and plate-format checks. */
    val accepted: Boolean,
    /** Why it was rejected, when not accepted. */
    val reason: String? = null,
    /** Matching reads so far / needed to save (accepted plates only). */
    val matches: Int = 0,
    val needed: Int = 0,
    /** True once this plate has been saved; [savedText] is what it was saved as. */
    val saved: Boolean = false,
    val savedText: String? = null,
)

/** What the reader saw in the latest frame, for Settings → Show live readings. */
class FrameInfo(
    val width: Int,
    val height: Int,
    val millis: Long,
    val boxes: List<LiveBox>,
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
        set(value) {
            tracker.agreeReads = value
            retime()
        }

    /** 0 = video frames; otherwise the interval between still photos (Settings). */
    var photoIntervalMs: Long = 0
        set(value) {
            field = value
            retime()
        }

    /**
     * Video gives several images a second; photos may be seconds apart, so a passing car may
     * appear in only one or two. In photo mode a single (sharp, full-resolution) read is
     * enough for the fallback save, and "gone" / "waited long enough" scale with the interval.
     */
    private fun retime() {
        val interval = photoIntervalMs
        if (interval <= 0) {
            tracker.minReads = 2
            tracker.settleMs = 1_200
            tracker.maxWaitMs = 3_000
        } else {
            tracker.minReads = 1
            tracker.settleMs = (interval * 3 / 2 + 500)
            tracker.maxWaitMs = maxOf(3_000L, interval * tracker.agreeReads + 500)
        }
    }

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
            val listener = onFrameInfo
            val live = if (listener != null) ArrayList<LiveBox>() else null
            val reads = findAndRead(frame, live)
            val now = SystemClock.elapsedRealtime()
            // Every frame goes to the tracker, even with no reads, so it notices plates leaving.
            val scale = vehicleScale
            tracker.onFrame(now, reads) { box -> frame.cropVehicle(box, scale) }.forEach { emit(it) }
            if (listener != null && live != null) {
                val withStatus = live.map { b ->
                    if (!b.accepted || b.text == null) b
                    else tracker.status(b.text).let { st ->
                        b.copy(matches = st.matches, needed = st.needed, saved = st.saved, savedText = st.savedText)
                    }
                }
                listener(FrameInfo(frame.width, frame.height, now - start, withStatus))
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
            .distinctBy { it.text }
            .map { PlateDetection(it.text, it.rawText, it.crop, bitmap.cropVehicle(it.box, vehicleScale)) }
    } catch (t: Throwable) {
        Log.e(TAG, "Still scan failed", t)
        emptyList()
    }

    /** Steps 1–4: detect, crop, read, check format. */
    private fun findAndRead(frame: Bitmap, live: MutableList<LiveBox>? = null): List<PlateTracker.Read> {
        val models = engine ?: return emptyList()
        return models.detect(frame).mapNotNull { detection ->
            val crop = frame.cropAround(detection.box)
            val reading = models.read(crop)
            val text = reading.text.ifEmpty { null }
            if (reading.text.length < 2 || reading.minConfidence < MIN_CHAR_CONFIDENCE) {
                live?.add(LiveBox(detection.box, text, reading.minConfidence, accepted = false, reason = "unsure"))
                return@mapNotNull null
            }
            val plate = PlateTextFilter.accept(PlateTextFilter.normalize(reading.text))
            live?.add(
                LiveBox(
                    detection.box, plate ?: text, reading.minConfidence, accepted = plate != null,
                    reason = if (plate == null) "format" else null,
                )
            )
            if (plate == null) return@mapNotNull null
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
