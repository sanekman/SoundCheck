package com.example.audio

import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

enum class FrequencyWeighting(val code: String, val label: String) {
    Z("Z", "dBZ (Flat)"),
    C("C", "dBC"),
    A("A", "dBA")
}

enum class AveragingMode(val code: String, val label: String, val attackAlpha: Float, val releaseAlpha: Float) {
    FAST("Fast", "Быстро", 0.55f, 0.30f),
    SLOW("Slow", "Медленно", 0.20f, 0.10f),
    RTA_AVG("Avg", "Усреднение", 0.08f, 0.08f)
}

enum class RtaDisplayMode(val label: String) {
    BARS("Полосы"),
    LINE("Кривая"),
    BOTH("Оба")
}

enum class FrequencyRangeFilter(val label: String, val minHz: Float, val maxHz: Float) {
    ALL("Все", 20f, 20000f),
    SUB("Саб", 20f, 100f),
    MIDBASS("Мидбас", 80f, 400f),
    MIDRANGE("СЧ", 400f, 3150f),
    TWEETER("ВЧ", 3150f, 20000f)
}

data class IsoBand(
    val index: Int,
    val nominalHz: Float,
    val label: String,
    val lowerHz: Float,
    val upperHz: Float
)

object RtaBands {
    val ISO_31_FREQS: FloatArray = floatArrayOf(
        20f, 25f, 31.5f, 40f, 50f, 63f, 80f, 100f, 125f, 160f,
        200f, 250f, 315f, 400f, 500f, 630f, 800f, 1000f, 1250f, 1600f,
        2000f, 2500f, 3150f, 4000f, 5000f, 6300f, 8000f, 10000f, 12500f, 16000f, 20000f
    )

    private val BAND_EDGE_FACTOR = 2.0.pow(1.0 / 6.0).toFloat()

    val ISO_31_BANDS: List<IsoBand> = ISO_31_FREQS.mapIndexed { idx, freq ->
        IsoBand(
            index = idx,
            nominalHz = freq,
            label = formatFrequencyShort(freq),
            lowerHz = freq / BAND_EDGE_FACTOR,
            upperHz = freq * BAND_EDGE_FACTOR
        )
    }

    const val TRACE_POINTS = 128
    val LOG_TRACE_FREQS: FloatArray = FloatArray(TRACE_POINTS) { i ->
        val t = i.toDouble() / (TRACE_POINTS - 1)
        (20.0 * (20000.0 / 20.0).pow(t)).toFloat()
    }

    fun formatFrequencyShort(hz: Float): String {
        return when {
            hz >= 10000f -> {
                val k = hz / 1000f
                if (k == k.toInt().toFloat()) "${k.toInt()}k" else String.format(java.util.Locale.US, "%.1fk", k)
            }
            hz >= 1000f -> {
                val k = hz / 1000f
                when {
                    k == k.toInt().toFloat() -> "${k.toInt()}k"
                    (k * 10f) % 1f == 0f -> String.format(java.util.Locale.US, "%.1fk", k)
                    else -> String.format(java.util.Locale.US, "%.2fk", k)
                }
            }
            hz == hz.toInt().toFloat() -> "${hz.toInt()}"
            else -> String.format(java.util.Locale.US, "%.1f", hz)
        }
    }

    fun formatFrequencyHz(hz: Float): String {
        return if (hz >= 1000f) {
            String.format(java.util.Locale.US, "%.2f кГц", hz / 1000f)
        } else {
            String.format(java.util.Locale.US, "%.0f Гц", hz)
        }
    }

    /**
     * IEC 61672-1 exact weighting offset in dB for frequency f (Hz).
     */
    fun weightingDb(freqHz: Float, weighting: FrequencyWeighting): Float {
        if (weighting == FrequencyWeighting.Z || freqHz <= 1f) return 0f
        val f2 = freqHz.toDouble() * freqHz.toDouble()
        val c1 = 12194.0 * 12194.0
        val c2 = 20.6 * 20.6
        return when (weighting) {
            FrequencyWeighting.Z -> 0f
            FrequencyWeighting.C -> {
                val rc = (c1 * f2) / ((f2 + c2) * (f2 + c1))
                (20.0 * log10(rc) + 0.06).toFloat()
            }
            FrequencyWeighting.A -> {
                val c3 = 107.7 * 107.7
                val c4 = 737.9 * 737.9
                val ra = (c1 * f2 * f2) / ((f2 + c2) * sqrt((f2 + c3) * (f2 + c4)) * (f2 + c1))
                (20.0 * log10(ra) + 2.0).toFloat()
            }
        }
    }
}
