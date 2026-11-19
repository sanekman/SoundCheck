package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.audio.AveragingMode
import com.example.audio.FrequencyWeighting
import com.example.audio.RtaDisplayMode
import com.example.audio.TargetCurves
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.rtaDataStore by preferencesDataStore(name = "autorta_prefs")

data class RtaSettings(
    val targetCurveId: String = TargetCurves.HARMAN.id,
    val showTargetCurve: Boolean = true,
    val targetToleranceDb: Float = 1.5f,
    val weighting: FrequencyWeighting = FrequencyWeighting.Z,
    val averagingMode: AveragingMode = AveragingMode.SLOW,
    val displayMode: RtaDisplayMode = RtaDisplayMode.BOTH,
    val peakHoldEnabled: Boolean = true,
    val autoAlignTarget: Boolean = true,
    val manualTargetRefDb: Float = 75f,
    val manualSplOffsetDb: Float = 0f,
    val activeCalibrationProfileId: Long = -1L
)

class RtaPreferences(private val context: Context) {

    private object Keys {
        val TARGET_CURVE_ID = stringPreferencesKey("target_curve_id")
        val SHOW_TARGET_CURVE = booleanPreferencesKey("show_target_curve")
        val TARGET_TOLERANCE_DB = floatPreferencesKey("target_tolerance_db")
        val WEIGHTING = stringPreferencesKey("weighting")
        val AVERAGING = stringPreferencesKey("averaging")
        val DISPLAY_MODE = stringPreferencesKey("display_mode")
        val PEAK_HOLD = booleanPreferencesKey("peak_hold")
        val AUTO_ALIGN = booleanPreferencesKey("auto_align")
        val MANUAL_TARGET_REF_DB = floatPreferencesKey("manual_target_ref_db")
        val MANUAL_SPL_OFFSET_DB = floatPreferencesKey("manual_spl_offset_db")
        val ACTIVE_CAL_ID = longPreferencesKey("active_cal_id")
    }

    val settingsFlow: Flow<RtaSettings> = context.rtaDataStore.data.map { prefs ->
        RtaSettings(
            targetCurveId = prefs[Keys.TARGET_CURVE_ID] ?: TargetCurves.HARMAN.id,
            showTargetCurve = prefs[Keys.SHOW_TARGET_CURVE] ?: true,
            targetToleranceDb = prefs[Keys.TARGET_TOLERANCE_DB] ?: 1.5f,
            weighting = prefs[Keys.WEIGHTING]?.let { name ->
                FrequencyWeighting.entries.firstOrNull { it.name == name }
            } ?: FrequencyWeighting.Z,
            averagingMode = prefs[Keys.AVERAGING]?.let { name ->
                AveragingMode.entries.firstOrNull { it.name == name }
            } ?: AveragingMode.SLOW,
            displayMode = prefs[Keys.DISPLAY_MODE]?.let { name ->
                RtaDisplayMode.entries.firstOrNull { it.name == name }
            } ?: RtaDisplayMode.BOTH,
            peakHoldEnabled = prefs[Keys.PEAK_HOLD] ?: true,
            autoAlignTarget = prefs[Keys.AUTO_ALIGN] ?: true,
            manualTargetRefDb = prefs[Keys.MANUAL_TARGET_REF_DB] ?: 75f,
            manualSplOffsetDb = prefs[Keys.MANUAL_SPL_OFFSET_DB] ?: 0f,
            activeCalibrationProfileId = prefs[Keys.ACTIVE_CAL_ID] ?: -1L
        )
    }

    suspend fun setTargetCurve(id: String) {
        context.rtaDataStore.edit { it[Keys.TARGET_CURVE_ID] = id }
    }

    suspend fun setShowTargetCurve(show: Boolean) {
        context.rtaDataStore.edit { it[Keys.SHOW_TARGET_CURVE] = show }
    }

    suspend fun setTargetToleranceDb(db: Float) {
        context.rtaDataStore.edit { it[Keys.TARGET_TOLERANCE_DB] = db.coerceIn(0.5f, 5.0f) }
    }

    suspend fun setWeighting(weighting: FrequencyWeighting) {
        context.rtaDataStore.edit { it[Keys.WEIGHTING] = weighting.name }
    }

    suspend fun setAveraging(mode: AveragingMode) {
        context.rtaDataStore.edit { it[Keys.AVERAGING] = mode.name }
    }

    suspend fun setDisplayMode(mode: RtaDisplayMode) {
        context.rtaDataStore.edit { it[Keys.DISPLAY_MODE] = mode.name }
    }

    suspend fun setPeakHold(enabled: Boolean) {
        context.rtaDataStore.edit { it[Keys.PEAK_HOLD] = enabled }
    }

    suspend fun setAutoAlignTarget(enabled: Boolean) {
        context.rtaDataStore.edit { it[Keys.AUTO_ALIGN] = enabled }
    }

    suspend fun setManualTargetRefDb(db: Float) {
        context.rtaDataStore.edit { it[Keys.MANUAL_TARGET_REF_DB] = db }
    }

    suspend fun setManualSplOffsetDb(db: Float) {
        context.rtaDataStore.edit { it[Keys.MANUAL_SPL_OFFSET_DB] = db }
    }

    suspend fun setActiveCalibrationProfileId(id: Long) {
        context.rtaDataStore.edit { it[Keys.ACTIVE_CAL_ID] = id }
    }
}
