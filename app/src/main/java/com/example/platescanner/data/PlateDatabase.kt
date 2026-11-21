package com.example.platescanner.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [PlateEntity::class, SightingEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class PlateDatabase : RoomDatabase() {
    abstract fun plateDao(): PlateDao

    companion object {
        @Volatile private var instance: PlateDatabase? = null

        fun get(context: Context): PlateDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    PlateDatabase::class.java,
                    "plates.db",
                ).build().also { instance = it }
            }
    }
}
