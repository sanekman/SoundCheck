package com.example.ai

import com.example.audio.AudioSystemConfig
import com.example.audio.ChannelCrossoverConfig
import com.example.audio.CrossoverTopology
import com.example.audio.EqAnalysisSummary
import com.example.audio.RtaBands
import com.example.audio.SpeakerGroupType
import com.example.audio.SpeakerMeasurementResult
import com.example.audio.TargetCurvePreset
import com.example.audio.WizardAnalysisReport
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * Standard car audio head unit / external DSP equalizer band layouts.
 * Allows the AI recommendation engine to map 31-band RTA deviations directly
 * to the exact bands available on the user's car stereo.
 */
enum class HeadUnitEqProfile(
    val id: String,
    val shortLabel: String,
    val fullLabel: String,
    val centerFrequenciesHz: List<Float>,
    val typicalQLabel: String
) {
    EQ_5_BAND(
        id = "hu_5",
        shortLabel = "5 полос",
        fullLabel = "5-полосный эквалайзер ГУ",
        centerFrequenciesHz = listOf(63f, 250f, 1000f, 4000f, 12500f),
        typicalQLabel = "Широкая полоса (Q ≈ 0.8–1.0)"
    ),
    EQ_7_BAND(
        id = "hu_7",
        shortLabel = "7 полос",
        fullLabel = "7-полосный эквалайзер ГУ",
        centerFrequenciesHz = listOf(63f, 160f, 400f, 1000f, 2500f, 6300f, 16000f),
        typicalQLabel = "Средняя полоса (Q ≈ 1.2)"
    ),
    EQ_10_BAND(
        id = "hu_10",
        shortLabel = "10 полос",
        fullLabel = "10-полосный октавный эквалайзер ГУ",
        centerFrequenciesHz = listOf(31.5f, 63f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f),
        typicalQLabel = "Октавный (Q ≈ 1.4)"
    ),
    EQ_13_BAND(
        id = "hu_13",
        shortLabel = "13 полос",
        fullLabel = "13-полосный ГУ (Pioneer / Kenwood / JVC)",
        centerFrequenciesHz = listOf(50f, 80f, 125f, 200f, 315f, 500f, 800f, 1250f, 2000f, 3150f, 5000f, 8000f, 12500f),
        typicalQLabel = "Графический 2/3 окт. (Q ≈ 2.0)"
    ),
    EQ_31_BAND(
        id = "dsp_31",
        shortLabel = "31 полоса",
        fullLabel = "31-полосный графический / DSP (1/3 окт.)",
        centerFrequenciesHz = RtaBands.ISO_31_BANDS.map { it.nominalHz },
        typicalQLabel = "1/3 октавы (Q ≈ 4.3)"
    )
}

data class AiTuningRequest(
    val sourceLabel: String,
    val bands31Db: FloatArray,
    val targetCurve: TargetCurvePreset,
    val eqAnalysis: EqAnalysisSummary,
    val overallSplDb: Float,
    val weightingLabel: String,
    val headUnitProfile: HeadUnitEqProfile,
    val toleranceDb: Float,
    val hasReceivedSignal: Boolean,
    val systemConfig: AudioSystemConfig? = null,
    val speakerMeasurements: Map<String, SpeakerMeasurementResult> = emptyMap(),
    val wizardReport: WizardAnalysisReport? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AiTuningRequest) return false
        return sourceLabel == other.sourceLabel &&
            bands31Db.contentEquals(other.bands31Db) &&
            targetCurve == other.targetCurve &&
            eqAnalysis == other.eqAnalysis &&
            overallSplDb == other.overallSplDb &&
            weightingLabel == other.weightingLabel &&
            headUnitProfile == other.headUnitProfile &&
            toleranceDb == other.toleranceDb &&
            hasReceivedSignal == other.hasReceivedSignal &&
            systemConfig == other.systemConfig &&
            speakerMeasurements == other.speakerMeasurements
    }

    override fun hashCode(): Int {
        var result = sourceLabel.hashCode()
        result = 31 * result + bands31Db.contentHashCode()
        result = 31 * result + targetCurve.hashCode()
        result = 31 * result + eqAnalysis.hashCode()
        result = 31 * result + overallSplDb.hashCode()
        result = 31 * result + weightingLabel.hashCode()
        result = 31 * result + headUnitProfile.hashCode()
        result = 31 * result + toleranceDb.hashCode()
        result = 31 * result + hasReceivedSignal.hashCode()
        result = 31 * result + (systemConfig?.hashCode() ?: 0)
        result = 31 * result + speakerMeasurements.hashCode()
        return result
    }
}

data class AiEqStep(
    val stepNumber: Int,
    val bandFreqHz: Float,
    val bandFreqLabel: String,
    val currentDeviationDb: Float,
    val recommendedGainDb: Float,
    val recommendedQ: String,
    val actionTitle: String,
    val reason: String,
    val verificationTip: String
)

data class AiTuningPlan(
    val sourceLabel: String,
    val targetCurveName: String,
    val headUnitProfileLabel: String,
    val matchScoreBefore: Int,
    val rmsDeviationBefore: Float,
    val isPlaceholder: Boolean,
    val noticeBanner: String,
    val overallAssessment: String,
    val priorityIssues: List<String>,
    val steps: List<AiEqStep>,
    val crossoverAndGainTips: List<String>,
    val expectedOutcome: String
)

sealed interface AiAdvisorUiState {
    data object Idle : AiAdvisorUiState
    data object Loading : AiAdvisorUiState
    data class Success(val plan: AiTuningPlan) : AiAdvisorUiState
    data class Error(val message: String) : AiAdvisorUiState
}

/**
 * Single abstraction for all AI car-audio tuning recommendations.
 * Swap [PlaceholderAiTuningRepository] with a Firebase AI Logic (Gemini)
 * implementation once Firebase is enabled for this project.
 */
interface AiTuningRepository {
    suspend fun generateTuningPlan(request: AiTuningRequest): AiTuningPlan
}

/**
 * Placeholder implementation used when Firebase AI Logic is not enabled.
 * Maps RTA deviations (and optional per-speaker wizard measurements + DSP crossovers)
 * onto the user's selected head unit/DSP EQ topology and returns clearly labeled sample instructions.
 */
class PlaceholderAiTuningRepository : AiTuningRepository {

    override suspend fun generateTuningPlan(request: AiTuningRequest): AiTuningPlan {
        // Brief pause for smooth loading transition in UI
        delay(300L)

        val headUnitBands = request.headUnitProfile.centerFrequenciesHz
        val useSampleCurve = !request.hasReceivedSignal && request.speakerMeasurements.isEmpty()

        val mappedBands = if (useSampleCurve) {
            buildSampleFallbackBands(headUnitBands)
        } else {
            map31BandsToHeadUnit(
                request = request,
                headUnitCentersHz = headUnitBands
            )
        }

        // Respect system minimum bass cutoff if subwoofer is absent
        val minEffectiveHz = if (request.systemConfig?.hasSubwoofer == false) {
            request.systemConfig.crossoverFor(SpeakerGroupType.MIDBASS)?.hpfHz
                ?: request.systemConfig.crossoverFor(SpeakerGroupType.FRONT_PASSIVE)?.hpfHz
                ?: 45f
        } else {
            20f
        }

        // Follow car audio tuning best practice: cut resonant peaks first, then apply gentle boosts.
        val significantAdjustments = mappedBands
            .filter { it.centerHz >= minEffectiveHz * 0.85f }
            .filter { abs(it.recommendedGainDb) >= request.toleranceDb.coerceAtLeast(1.0f) }
            .sortedWith(
                compareBy<MappedBandAdjustment> { it.recommendedGainDb > 0f } // cuts (< 0) first
                    .thenByDescending { abs(it.recommendedGainDb) }
            )
            .take(if (request.headUnitProfile == HeadUnitEqProfile.EQ_31_BAND) 12 else headUnitBands.size)

        val steps = significantAdjustments.mapIndexed { idx, adj ->
            val label = formatFreqLabel(adj.centerHz)
            val isCut = adj.recommendedGainDb < 0f
            val signStr = String.format(java.util.Locale.US, "%+.1f дБ", adj.recommendedGainDb)
            val actionVerb = if (isCut) "Убавьте" else "Поднимите"
            val devSign = String.format(java.util.Locale.US, "%+.1f дБ", -adj.recommendedGainDb)
            val channelHint = resolveChannelHintForFreq(adj.centerHz, request.systemConfig)

            AiEqStep(
                stepNumber = idx + 1,
                bandFreqHz = adj.centerHz,
                bandFreqLabel = label,
                currentDeviationDb = -adj.recommendedGainDb,
                recommendedGainDb = adj.recommendedGainDb,
                recommendedQ = buildQAndChannelLabel(request.headUnitProfile.typicalQLabel, channelHint),
                actionTitle = "$actionVerb $label на $signStr ($channelHint)",
                reason = buildAcousticReason(adj.centerHz, isCut, devSign, useSampleCurve),
                verificationTip = "После правки $label проверьте на RTA, что пик/провал приблизился к кривой «${request.targetCurve.shortName}» в пределах ±${String.format(java.util.Locale.US, "%.1f", request.toleranceDb)} дБ."
            )
        }

        val issues = buildPriorityIssues(
            adjustments = significantAdjustments,
            targetCurveName = request.targetCurve.name,
            useSampleCurve = useSampleCurve,
            wizardReport = request.wizardReport
        )
        val assessment = buildOverallAssessment(
            request = request,
            significantCount = steps.size,
            useSampleCurve = useSampleCurve
        )

        val crossoverTips = buildList {
            add("Отключите на магнитоле Bass Boost, Loudness, SLA и заводские пресеты EQ перед внесением поправок.")
            request.wizardReport?.levelMatchTips?.filter { it.isWarning || abs(it.recommendedGainDb) >= 1.5f }
                ?.take(3)
                ?.forEach { tip -> add(tip.detail) }
            request.wizardReport?.crossoverDiagnostics?.take(3)?.forEach { diag ->
                add("${diag.title}: ${diag.detail}")
            }
            val cfg = request.systemConfig
            if (cfg != null) {
                val subX = cfg.crossoverFor(SpeakerGroupType.SUBWOOFER)
                if (cfg.hasSubwoofer && subX != null) {
                    add("Конфигурация сабвуфера: ${subX.formatPassbandSummary()}. Сначала выровняйте Sub Level, затем переходите к эквалайзеру.")
                } else if (!cfg.hasSubwoofer) {
                    add("Система без сабвуфера: не поднимайте частоты ниже ${ChannelCrossoverConfig.formatHz(minEffectiveHz)} во избежание перегрузки мидбасов.")
                }
            } else {
                add("Проверьте стык сабвуфера и мидбаса в зоне 63–80 Гц: при глубоком провале попробуйте инвертировать фазу сабвуфера (0° / 180°).")
            }
            add("После внесения всех правок повторно замерьте общую АЧХ розовым шумом на вкладке RTA для контроля стыков.")
        }

        val expectedOutcome = if (steps.isEmpty()) {
            "[Пример ответа AI] Текущая АЧХ уже близка к целевой кривой «${request.targetCurve.name}» в пределах допуска ±${String.format(java.util.Locale.US, "%.1f", request.toleranceDb)} дБ."
        } else {
            "[Пример ответа AI] После выполнения ${steps.size} шагов на ${request.headUnitProfile.shortLabel} эквалайзере с учётом ваших срезов DSP кривая АЧХ станет заметно ближе к «${request.targetCurve.name}»."
        }

        return AiTuningPlan(
            sourceLabel = if (useSampleCurve) "Демо-профиль (нет замеров RTA)" else request.sourceLabel,
            targetCurveName = request.targetCurve.name,
            headUnitProfileLabel = request.headUnitProfile.fullLabel,
            matchScoreBefore = if (useSampleCurve) 62 else request.eqAnalysis.matchScorePercent,
            rmsDeviationBefore = if (useSampleCurve) 3.4f else request.eqAnalysis.rmsDeviationDb,
            isPlaceholder = true,
            noticeBanner = "AI-рекомендации Gemini требуют включения Firebase AI Logic. Сейчас показан демонстрационный план, сформированный локальным шаблоном по вашим замерам и срезам DSP.",
            overallAssessment = assessment,
            priorityIssues = issues,
            steps = steps,
            crossoverAndGainTips = crossoverTips,
            expectedOutcome = expectedOutcome
        )
    }

    private fun resolveChannelHintForFreq(freqHz: Float, config: AudioSystemConfig?): String {
        if (config == null) return "эквалайзер ГУ"
        if (config.hasSubwoofer) {
            val subX = config.crossoverFor(SpeakerGroupType.SUBWOOFER)
            if (subX != null && freqHz <= subX.effectiveMaxHz()) {
                return "канал Сабвуфер"
            }
        }
        if (config.crossoverTopology == CrossoverTopology.PASSIVE_CROSSOVER) {
            return "канал Фронт (кросс.)"
        }
        val twX = config.crossoverFor(SpeakerGroupType.TWEETER)
        if (twX != null && freqHz >= twX.effectiveMinHz()) {
            return "канал ВЧ (Твитер)"
        }
        val midX = config.crossoverFor(SpeakerGroupType.MIDRANGE)
        if (midX != null && freqHz in midX.effectiveMinHz()..midX.effectiveMaxHz()) {
            return "канал СЧ"
        }
        return "канал Мидбас"
    }

    private fun buildQAndChannelLabel(baseQ: String, channelHint: String): String {
        return "$channelHint • $baseQ"
    }

    private data class MappedBandAdjustment(
        val centerHz: Float,
        val recommendedGainDb: Float
    )

    private fun map31BandsToHeadUnit(
        request: AiTuningRequest,
        headUnitCentersHz: List<Float>
    ): List<MappedBandAdjustment> {
        val advice = request.eqAnalysis.adviceList
        if (advice.isEmpty()) return emptyList()

        return headUnitCentersHz.mapIndexed { idx, centerHz ->
            val logCenter = ln(centerHz.toDouble())
            val prevLog = if (idx > 0) ln(headUnitCentersHz[idx - 1].toDouble()) else logCenter - 0.8
            val nextLog = if (idx < headUnitCentersHz.lastIndex) ln(headUnitCentersHz[idx + 1].toDouble()) else logCenter + 0.8
            val lowerBound = (prevLog + logCenter) / 2.0
            val upperBound = (logCenter + nextLog) / 2.0

            var weightedSum = 0.0
            var weightTotal = 0.0
            for (band in advice) {
                val logF = ln(band.freqHz.toDouble())
                val inWindow = (idx == 0 && logF <= upperBound) ||
                    (idx == headUnitCentersHz.lastIndex && logF >= lowerBound) ||
                    (logF in lowerBound..upperBound)
                if (inWindow) {
                    val dist = abs(logF - logCenter)
                    val weight = 1.0 / (1.0 + dist * 3.0)
                    weightedSum += band.deltaDb * weight
                    weightTotal += weight
                }
            }

            val avgDelta = if (weightTotal > 0.0) (weightedSum / weightTotal).toFloat() else 0f
            // Limit boosts more conservatively than cuts for head unit headroom safety
            val clamped = avgDelta.coerceIn(-9.0f, 6.0f)
            val roundedHalfDb = (clamped * 2f).roundToInt() / 2f
            MappedBandAdjustment(centerHz = centerHz, recommendedGainDb = roundedHalfDb)
        }
    }

    private fun buildSampleFallbackBands(headUnitCentersHz: List<Float>): List<MappedBandAdjustment> {
        // Typical car cabin acoustic signature (sample placeholder when mic isn't actively measuring):
        // cabin boom around 50-80 Hz, door midbass dip around 125-200 Hz, windshield reflection peak around 2.5-4 kHz
        return headUnitCentersHz.map { f ->
            val sampleGain = when {
                f in 45f..85f -> -4.0f
                f in 120f..210f -> +2.5f
                f in 400f..650f -> -2.0f
                f in 2400f..4500f -> -3.5f
                f >= 10000f -> +1.5f
                else -> 0f
            }
            MappedBandAdjustment(centerHz = f, recommendedGainDb = sampleGain)
        }
    }

    private fun averageDeltaInRange(request: AiTuningRequest, minHz: Float, maxHz: Float): Float {
        val items = request.eqAnalysis.adviceList.filter { it.freqHz in minHz..maxHz }
        if (items.isEmpty()) return 0f
        return items.map { it.deltaDb }.average().toFloat()
    }

    private fun buildOverallAssessment(
        request: AiTuningRequest,
        significantCount: Int,
        useSampleCurve: Boolean
    ): String {
        val sysTag = request.systemConfig?.let { cfg ->
            val subText = if (cfg.hasSubwoofer) "+ Саб" else "без саба"
            " (${cfg.wayCount.shortLabel}, ${cfg.crossoverTopology.shortLabel}, $subText)"
        } ?: ""
        if (useSampleCurve) {
            return "[Пример ответа AI] Показан типовой сценарий настройки системы$sysTag под кривую «${request.targetCurve.name}» для ${request.headUnitProfile.fullLabel}. Замерьте динамики по порядку на шаге 3, чтобы рекомендации опирались на ваши реальные данные."
        }
        val measuredTag = if (request.speakerMeasurements.isNotEmpty()) {
            " Учтены индивидуальные замеры (${request.speakerMeasurements.size} кан.) и срезы DSP."
        } else {
            ""
        }
        val score = request.eqAnalysis.matchScorePercent
        val rms = String.format(java.util.Locale.US, "%.1f", request.eqAnalysis.rmsDeviationDb)
        return when {
            score >= 82 || significantCount == 0 ->
                "[Пример ответа AI] Высокое соответствие цели «${request.targetCurve.name}»$sysTag ($score%, RMS ±$rms дБ).$measuredTag Требуются лишь минимальные точечные правки."
            score >= 55 ->
                "[Пример ответа AI] Умеренные отклонения от цели «${request.targetCurve.name}»$sysTag (SQ $score%, RMS ±$rms дБ).$measuredTag Подготовлено $significantCount шагов для ${request.headUnitProfile.shortLabel}: сначала ослабление пиков, затем выравнивание провалов."
            else ->
                "[Пример ответа AI] Заметный дисбаланс АЧХ относительно «${request.targetCurve.name}»$sysTag (SQ $score%, RMS ±$rms дБ).$measuredTag Начните с выравнивания уровней каналов и срезов DSP, затем выполните $significantCount шагов EQ."
        }
    }

    private fun buildPriorityIssues(
        adjustments: List<MappedBandAdjustment>,
        targetCurveName: String,
        useSampleCurve: Boolean,
        wizardReport: WizardAnalysisReport?
    ): List<String> {
        val list = mutableListOf<String>()
        wizardReport?.crossoverDiagnostics?.filter { it.isWarning }?.take(2)?.forEach {
            list.add("Срезы DSP: ${it.title}")
        }
        wizardReport?.levelMatchTips?.filter { it.isWarning }?.take(2)?.forEach {
            list.add("${it.title}: ${it.detail}")
        }
        if (adjustments.isEmpty() && list.isEmpty()) {
            return listOf("Критических отклонений от кривой «$targetCurveName» не обнаружено.")
        }
        val prefix = if (useSampleCurve) "Пример: " else ""
        adjustments.take(3).forEach { adj ->
            val label = formatFreqLabel(adj.centerHz)
            if (adj.recommendedGainDb < 0f) {
                val excess = String.format(java.util.Locale.US, "+%.1f", abs(adj.recommendedGainDb))
                list.add("${prefix}Пик на $label ($excess дБ выше цели) — вероятный резонанс салона или отражение от стекла.")
            } else {
                val dip = String.format(java.util.Locale.US, "-%.1f", adj.recommendedGainDb)
                list.add("${prefix}Провал на $label ($dip дБ ниже цели) — возможное фазовое сложение или спад динамика.")
            }
        }
        return list.take(5)
    }

    private fun buildAcousticReason(
        freqHz: Float,
        isCut: Boolean,
        deviationText: String,
        useSampleCurve: Boolean
    ): String {
        val sampleTag = if (useSampleCurve) "Пример анализа: " else ""
        return when {
            freqHz < 100f && isCut ->
                "${sampleTag}избыток энергии ($deviationText) в суббасовой зоне вызывает гул и «размазывание» баса в салоне."
            freqHz < 100f ->
                "${sampleTag}нехватка уровня ($deviationText) в нижнем басе ослабляет фундамент и глубину композиций."
            freqHz < 300f && isCut ->
                "${sampleTag}подъём мидбаса ($deviationText) создаёт «коробочный» гул дверей и маскирует нижнюю середину."
            freqHz < 300f ->
                "${sampleTag}провал мидбаса ($deviationText) снижает плотность и «панч» ударных; убедитесь также в правильной полярности дверных динамиков."
            freqHz < 1500f && isCut ->
                "${sampleTag}горб в нижней/средней середине ($deviationText) даёт «носовой» окрас вокала и акустических инструментов."
            freqHz < 1500f ->
                "${sampleTag}провал середины ($deviationText) делает вокал отдалённым и менее разборчивым."
            freqHz < 5500f && isCut ->
                "${sampleTag}пик в верхней середине ($deviationText) от отражений лобового стекла вызывает резкость и утомляемость слуха."
            freqHz < 5500f ->
                "${sampleTag}спад верхней середины ($deviationText) приглушает атаку инструментов и детализацию сцены."
            isCut ->
                "${sampleTag}избыток ВЧ ($deviationText) подчёркивает сибилянты («с/ц») и делает твитеры навязчивыми."
            else ->
                "${sampleTag}ранний спад ВЧ ($deviationText) снижает «воздух» и прозрачность на верхних частотах."
        }
    }

    private fun formatFreqLabel(freqHz: Float): String {
        return if (freqHz >= 1000f) {
            val khz = freqHz / 1000f
            if (khz == khz.toInt().toFloat()) {
                "${khz.toInt()} кГц"
            } else {
                String.format(java.util.Locale.US, "%.2f кГц", khz).replace("0 кГц", " кГц")
            }
        } else {
            if (freqHz == freqHz.toInt().toFloat()) {
                "${freqHz.toInt()} Гц"
            } else {
                String.format(java.util.Locale.US, "%.1f Гц", freqHz)
            }
        }
    }
}
