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
import com.example.platescanner.scan.PlateDetection
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

/** Short message shown on the camera screen. */
data class Notice(val text: String, val kind: Kind) {
    enum class Kind { NEW_PLATE, REPEAT_PLATE, INFO }
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

    /** Single background thread for frame analysis; outlives camera rebinds on rotation. */
    val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    val analyzer = PlateAnalyzer { detection -> save(listOf(detection)) }

    init {
        analyzer.cooldownMs = _cooldownSeconds.value * 1_000L
    }

    fun toggleRecording() {
        val on = !_recording.value
        if (on) _recordingSince.value = SystemClock.elapsedRealtime()
        analyzer.enabled = on
        _recording.value = on
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
            analyzer.scanStill(bitmap) { detections ->
                if (detections.isEmpty()) {
                    _notices.tryEmit(Notice("No plate found in photo", Notice.Kind.INFO))
                } else {
                    save(detections)
                }
            }
        }
    }

    private fun save(detections: List<PlateDetection>) {
        viewModelScope.launch {
            for (d in detections) {
                try {
                    val isNew = repository.save(d.plate, d.rawText, d.crop)
                    val kind = if (isNew) Notice.Kind.NEW_PLATE else Notice.Kind.REPEAT_PLATE
                    _notices.tryEmit(Notice(d.plate, kind))
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
    }
}
