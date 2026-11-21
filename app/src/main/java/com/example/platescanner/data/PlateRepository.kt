package com.example.platescanner.data

import android.content.Context
import android.graphics.Bitmap
import com.example.platescanner.scan.PlateTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** Saves plate crops to app-private storage and their metadata to Room. */
class PlateRepository(context: Context) {

    private val dao = PlateDatabase.get(context).plateDao()
    private val imageDir = File(context.filesDir, "plate_crops").apply { mkdirs() }

    val plates: Flow<List<PlateEntity>> = dao.observePlates()

    /** Plates to alert on that haven't been seen yet. */
    val watches: Flow<List<WatchEntity>> = dao.observeWatches()

    suspend fun addWatch(plateNumber: String) = withContext(Dispatchers.IO) {
        dao.insertWatch(WatchEntity(plateNumber, System.currentTimeMillis()))
    }

    suspend fun removeWatch(plateNumber: String) = withContext(Dispatchers.IO) {
        dao.deleteWatch(plateNumber)
    }

    fun plate(id: Long): Flow<PlateEntity?> = dao.observePlate(id)

    fun sightings(plateId: Long): Flow<List<SightingEntity>> = dao.observeSightings(plateId)

    /** @return whether the plate is new and whether it's on the watch list. */
    suspend fun save(plateNumber: String, rawText: String, crop: Bitmap, vehicle: Bitmap): SaveResult =
        withContext(Dispatchers.IO) {
            val timestamp = System.currentTimeMillis()
            val plateFile = File(imageDir, "${plateNumber}_$timestamp.jpg")
            FileOutputStream(plateFile).use { crop.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            val vehicleFile = File(imageDir, "${plateNumber}_${timestamp}_vehicle.jpg")
            FileOutputStream(vehicleFile).use { vehicle.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            // A typed-in watch matching this plate (allowing look-alike characters) turns into a
            // watched plate now that it has been seen.
            val folded = PlateTracker.fold(plateNumber)
            val watch = dao.allWatches().firstOrNull { PlateTracker.fold(it.plateNumber) == folded }
            val result = dao.recordSighting(
                plateNumber, rawText, timestamp, plateFile.absolutePath, vehicleFile.absolutePath,
                markWatched = watch != null,
            )
            if (watch != null) dao.deleteWatch(watch.plateNumber)
            result
        }

    suspend fun delete(plateId: Long) = withContext(Dispatchers.IO) {
        val paths = dao.imagePathsFor(plateId) + dao.vehicleImagePathsFor(plateId)
        dao.deletePlate(plateId)
        paths.forEach { File(it).delete() }
    }

    suspend fun setWatched(plateId: Long, watched: Boolean) = withContext(Dispatchers.IO) {
        dao.setWatched(plateId, watched)
    }

    suspend fun merge(fromId: Long, intoId: Long) = withContext(Dispatchers.IO) {
        dao.merge(fromId, intoId)
    }

    suspend fun setStarred(plateId: Long, starred: Boolean) = withContext(Dispatchers.IO) {
        dao.setStarred(plateId, starred)
    }

    /** Deletes plates (and their images) not seen since [cutoff], except watched or starred. */
    suspend fun deleteOlderThan(cutoff: Long): Int = withContext(Dispatchers.IO) {
        val ids = dao.expiredPlateIds(cutoff)
        ids.forEach { delete(it) }
        ids.size
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        dao.deleteEverything()
        imageDir.listFiles()?.forEach { it.delete() }
    }
}
