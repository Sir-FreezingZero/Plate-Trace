package com.example.platescanner.data

import android.content.Context
import android.graphics.Bitmap
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
            dao.recordSighting(plateNumber, rawText, timestamp, plateFile.absolutePath, vehicleFile.absolutePath)
        }

    suspend fun delete(plateId: Long) = withContext(Dispatchers.IO) {
        val paths = dao.imagePathsFor(plateId) + dao.vehicleImagePathsFor(plateId)
        dao.deletePlate(plateId)
        paths.forEach { File(it).delete() }
    }

    suspend fun setWatched(plateId: Long, watched: Boolean) = withContext(Dispatchers.IO) {
        dao.setWatched(plateId, watched)
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        dao.deleteEverything()
        imageDir.listFiles()?.forEach { it.delete() }
    }
}
