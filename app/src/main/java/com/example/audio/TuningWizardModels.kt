package com.example.audio

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class SystemWayCount(val label: String, val shortLabel: String, val description: String) {
    TWO_WAY(
        label = "2-полосная система",
        shortLabel = "2 полосы",
        description = "Мидбас (НЧ/СЧ) + Твитер (ВЧ)"
    ),
    THREE_WAY(
        label = "3-полосная система",
        shortLabel = "3 полосы",
        description = "Мидбас (НЧ) + Середина (СЧ) + Твитер (ВЧ)"
    )
}

enum class CrossoverTopology(val label: String, val shortLabel: String, val description: String) {
    ACTIVE_DSP(
        label = "Поканально (DSP)",
        shortLabel = "Поканально",
        description = "Каждый динамик на своём канале усилителя/DSP со своими срезами"
    ),
    PASSIVE_CROSSOVER(
        label = "Через кроссоверы",
        shortLabel = "Кроссоверы",
        description = "Фронт разделён пассивными кроссоверами (каналы Фронт Л и Фронт П)"
    )
}

enum class FilterType(val code: String, val label: String) {
    LINKWITZ_RILEY("LR", "Linkwitz-Riley"),
    BUTTERWORTH("BW", "Butterworth"),
    BESSEL("BS", "Bessel")
}

enum class FilterSlope(val dbPerOct: Int, val label: String) {
    DB_6(6, "6 дБ/окт"),
    DB_12(12, "12 дБ/окт"),
    DB_18(18, "18 дБ/окт"),
    DB_24(24, "24 дБ/окт"),
    DB_36(36, "36 дБ/окт"),
    DB_48(48, "48 дБ/окт")
}

enum class SpeakerGroupType(val label: String, val shortLabel: String) {
    SUBWOOFER("Сабвуфер", "Саб"),
    MIDBASS("Мидбас (НЧ)", "Мидбас"),
    MIDRANGE("Среднечастотник (СЧ)", "СЧ"),
    TWEETER("Твитер (ВЧ)", "ВЧ"),
    FRONT_PASSIVE("Фронт (пасс. кроссовер)", "Фронт"),
    FULL_SYSTEM("Общий замер (Вся система)", "Общий")
}

enum class SpeakerSide(val label: String, val generatorChannel: OutputChannel) {
    LEFT("Левый (L)", OutputChannel.LEFT),
    RIGHT("Правый (R)", OutputChannel.RIGHT),
    MONO_BOTH("Оба (L+R)", OutputChannel.BOTH)
}

enum class WizardStep(val index: Int, val shortTitle: String, val fullTitle: String) {
    SYSTEM_CONFIG(0, "1. Система", "Состав аудиосистемы"),
    DSP_CROSSOVERS(1, "2. Срезы DSP", "Срезы кроссоверов на DSP"),
    SPEAKER_MEASUREMENT(2, "3. Замеры", "Поочерёдный замер динамиков"),
    RECOMMENDATIONS(3, "4. Советы", "Точные рекомендации настройки")
}

/**
 * DSP crossover configuration entered by the user to describe how their car DSP / head unit
 * filters a speaker group (AutoRTA uses this to restrict EQ advice to each driver's real passband
 * and diagnose crossover junctions).
 */
data class ChannelCrossoverConfig(
    val group: SpeakerGroupType,
    val hpfEnabled: Boolean,
    val hpfHz: Float,
    val hpfSlope: FilterSlope,
    val hpfType: FilterType,
    val lpfEnabled: Boolean,
    val lpfHz: Float,
    val lpfSlope: FilterSlope,
    val lpfType: FilterType,
    val phaseInverted: Boolean = false
) {
    fun effectiveMinHz(): Float = if (hpfEnabled) hpfHz else 20f
    fun effectiveMaxHz(): Float = if (lpfEnabled) lpfHz else 20000f

    fun formatPassbandSummary(): String {
        val hpfStr = if (hpfEnabled) {
            "ФВЧ ${formatHz(hpfHz)} (${hpfSlope.dbPerOct} дБ ${hpfType.code})"
        } else {
            "ФВЧ выкл"
        }
        val lpfStr = if (lpfEnabled) {
            "ФНЧ ${formatHz(lpfHz)} (${lpfSlope.dbPerOct} дБ ${lpfType.code})"
        } else {
            "ФНЧ выкл"
        }
        val phaseStr = if (phaseInverted) " • 180°" else ""
        return "$hpfStr  |  $lpfStr$phaseStr"
    }

    companion object {
        fun formatHz(hz: Float): String = if (hz >= 1000f) {
            val khz = hz / 1000f
            if (khz == khz.toInt().toFloat()) "${khz.toInt()} кГц"
            else String.format(java.util.Locale.US, "%.2f кГц", khz).replace("0 кГц", " кГц")
        } else {
            "${hz.roundToInt()} Гц"
        }
    }
}

data class AudioSystemConfig(
    val wayCount: SystemWayCount = SystemWayCount.TWO_WAY,
    val crossoverTopology: CrossoverTopology = CrossoverTopology.ACTIVE_DSP,
    val hasSubwoofer: Boolean = true,
    val crossovers: Map<SpeakerGroupType, ChannelCrossoverConfig> =
        TuningWizardEngine.defaultCrossoversFor(
            wayCount = SystemWayCount.TWO_WAY,
            topology = CrossoverTopology.ACTIVE_DSP,
            hasSubwoofer = true
        )
) {
    fun activeChannelGroups(): List<SpeakerGroupType> = buildList {
        when (crossoverTopology) {
            CrossoverTopology.PASSIVE_CROSSOVER -> add(SpeakerGroupType.FRONT_PASSIVE)
            CrossoverTopology.ACTIVE_DSP -> {
                add(SpeakerGroupType.TWEETER)
                if (wayCount == SystemWayCount.THREE_WAY) {
                    add(SpeakerGroupType.MIDRANGE)
                }
                add(SpeakerGroupType.MIDBASS)
            }
        }
        if (hasSubwoofer) add(SpeakerGroupType.SUBWOOFER)
    }

    fun crossoverFor(group: SpeakerGroupType): ChannelCrossoverConfig? = crossovers[group]
}

data class WizardSpeakerSlot(
    val id: String,
    val orderIndex: Int,
    val label: String,
    val shortLabel: String,
    val group: SpeakerGroupType,
    val side: SpeakerSide,
    val instruction: String
)

data class SpeakerMeasurementResult(
    val slotId: String,
    val slotLabel: String,
    val group: SpeakerGroupType,
    val side: SpeakerSide,
    val bands31Db: FloatArray,
    val overallSplDb: Float,
    val passbandAvgSplDb: Float,
    val passbandRmsErrorDb: Float,
    val passbandAvgDeltaDb: Float,
    val passbandMinHz: Float,
    val passbandMaxHz: Float,
    val measuredAtMs: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SpeakerMeasurementResult) return false
        return slotId == other.slotId &&
            bands31Db.contentEquals(other.bands31Db) &&
            measuredAtMs == other.measuredAtMs
    }

    override fun hashCode(): Int {
        var result = slotId.hashCode()
        result = 31 * result + bands31Db.contentHashCode()
        result = 31 * result + measuredAtMs.hashCode()
        return result
    }
}

data class ChannelLevelAdvice(
    val title: String,
    val recommendedGainDb: Float,
    val detail: String,
    val isWarning: Boolean
)

data class CrossoverDiagnosticItem(
    val title: String,
    val detail: String,
    val isWarning: Boolean
)

data class SpeakerPassbandRecommendation(
    val slotId: String,
    val slotLabel: String,
    val passbandText: String,
    val passbandAvgSplDb: Float,
    val passbandRmsErrorDb: Float,
    val topBandFixes: List<EqBandAdvice>
)

data class WizardAnalysisReport(
    val measuredSpeakerCount: Int,
    val totalSpeakerCount: Int,
    val levelMatchTips: List<ChannelLevelAdvice>,
    val crossoverDiagnostics: List<CrossoverDiagnosticItem>,
    val perSpeakerRecommendations: List<SpeakerPassbandRecommendation>
)

data class TuningWizardUiState(
    val currentStep: WizardStep = WizardStep.SYSTEM_CONFIG,
    val systemConfig: AudioSystemConfig = AudioSystemConfig(),
    val speakerQueue: List<WizardSpeakerSlot> = TuningWizardEngine.buildSpeakerQueue(AudioSystemConfig()),
    val activeSlotIndex: Int = 0,
    val measurements: Map<String, SpeakerMeasurementResult> = emptyMap(),
    val isMeasuringSpeaker: Boolean = false,
    val measurementProgress: Float = 0f,
    val analysisReport: WizardAnalysisReport? = null
)

object TuningWizardEngine {

    /**
     * Standard ISO / DSP crossover frequencies for intuitive steppers & sliders.
     */
    val STANDARD_DSP_FREQS_HZ: List<Float> = listOf(
        20f, 25f, 31.5f, 40f, 50f, 63f, 80f, 100f, 125f, 160f,
        200f, 250f, 315f, 400f, 500f, 630f, 800f, 1000f, 1250f, 1600f,
        2000f, 2500f, 3150f, 4000f, 5000f, 6300f, 8000f, 10000f, 12500f
    )

    fun defaultCrossoversFor(
        wayCount: SystemWayCount,
        topology: CrossoverTopology,
        hasSubwoofer: Boolean
    ): Map<SpeakerGroupType, ChannelCrossoverConfig> {
        val map = linkedMapOf<SpeakerGroupType, ChannelCrossoverConfig>()

        if (hasSubwoofer) {
            map[SpeakerGroupType.SUBWOOFER] = ChannelCrossoverConfig(
                group = SpeakerGroupType.SUBWOOFER,
                hpfEnabled = true,
                hpfHz = 25f,
                hpfSlope = FilterSlope.DB_24,
                hpfType = FilterType.LINKWITZ_RILEY,
                lpfEnabled = true,
                lpfHz = 63f,
                lpfSlope = FilterSlope.DB_24,
                lpfType = FilterType.LINKWITZ_RILEY,
                phaseInverted = false
            )
        }

        if (topology == CrossoverTopology.PASSIVE_CROSSOVER) {
            map[SpeakerGroupType.FRONT_PASSIVE] = ChannelCrossoverConfig(
                group = SpeakerGroupType.FRONT_PASSIVE,
                hpfEnabled = hasSubwoofer,
                hpfHz = if (hasSubwoofer) 80f else 50f,
                hpfSlope = FilterSlope.DB_12,
                hpfType = FilterType.BUTTERWORTH,
                lpfEnabled = false,
                lpfHz = 20000f,
                lpfSlope = FilterSlope.DB_12,
                lpfType = FilterType.BUTTERWORTH
            )
        } else {
            val midbassHpf = if (hasSubwoofer) 63f else 50f
            val midbassLpf = if (wayCount == SystemWayCount.THREE_WAY) 315f else 2500f

            map[SpeakerGroupType.MIDBASS] = ChannelCrossoverConfig(
                group = SpeakerGroupType.MIDBASS,
                hpfEnabled = true,
                hpfHz = midbassHpf,
                hpfSlope = FilterSlope.DB_24,
                hpfType = FilterType.LINKWITZ_RILEY,
                lpfEnabled = true,
                lpfHz = midbassLpf,
                lpfSlope = if (wayCount == SystemWayCount.THREE_WAY) FilterSlope.DB_24 else FilterSlope.DB_12,
                lpfType = FilterType.LINKWITZ_RILEY
            )

            if (wayCount == SystemWayCount.THREE_WAY) {
                map[SpeakerGroupType.MIDRANGE] = ChannelCrossoverConfig(
                    group = SpeakerGroupType.MIDRANGE,
                    hpfEnabled = true,
                    hpfHz = 315f,
                    hpfSlope = FilterSlope.DB_24,
                    hpfType = FilterType.LINKWITZ_RILEY,
                    lpfEnabled = true,
                    lpfHz = 3150f,
                    lpfSlope = FilterSlope.DB_24,
                    lpfType = FilterType.LINKWITZ_RILEY
                )
            }

            val tweeterHpf = if (wayCount == SystemWayCount.THREE_WAY) 3150f else 3150f
            map[SpeakerGroupType.TWEETER] = ChannelCrossoverConfig(
                group = SpeakerGroupType.TWEETER,
                hpfEnabled = true,
                hpfHz = tweeterHpf,
                hpfSlope = FilterSlope.DB_24,
                hpfType = FilterType.LINKWITZ_RILEY,
                lpfEnabled = false,
                lpfHz = 20000f,
                lpfSlope = FilterSlope.DB_12,
                lpfType = FilterType.BUTTERWORTH
            )
        }

        return map
    }

    fun buildSpeakerQueue(config: AudioSystemConfig): List<WizardSpeakerSlot> {
        val slots = mutableListOf<WizardSpeakerSlot>()
        var order = 1

        fun addSlot(
            id: String,
            label: String,
            shortLabel: String,
            group: SpeakerGroupType,
            side: SpeakerSide,
            instruction: String
        ) {
            slots.add(
                WizardSpeakerSlot(
                    id = id,
                    orderIndex = order++,
                    label = label,
                    shortLabel = shortLabel,
                    group = group,
                    side = side,
                    instruction = instruction
                )
            )
        }

        when (config.crossoverTopology) {
            CrossoverTopology.ACTIVE_DSP -> {
                addSlot(
                    id = "tw_l",
                    label = "ВЧ (Твитер) Левый",
                    shortLabel = "ВЧ Л",
                    group = SpeakerGroupType.TWEETER,
                    side = SpeakerSide.LEFT,
                    instruction = "Включите только канал «ВЧ Левый» (проверьте наличие защитного ФВЧ!). Включите умеренный розовый шум на ГУ или с телефона (L)."
                )
                addSlot(
                    id = "tw_r",
                    label = "ВЧ (Твитер) Правый",
                    shortLabel = "ВЧ П",
                    group = SpeakerGroupType.TWEETER,
                    side = SpeakerSide.RIGHT,
                    instruction = "Включите только канал «ВЧ Правый» (проверьте наличие защитного ФВЧ!). Включите умеренный розовый шум на ГУ или с телефона (R)."
                )

                if (config.wayCount == SystemWayCount.THREE_WAY) {
                    addSlot(
                        id = "mid_l",
                        label = "СЧ Левый",
                        shortLabel = "СЧ Л",
                        group = SpeakerGroupType.MIDRANGE,
                        side = SpeakerSide.LEFT,
                        instruction = "Включите только канал «СЧ Левый» (остальные каналы в Mute на DSP). Включите розовый шум на ГУ или с телефона (L)."
                    )
                    addSlot(
                        id = "mid_r",
                        label = "СЧ Правый",
                        shortLabel = "СЧ П",
                        group = SpeakerGroupType.MIDRANGE,
                        side = SpeakerSide.RIGHT,
                        instruction = "Включите только канал «СЧ Правый» (остальные каналы в Mute на DSP). Включите розовый шум на ГУ или с телефона (R)."
                    )
                }

                addSlot(
                    id = "midbass_l",
                    label = "Мидбас Левый",
                    shortLabel = "Мид Л",
                    group = SpeakerGroupType.MIDBASS,
                    side = SpeakerSide.LEFT,
                    instruction = "Включите только канал «Мидбас Левый» (остальные каналы в Mute на DSP). Включите розовый шум на ГУ или с телефона (L)."
                )
                addSlot(
                    id = "midbass_r",
                    label = "Мидбас Правый",
                    shortLabel = "Мид П",
                    group = SpeakerGroupType.MIDBASS,
                    side = SpeakerSide.RIGHT,
                    instruction = "Включите только канал «Мидбас Правый» (остальные каналы в Mute на DSP). Включите розовый шум на ГУ или с телефона (R)."
                )
            }

            CrossoverTopology.PASSIVE_CROSSOVER -> {
                val wayLabel = if (config.wayCount == SystemWayCount.THREE_WAY) "3-полосный" else "2-полосный"
                addSlot(
                    id = "front_l",
                    label = "Фронт Левый ($wayLabel кросс.)",
                    shortLabel = "Фронт Л",
                    group = SpeakerGroupType.FRONT_PASSIVE,
                    side = SpeakerSide.LEFT,
                    instruction = "Переведите баланс на Левый канал (выключите сабвуфер). Включите розовый шум на ГУ или с телефона (L)."
                )
                addSlot(
                    id = "front_r",
                    label = "Фронт Правый ($wayLabel кросс.)",
                    shortLabel = "Фронт П",
                    group = SpeakerGroupType.FRONT_PASSIVE,
                    side = SpeakerSide.RIGHT,
                    instruction = "Переведите баланс на Правый канал (выключите сабвуфер). Включите розовый шум на ГУ или с телефона (R)."
                )
            }
        }

        if (config.hasSubwoofer) {
            addSlot(
                id = "sub",
                label = "Сабвуфер",
                shortLabel = "Саб",
                group = SpeakerGroupType.SUBWOOFER,
                side = SpeakerSide.MONO_BOTH,
                instruction = "Оставьте включённым только канал сабвуфера (отключите фронт в DSP/усилителе). Включите розовый шум на ГУ или с телефона (L+R)."
            )
        }

        // Always finish with full-system summed capture so phase & crossover junctions are verified
        addSlot(
            id = "full_system",
            label = "Вся система (общий замер)",
            shortLabel = "Общий",
            group = SpeakerGroupType.FULL_SYSTEM,
            side = SpeakerSide.MONO_BOTH,
            instruction = "Снимите Mute со всех динамиков и сабвуфера. Замерьте итоговую суммарную АЧХ салона на розовом шуме с ГУ или телефона (L+R)."
        )

        return slots
    }

    /**
     * Computes passband-restricted metrics for a single measured speaker using its configured DSP crossovers.
     */
    fun computeSpeakerMeasurement(
        slot: WizardSpeakerSlot,
        bands31Db: FloatArray,
        overallSplDb: Float,
        config: AudioSystemConfig,
        targetCurve: TargetCurvePreset,
        referenceDb: Float,
        toleranceDb: Float
    ): SpeakerMeasurementResult {
        val xover = config.crossoverFor(slot.group)
        val minHz = xover?.effectiveMinHz() ?: 20f
        val maxHz = xover?.effectiveMaxHz() ?: 20000f

        val passbandIndices = RtaBands.ISO_31_BANDS.mapIndexedNotNull { idx, band ->
            if (band.nominalHz in minHz..maxHz) idx else null
        }.ifEmpty { (0 until 31).toList() }

        val avgSpl = passbandIndices.map { bands31Db[it] }.average().toFloat()

        var sumDelta = 0.0
        var sumSq = 0.0
        for (idx in passbandIndices) {
            val targetAtBand = referenceDb + targetCurve.relativeDb31[idx]
            val delta = (targetAtBand - bands31Db[idx]).toDouble()
            sumDelta += delta
            sumSq += delta * delta
        }
        val avgDelta = (sumDelta / passbandIndices.size).toFloat()
        val rmsErr = sqrt(sumSq / passbandIndices.size).toFloat()

        return SpeakerMeasurementResult(
            slotId = slot.id,
            slotLabel = slot.label,
            group = slot.group,
            side = slot.side,
            bands31Db = bands31Db.copyOf(),
            overallSplDb = overallSplDb,
            passbandAvgSplDb = avgSpl,
            passbandRmsErrorDb = rmsErr,
            passbandAvgDeltaDb = avgDelta,
            passbandMinHz = minHz,
            passbandMaxHz = maxHz,
            measuredAtMs = System.currentTimeMillis()
        )
    }

    /**
     * Generates comprehensive crossover-aware car audio recommendations:
     * 1. Channel-to-channel & Left/Right gain matching
     * 2. DSP crossover junction, slope, gap/overlap, and tweeter protection diagnostics
     * 3. Per-speaker EQ adjustments strictly scoped to each driver's DSP passband
     */
    fun analyzeWizardSystem(
        config: AudioSystemConfig,
        queue: List<WizardSpeakerSlot>,
        measurements: Map<String, SpeakerMeasurementResult>,
        targetCurve: TargetCurvePreset,
        referenceDb: Float,
        toleranceDb: Float
    ): WizardAnalysisReport {
        val levelTips = mutableListOf<ChannelLevelAdvice>()
        val xoverDiagnostics = mutableListOf<CrossoverDiagnosticItem>()
        val perSpeakerRecs = mutableListOf<SpeakerPassbandRecommendation>()

        // 1. Validate DSP crossover settings structurally (even before all speakers are measured)
        val subX = config.crossoverFor(SpeakerGroupType.SUBWOOFER)
        val mbX = config.crossoverFor(SpeakerGroupType.MIDBASS)
        val midX = config.crossoverFor(SpeakerGroupType.MIDRANGE)
        val twX = config.crossoverFor(SpeakerGroupType.TWEETER)
        val frontX = config.crossoverFor(SpeakerGroupType.FRONT_PASSIVE)

        if (twX != null) {
            if (!twX.hpfEnabled) {
                xoverDiagnostics.add(
                    CrossoverDiagnosticItem(
                        title = "Опасность для твитеров: ФВЧ выключен!",
                        detail = "В поканальной системе обязательно включите ФВЧ на ВЧ (обычно 2.5–4 кГц, 12–24 дБ/окт), чтобы не сжечь твитеры.",
                        isWarning = true
                    )
                )
            } else if (twX.hpfHz < 2000f) {
                xoverDiagnostics.add(
                    CrossoverDiagnosticItem(
                        title = "Слишком низкий срез ВЧ (${ChannelCrossoverConfig.formatHz(twX.hpfHz)})",
                        detail = "Рекомендуется поднять ФВЧ твитеров минимум до 2.5–3.15 кГц (особенно при 12 дБ/окт), чтобы снизить искажения.",
                        isWarning = true
                    )
                )
            }
        }

        // Check Sub <-> Midbass / Front junction
        val lowFrontX = mbX ?: frontX
        if (subX != null && lowFrontX != null && subX.lpfEnabled && lowFrontX.hpfEnabled) {
            val ratio = lowFrontX.hpfHz / subX.lpfHz
            when {
                ratio > 1.6f -> xoverDiagnostics.add(
                    CrossoverDiagnosticItem(
                        title = "Разрыв на стыке Саб ↔ Фронт (${ChannelCrossoverConfig.formatHz(subX.lpfHz)} / ${ChannelCrossoverConfig.formatHz(lowFrontX.hpfHz)})",
                        detail = "Между ФНЧ сабвуфера и ФВЧ фронта слишком широкий зазор — возможен провал панча. Сблизьте срезы (напр., 63 Гц / 63–80 Гц).",
                        isWarning = true
                    )
                )
                ratio < 0.75f -> xoverDiagnostics.add(
                    CrossoverDiagnosticItem(
                        title = "Сильное перекрытие Саб ↔ Фронт (${ChannelCrossoverConfig.formatHz(subX.lpfHz)} / ${ChannelCrossoverConfig.formatHz(lowFrontX.hpfHz)})",
                        detail = "Перехлёст басовых полос вызывает гул и фазовую кашу. Разведите срезы или примените крутизну 24 дБ/окт LR.",
                        isWarning = true
                    )
                )
                else -> xoverDiagnostics.add(
                    CrossoverDiagnosticItem(
                        title = "Стык Саб ↔ Фронт настроен корректно (${ChannelCrossoverConfig.formatHz(subX.lpfHz)} / ${ChannelCrossoverConfig.formatHz(lowFrontX.hpfHz)})",
                        detail = "Если на общем замере в этой зоне виден провал, переключите фазу сабвуфера (0° / 180°) и сравните АЧХ.",
                        isWarning = false
                    )
                )
            }
        }

        // Check Midbass <-> Midrange junction (3-way active)
        if (mbX != null && midX != null && mbX.lpfEnabled && midX.hpfEnabled) {
            val ratio = midX.hpfHz / mbX.lpfHz
            if (ratio > 1.5f || ratio < 0.7f) {
                xoverDiagnostics.add(
                    CrossoverDiagnosticItem(
                        title = "Проверьте стык Мидбас ↔ СЧ (${ChannelCrossoverConfig.formatHz(mbX.lpfHz)} / ${ChannelCrossoverConfig.formatHz(midX.hpfHz)})",
                        detail = "Для ровной сшивки 3-полоски обычно используют симметричные срезы Linkwitz-Riley 24 дБ/окт на одной частоте (250–400 Гц).",
                        isWarning = true
                    )
                )
            }
        }

        // Check Upper Mid/Midbass <-> Tweeter junction
        val belowTwX = midX ?: mbX
        if (belowTwX != null && twX != null && belowTwX.lpfEnabled && twX.hpfEnabled) {
            val ratio = twX.hpfHz / belowTwX.lpfHz
            if (ratio > 1.6f) {
                xoverDiagnostics.add(
                    CrossoverDiagnosticItem(
                        title = "Зазор на стыке ${belowTwX.group.shortLabel} ↔ ВЧ (${ChannelCrossoverConfig.formatHz(belowTwX.lpfHz)} / ${ChannelCrossoverConfig.formatHz(twX.hpfHz)})",
                        detail = "Большой разрыв частот может дать провал разборчивости вокала. Подтяните ФНЧ ${belowTwX.group.shortLabel} ближе к ФВЧ ВЧ.",
                        isWarning = true
                    )
                )
            } else if (ratio < 0.75f) {
                xoverDiagnostics.add(
                    CrossoverDiagnosticItem(
                        title = "Перехлёст ${belowTwX.group.shortLabel} ↔ ВЧ (${ChannelCrossoverConfig.formatHz(belowTwX.lpfHz)} / ${ChannelCrossoverConfig.formatHz(twX.hpfHz)})",
                        detail = "Совместная работа двух излучателей в верхней середине усиливает гребенчатую фильтрацию и резкость.",
                        isWarning = true
                    )
                )
            }
        }

        if (config.crossoverTopology == CrossoverTopology.PASSIVE_CROSSOVER) {
            xoverDiagnostics.add(
                CrossoverDiagnosticItem(
                    title = "Режим пассивных кроссоверов",
                    detail = "Стык между мидбасом и ВЧ фиксирован пассивным кроссовером. Используйте ФВЧ фронта на ГУ/усилителе для защиты мидбаса от перегрузки и стыковки с сабом.",
                    isWarning = false
                )
            )
        }

        // 2. Left vs Right symmetry & band level matching from actual speaker measurements
        val pairs = listOf(
            "front_l" to "front_r",
            "midbass_l" to "midbass_r",
            "mid_l" to "mid_r",
            "tw_l" to "tw_r"
        )
        for ((leftId, rightId) in pairs) {
            val left = measurements[leftId]
            val right = measurements[rightId]
            if (left != null && right != null) {
                val diffLr = left.passbandAvgSplDb - right.passbandAvgSplDb // + means Left is louder
                val roundedAdj = ((-diffLr) * 2f).roundToInt() / 2f
                if (abs(diffLr) >= 1.5f) {
                    val louderName = if (diffLr > 0f) left.slotLabel else right.slotLabel
                    val quieterName = if (diffLr > 0f) right.slotLabel else left.slotLabel
                    val cutDb = -(abs(diffLr) * 2f).roundToInt() / 2f
                    levelTips.add(
                        ChannelLevelAdvice(
                            title = "Разбаланс Л/П: ${left.group.shortLabel}",
                            recommendedGainDb = cutDb,
                            detail = "$louderName громче чем $quieterName на ${String.format(java.util.Locale.US, "%.1f", abs(diffLr))} дБ. Убавьте гейн/уровень «$louderName» на ${String.format(java.util.Locale.US, "%.1f", cutDb)} дБ на DSP (или балансом).",
                            isWarning = abs(diffLr) >= 3.0f
                        )
                    )
                } else {
                    levelTips.add(
                        ChannelLevelAdvice(
                            title = "Баланс Л/П (${left.group.shortLabel}) в норме",
                            recommendedGainDb = roundedAdj,
                            detail = "Разница между левым и правым каналом всего ${String.format(java.util.Locale.US, "%.1f", abs(diffLr))} дБ — отличная симметрия.",
                            isWarning = false
                        )
                    )
                }
            }
        }

        // Band-to-target average level tips for each measured speaker
        for (slot in queue) {
            val m = measurements[slot.id] ?: continue
            if (slot.group != SpeakerGroupType.FULL_SYSTEM && abs(m.passbandAvgDeltaDb) >= 2.0f) {
                val gainAdj = (m.passbandAvgDeltaDb.coerceIn(-9f, 9f) * 2f).roundToInt() / 2f
                val verb = if (gainAdj > 0f) "Поднимите уровень канала" else "Убавьте уровень канала"
                levelTips.add(
                    ChannelLevelAdvice(
                        title = "Уровень полосы: ${slot.shortLabel}",
                        recommendedGainDb = gainAdj,
                        detail = "$verb «${slot.label}» на ${String.format(java.util.Locale.US, "%+.1f дБ", gainAdj)} относительно цели «${targetCurve.shortName}» перед эквализацией.",
                        isWarning = abs(gainAdj) >= 4.0f
                    )
                )
            }
        }

        // 3. Per-speaker passband EQ recommendations (strictly within each speaker's DSP HPF..LPF range)
        for (slot in queue) {
            val m = measurements[slot.id] ?: continue
            val xover = config.crossoverFor(slot.group)
            val passbandMin = m.passbandMinHz
            val passbandMax = m.passbandMaxHz

            // Align target curve to the speaker's own passband average so EQ fixes shape (not global gain)
            val passbandIndices = RtaBands.ISO_31_BANDS.mapIndexedNotNull { idx, band ->
                if (band.nominalHz in passbandMin..passbandMax) idx else null
            }.ifEmpty { (0 until 31).toList() }

            val localRefDb = passbandIndices.map { idx ->
                m.bands31Db[idx] - targetCurve.relativeDb31[idx]
            }.average().toFloat()

            val fullAnalysis = TargetCurves.analyzeAgainstTarget(
                measuredBandsDb = m.bands31Db,
                preset = targetCurve,
                autoAlign = false,
                manualTargetRefDb = if (slot.group == SpeakerGroupType.FULL_SYSTEM) referenceDb else localRefDb,
                toleranceDb = toleranceDb
            )

            val passbandFixes = fullAnalysis.adviceList
                .filter { it.freqHz in passbandMin..passbandMax && !it.inTolerance }
                .sortedWith(
                    compareBy<EqBandAdvice> { it.deltaDb > 0f } // cuts first
                        .thenByDescending { abs(it.deltaDb) }
                )
                .take(5)

            val passbandText = xover?.formatPassbandSummary() ?: "Полный диапазон (20 Гц – 20 кГц)"
            perSpeakerRecs.add(
                SpeakerPassbandRecommendation(
                    slotId = slot.id,
                    slotLabel = slot.label,
                    passbandText = passbandText,
                    passbandAvgSplDb = m.passbandAvgSplDb,
                    passbandRmsErrorDb = m.passbandRmsErrorDb,
                    topBandFixes = passbandFixes
                )
            )
        }

        return WizardAnalysisReport(
            measuredSpeakerCount = measurements.size,
            totalSpeakerCount = queue.size,
            levelMatchTips = levelTips,
            crossoverDiagnostics = xoverDiagnostics,
            perSpeakerRecommendations = perSpeakerRecs
        )
    }
}
