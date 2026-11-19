package com.example.audio

import kotlin.math.abs

enum class AutoCalStep {
    IDLE,
    QUIET_CHECK,
    SENSITIVITY_MEASUREMENT,
    FR_SWEEP_MEASUREMENT,
    REVIEW_RESULT
}

enum class AutoCalSignalSource(
    val shortLabel: String,
    val description: String
) {
    HEAD_UNIT_MANUAL(
        shortLabel = "ГУ (вручную)",
        description = "Тестовый трек (1 кГц / розовый шум) включается вручную на головном устройстве автомобиля (USB / CD / FLAC / BT)."
    ),
    PHONE_GENERATOR(
        shortLabel = "Телефон (BT/AUX)",
        description = "Встроенный генератор приложения подаёт сигнал с телефона в аудиосистему (или можно включить шум вручную перед замером)."
    ),
    ACOUSTIC_CALIBRATOR(
        shortLabel = "Калибратор 94 дБ",
        description = "Внешний акустический калибратор (пистонфон 94 дБ, 1 кГц) надевается на капсюль микрофона; шум для АЧХ запускается на ГУ."
    )
}

data class AutoCalResult(
    val profileName: String,
    val noiseFloorDb: Float,
    val measured1kHzDb: Float,
    val targetReferenceSplDb: Float,
    val sensitivityOffsetDb: Float,
    val estimatedSnrDb: Float,
    val correctionPoints: List<CalibrationPoint>
)

data class AutoCalUiState(
    val step: AutoCalStep = AutoCalStep.IDLE,
    val isMeasuring: Boolean = false,
    val progress: Float = 0f,
    val statusMessage: String = "",
    val signalSource: AutoCalSignalSource = AutoCalSignalSource.HEAD_UNIT_MANUAL,
    val targetReferenceSplDb: Float = 75f, // 75 dB in-car acoustic reference or 94 dB calibrator
    val useExternalCalibrator: Boolean = false,
    val currentAmbientDb: Float = 0f,
    val measuredNoiseFloorDb: Float? = null,
    val isEnvironmentQuiet: Boolean = true,
    val measured1kHzDb: Float? = null,
    val result: AutoCalResult? = null
)

/**
 * Pure acoustic calibration accumulator for the 4-step External RTA Auto-Calibration Wizard.
 * Works directly with live FFT frames captured from the external RTA microphone.
 */
class AutoCalibrationAccumulator {
    private val ambientSamples = mutableListOf<Float>()
    private val sensitivitySamples = mutableListOf<Float>()
    private val bandAccumulators = DoubleArray(31)
    private var frFrameCount = 0

    fun resetAmbient() {
        ambientSamples.clear()
    }

    fun addAmbientFrame(overallSplDb: Float) {
        ambientSamples.add(overallSplDb)
    }

    fun finishAmbient(): Float {
        if (ambientSamples.isEmpty()) return 40f
        return ambientSamples.average().toFloat()
    }

    fun resetSensitivity() {
        sensitivitySamples.clear()
    }

    fun addSensitivityFrame(bands31Db: FloatArray, overallSplDb: Float) {
        // Index 17 is 1000 Hz ISO 1/3-octave band
        val val1k = if (bands31Db.size > 17) bands31Db[17] else overallSplDb
        sensitivitySamples.add(val1k)
    }

    fun finishSensitivity(): Float {
        if (sensitivitySamples.isEmpty()) return 75f
        return sensitivitySamples.average().toFloat()
    }

    fun resetFrSweep() {
        bandAccumulators.fill(0.0)
        frFrameCount = 0
    }

    fun addFrFrame(bands31Db: FloatArray) {
        if (bands31Db.size < 31) return
        for (i in 0 until 31) {
            bandAccumulators[i] += bands31Db[i].toDouble()
        }
        frFrameCount++
    }

    /**
     * Generates a smoothed 31-point microphone frequency response calibration curve.
     * Each CalibrationPoint stores the measured mic error (dB) relative to 1 kHz,
     * so CalibrationParser.interpolateCorrectionDb(-offsetDb) flattens the mic response!
     */
    fun buildCalibrationResult(
        micName: String,
        noiseFloorDb: Float,
        measured1kHzDb: Float,
        targetReferenceSplDb: Float
    ): AutoCalResult {
        val avgBands = FloatArray(31) { i ->
            if (frFrameCount > 0) (bandAccumulators[i] / frFrameCount).toFloat() else measured1kHzDb
        }

        // Reference anchor around 1 kHz (bands 16, 17, 18 -> 800Hz, 1kHz, 1.25kHz)
        val ref1k = (avgBands[16] + avgBands[17] + avgBands[18]) / 3f

        // Compute raw deviation from 1kHz anchor and apply 3-point moving smoothing
        // so sharp cabin reflections don't cause excessive narrowband mic compensation
        val rawDev = FloatArray(31) { i ->
            (avgBands[i] - ref1k).coerceIn(-12f, 12f)
        }
        val smoothedDev = FloatArray(31) { i ->
            when (i) {
                0 -> (rawDev[0] * 0.7f + rawDev[1] * 0.3f)
                30 -> (rawDev[29] * 0.3f + rawDev[30] * 0.7f)
                else -> (rawDev[i - 1] * 0.25f + rawDev[i] * 0.5f + rawDev[i + 1] * 0.25f)
            }
        }

        // Clamp extremes gently and preserve exact 0 dB at 1 kHz anchor
        val anchorShift = smoothedDev[17]
        val points = RtaBands.ISO_31_BANDS.mapIndexed { idx, band ->
            val errDb = (smoothedDev[idx] - anchorShift).coerceIn(-10f, 10f)
            // Round to 0.1 dB for clean display & export
            val rounded = kotlin.math.round(errDb * 10f) / 10f
            CalibrationPoint(band.nominalHz, rounded)
        }

        val sensitivityOffset = (targetReferenceSplDb - measured1kHzDb).coerceIn(-35f, 35f)
        val roundedSens = kotlin.math.round(sensitivityOffset * 10f) / 10f
        val snr = abs(measured1kHzDb - noiseFloorDb).coerceAtLeast(0f)

        val cleanMicName = micName.take(26).trim().ifEmpty { "RTA Mic" }

        return AutoCalResult(
            profileName = "Авто: $cleanMicName",
            noiseFloorDb = noiseFloorDb,
            measured1kHzDb = measured1kHzDb,
            targetReferenceSplDb = targetReferenceSplDb,
            sensitivityOffsetDb = roundedSens,
            estimatedSnrDb = snr,
            correctionPoints = points
        )
    }
}
