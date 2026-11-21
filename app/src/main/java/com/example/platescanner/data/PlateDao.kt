package com.example.platescanner.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Outcome of saving a sighting. */
data class SaveResult(val isNew: Boolean, val watched: Boolean)

@Dao
abstract class PlateDao {

    @Query("UPDATE plates SET watched = :watched WHERE id = :id")
    abstract suspend fun setWatched(id: Long, watched: Boolean)


    @Query("SELECT * FROM plates ORDER BY lastSeen DESC")
    abstract fun observePlates(): Flow<List<PlateEntity>>

    @Query("SELECT * FROM plates WHERE id = :id")
    abstract fun observePlate(id: Long): Flow<PlateEntity?>

    @Query("SELECT * FROM sightings WHERE plateId = :plateId ORDER BY timestamp DESC")
    abstract fun observeSightings(plateId: Long): Flow<List<SightingEntity>>

    @Query("SELECT * FROM plates WHERE plateNumber = :number LIMIT 1")
    abstract suspend fun findByNumber(number: String): PlateEntity?

    @Query("SELECT imagePath FROM sightings WHERE plateId = :plateId")
    abstract suspend fun imagePathsFor(plateId: Long): List<String>

    @Query("SELECT vehicleImagePath FROM sightings WHERE plateId = :plateId AND vehicleImagePath IS NOT NULL")
    abstract suspend fun vehicleImagePathsFor(plateId: Long): List<String>

    @Insert
    abstract suspend fun insertPlate(plate: PlateEntity): Long

    @Update
    abstract suspend fun updatePlate(plate: PlateEntity)

    @Insert
    abstract suspend fun insertSighting(sighting: SightingEntity): Long

    @Query("DELETE FROM sightings WHERE plateId = :plateId")
    abstract suspend fun deleteSightingsFor(plateId: Long)

    @Query("DELETE FROM plates WHERE id = :id")
    abstract suspend fun deletePlateRow(id: Long)

    /**
     * Adds a sighting, creating the plate if needed.
     * @return whether the plate is new and whether it's on the watch list.
     */
    @Transaction
    open suspend fun recordSighting(
        number: String,
        rawText: String,
        timestamp: Long,
        imagePath: String,
        vehicleImagePath: String?,
    ): SaveResult {
        val existing = findByNumber(number)
        val plateId: Long
        val isNew: Boolean
        if (existing == null) {
            plateId = insertPlate(
                PlateEntity(
                    plateNumber = number,
                    firstSeen = timestamp,
                    lastSeen = timestamp,
                    sightingCount = 1,
                    latestImagePath = imagePath,
                    latestVehicleImagePath = vehicleImagePath,
                )
            )
            isNew = true
        } else {
            updatePlate(
                existing.copy(
                    lastSeen = timestamp,
                    sightingCount = existing.sightingCount + 1,
                    latestImagePath = imagePath,
                    latestVehicleImagePath = vehicleImagePath,
                )
            )
            plateId = existing.id
            isNew = false
        }
        insertSighting(
            SightingEntity(
                plateId = plateId,
                timestamp = timestamp,
                imagePath = imagePath,
                vehicleImagePath = vehicleImagePath,
                rawText = rawText,
            )
        )
        return SaveResult(isNew = isNew, watched = existing?.watched == true)
    }

    @Query("DELETE FROM sightings")
    abstract suspend fun deleteAllSightings()

    @Query("DELETE FROM plates")
    abstract suspend fun deleteAllPlates()

    @Transaction
    open suspend fun deleteEverything() {
        deleteAllSightings()
        deleteAllPlates()
    }

    @Transaction
    open suspend fun deletePlate(id: Long) {
        deleteSightingsFor(id)
        deletePlateRow(id)
    }
}
