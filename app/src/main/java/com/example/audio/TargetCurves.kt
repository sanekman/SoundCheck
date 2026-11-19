package com.example.audio

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class TargetCurvePreset(
    val id: String,
    val name: String,
    val shortName: String,
    val description: String,
    val relativeDb31: FloatArray,
    val isCustom: Boolean = false,
    val customDbId: Long? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TargetCurvePreset) return false
        return id == other.id && relativeDb31.contentEquals(other.relativeDb31)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + relativeDb31.contentHashCode()
        return result
    }
}

data class EqBandAdvice(
    val bandIndex: Int,
    val freqHz: Float,
    val freqLabel: String,
    val measuredDb: Float,
    val targetDb: Float,
    val deltaDb: Float,       // targetDb - measuredDb (+ means boost in DSP, - means cut in DSP)
    val recommendedQ: Float,
    val inTolerance: Boolean
)

data class EqAnalysisSummary(
    val alignedReferenceDb: Float,
    val matchScorePercent: Int,
    val rmsDeviationDb: Float,
    val adviceList: List<EqBandAdvice>
)

object TargetCurves {

    // 31 ISO bands: 20, 25, 31.5, 40, 50, 63, 80, 100, 125, 160, 200, 250, 315, 400, 500, 630,
    // 800, 1k, 1.25k, 1.6k, 2k, 2.5k, 3.15k, 4k, 5k, 6.3k, 8k, 10k, 12.5k, 16k, 20k

    val HARMAN = TargetCurvePreset(
        id = "harman",
        name = "Harman Car SQ",
        shortName = "Harman",
        description = "Классическая кривая Harman для авто: тёплый подъём НЧ +6 дБ и мягкий спад ВЧ.",
        relativeDb31 = floatArrayOf(
            4.5f, 5.5f, 6.0f, 6.0f, 5.8f, 5.2f, 4.3f, 3.3f, 2.4f, 1.6f,
            1.0f, 0.7f, 0.4f, 0.2f, 0.1f, 0.0f, 0.0f, 0.0f, -0.2f, -0.5f,
            -0.8f, -1.2f, -1.6f, -2.0f, -2.4f, -2.8f, -3.2f, -3.6f, -4.0f, -4.5f, -5.0f
        )
    )

    val AUDIOFROG = TargetCurvePreset(
        id = "audiofrog",
        name = "Audiofrog Target",
        shortName = "Audiofrog",
        description = "Референс Энди Вемейера: плотный суббас +8 дБ, ровная середина, деликатные ВЧ.",
        relativeDb31 = floatArrayOf(
            6.5f, 7.5f, 8.0f, 8.0f, 7.5f, 6.5f, 5.0f, 3.5f, 2.2f, 1.2f,
            0.5f, 0.2f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, -0.2f,
            -0.5f, -0.9f, -1.3f, -1.7f, -2.1f, -2.5f, -3.0f, -3.5f, -4.0f, -4.5f, -5.2f
        )
    )

    val EMMA_SQ = TargetCurvePreset(
        id = "emma_sq",
        name = "EMMA / SQ Референс",
        shortName = "EMMA SQ",
        description = "Соревновательная SQ-кривая: тональный баланс, ровные СЧ и детальные ВЧ.",
        relativeDb31 = floatArrayOf(
            3.0f, 4.0f, 4.5f, 4.5f, 4.2f, 3.6f, 2.8f, 2.0f, 1.3f, 0.8f,
            0.4f, 0.1f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f,
            -0.2f, -0.4f, -0.6f, -0.8f, -1.0f, -1.2f, -1.5f, -1.8f, -2.2f, -2.8f, -3.5f
        )
    )

    val SPL_BASS = TargetCurvePreset(
        id = "spl_bass",
        name = "Динамичный Bass+",
        shortName = "Bass+",
        description = "Энергичная подача для повседневной системы: глубокий саб +10 дБ и яркие ВЧ.",
        relativeDb31 = floatArrayOf(
            7.5f, 9.0f, 10.0f, 10.0f, 9.2f, 8.0f, 6.2f, 4.5f, 3.0f, 1.8f,
            0.8f, 0.3f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.1f, 0.2f,
            0.3f, 0.4f, 0.5f, 0.5f, 0.4f, 0.2f, 0.0f, -0.3f, -0.7f, -1.2f, -1.8f
        )
    )

    val FLAT = TargetCurvePreset(
        id = "flat",
        name = "Линейная (Flat 0 дБ)",
        shortName = "Flat",
        description = "Абсолютно ровная АЧХ 0 дБ для сведения стыков кроссовера и проверки фазы.",
        relativeDb31 = FloatArray(31) { 0f }
    )

    val ALL_PRESETS: List<TargetCurvePreset> = listOf(HARMAN, AUDIOFROG, EMMA_SQ, SPL_BASS, FLAT)

    fun byId(id: String): TargetCurvePreset = ALL_PRESETS.firstOrNull { it.id == id } ?: HARMAN

    /**
     * Computes aligned target level (SPL) based on measured midrange average (250 Hz – 2.5 kHz)
     * plus optional manual user offset, and returns 31-band DSP EQ correction advice.
     */
    fun analyzeAgainstTarget(
        measuredBandsDb: FloatArray,
        preset: TargetCurvePreset,
        autoAlign: Boolean,
        manualTargetRefDb: Float,
        toleranceDb: Float = 1.5f
    ): EqAnalysisSummary {
        // Midrange indices: 250 Hz (11) to 2.5 kHz (21)
        val referenceDb = if (autoAlign) {
            var sumDiff = 0f
            var count = 0
            for (i in 11..21) {
                if (measuredBandsDb[i] > 10f) {
                    sumDiff += (measuredBandsDb[i] - preset.relativeDb31[i])
                    count++
                }
            }
            if (count > 0) sumDiff / count else manualTargetRefDb
        } else {
            manualTargetRefDb
        }

        val advice = mutableListOf<EqBandAdvice>()
        var sumSqErr = 0.0
        var activeCount = 0

        for (i in 0 until 31) {
            val band = RtaBands.ISO_31_BANDS[i]
            val targetDb = referenceDb + preset.relativeDb31[i]
            val measured = measuredBandsDb[i]
            // Positive delta = measured is below target -> Boost in DSP
            // Negative delta = measured is above target -> Cut in DSP
            val rawDelta = (targetDb - measured).coerceIn(-15f, 15f)
            val roundedDelta = (rawDelta * 2f).roundToInt() / 2f // 0.5 dB DSP step
            val inTol = abs(rawDelta) <= toleranceDb

            // Recommend sharper Q for narrow peaks (cuts), gentler Q for broad dips (boosts)
            val q = when {
                abs(rawDelta) >= 6f && rawDelta < 0f -> 5.0f
                abs(rawDelta) >= 4f && rawDelta < 0f -> 4.3f
                rawDelta > 3f -> 2.8f
                else -> 4.3f // Standard 1/3-octave graphic EQ Q
            }

            if (measured > 15f) {
                sumSqErr += (rawDelta * rawDelta).toDouble()
                activeCount++
            }

            advice.add(
                EqBandAdvice(
                    bandIndex = i,
                    freqHz = band.nominalHz,
                    freqLabel = band.label,
                    measuredDb = measured,
                    targetDb = targetDb,
                    deltaDb = roundedDelta,
                    recommendedQ = q,
                    inTolerance = inTol
                )
            )
        }

        val rmsDev = if (activeCount > 0) sqrt(sumSqErr / activeCount).toFloat() else 0f
        // Map RMS deviation (0 dB -> 100%, 8+ dB -> 0%)
        val score = if (activeCount > 0) {
            (100f - (rmsDev / 8.0f) * 100f).roundToInt().coerceIn(0, 100)
        } else {
            0
        }

        return EqAnalysisSummary(
            alignedReferenceDb = referenceDb,
            matchScorePercent = score,
            rmsDeviationDb = rmsDev,
            adviceList = advice
        )
    }
}
