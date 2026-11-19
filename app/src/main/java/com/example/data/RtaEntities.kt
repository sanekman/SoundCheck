package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "measurement_snapshots")
data class MeasurementSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val channelTag: String,
    val timestampMs: Long = System.currentTimeMillis(),
    val overallSplDb: Float,
    val weightingCode: String,
    val targetCurveId: String,
    val micName: String,
    val calibrationName: String,
    val bands31Csv: String
)

@Entity(tableName = "calibration_profiles")
data class CalibrationProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sensitivityOffsetDb: Float,
    val pointCount: Int,
    val encodedPoints: String,
    val sourceType: String, // "IMPORTED_CAL" or "AUTO_CAL"
    val createdAtMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "custom_target_curves")
data class CustomTargetCurveEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String,
    val sourcePointCount: Int,
    val relativeDb31Csv: String,
    val createdAtMs: Long = System.currentTimeMillis()
)
