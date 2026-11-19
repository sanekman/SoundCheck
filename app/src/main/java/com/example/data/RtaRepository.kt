package com.example.data

import com.example.audio.CalibrationParser
import com.example.audio.CalibrationPoint
import com.example.audio.TargetCurvePreset
import kotlinx.coroutines.flow.Flow

data class RtaSnapshotModel(
    val id: Long,
    val name: String,
    val channelTag: String,
    val timestampMs: Long,
    val overallSplDb: Float,
    val weightingCode: String,
    val targetCurveId: String,
    val micName: String,
    val calibrationName: String,
    val bands31Db: FloatArray
)

data class CalibrationProfileModel(
    val id: Long,
    val name: String,
    val sensitivityOffsetDb: Float,
    val points: List<CalibrationPoint>,
    val sourceType: String,
    val createdAtMs: Long
)

class RtaRepository(
    private val snapshotDao: MeasurementSnapshotDao,
    private val calibrationDao: CalibrationProfileDao,
    private val customTargetCurveDao: CustomTargetCurveDao,
    val preferences: RtaPreferences
) {
    val snapshotsFlow: Flow<List<MeasurementSnapshotEntity>> = snapshotDao.getAllSnapshots()
    val calibrationProfilesFlow: Flow<List<CalibrationProfileEntity>> = calibrationDao.getAllProfiles()
    val customTargetCurvesFlow: Flow<List<CustomTargetCurveEntity>> = customTargetCurveDao.getAllCustomCurves()

    suspend fun saveSnapshot(
        name: String,
        channelTag: String,
        overallSplDb: Float,
        weightingCode: String,
        targetCurveId: String,
        micName: String,
        calibrationName: String,
        bands31Db: FloatArray
    ): Long {
        val csv = bands31Db.joinToString(",") { String.format(java.util.Locale.US, "%.2f", it) }
        return snapshotDao.insertSnapshot(
            MeasurementSnapshotEntity(
                name = name,
                channelTag = channelTag,
                overallSplDb = overallSplDb,
                weightingCode = weightingCode,
                targetCurveId = targetCurveId,
                micName = micName,
                calibrationName = calibrationName,
                bands31Csv = csv
            )
        )
    }

    suspend fun deleteSnapshot(id: Long) = snapshotDao.deleteSnapshotById(id)

    suspend fun saveCalibrationProfile(
        name: String,
        sensitivityOffsetDb: Float,
        points: List<CalibrationPoint>,
        sourceType: String
    ): Long {
        val encoded = CalibrationParser.encodePoints(points)
        return calibrationDao.insertProfile(
            CalibrationProfileEntity(
                name = name,
                sensitivityOffsetDb = sensitivityOffsetDb,
                pointCount = points.size,
                encodedPoints = encoded,
                sourceType = sourceType
            )
        )
    }

    suspend fun deleteCalibrationProfile(id: Long) = calibrationDao.deleteProfileById(id)

    suspend fun saveCustomTargetCurve(
        name: String,
        description: String,
        sourcePointCount: Int,
        relativeDb31: FloatArray
    ): Long {
        val csv = relativeDb31.joinToString(",") { String.format(java.util.Locale.US, "%.2f", it) }
        return customTargetCurveDao.insertCurve(
            CustomTargetCurveEntity(
                name = name,
                description = description,
                sourcePointCount = sourcePointCount,
                relativeDb31Csv = csv
            )
        )
    }

    suspend fun deleteCustomTargetCurve(id: Long) = customTargetCurveDao.deleteCurveById(id)

    fun toSnapshotModel(entity: MeasurementSnapshotEntity): RtaSnapshotModel {
        val parsed = entity.bands31Csv.split(",").mapNotNull { it.toFloatOrNull() }
        val arr = FloatArray(31) { idx -> parsed.getOrElse(idx) { 25f } }
        return RtaSnapshotModel(
            id = entity.id,
            name = entity.name,
            channelTag = entity.channelTag,
            timestampMs = entity.timestampMs,
            overallSplDb = entity.overallSplDb,
            weightingCode = entity.weightingCode,
            targetCurveId = entity.targetCurveId,
            micName = entity.micName,
            calibrationName = entity.calibrationName,
            bands31Db = arr
        )
    }

    fun toCalibrationModel(entity: CalibrationProfileEntity): CalibrationProfileModel {
        return CalibrationProfileModel(
            id = entity.id,
            name = entity.name,
            sensitivityOffsetDb = entity.sensitivityOffsetDb,
            points = CalibrationParser.decodePoints(entity.encodedPoints),
            sourceType = entity.sourceType,
            createdAtMs = entity.createdAtMs
        )
    }

    fun toTargetCurvePreset(entity: CustomTargetCurveEntity): TargetCurvePreset {
        val parsed = entity.relativeDb31Csv.split(",").mapNotNull { it.toFloatOrNull() }
        val arr = FloatArray(31) { idx -> parsed.getOrElse(idx) { 0f } }
        val short = entity.name.removeSuffix(".csv").removeSuffix(".txt").take(12)
        return TargetCurvePreset(
            id = "custom_${entity.id}",
            name = entity.name,
            shortName = short,
            description = entity.description,
            relativeDb31 = arr,
            isCustom = true,
            customDbId = entity.id
        )
    }
}
