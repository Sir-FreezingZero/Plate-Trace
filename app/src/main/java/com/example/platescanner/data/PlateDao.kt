package com.example.platescanner.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
abstract class PlateDao {

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
     * @return true if this was the first time the plate was seen.
     */
    @Transaction
    open suspend fun recordSighting(
        number: String,
        rawText: String,
        timestamp: Long,
        imagePath: String,
    ): Boolean {
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
                )
            )
            isNew = true
        } else {
            updatePlate(
                existing.copy(
                    lastSeen = timestamp,
                    sightingCount = existing.sightingCount + 1,
                    latestImagePath = imagePath,
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
                rawText = rawText,
            )
        )
        return isNew
    }

    @Transaction
    open suspend fun deletePlate(id: Long) {
        deleteSightingsFor(id)
        deletePlateRow(id)
    }
}
