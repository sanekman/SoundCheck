package com.example

import com.example.audio.AutoCalibrationAccumulator
import com.example.audio.CalibrationParser
import com.example.audio.FftProcessor
import com.example.audio.FrequencyWeighting
import com.example.audio.RtaBands
import com.example.audio.TargetCurves
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class DspEngineUnitTest {

    @Test
    fun fftDetects1kHzSinePeakAccurately() {
        val sampleRate = 48000
        val fftSize = 8192
        val processor = FftProcessor(fftSize = fftSize, sampleRate = sampleRate)
        val pcm = ShortArray(fftSize) { i ->
            val v = 0.5 * sin(2.0 * PI * 1000.0 * i / sampleRate)
            (v * 32767.0).toInt().toShort()
        }

        val result = processor.process(
            pcmSamples = pcm,
            weighting = FrequencyWeighting.Z,
            calibrationPoints = emptyList(),
            calibrationSensitivityOffsetDb = 0f,
            manualSplOffsetDb = 0f
        )

        assertEquals(1000f, result.peakFreqHz, 0.1f)
        // Band 17 is 1000 Hz, should be far above neighbor bands
        assertTrue(result.bands31Db[17] > result.bands31Db[10] + 25f)
        assertTrue(result.overallSplDb > 80f)
    }

    @Test
    fun calibrationParserParsesUmikFormatAndInterpolates() {
        val sampleCal = """
            "Sens Factor =-18.234dB, SERNO: 7001234"
            * Frequency(Hz) SPL(dB) Phase
            20.0   -2.0
            1000.0  0.0
            20000.0 +3.0
        """.trimIndent()

        val parsed = CalibrationParser.parse(sampleCal)
        assertEquals(-18.234f, parsed.sensitivityOffsetDb, 0.001f)
        assertEquals(3, parsed.points.size)

        // Correction is inverse of mic error (-offsetDb)
        val corrAt20 = CalibrationParser.interpolateCorrectionDb(parsed.points, 20f)
        val corrAt1k = CalibrationParser.interpolateCorrectionDb(parsed.points, 1000f)
        val corrAt20k = CalibrationParser.interpolateCorrectionDb(parsed.points, 20000f)
        assertEquals(2.0f, corrAt20, 0.01f)
        assertEquals(0.0f, corrAt1k, 0.01f)
        assertEquals(-3.0f, corrAt20k, 0.01f)
    }

    @Test
    fun autoCalibrationComputesSensitivityAnd31PointCorrection() {
        val acc = AutoCalibrationAccumulator()
        acc.addAmbientFrame(38f)
        acc.addAmbientFrame(40f)
        val floor = acc.finishAmbient()
        assertEquals(39f, floor, 0.1f)

        val bands1k = FloatArray(31) { 60f }.also { it[17] = 72f }
        acc.addSensitivityFrame(bands1k, 72f)
        val measured1k = acc.finishSensitivity()
        assertEquals(72f, measured1k, 0.1f)

        val sweepBands = FloatArray(31) { i ->
            when {
                i < 5 -> 68f // -4 dB bass rolloff in mic
                i > 25 -> 75f // +3 dB HF bump in mic
                else -> 72f
            }
        }
        acc.addFrFrame(sweepBands)
        val result = acc.buildCalibrationResult(
            micName = "UMIK-1 USB",
            noiseFloorDb = floor,
            measured1kHzDb = measured1k,
            targetReferenceSplDb = 94f
        )

        // Target 94 dB - measured 72 dB = +22.0 dB sensitivity offset
        assertEquals(22.0f, result.sensitivityOffsetDb, 0.2f)
        assertEquals(31, result.correctionPoints.size)
        // Mic had -4 dB rolloff at 20 Hz, so raw point offset is negative -> inverse correction boosts +4 dB
        assertTrue(CalibrationParser.interpolateCorrectionDb(result.correctionPoints, 20f) > 2.5f)
        assertEquals(0f, result.correctionPoints[17].offsetDb, 0.1f)
    }

    @Test
    fun targetCurveAnalysisComputesMatchScore() {
        val perfectHarman = FloatArray(31) { idx -> 75f + TargetCurves.HARMAN.relativeDb31[idx] }
        val analysis = TargetCurves.analyzeAgainstTarget(
            measuredBandsDb = perfectHarman,
            preset = TargetCurves.HARMAN,
            autoAlign = true,
            manualTargetRefDb = 75f
        )
        assertEquals(100, analysis.matchScorePercent)
        assertEquals(0f, analysis.rmsDeviationDb, 0.1f)
        assertEquals(31, analysis.adviceList.size)
    }

    @Test
    fun targetCurveCsvParserParsesStandardAndAbsoluteSplCsv() {
        val standardCsv = """
            # Custom Harman-like target
            Frequency,dB
            20, 6.0
            100, 3.0
            1000, 0.0
            10000, -3.0
            20000, -5.0
        """.trimIndent()

        val parsed = com.example.audio.TargetCurveCsvParser.parseCsv(standardCsv)
        org.junit.Assert.assertNotNull(parsed)
        requireNotNull(parsed)
        assertEquals(5, parsed.sourcePointCount)
        assertEquals(false, parsed.normalizedTo1kHz)
        assertEquals(6.0f, parsed.relativeDb31[0], 0.1f)  // 20 Hz
        assertEquals(0.0f, parsed.relativeDb31[17], 0.1f) // 1 kHz
        assertEquals(-5.0f, parsed.relativeDb31[30], 0.1f) // 20 kHz

        // Semicolon CSV with absolute SPL (e.g. 75..81 dB) should normalize around 1 kHz = 0 dB
        val absSplCsv = """
            Hz;SPL
            20;81,0
            1000;75,0
            20000;71,0
        """.trimIndent()
        val parsedAbs = com.example.audio.TargetCurveCsvParser.parseCsv(absSplCsv)
        org.junit.Assert.assertNotNull(parsedAbs)
        requireNotNull(parsedAbs)
        assertEquals(true, parsedAbs.normalizedTo1kHz)
        assertEquals(6.0f, parsedAbs.relativeDb31[0], 0.1f)
        assertEquals(0.0f, parsedAbs.relativeDb31[17], 0.1f)
        assertEquals(-4.0f, parsedAbs.relativeDb31[30], 0.1f)
    }

    @Test
    fun snapshotExporterBuildsValidCsvAndRoundTrips() {
        val sampleBands = FloatArray(31) { idx -> 75f + TargetCurves.HARMAN.relativeDb31[idx] }
        val payload = com.example.audio.SnapshotExportPayload(
            name = "Фронт L + Саб",
            channelTag = "Фронт Л",
            timestampMs = 1700000000000L,
            overallSplDb = 78.4f,
            weightingCode = "Z",
            micName = "UMIK-1 USB",
            calibrationName = "UMIK-1_cal.txt",
            bands31Db = sampleBands,
            targetCurve = TargetCurves.HARMAN,
            targetReferenceDb = 75f,
            showTargetCurve = true,
            targetToleranceDb = 1.5f
        )

        val csv = com.example.audio.SnapshotExporter.buildCsvContent(payload)
        assertTrue(csv.contains("# AutoRTA Measurement Snapshot"))
        assertTrue(csv.contains("Frequency_Hz,Measured_dB,Target_dB,Delta_dB"))
        assertTrue(csv.contains("1000.0,75.00,75.00,0.00"))

        // Exported CSV must also be re-importable by TargetCurveCsvParser!
        val reimported = com.example.audio.TargetCurveCsvParser.parseCsv(csv)
        org.junit.Assert.assertNotNull(reimported)
        requireNotNull(reimported)
        assertEquals(31, reimported.sourcePointCount)
        assertEquals(0.0f, reimported.relativeDb31[17], 0.1f)

        val safeName = com.example.audio.SnapshotExporter.sanitizeFileName("Front L / Sub!", "png", 1700000000000L)
        assertTrue(safeName.startsWith("Front_L_Sub_"))
        assertTrue(safeName.endsWith(".png"))
    }

    @Test
    fun placeholderAiTuningRepositoryMapsDeviationsAndPrioritizesCuts() {
        val repo = com.example.ai.PlaceholderAiTuningRepository()
        // Start with Harman target at 75 dB, add a +6 dB cabin boom at 63 Hz (idx 5) and a -4 dB dip at 200 Hz (idx 10)
        val measured = FloatArray(31) { idx -> 75f + TargetCurves.HARMAN.relativeDb31[idx] }
        measured[5] += 6.0f
        measured[10] -= 4.0f

        val analysis = TargetCurves.analyzeAgainstTarget(
            measuredBandsDb = measured,
            preset = TargetCurves.HARMAN,
            autoAlign = true,
            manualTargetRefDb = 75f,
            toleranceDb = 1.5f
        )

        val request = com.example.ai.AiTuningRequest(
            sourceLabel = "Тест салона",
            bands31Db = measured,
            targetCurve = TargetCurves.HARMAN,
            eqAnalysis = analysis,
            overallSplDb = 78f,
            weightingLabel = "Z",
            headUnitProfile = com.example.ai.HeadUnitEqProfile.EQ_10_BAND,
            toleranceDb = 1.5f,
            hasReceivedSignal = true
        )

        val plan = kotlinx.coroutines.runBlocking { repo.generateTuningPlan(request) }
        assertTrue(plan.isPlaceholder)
        assertTrue(plan.noticeBanner.contains("Firebase"))
        assertTrue(plan.steps.isNotEmpty())
        // First step should be a cut (negative gain) for the 63 Hz peak before any positive boost
        assertTrue(plan.steps.first().recommendedGainDb < 0f)
        assertEquals(63f, plan.steps.first().bandFreqHz, 0.1f)
    }

    @Test
    fun tuningWizardEngineBuildsQueuesAndAnalyzesCrossoversAndLrBalance() {
        // 3-way Active + Subwoofer (Tweeters -> Midrange -> Midbass -> Subwoofer -> Full System = 8 steps)
        val threeWayActive = com.example.audio.AudioSystemConfig(
            wayCount = com.example.audio.SystemWayCount.THREE_WAY,
            crossoverTopology = com.example.audio.CrossoverTopology.ACTIVE_DSP,
            hasSubwoofer = true,
            crossovers = com.example.audio.TuningWizardEngine.defaultCrossoversFor(
                com.example.audio.SystemWayCount.THREE_WAY,
                com.example.audio.CrossoverTopology.ACTIVE_DSP,
                true
            )
        )
        val q3 = com.example.audio.TuningWizardEngine.buildSpeakerQueue(threeWayActive)
        assertEquals(8, q3.size)
        assertEquals("tw_l", q3[0].id)
        assertEquals("tw_r", q3[1].id)
        assertEquals("mid_l", q3[2].id)
        assertEquals("mid_r", q3[3].id)
        assertEquals("midbass_l", q3[4].id)
        assertEquals("midbass_r", q3[5].id)
        assertEquals("sub", q3[6].id)
        assertEquals("full_system", q3.last().id)

        // 2-way Passive without Subwoofer -> Front L + Front R + Full System = 3 steps
        val twoWayPassiveNoSub = com.example.audio.AudioSystemConfig(
            wayCount = com.example.audio.SystemWayCount.TWO_WAY,
            crossoverTopology = com.example.audio.CrossoverTopology.PASSIVE_CROSSOVER,
            hasSubwoofer = false,
            crossovers = com.example.audio.TuningWizardEngine.defaultCrossoversFor(
                com.example.audio.SystemWayCount.TWO_WAY,
                com.example.audio.CrossoverTopology.PASSIVE_CROSSOVER,
                false
            )
        )
        val q2p = com.example.audio.TuningWizardEngine.buildSpeakerQueue(twoWayPassiveNoSub)
        assertEquals(3, q2p.size)
        assertEquals("front_l", q2p[0].id)
        assertEquals("front_r", q2p[1].id)

        // Simulate Left Tweeter being +4 dB louder than Right Tweeter, and verify passband-only EQ fixes
        val twLeftSlot = q3.first { it.id == "tw_l" }
        val twRightSlot = q3.first { it.id == "tw_r" }
        val twLeftBands = FloatArray(31) { idx ->
            val base = 75f + TargetCurves.HARMAN.relativeDb31[idx] + 4.0f
            if (idx == 23) base + 5.0f else base // +5 dB peak at 4 kHz (inside tweeter passband!)
        }
        val twRightBands = FloatArray(31) { idx ->
            75f + TargetCurves.HARMAN.relativeDb31[idx]
        }

        val mLeft = com.example.audio.TuningWizardEngine.computeSpeakerMeasurement(
            slot = twLeftSlot,
            bands31Db = twLeftBands,
            overallSplDb = 79f,
            config = threeWayActive,
            targetCurve = TargetCurves.HARMAN,
            referenceDb = 75f,
            toleranceDb = 1.5f
        )
        val mRight = com.example.audio.TuningWizardEngine.computeSpeakerMeasurement(
            slot = twRightSlot,
            bands31Db = twRightBands,
            overallSplDb = 75f,
            config = threeWayActive,
            targetCurve = TargetCurves.HARMAN,
            referenceDb = 75f,
            toleranceDb = 1.5f
        )

        val report = com.example.audio.TuningWizardEngine.analyzeWizardSystem(
            config = threeWayActive,
            queue = q3,
            measurements = mapOf("tw_l" to mLeft, "tw_r" to mRight),
            targetCurve = TargetCurves.HARMAN,
            referenceDb = 75f,
            toleranceDb = 1.5f
        )

        // Should detect L/R imbalance on Tweeter
        assertTrue(report.levelMatchTips.any { it.title.contains("Разбаланс Л/П") && it.isWarning })
        // Tweeter EQ fixes must strictly be >= 3150 Hz (tweeter DSP HPF)
        val twLeftRec = report.perSpeakerRecommendations.first { it.slotId == "tw_l" }
        assertTrue(twLeftRec.topBandFixes.isNotEmpty())
        assertTrue(twLeftRec.topBandFixes.all { it.freqHz >= 3150f })
    }

    @Test
    fun waterfallEngineDetectsSlowDecayingCabinResonance() {
        val sampleRate = 48000
        val totalSamples = sampleRate // 1.0 sec buffer
        val pcm = ShortArray(totalSamples) { i ->
            val t = i.toDouble() / sampleRate
            // 63 Hz slow-decaying cabin resonance (tau = 0.35s) + 1000 Hz fast-decaying tone (tau = 0.04s)
            val slow63 = 14000.0 * kotlin.math.exp(-t / 0.35) * kotlin.math.sin(2.0 * kotlin.math.PI * 63.0 * t)
            val fast1k = 14000.0 * kotlin.math.exp(-t / 0.04) * kotlin.math.sin(2.0 * kotlin.math.PI * 1000.0 * t)
            (slow63 + fast1k).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        val result = com.example.audio.WaterfallEngine.computeCsdFromPcm(
            pcmBuffer = pcm,
            cutoffSampleIndex = 0,
            windowMs = 300,
            sliceCount = 16,
            sampleRate = sampleRate
        )

        assertEquals(16, result.slices.size)
        // 63 Hz (index 5) should decay much slower than 1000 Hz (index 17)
        val decay63 = result.bandDecayMs31[5]
        val decay1k = result.bandDecayMs31[17]
        assertTrue("Expected 63 Hz decay ($decay63 ms) > 1 kHz decay ($decay1k ms)", decay63 > decay1k + 80f)
        assertTrue("Expected 63 Hz hotspot to be flagged", result.resonances.any { it.bandIndex == 5 })
    }
}
