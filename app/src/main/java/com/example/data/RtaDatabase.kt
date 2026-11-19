package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        MeasurementSnapshotEntity::class,
        CalibrationProfileEntity::class,
        CustomTargetCurveEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class RtaDatabase : RoomDatabase() {
    abstract fun snapshotDao(): MeasurementSnapshotDao
    abstract fun calibrationDao(): CalibrationProfileDao
    abstract fun customTargetCurveDao(): CustomTargetCurveDao

    companion object {
        @Volatile
        private var INSTANCE: RtaDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `custom_target_curves` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `sourcePointCount` INTEGER NOT NULL,
                        `relativeDb31Csv` TEXT NOT NULL,
                        `createdAtMs` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): RtaDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    RtaDatabase::class.java,
                    "autorta_acoustic.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
