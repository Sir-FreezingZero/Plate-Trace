package com.example.platescanner.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** One row per unique plate number ever recognised. */
@Entity(
    tableName = "plates",
    indices = [Index(value = ["plateNumber"], unique = true)]
)
data class PlateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val plateNumber: String,
    val firstSeen: Long,
    val lastSeen: Long,
    val sightingCount: Int,
    /** Absolute path of the most recent cropped image, used as the list thumbnail. */
    val latestImagePath: String,
    /** Wider shot of the back of the car from the latest sighting (null for older data). */
    val latestVehicleImagePath: String? = null,
    /** On the watch list: alert when this plate is seen. */
    @ColumnInfo(defaultValue = "0")
    val watched: Boolean = false,
    /** Starred: kept forever (never auto-deleted). */
    @ColumnInfo(defaultValue = "0")
    val starred: Boolean = false,
)

/** One row per time a plate was seen; each has its own cropped image. */
@Entity(
    tableName = "sightings",
    foreignKeys = [
        ForeignKey(
            entity = PlateEntity::class,
            parentColumns = ["id"],
            childColumns = ["plateId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index(value = ["plateId"])]
)
data class SightingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val plateId: Long,
    val timestamp: Long,
    val imagePath: String,
    /** Wider crop showing the back of the vehicle (null for sightings saved before v2). */
    val vehicleImagePath: String? = null,
    /** Where it was seen (null if location was off or unavailable). */
    val latitude: Double? = null,
    val longitude: Double? = null,
    /** GPS accuracy radius in metres. */
    val accuracyM: Float? = null,
    /** Looked up from the coordinates when online (may stay null). */
    val road: String? = null,
    val city: String? = null,
    val state: String? = null,
    /** Text exactly as the recogniser read it, before normalisation (e.g. "ABC-1234"). */
    val rawText: String,
)

/** A plate number to alert on that hasn't been seen yet (typed in on the Plates page). */
@Entity(tableName = "watches")
data class WatchEntity(
    @PrimaryKey val plateNumber: String,
    val createdAt: Long,
)
