package com.example.platescanner.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Outcome of saving a sighting. */
data class SaveResult(val isNew: Boolean, val watched: Boolean, val plateNumber: String = "")

@Dao
abstract class PlateDao {

    @Query("SELECT * FROM watches ORDER BY createdAt DESC")
    abstract fun observeWatches(): Flow<List<WatchEntity>>

    @Query("SELECT * FROM watches")
    abstract suspend fun allWatches(): List<WatchEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertWatch(watch: WatchEntity)

    @Query("DELETE FROM watches WHERE plateNumber = :plateNumber")
    abstract suspend fun deleteWatch(plateNumber: String)

    @Query("SELECT * FROM plates WHERE lastSeen >= :since")
    abstract suspend fun platesSeenSince(since: Long): List<PlateEntity>

    @Query("SELECT * FROM plates WHERE id = :id")
    abstract suspend fun plateById(id: Long): PlateEntity?

    @Query("UPDATE sightings SET plateId = :intoId WHERE plateId = :fromId")
    abstract suspend fun moveSightings(fromId: Long, intoId: Long)

    /** Moves all of [fromId]'s sightings onto [intoId] and removes [fromId]. */
    @Transaction
    open suspend fun merge(fromId: Long, intoId: Long) {
        if (fromId == intoId) return
        val from = plateById(fromId) ?: return
        val into = plateById(intoId) ?: return
        moveSightings(fromId, intoId)
        val fromIsNewer = from.lastSeen > into.lastSeen
        updatePlate(
            into.copy(
                sightingCount = into.sightingCount + from.sightingCount,
                firstSeen = minOf(into.firstSeen, from.firstSeen),
                lastSeen = maxOf(into.lastSeen, from.lastSeen),
                latestImagePath = if (fromIsNewer) from.latestImagePath else into.latestImagePath,
                latestVehicleImagePath =
                    if (fromIsNewer) from.latestVehicleImagePath else into.latestVehicleImagePath,
                watched = into.watched || from.watched,
                starred = into.starred || from.starred,
            )
        )
        deletePlateRow(fromId)
    }

    @Query("UPDATE plates SET starred = :starred WHERE id = :id")
    abstract suspend fun setStarred(id: Long, starred: Boolean)

    /** Plates not seen since [cutoff] that are neither watched nor starred. */
    @Query("SELECT id FROM plates WHERE lastSeen < :cutoff AND watched = 0 AND starred = 0")
    abstract suspend fun expiredPlateIds(cutoff: Long): List<Long>

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
        markWatched: Boolean = false,
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
                    watched = markWatched,
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
                    watched = existing.watched || markWatched,
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
        return SaveResult(
            isNew = isNew,
            watched = existing?.watched == true || markWatched,
            plateNumber = number,
        )
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
