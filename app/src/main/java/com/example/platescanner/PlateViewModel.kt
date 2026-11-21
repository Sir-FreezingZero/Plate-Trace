package com.example.platescanner

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.platescanner.data.PlateEntity
import com.example.platescanner.data.PlateRepository
import com.example.platescanner.data.SightingEntity
import com.example.platescanner.scan.PlateAnalyzer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Shown briefly on the camera screen when a plate is saved. */
data class SaveEvent(val plate: String, val isNew: Boolean)

class PlateViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = PlateRepository(app)

    val plates: StateFlow<List<PlateEntity>> = repository.plates
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _saveEvents = MutableSharedFlow<SaveEvent>(extraBufferCapacity = 16)
    val saveEvents: SharedFlow<SaveEvent> = _saveEvents

    /** Single background thread for frame analysis; outlives camera rebinds on rotation. */
    val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    val analyzer = PlateAnalyzer { detection ->
        viewModelScope.launch {
            try {
                val isNew = repository.save(detection.plate, detection.rawText, detection.crop)
                _saveEvents.tryEmit(SaveEvent(detection.plate, isNew))
            } catch (t: Throwable) {
                Log.e("PlateViewModel", "Could not save ${detection.plate}", t)
            }
        }
    }

    fun plate(id: Long): Flow<PlateEntity?> = repository.plate(id)

    fun sightings(plateId: Long): Flow<List<SightingEntity>> = repository.sightings(plateId)

    fun delete(plateId: Long) {
        viewModelScope.launch { repository.delete(plateId) }
    }

    override fun onCleared() {
        analysisExecutor.shutdown()
        analyzer.close()
    }
}
