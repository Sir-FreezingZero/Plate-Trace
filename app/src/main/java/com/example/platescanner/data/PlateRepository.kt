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

    /** @return true if the plate had never been seen before. */
    suspend fun save(plateNumber: String, rawText: String, crop: Bitmap): Boolean =
        withContext(Dispatchers.IO) {
            val timestamp = System.currentTimeMillis()
            val file = File(imageDir, "${plateNumber}_$timestamp.jpg")
            FileOutputStream(file).use { crop.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            dao.recordSighting(plateNumber, rawText, timestamp, file.absolutePath)
        }

    suspend fun delete(plateId: Long) = withContext(Dispatchers.IO) {
        val paths = dao.imagePathsFor(plateId)
        dao.deletePlate(plateId)
        paths.forEach { File(it).delete() }
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        dao.deleteEverything()
        imageDir.listFiles()?.forEach { it.delete() }
    }
}
