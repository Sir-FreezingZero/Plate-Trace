package com.example.platescanner

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.platescanner.data.PlateEntity
import com.example.platescanner.data.PlateRepository
import com.example.platescanner.data.SightingEntity
import com.example.platescanner.scan.PhotoLoader
import com.example.platescanner.scan.PlateAnalyzer
import com.example.platescanner.scan.FormatMode
import com.example.platescanner.scan.FrameInfo
import com.example.platescanner.scan.PlateDetection
import com.example.platescanner.scan.PlateRules
import com.example.platescanner.scan.PlateTextFilter
import com.example.platescanner.scan.PlateTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/** Where plates are read from (Settings). */
enum class CaptureMode { VIDEO, PHOTO }

/** Short message shown on the camera screen. */
data class Notice(val text: String, val kind: Kind) {
    enum class Kind { NEW_PLATE, REPEAT_PLATE, WATCH, INFO }
}

class PlateViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = PlateRepository(app)
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    val plates: StateFlow<List<PlateEntity>> = repository.plates
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _notices = MutableSharedFlow<Notice>(extraBufferCapacity = 16)
    val notices: SharedFlow<Notice> = _notices

    private val _recording = MutableStateFlow(false)
    val recording: StateFlow<Boolean> = _recording.asStateFlow()

    /** SystemClock.elapsedRealtime() when recording started. */
    private val _recordingSince = MutableStateFlow(0L)
    val recordingSince: StateFlow<Long> = _recordingSince.asStateFlow()

    private val _cooldownSeconds = MutableStateFlow(prefs.getInt(KEY_COOLDOWN, 15))
    val cooldownSeconds: StateFlow<Int> = _cooldownSeconds.asStateFlow()

    private val _formatRules = MutableStateFlow(
        PlateRules(
            mode = runCatching { FormatMode.valueOf(prefs.getString(KEY_FORMAT_MODE, null) ?: "US") }
                .getOrDefault(FormatMode.US),
            masks = (prefs.getStringSet(KEY_FORMAT_MASKS, null) ?: DEFAULT_MASKS).sorted(),
        )
    )
    val formatRules: StateFlow<PlateRules> = _formatRules.asStateFlow()

    private val _vehicleScale = MutableStateFlow(loadVehicleScale())
    val vehicleScale: StateFlow<Float> = _vehicleScale.asStateFlow()

    private val _captureMode = MutableStateFlow(
        runCatching { CaptureMode.valueOf(prefs.getString(KEY_CAPTURE_MODE, null) ?: "VIDEO") }
            .getOrDefault(CaptureMode.VIDEO)
    )
    val captureMode: StateFlow<CaptureMode> = _captureMode.asStateFlow()

    private val _photoIntervalMs = MutableStateFlow(prefs.getLong(KEY_PHOTO_INTERVAL, 1_000L))
    val photoIntervalMs: StateFlow<Long> = _photoIntervalMs.asStateFlow()

    private val _agreeReads = MutableStateFlow(prefs.getInt(KEY_AGREE_READS, 3))
    val agreeReads: StateFlow<Int> = _agreeReads.asStateFlow()

    /** Percent, 5–95. */
    private val _detectThreshold = MutableStateFlow(prefs.getInt(KEY_DETECT_THRESHOLD, 40))
    val detectThreshold: StateFlow<Int> = _detectThreshold.asStateFlow()

    /** Percent, 5–95. */
    private val _readThreshold = MutableStateFlow(prefs.getInt(KEY_READ_THRESHOLD, 40))
    val readThreshold: StateFlow<Int> = _readThreshold.asStateFlow()

    private val _showLive = MutableStateFlow(prefs.getBoolean(KEY_SHOW_LIVE, false))
    val showLive: StateFlow<Boolean> = _showLive.asStateFlow()

    /** Latest frame's reads, when "Show live readings" is on. */
    private val _liveInfo = MutableStateFlow<FrameInfo?>(null)
    val liveInfo: StateFlow<FrameInfo?> = _liveInfo.asStateFlow()

    /** Single background thread for frame analysis; outlives camera rebinds on rotation. */
    val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    val analyzer = PlateAnalyzer(app) { detection -> save(listOf(detection)) }

    init {
        analyzer.cooldownMs = _cooldownSeconds.value * 1_000L
        analyzer.vehicleScale = _vehicleScale.value
        PlateTextFilter.rules = _formatRules.value
        applyShowLive(_showLive.value)
        analyzer.agreeReads = _agreeReads.value
        applyCapture()
        analyzer.detectThreshold = _detectThreshold.value / 100f
        analyzer.minCharConfidence = _readThreshold.value / 100f
    }

    fun setDetectThreshold(percent: Int) {
        prefs.edit().putInt(KEY_DETECT_THRESHOLD, percent).apply()
        _detectThreshold.value = percent
        analyzer.detectThreshold = percent / 100f
    }

    fun setReadThreshold(percent: Int) {
        prefs.edit().putInt(KEY_READ_THRESHOLD, percent).apply()
        _readThreshold.value = percent
        analyzer.minCharConfidence = percent / 100f
    }

    fun setCaptureMode(mode: CaptureMode) {
        prefs.edit().putString(KEY_CAPTURE_MODE, mode.name).apply()
        _captureMode.value = mode
        applyCapture()
    }

    fun setPhotoInterval(ms: Long) {
        prefs.edit().putLong(KEY_PHOTO_INTERVAL, ms).apply()
        _photoIntervalMs.value = ms
        applyCapture()
    }

    private fun applyCapture() {
        analyzer.photoIntervalMs =
            if (_captureMode.value == CaptureMode.PHOTO) _photoIntervalMs.value else 0L
    }

    fun setAgreeReads(count: Int) {
        prefs.edit().putInt(KEY_AGREE_READS, count).apply()
        _agreeReads.value = count
        analyzer.agreeReads = count
    }

    fun setShowLive(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_LIVE, show).apply()
        _showLive.value = show
        applyShowLive(show)
    }

    private fun applyShowLive(show: Boolean) {
        analyzer.onFrameInfo = if (show) { info -> _liveInfo.value = info } else null
        if (!show) _liveInfo.value = null
    }

    fun toggleRecording() {
        val on = !_recording.value
        if (on) _recordingSince.value = SystemClock.elapsedRealtime()
        analyzer.enabled = on
        _recording.value = on
        if (!on) {
            // Save plates still being voted on. Runs on the analysis thread, after any frame
            // that's mid-processing.
            analysisExecutor.execute { analyzer.flush() }
            _liveInfo.value = null
        }
    }

    /** Runs plate recognition on a photo from the gallery and saves what it finds. */
    fun scanPhoto(uri: Uri) {
        viewModelScope.launch {
            _notices.tryEmit(Notice("Scanning photo…", Notice.Kind.INFO))
            val bitmap = withContext(Dispatchers.IO) { PhotoLoader.load(getApplication<Application>(), uri) }
            if (bitmap == null) {
                _notices.tryEmit(Notice("Couldn't open that photo", Notice.Kind.INFO))
                return@launch
            }
            val detections = withContext(Dispatchers.Default) { analyzer.scanStill(bitmap) }
            if (detections.isEmpty()) {
                _notices.tryEmit(Notice("No plate found in photo", Notice.Kind.INFO))
            } else {
                save(detections)
            }
        }
    }

    private fun save(detections: List<PlateDetection>) {
        viewModelScope.launch {
            for (d in detections) {
                try {
                    val result = repository.save(d.plate, d.rawText, d.crop, d.vehicle)
                    // Also alert for a reading that differs from a watched plate only by
                    // look-alike characters (0/O, 8/B …).
                    val folded = PlateTracker.fold(d.plate)
                    val watched = result.watched || plates.value.any {
                        it.watched && PlateTracker.fold(it.plateNumber) == folded
                    }
                    if (watched) {
                        WatchAlerts.alert(getApplication<Application>(), d.plate, d.vehicle)
                        _notices.tryEmit(Notice(d.plate, Notice.Kind.WATCH))
                    } else {
                        val kind = if (result.isNew) Notice.Kind.NEW_PLATE else Notice.Kind.REPEAT_PLATE
                        _notices.tryEmit(Notice(d.plate, kind))
                    }
                } catch (t: Throwable) {
                    Log.e("PlateViewModel", "Could not save ${d.plate}", t)
                }
            }
        }
    }

    fun setCooldown(seconds: Int) {
        prefs.edit().putInt(KEY_COOLDOWN, seconds).apply()
        _cooldownSeconds.value = seconds
        analyzer.cooldownMs = seconds * 1_000L
    }

    fun setFormatMode(mode: FormatMode) = updateRules(_formatRules.value.copy(mode = mode))

    fun setMaskEnabled(mask: String, enabled: Boolean) {
        val masks = _formatRules.value.masks.toMutableSet()
        if (enabled) masks.add(mask) else masks.remove(mask)
        updateRules(_formatRules.value.copy(masks = masks.sorted()))
    }

    private fun updateRules(rules: PlateRules) {
        prefs.edit()
            .putString(KEY_FORMAT_MODE, rules.mode.name)
            .putStringSet(KEY_FORMAT_MASKS, rules.masks.toSet())
            .apply()
        _formatRules.value = rules
        PlateTextFilter.rules = rules
    }

    /** Reads the car-shot setting, converting an older text-width-based value if present. */
    private fun loadVehicleScale(): Float = when {
        prefs.contains(KEY_VEHICLE_SCALE) -> prefs.getFloat(KEY_VEHICLE_SCALE, DEFAULT_VEHICLE_SCALE)
        prefs.contains(KEY_VEHICLE_SCALE_V1) ->
            // Plate text is about 3/4 of the plate's width.
            ((prefs.getFloat(KEY_VEHICLE_SCALE_V1, 6f) * 0.75f) * 2).roundToInt() / 2f
        else -> DEFAULT_VEHICLE_SCALE
    }.coerceIn(2f, 9f)

    fun setVehicleScale(scale: Float) {
        prefs.edit().putFloat(KEY_VEHICLE_SCALE, scale).apply()
        _vehicleScale.value = scale
        analyzer.vehicleScale = scale
    }

    /** Typed-in watches for plates not seen yet. */
    val watches: StateFlow<List<com.example.platescanner.data.WatchEntity>> = repository.watches
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Alert for [input] whenever it's seen. If it matches a saved plate (allowing look-alike
     * characters) that plate is watched; otherwise it's kept as a "waiting for" watch.
     * @return false if [input] isn't a usable plate number.
     */
    fun addWatch(input: String): Boolean {
        val plate = PlateTextFilter.normalize(input)
        if (plate.length !in 2..10) return false
        val folded = PlateTracker.fold(plate)
        val existing = plates.value.firstOrNull { PlateTracker.fold(it.plateNumber) == folded }
        viewModelScope.launch {
            if (existing != null) repository.setWatched(existing.id, true) else repository.addWatch(plate)
        }
        return true
    }

    fun removeWatch(plateNumber: String) {
        viewModelScope.launch { repository.removeWatch(plateNumber) }
    }

    fun setWatched(plateId: Long, watched: Boolean) {
        viewModelScope.launch { repository.setWatched(plateId, watched) }
    }

    fun plate(id: Long): Flow<PlateEntity?> = repository.plate(id)

    fun sightings(plateId: Long): Flow<List<SightingEntity>> = repository.sightings(plateId)

    fun delete(plateId: Long) {
        viewModelScope.launch { repository.delete(plateId) }
    }

    fun deleteAll() {
        viewModelScope.launch { repository.deleteAll() }
    }

    override fun onCleared() {
        analyzer.enabled = false
        analysisExecutor.shutdown()
        analyzer.close()
    }

    private companion object {
        const val KEY_COOLDOWN = "cooldown_seconds"
        const val KEY_FORMAT_MODE = "format_mode"
        const val KEY_SHOW_LIVE = "show_live_readings"
        const val KEY_AGREE_READS = "agree_reads"
        const val KEY_CAPTURE_MODE = "capture_mode"
        const val KEY_DETECT_THRESHOLD = "detect_threshold_pct"
        const val KEY_READ_THRESHOLD = "read_threshold_pct"
        const val KEY_PHOTO_INTERVAL = "photo_interval_ms"
        const val KEY_FORMAT_MASKS = "format_masks"
        /** v2: multiple of the detected plate's width (v1 was of the text width). */
        const val KEY_VEHICLE_SCALE = "vehicle_scale_v2"
        const val KEY_VEHICLE_SCALE_V1 = "vehicle_scale"
        const val DEFAULT_VEHICLE_SCALE = 4.5f
        val DEFAULT_MASKS = setOf("LLL0000")
    }
}
