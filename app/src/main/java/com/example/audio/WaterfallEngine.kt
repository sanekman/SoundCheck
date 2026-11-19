package com.example.audio

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

enum class WaterfallMode(val label: String) {
    CSD_DECAY("CSD Затухание"),
    LIVE_ROLLING("Live Водопад")
}

enum class WaterfallSignalSource(val label: String) {
    HEAD_UNIT_MANUAL("ГУ (вручную)"),
    PHONE_BURST("Телефон (авто)")
}

enum class WaterfallCapturePhase {
    IDLE,
    WAITING_SIGNAL,
    SIGNAL_READY_WAIT_MUTE,
    PLAYING_PHONE_BURST,
    CAPTURING_DECAY,
    COMPLETED
}

enum class WaterfallFreqRange(val label: String, val minHz: Float, val maxHz: Float) {
    BASS_FOCUS("20–300 Гц (Бас)", 20f, 300f),
    MID_BASS("20–1 кГц", 20f, 1000f),
    FULL_RANGE("20–20 кГц (Весь)", 20f, 20000f)
}

data class WaterfallSlice(
    val timeMs: Float,
    val bands31Db: FloatArray,
    val trace128Db: FloatArray,
    val overallSplDb: Float
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WaterfallSlice) return false
        return timeMs == other.timeMs &&
            bands31Db.contentEquals(other.bands31Db) &&
            trace128Db.contentEquals(other.trace128Db)
    }

    override fun hashCode(): Int {
        var result = timeMs.hashCode()
        result = 31 * result + bands31Db.contentHashCode()
        return result
    }
}

data class ResonanceHotspot(
    val bandIndex: Int,
    val freqHz: Float,
    val estimatedDecayMs: Float,
    val residualDropDb: Float,
    val severity: String,
    val advice: String
)

data class WaterfallMeasurementResult(
    val slices: List<WaterfallSlice>,
    val windowMs: Int,
    val peakSplDb: Float,
    val bandDecayMs31: FloatArray,
    val resonances: List<ResonanceHotspot>,
    val measuredAtMs: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WaterfallMeasurementResult) return false
        return measuredAtMs == other.measuredAtMs &&
            windowMs == other.windowMs &&
            slices == other.slices
    }

    override fun hashCode(): Int {
        var result = slices.hashCode()
        result = 31 * result + windowMs
        result = 31 * result + measuredAtMs.hashCode()
        return result
    }
}

data class WaterfallUiState(
    val mode: WaterfallMode = WaterfallMode.CSD_DECAY,
    val signalSource: WaterfallSignalSource = WaterfallSignalSource.HEAD_UNIT_MANUAL,
    val windowMs: Int = 300,
    val freqRange: WaterfallFreqRange = WaterfallFreqRange.BASS_FOCUS,
    val phase: WaterfallCapturePhase = WaterfallCapturePhase.IDLE,
    val statusMessage: String = "Выберите источник шума и запустите замер затухания (Waterfall CSD) для поиска резонансов салона.",
    val progress: Float = 0f,
    val detectedSteadySplDb: Float? = null,
    val liveRollingSlices: List<WaterfallSlice> = emptyList(),
    val csdResult: WaterfallMeasurementResult? = null
)

/**
 * Pure-Kotlin Cumulative Spectral Decay (Waterfall / CSD) engine.
 * Computes sliding-window STFT decay slices from real captured PCM audio frames
 * and detects lingering car-cabin resonances (standing waves, panel ringing, port overhang).
 */
object WaterfallEngine {

    /**
     * Computes a multi-slice Cumulative Spectral Decay (CSD) from a raw PCM buffer
     * captured starting right before signal cutoff through [windowMs] of post-cutoff decay.
     *
     * @param pcmBuffer Raw 16-bit mono PCM samples at [sampleRate].
     * @param cutoffSampleIndex Index in [pcmBuffer] corresponding to t = 0 ms (signal cutoff).
     * @param windowMs Total decay window to analyze (typically 200, 300, or 500 ms).
     * @param sliceCount Number of waterfall depth slices (default 20).
     */
    fun computeCsdFromPcm(
        pcmBuffer: ShortArray,
        cutoffSampleIndex: Int,
        windowMs: Int = 300,
        sliceCount: Int = 20,
        sampleRate: Int = 48000,
        weighting: FrequencyWeighting = FrequencyWeighting.Z,
        calibrationPoints: List<CalibrationPoint> = emptyList(),
        calibrationSensitivityOffsetDb: Float = 0f,
        manualSplOffsetDb: Float = 0f
    ): WaterfallMeasurementResult {
        val fftSize = 4096 // 85.3 ms window @ 48 kHz (11.7 Hz bin resolution, fast decay tracking)
        val processor = FftProcessor(fftSize = fftSize, sampleRate = sampleRate)
        val safeSlices = sliceCount.coerceIn(8, 28)
        val safeWindowMs = windowMs.coerceIn(150, 800)

        val windowSamples = (safeWindowMs / 1000.0 * sampleRate).toInt()
        val startBase = cutoffSampleIndex.coerceIn(0, (pcmBuffer.size - fftSize).coerceAtLeast(0))
        val slices = ArrayList<WaterfallSlice>(safeSlices)
        val frameBuf = ShortArray(fftSize)

        var peakSpl = 0f
        for (s in 0 until safeSlices) {
            val frac = if (safeSlices > 1) s.toFloat() / (safeSlices - 1) else 0f
            val timeMs = frac * safeWindowMs
            val sampleOffset = (startBase + (frac * windowSamples).toInt())
                .coerceIn(0, (pcmBuffer.size - 1).coerceAtLeast(0))

            frameBuf.fill(0)
            val available = (pcmBuffer.size - sampleOffset).coerceAtMost(fftSize)
            if (available > 0) {
                System.arraycopy(pcmBuffer, sampleOffset, frameBuf, 0, available)
            }

            val res = processor.process(
                pcmSamples = frameBuf,
                weighting = weighting,
                calibrationPoints = calibrationPoints,
                calibrationSensitivityOffsetDb = calibrationSensitivityOffsetDb,
                manualSplOffsetDb = manualSplOffsetDb
            )
            if (res.overallSplDb > peakSpl) peakSpl = res.overallSplDb

            slices.add(
                WaterfallSlice(
                    timeMs = timeMs,
                    bands31Db = res.bands31Db.copyOf(),
                    trace128Db = res.trace128Db.copyOf(),
                    overallSplDb = res.overallSplDb
                )
            )
        }

        val (decayMs31, resonances) = analyzeResonances(slices, safeWindowMs)

        return WaterfallMeasurementResult(
            slices = slices,
            windowMs = safeWindowMs,
            peakSplDb = peakSpl,
            bandDecayMs31 = decayMs31,
            resonances = resonances,
            measuredAtMs = System.currentTimeMillis()
        )
    }

    /**
     * Estimates per-band decay time (time to drop by 20 dB, extrapolated to T30) and identifies
     * the worst slow-decaying cabin resonance ridges.
     */
    fun analyzeResonances(
        slices: List<WaterfallSlice>,
        windowMs: Int
    ): Pair<FloatArray, List<ResonanceHotspot>> {
        val decayMs31 = FloatArray(31) { 0f }
        if (slices.size < 3) return decayMs31 to emptyList()

        val initial = slices.first().bands31Db
        val last = slices.last().bands31Db
        val hotspots = mutableListOf<ResonanceHotspot>()

        // Compute median initial band energy to ignore silent / ultra-low bands outside speaker passband
        val sortedInitial = initial.sorted()
        val medianLevel = sortedInitial[sortedInitial.size / 2]

        for (b in 0 until 31) {
            val startDb = initial[b]
            val endDb = last[b]
            val totalDropDb = (startDb - endDb).coerceAtLeast(0.5f)

            // Find first slice where band drops by >= 15 dB, or extrapolate linearly across windowMs
            val targetDrop = 15f
            var timeTo15Db: Float? = null
            for (s in 1 until slices.size) {
                val drop = startDb - slices[s].bands31Db[b]
                if (drop >= targetDrop) {
                    val prevDrop = startDb - slices[s - 1].bands31Db[b]
                    val seg = (drop - prevDrop).coerceAtLeast(0.1f)
                    val interp = ((targetDrop - prevDrop) / seg).coerceIn(0f, 1f)
                    timeTo15Db = slices[s - 1].timeMs + interp * (slices[s].timeMs - slices[s - 1].timeMs)
                    break
                }
            }

            // Extrapolate T25 (~effective cabin ringing time)
            val estDecayMs = if (timeTo15Db != null) {
                (timeTo15Db * (25f / 15f)).coerceIn(30f, windowMs * 2.5f)
            } else {
                // Dropped less than 15 dB across entire window -> very long resonance or noise floor
                val rateDbPerMs = totalDropDb / max(1f, windowMs.toFloat())
                (25f / rateDbPerMs).coerceIn(windowMs.toFloat(), windowMs * 3f)
            }
            decayMs31[b] = estDecayMs

            val freqHz = RtaBands.ISO_31_FREQS[b]
            // Flag significant ridges that actually had strong initial excitation (>= median - 8 dB)
            val hasRealEnergy = startDb >= (medianLevel - 8f) && startDb >= 48f
            val slowThresholdMs = if (freqHz <= 160f) 210f else 155f
            if (hasRealEnergy && estDecayMs >= slowThresholdMs && totalDropDb < 24f) {
                val severity = if (estDecayMs >= slowThresholdMs * 1.45f || totalDropDb < 10f) {
                    "Сильный резонанс"
                } else {
                    "Умеренное затягивание"
                }
                val advice = when {
                    freqHz <= 80f -> "Гул сабвуфера / стоячая волна салона. Сделайте узкий вырез EQ (Q 4–6) на -2…-4 дБ или проверьте фазу/порт саба."
                    freqHz <= 250f -> "Резонанс дверной карты или обшивки. Проверьте виброизоляцию дверей и приберите пик узким фильтром EQ."
                    freqHz <= 1250f -> "Отражения от стёкол и торпедо. Проверьте направление динамиков и снизьте пик на -1.5…-3 дБ."
                    else -> "Ранние ВЧ-отражения от лобового стекла. Скорректируйте угол доворота твитеров."
                }
                hotspots.add(
                    ResonanceHotspot(
                        bandIndex = b,
                        freqHz = freqHz,
                        estimatedDecayMs = estDecayMs.roundToInt().toFloat(),
                        residualDropDb = totalDropDb,
                        severity = severity,
                        advice = advice
                    )
                )
            }
        }

        // Deduplicate adjacent 1/3-octave bands by prioritizing true peak prominence (initial SPL + slow decay)
        val filtered = mutableListOf<ResonanceHotspot>()
        val ranked = hotspots.sortedWith(
            compareByDescending<ResonanceHotspot> { spot ->
                val initialDb = initial[spot.bandIndex]
                val leftDb = if (spot.bandIndex > 0) initial[spot.bandIndex - 1] else initialDb - 10f
                val rightDb = if (spot.bandIndex < 30) initial[spot.bandIndex + 1] else initialDb - 10f
                val isLocalPeakBonus = if (initialDb >= leftDb && initialDb >= rightDb) 250f else 0f
                spot.estimatedDecayMs + (initialDb - medianLevel) * 6f + isLocalPeakBonus
            }
        )
        for (spot in ranked) {
            if (filtered.none { abs(it.bandIndex - spot.bandIndex) <= 1 }) {
                filtered.add(spot)
            }
            if (filtered.size >= 4) break
        }

        return decayMs31 to filtered
    }
}
