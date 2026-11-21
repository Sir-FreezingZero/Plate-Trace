package com.example.platescanner.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [PlateEntity::class, SightingEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class PlateDatabase : RoomDatabase() {
    abstract fun plateDao(): PlateDao

    companion object {
        /** v3: watch list. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE plates ADD COLUMN watched INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v2: vehicle images alongside plate crops. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE plates ADD COLUMN latestVehicleImagePath TEXT")
                db.execSQL("ALTER TABLE sightings ADD COLUMN vehicleImagePath TEXT")
            }
        }

        @Volatile private var instance: PlateDatabase? = null

        fun get(context: Context): PlateDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    PlateDatabase::class.java,
                    "plates.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
            }
    }
}
