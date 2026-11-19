package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MeasurementSnapshotDao {
    @Query("SELECT * FROM measurement_snapshots ORDER BY timestampMs DESC")
    fun getAllSnapshots(): Flow<List<MeasurementSnapshotEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshot(snapshot: MeasurementSnapshotEntity): Long

    @Query("DELETE FROM measurement_snapshots WHERE id = :id")
    suspend fun deleteSnapshotById(id: Long)
}

@Dao
interface CalibrationProfileDao {
    @Query("SELECT * FROM calibration_profiles ORDER BY createdAtMs DESC")
    fun getAllProfiles(): Flow<List<CalibrationProfileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: CalibrationProfileEntity): Long

    @Query("DELETE FROM calibration_profiles WHERE id = :id")
    suspend fun deleteProfileById(id: Long)
}

@Dao
interface CustomTargetCurveDao {
    @Query("SELECT * FROM custom_target_curves ORDER BY createdAtMs DESC")
    fun getAllCustomCurves(): Flow<List<CustomTargetCurveEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCurve(curve: CustomTargetCurveEntity): Long

    @Query("DELETE FROM custom_target_curves WHERE id = :id")
    suspend fun deleteCurveById(id: Long)
}
