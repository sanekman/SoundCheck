package com.example.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

data class RtaFrameResult(
    val bands31Db: FloatArray,
    val trace128Db: FloatArray,
    val overallSplDb: Float,
    val peakFreqHz: Float,
    val peakBandDb: Float,
    val inputPeakNormalized: Float,
    val isClipping: Boolean
)

/**
 * Pure Kotlin Radix-2 Cooley-Tukey 8192-point FFT spectrum analyzer
 * calibrated for 48 kHz audio (5.86 Hz bin resolution, accurate down to 20 Hz).
 */
class FftProcessor(
    val fftSize: Int = 8192,
    val sampleRate: Int = 48000
) {
    private val hannWindow = DoubleArray(fftSize) { i ->
        0.5 * (1.0 - cos(2.0 * PI * i / (fftSize - 1)))
    }
    private val windowEnergyFactor: Double = run {
        var sumSq = 0.0
        for (w in hannWindow) sumSq += w * w
        sumSq
    }

    private val real = DoubleArray(fftSize)
    private val imag = DoubleArray(fftSize)

    // Precomputed bit-reversal indices
    private val bitReverse = IntArray(fftSize).also { rev ->
        val bits = Integer.numberOfTrailingZeros(fftSize)
        for (i in 0 until fftSize) {
            rev[i] = Integer.reverse(i) ushr (32 - bits)
        }
    }

    // Precomputed twiddle tables for each FFT stage
    private val cosTable = DoubleArray(fftSize / 2) { k -> cos(-2.0 * PI * k / fftSize) }
    private val sinTable = DoubleArray(fftSize / 2) { k -> sin(-2.0 * PI * k / fftSize) }

    // Reference nominal SPL offset: maps 0 dBFS to ~105 dB SPL (typical calibrated UMIK-1 / smartphone reference)
    private val baseDbfsToSplOffset = 105.0

    fun process(
        pcmSamples: ShortArray,
        weighting: FrequencyWeighting,
        calibrationPoints: List<CalibrationPoint>,
        calibrationSensitivityOffsetDb: Float,
        manualSplOffsetDb: Float
    ): RtaFrameResult {
        var peakNorm = 0f
        val count = minOf(pcmSamples.size, fftSize)

        for (i in 0 until fftSize) {
            val sample = if (i < count) pcmSamples[i] / 32768.0 else 0.0
            val absSample = abs(sample).toFloat()
            if (absSample > peakNorm) peakNorm = absSample
            real[i] = sample * hannWindow[i]
            imag[i] = 0.0
        }

        computeFftInPlace()

        val halfSize = fftSize / 2
        val binHz = sampleRate.toDouble() / fftSize.toDouble()
        val totalSplOffset = baseDbfsToSplOffset + calibrationSensitivityOffsetDb + manualSplOffsetDb

        // Compute power spectrum per bin (one-sided) with weighting + mic calibration
        val binPower = DoubleArray(halfSize)
        var totalWeightedPower = 0.0

        for (k in 1 until halfSize) {
            val freq = (k * binHz).toFloat()
            val re = real[k]
            val im = imag[k]
            // One-sided power normalized by window energy
            val rawPower = 2.0 * (re * re + im * im) / (fftSize.toDouble() * windowEnergyFactor)
            val weightDb = RtaBands.weightingDb(freq, weighting)
            val calDb = CalibrationParser.interpolateCorrectionDb(calibrationPoints, freq)
            val linearGain = Math.pow(10.0, (weightDb + calDb) / 10.0)
            val correctedPower = rawPower * linearGain
            binPower[k] = correctedPower
            if (freq in 20f..20000f) {
                totalWeightedPower += correctedPower
            }
        }

        // Aggregate into 31 ISO 1/3-octave bands
        val bands31 = FloatArray(31)
        var maxBandDb = 0f
        var peakFreq = 1000f

        for (b in 0 until 31) {
            val band = RtaBands.ISO_31_BANDS[b]
            val startBin = max(1, floor(band.lowerHz / binHz).toInt())
            val endBin = minOf(halfSize - 1, floor(band.upperHz / binHz).toInt())
            var sumPower = 0.0
            if (endBin >= startBin) {
                for (k in startBin..endBin) {
                    sumPower += binPower[k]
                }
            } else {
                // Narrow sub-bass band interpolation near 20 Hz
                val exactBin = (band.nominalHz / binHz).coerceIn(1.0, (halfSize - 2).toDouble())
                val k0 = exactBin.toInt()
                val frac = exactBin - k0
                sumPower = binPower[k0] * (1.0 - frac) + binPower[k0 + 1] * frac
            }
            val db = (10.0 * log10(sumPower + 1e-12) + totalSplOffset).toFloat().coerceIn(15f, 130f)
            bands31[b] = db
            if (db > maxBandDb) {
                maxBandDb = db
                peakFreq = band.nominalHz
            }
        }

        // Aggregate 128 log-spaced points for smooth RTA line trace (1/12 octave style smoothing)
        val trace128 = FloatArray(RtaBands.TRACE_POINTS)
        val halfStepFactor = Math.pow(2.0, 1.0 / 24.0) // ~1/12 oct window
        for (i in 0 until RtaBands.TRACE_POINTS) {
            val fc = RtaBands.LOG_TRACE_FREQS[i].toDouble()
            val fLow = fc / halfStepFactor
            val fHigh = fc * halfStepFactor
            val kStart = max(1, floor(fLow / binHz).toInt())
            val kEnd = minOf(halfSize - 1, floor(fHigh / binHz).toInt())
            var power = 0.0
            if (kEnd >= kStart) {
                for (k in kStart..kEnd) power += binPower[k]
                // Scale to equivalent 1/3-octave energy (+6 dB approx for 1/12 -> 1/3 visual alignment)
                power *= 4.0
            } else {
                val exact = (fc / binHz).coerceIn(1.0, (halfSize - 2).toDouble())
                val k0 = exact.toInt()
                val frac = exact - k0
                power = (binPower[k0] * (1.0 - frac) + binPower[k0 + 1] * frac) * 4.0
            }
            trace128[i] = (10.0 * log10(power + 1e-12) + totalSplOffset).toFloat().coerceIn(15f, 130f)
        }

        val overallSpl = (10.0 * log10(totalWeightedPower + 1e-12) + totalSplOffset)
            .toFloat()
            .coerceIn(20f, 135f)

        return RtaFrameResult(
            bands31Db = bands31,
            trace128Db = trace128,
            overallSplDb = overallSpl,
            peakFreqHz = peakFreq,
            peakBandDb = maxBandDb,
            inputPeakNormalized = peakNorm,
            isClipping = peakNorm >= 0.97f
        )
    }

    private fun computeFftInPlace() {
        // Bit-reversal permutation
        for (i in 0 until fftSize) {
            val j = bitReverse[i]
            if (j > i) {
                val tmpR = real[i]; real[i] = real[j]; real[j] = tmpR
                val tmpI = imag[i]; imag[i] = imag[j]; imag[j] = tmpI
            }
        }

        // Butterfly stages
        var size = 2
        while (size <= fftSize) {
            val halfSize = size ushr 1
            val tableStep = fftSize / size
            var i = 0
            while (i < fftSize) {
                var k = 0
                for (j in i until i + halfSize) {
                    val cosW = cosTable[k]
                    val sinW = sinTable[k]
                    val l = j + halfSize
                    val tReal = real[l] * cosW - imag[l] * sinW
                    val tImag = real[l] * sinW + imag[l] * cosW
                    real[l] = real[j] - tReal
                    imag[l] = imag[j] - tImag
                    real[j] += tReal
                    imag[j] += tImag
                    k += tableStep
                }
                i += size
            }
            size = size shl 1
        }
    }
}
