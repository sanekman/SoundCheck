package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ai.AiAdvisorUiState
import com.example.ai.HeadUnitEqProfile
import com.example.audio.ChannelCrossoverConfig
import com.example.audio.CrossoverTopology
import com.example.audio.EqBandAdvice
import com.example.audio.FrequencyRangeFilter
import com.example.audio.OutputChannel
import com.example.audio.RtaDisplayMode
import com.example.audio.SpeakerGroupType
import com.example.audio.SystemWayCount
import com.example.audio.TargetCurvePreset
import com.example.audio.TuningWizardUiState
import com.example.audio.WizardStep
import com.example.data.RtaSettings
import com.example.data.RtaSnapshotModel
import com.example.ui.GeneratorUiState
import com.example.ui.RtaLiveUiState
import com.example.ui.SpeakerDistance
import com.example.ui.components.RtaSpectrumCanvas
import com.example.ui.components.TuningWizardPanel
import com.example.ui.theme.LocalRtaColors
import kotlin.math.abs

/**
 * Tuning Wizard ("Мастер настройки") & Acoustic Recommendations Screen.
 * Guides the user through system configuration (2/3-way, active/passive, subwoofer),
 * DSP crossover entry, sequential per-speaker RTA measurement, crossover-aware tuning advice,
 * 31-band RTA deviation table, and Time Alignment delay calculation.
 */
@Composable
fun DspEqScreen(
    liveState: RtaLiveUiState,
    settings: RtaSettings,
    activeTargetCurve: TargetCurvePreset,
    generatorState: GeneratorUiState,
    wizardState: TuningWizardUiState,
    isCapturing: Boolean,
    rangeFilter: FrequencyRangeFilter,
    sortByPriority: Boolean,
    speakerDistances: List<SpeakerDistance>,
    cabinTempC: Float,
    snapshots: List<RtaSnapshotModel>,
    aiAdvisorState: AiAdvisorUiState,
    selectedHeadUnitProfile: HeadUnitEqProfile,
    completedAiSteps: Set<Int>,
    calculateDelayMs: (Float) -> Float,
    onToggleCapture: () -> Unit,
    onToggleAutoAlign: (Boolean) -> Unit,
    onManualTargetRefChange: (Float) -> Unit,
    onSelectRangeFilter: (FrequencyRangeFilter) -> Unit,
    onToggleSortPriority: (Boolean) -> Unit,
    onUpdateSpeakerDistance: (String, Float) -> Unit,
    onUpdateCabinTemp: (Float) -> Unit,
    onSelectWizardStep: (WizardStep) -> Unit,
    onSetWizardWayCount: (SystemWayCount) -> Unit,
    onSetWizardTopology: (CrossoverTopology) -> Unit,
    onSetWizardHasSubwoofer: (Boolean) -> Unit,
    onUpdateWizardCrossover: (SpeakerGroupType, ChannelCrossoverConfig) -> Unit,
    onResetWizardCrossovers: () -> Unit,
    onSelectWizardSpeakerSlot: (Int) -> Unit,
    onStartMeasureWizardSpeaker: () -> Unit,
    onToggleWizardPinkNoise: (OutputChannel) -> Unit,
    onClearWizardMeasurements: () -> Unit,
    onSelectHeadUnitProfile: (HeadUnitEqProfile) -> Unit,
    onAnalyzeLiveWithAi: () -> Unit,
    onAnalyzeSnapshotWithAi: (RtaSnapshotModel) -> Unit,
    onToggleAiStepCompleted: (Int) -> Unit,
    onResetAiChecklist: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rtaColors = LocalRtaColors.current
    // 0 = Мастер настройки, 1 = 31 полоса, 2 = Задержки (TA)
    var selectedSubTab by rememberSaveable { mutableIntStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Screen Header ("Мастер настройки") + Sub-tab selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.eq_screen_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(if (isCapturing) rtaColors.peakCoralDim else rtaColors.spectrumCyanGlow),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = onToggleCapture,
                    modifier = Modifier.testTag("advice_live_capture_toggle")
                ) {
                    Icon(
                        imageVector = if (isCapturing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isCapturing) "Пауза RTA" else "Старт RTA",
                        tint = if (isCapturing) rtaColors.peakCoral else rtaColors.spectrumCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Sub-tab selector row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val tabs = listOf(
                0 to stringResource(R.string.wizard_tab_label),
                1 to "31 полоса",
                2 to "Задержки (TA)"
            )
            tabs.forEach { (idx, title) ->
                FilterChip(
                    selected = selectedSubTab == idx,
                    onClick = { selectedSubTab = idx },
                    label = { Text(title, style = MaterialTheme.typography.labelMedium) },
                    modifier = Modifier.testTag("advice_subtab_$idx"),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = rtaColors.spectrumCyanGlow,
                        selectedLabelColor = rtaColors.spectrumCyan
                    )
                )
            }
        }

        if (selectedSubTab == 1) {
            // Live RTA АЧХ vs Target Curve Monitor for the 31-band table
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DotLegend(rtaColors.spectrumCyan, "Замер RTA")
                        if (settings.showTargetCurve) {
                            DotLegend(rtaColors.targetAmber, activeTargetCurve.shortName)
                        }
                    }

                    Text(
                        text = "SQ ${liveState.eqAnalysis.matchScorePercent}% • ±${String.format(java.util.Locale.US, "%.1f", liveState.eqAnalysis.rmsDeviationDb)} дБ",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = when {
                            liveState.eqAnalysis.matchScorePercent >= 75 -> rtaColors.emeraldOk
                            liveState.eqAnalysis.matchScorePercent >= 50 -> rtaColors.targetAmber
                            else -> rtaColors.peakCoral
                        }
                    )
                }

                RtaSpectrumCanvas(
                    bands31Db = liveState.bands31Db,
                    peakHold31Db = null,
                    trace128Db = liveState.trace128Db,
                    targetCurve = activeTargetCurve,
                    targetReferenceDb = liveState.eqAnalysis.alignedReferenceDb,
                    showTargetCurve = settings.showTargetCurve,
                    targetToleranceDb = settings.targetToleranceDb,
                    displayMode = RtaDisplayMode.BOTH,
                    showPeakHold = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(165.dp)
                )
            }
        }

        when (selectedSubTab) {
            0 -> TuningWizardPanel(
                wizardState = wizardState,
                liveState = liveState,
                settings = settings,
                activeTargetCurve = activeTargetCurve,
                generatorState = generatorState,
                snapshots = snapshots,
                aiAdvisorState = aiAdvisorState,
                selectedHeadUnitProfile = selectedHeadUnitProfile,
                completedAiSteps = completedAiSteps,
                onSelectWizardStep = onSelectWizardStep,
                onSetWayCount = onSetWizardWayCount,
                onSetCrossoverTopology = onSetWizardTopology,
                onSetHasSubwoofer = onSetWizardHasSubwoofer,
                onUpdateChannelCrossover = onUpdateWizardCrossover,
                onResetDefaultCrossovers = onResetWizardCrossovers,
                onSelectSpeakerSlot = onSelectWizardSpeakerSlot,
                onStartMeasureCurrentSpeaker = onStartMeasureWizardSpeaker,
                onToggleWizardPinkNoise = onToggleWizardPinkNoise,
                onClearSpeakerMeasurements = onClearWizardMeasurements,
                onSelectHeadUnitProfile = onSelectHeadUnitProfile,
                onAnalyzeWizardWithAi = onAnalyzeLiveWithAi,
                onAnalyzeSnapshotWithAi = onAnalyzeSnapshotWithAi,
                onToggleAiStepCompleted = onToggleAiStepCompleted,
                onResetAiChecklist = onResetAiChecklist,
                modifier = Modifier.weight(1f)
            )

            1 -> TuningRecommendationsPanel(
                liveState = liveState,
                settings = settings,
                rangeFilter = rangeFilter,
                sortByPriority = sortByPriority,
                onToggleAutoAlign = onToggleAutoAlign,
                onManualTargetRefChange = onManualTargetRefChange,
                onSelectRangeFilter = onSelectRangeFilter,
                onToggleSortPriority = onToggleSortPriority,
                modifier = Modifier.weight(1f)
            )

            else -> TimeAlignmentPanel(
                speakerDistances = speakerDistances,
                cabinTempC = cabinTempC,
                calculateDelayMs = calculateDelayMs,
                onUpdateSpeakerDistance = onUpdateSpeakerDistance,
                onUpdateCabinTemp = onUpdateCabinTemp,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun TuningRecommendationsPanel(
    liveState: RtaLiveUiState,
    settings: RtaSettings,
    rangeFilter: FrequencyRangeFilter,
    sortByPriority: Boolean,
    onToggleAutoAlign: (Boolean) -> Unit,
    onManualTargetRefChange: (Float) -> Unit,
    onSelectRangeFilter: (FrequencyRangeFilter) -> Unit,
    onToggleSortPriority: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val rtaColors = LocalRtaColors.current
    val allAdvice = liveState.eqAnalysis.adviceList

    // Compute per-zone average deviation (Sub, Midbass, Midrange, Tweeter) for actionable summary
    val zoneSummaries = remember(allAdvice) {
        listOf(
            FrequencyRangeFilter.SUB,
            FrequencyRangeFilter.MIDBASS,
            FrequencyRangeFilter.MIDRANGE,
            FrequencyRangeFilter.TWEETER
        ).map { zone ->
            val itemsInZone = allAdvice.filter { it.freqHz >= zone.minHz && it.freqHz <= zone.maxHz }
            val avgDelta = if (itemsInZone.isNotEmpty()) {
                itemsInZone.map { it.deltaDb }.average().toFloat()
            } else 0f
            zone to avgDelta
        }
    }

    val topSummaryTip = remember(zoneSummaries, liveState.hasReceivedSignal, settings.targetToleranceDb) {
        if (!liveState.hasReceivedSignal) {
            "Запустите RTA-анализатор и включите розовый шум на магнитоле — приложение подскажет точные поправки для вашего процессора или эквалайзера ГУ."
        } else {
            val worst = zoneSummaries.maxByOrNull { abs(it.second) }
            if (worst == null || abs(worst.second) <= settings.targetToleranceDb) {
                "Отличный тональный баланс! Все диапазоны находятся в пределах допуска ±${String.format(java.util.Locale.US, "%.1f", settings.targetToleranceDb)} дБ."
            } else {
                val (zone, delta) = worst
                val action = if (delta > 0f) "поднять уровень на +${String.format(java.util.Locale.US, "%.1f", delta)} дБ"
                else "убавить уровень на ${String.format(java.util.Locale.US, "%.1f", delta)} дБ"
                when (zone) {
                    FrequencyRangeFilter.SUB -> "Диапазон Саб (20–100 Гц): рекомендуется $action (проверьте гейн сабвуфера и стык ФНЧ)."
                    FrequencyRangeFilter.MIDBASS -> "Диапазон Мидбас (80–400 Гц): рекомендуется $action (проверьте полярность мидбасов и срез ФВЧ)."
                    FrequencyRangeFilter.MIDRANGE -> "Диапазон СЧ (400–3.15 кГц): рекомендуется $action в эквалайзере процессора."
                    FrequencyRangeFilter.TWEETER -> "Диапазон ВЧ (3.15–20 кГц): рекомендуется $action (проверьте уровень твитеров и угол их направления)."
                    FrequencyRangeFilter.ALL -> "Рекомендуется $action."
                }
            }
        }
    }

    val filteredList = remember(allAdvice, rangeFilter, sortByPriority) {
        val ranged = allAdvice.filter {
            it.freqHz >= rangeFilter.minHz && it.freqHz <= rangeFilter.maxHz
        }
        if (sortByPriority) ranged.sortedByDescending { abs(it.deltaDb) } else ranged
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        // Actionable Diagnostic Summary Card
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, rtaColors.hairline, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = if (liveState.hasReceivedSignal && liveState.eqAnalysis.matchScorePercent >= 80) {
                            Icons.Default.CheckCircle
                        } else {
                            Icons.Default.Lightbulb
                        },
                        contentDescription = null,
                        tint = if (liveState.hasReceivedSignal && liveState.eqAnalysis.matchScorePercent >= 80) {
                            rtaColors.emeraldOk
                        } else {
                            rtaColors.targetAmber
                        },
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = topSummaryTip,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // 4-Zone Balance Strip (Sub / Midbass / Mid / High)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    zoneSummaries.forEach { (zone, avgDelta) ->
                        val ok = abs(avgDelta) <= settings.targetToleranceDb
                        val accent = when {
                            !liveState.hasReceivedSignal -> MaterialTheme.colorScheme.onSurfaceVariant
                            ok -> rtaColors.emeraldOk
                            avgDelta > 0f -> rtaColors.spectrumCyan
                            else -> rtaColors.peakCoral
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = zone.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (liveState.hasReceivedSignal) {
                                    String.format(java.util.Locale.US, "%+.1f", avgDelta)
                                } else {
                                    "--"
                                },
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = accent
                            )
                        }
                    }
                }
            }
        }

        // Target Reference Level Alignment
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, rtaColors.hairline, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${stringResource(R.string.eq_auto_align)} (${String.format(java.util.Locale.US, "%.1f дБ", liveState.eqAnalysis.alignedReferenceDb)})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Switch(
                        checked = settings.autoAlignTarget,
                        onCheckedChange = onToggleAutoAlign,
                        colors = SwitchDefaults.colors(checkedThumbColor = rtaColors.targetAmber)
                    )
                }
                if (!settings.autoAlignTarget) {
                    Slider(
                        value = settings.manualTargetRefDb,
                        onValueChange = onManualTargetRefChange,
                        valueRange = 50f..100f,
                        colors = SliderDefaults.colors(
                            thumbColor = rtaColors.targetAmber,
                            activeTrackColor = rtaColors.targetAmber
                        )
                    )
                }
            }
        }

        // Range & Priority Sort Filter Chips
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FrequencyRangeFilter.entries.forEach { rf ->
                    FilterChip(
                        selected = rangeFilter == rf,
                        onClick = { onSelectRangeFilter(rf) },
                        label = { Text(rf.label, style = MaterialTheme.typography.labelMedium) }
                    )
                }
                FilterChip(
                    selected = sortByPriority,
                    onClick = { onToggleSortPriority(!sortByPriority) },
                    label = {
                        Text(
                            if (sortByPriority) stringResource(R.string.eq_sort_priority) else stringResource(R.string.eq_sort_freq),
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = rtaColors.peakCoralDim,
                        selectedLabelColor = rtaColors.peakCoral
                    )
                )
            }
        }

        // 31-Band Recommendations List
        items(filteredList, key = { it.bandIndex }) { item ->
            EqAdviceRow(item = item)
        }
    }
}

@Composable
private fun EqAdviceRow(item: EqBandAdvice) {
    val rtaColors = LocalRtaColors.current
    val deltaColor = when {
        item.inTolerance -> rtaColors.emeraldOk
        item.deltaDb > 0f -> rtaColors.spectrumCyan
        else -> rtaColors.peakCoral
    }
    val actionLabel = when {
        item.inTolerance -> "В норме"
        item.deltaDb > 0f -> "Поднять"
        else -> "Убавить"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, rtaColors.hairline, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.width(74.dp)) {
            Text(
                text = "${item.freqLabel} Гц",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "рек. Q ${String.format(java.util.Locale.US, "%.1f", item.recommendedQ)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = String.format(java.util.Locale.US, "Замер: %.1f дБ  •  Цель: %.1f дБ", item.measuredDb, item.targetDb),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            val norm = (abs(item.deltaDb) / 12f).coerceIn(0.05f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(norm)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(deltaColor)
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = String.format(java.util.Locale.US, "%+.1f дБ", item.deltaDb),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = deltaColor
            )
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelSmall,
                color = deltaColor.copy(alpha = 0.85f)
            )
        }
    }
}

@Composable
private fun TimeAlignmentPanel(
    speakerDistances: List<SpeakerDistance>,
    cabinTempC: Float,
    calculateDelayMs: (Float) -> Float,
    onUpdateSpeakerDistance: (String, Float) -> Unit,
    onUpdateCabinTemp: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val rtaColors = LocalRtaColors.current
    val speedOfSound = 331.3f + 0.606f * cabinTempC

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, rtaColors.hairline, RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Timer, contentDescription = null, tint = rtaColors.spectrumCyan)
                    Text(
                        text = stringResource(R.string.ta_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = stringResource(R.string.ta_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${stringResource(R.string.ta_temp_label)}: ${cabinTempC.toInt()} °C",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "c = ${String.format(java.util.Locale.US, "%.1f", speedOfSound)} м/с",
                        style = MaterialTheme.typography.labelMedium,
                        color = rtaColors.targetAmber
                    )
                }
                Slider(
                    value = cabinTempC,
                    onValueChange = onUpdateCabinTemp,
                    valueRange = -10f..40f,
                    colors = SliderDefaults.colors(
                        thumbColor = rtaColors.targetAmber,
                        activeTrackColor = rtaColors.targetAmber
                    )
                )
            }
        }

        items(speakerDistances, key = { it.id }) { spk ->
            val delayMs = calculateDelayMs(spk.distanceCm)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, rtaColors.hairline, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = spk.label,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "${spk.distanceCm.toInt()} см",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = String.format(java.util.Locale.US, "%.2f мс", delayMs),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = if (delayMs == 0f) rtaColors.emeraldOk else rtaColors.spectrumCyan
                        )
                    }
                }
                Slider(
                    value = spk.distanceCm,
                    onValueChange = { onUpdateSpeakerDistance(spk.id, it) },
                    valueRange = 40f..320f,
                    colors = SliderDefaults.colors(
                        thumbColor = rtaColors.spectrumCyan,
                        activeTrackColor = rtaColors.spectrumCyan
                    )
                )
            }
        }
    }
}

@Composable
private fun DotLegend(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
