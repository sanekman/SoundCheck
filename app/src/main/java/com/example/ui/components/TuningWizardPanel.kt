package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.example.audio.AudioSystemConfig
import com.example.audio.ChannelCrossoverConfig
import com.example.audio.CrossoverTopology
import com.example.audio.FilterSlope
import com.example.audio.FilterType
import com.example.audio.OutputChannel
import com.example.audio.RtaDisplayMode
import com.example.audio.SpeakerGroupType
import com.example.audio.SpeakerMeasurementResult
import com.example.audio.SystemWayCount
import com.example.audio.TargetCurvePreset
import com.example.audio.TuningWizardEngine
import com.example.audio.TuningWizardUiState
import com.example.audio.WizardSpeakerSlot
import com.example.audio.WizardStep
import com.example.data.RtaSettings
import com.example.data.RtaSnapshotModel
import com.example.ui.GeneratorUiState
import com.example.ui.RtaLiveUiState
import com.example.ui.theme.LocalRtaColors
import kotlin.math.abs

/**
 * 4-Step Guided Car Audio Tuning Wizard ("Мастер настройки"):
 *  1. System configuration (2-way / 3-way, Active DSP / Passive Crossovers, Subwoofer Yes/No)
 *  2. DSP crossover entry per channel group (HPF/LPF freq, slope, filter type, sub phase)
 *  3. Sequential per-speaker RTA measurement with channel-routed pink noise & live spectrum
 *  4. Crossover-aware accurate recommendations + step-by-step AI tuning plan
 */
@Composable
fun TuningWizardPanel(
    wizardState: TuningWizardUiState,
    liveState: RtaLiveUiState,
    settings: RtaSettings,
    activeTargetCurve: TargetCurvePreset,
    generatorState: GeneratorUiState,
    snapshots: List<RtaSnapshotModel>,
    aiAdvisorState: AiAdvisorUiState,
    selectedHeadUnitProfile: HeadUnitEqProfile,
    completedAiSteps: Set<Int>,
    onSelectWizardStep: (WizardStep) -> Unit,
    onSetWayCount: (SystemWayCount) -> Unit,
    onSetCrossoverTopology: (CrossoverTopology) -> Unit,
    onSetHasSubwoofer: (Boolean) -> Unit,
    onUpdateChannelCrossover: (SpeakerGroupType, ChannelCrossoverConfig) -> Unit,
    onResetDefaultCrossovers: () -> Unit,
    onSelectSpeakerSlot: (Int) -> Unit,
    onStartMeasureCurrentSpeaker: () -> Unit,
    onToggleWizardPinkNoise: (OutputChannel) -> Unit,
    onClearSpeakerMeasurements: () -> Unit,
    onSelectHeadUnitProfile: (HeadUnitEqProfile) -> Unit,
    onAnalyzeWizardWithAi: () -> Unit,
    onAnalyzeSnapshotWithAi: (RtaSnapshotModel) -> Unit,
    onToggleAiStepCompleted: (Int) -> Unit,
    onResetAiChecklist: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rtaColors = LocalRtaColors.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 4-Step Horizontal Stepper
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            WizardStep.entries.forEach { step ->
                val isCurrent = wizardState.currentStep == step
                val isDone = when (step) {
                    WizardStep.SYSTEM_CONFIG -> wizardState.currentStep.index > 0
                    WizardStep.DSP_CROSSOVERS -> wizardState.currentStep.index > 1
                    WizardStep.SPEAKER_MEASUREMENT ->
                        wizardState.measurements.size == wizardState.speakerQueue.size && wizardState.speakerQueue.isNotEmpty()
                    WizardStep.RECOMMENDATIONS -> false
                }
                FilterChip(
                    selected = isCurrent,
                    onClick = { onSelectWizardStep(step) },
                    label = {
                        Text(
                            text = step.shortTitle,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                            )
                        )
                    },
                    leadingIcon = if (isDone) {
                        {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = rtaColors.emeraldOk,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = rtaColors.spectrumCyanGlow,
                        selectedLabelColor = rtaColors.spectrumCyan
                    ),
                    modifier = Modifier.testTag("wizard_step_chip_${step.index}")
                )
            }
        }

        // Step Body
        when (wizardState.currentStep) {
            WizardStep.SYSTEM_CONFIG -> SystemConfigStep(
                config = wizardState.systemConfig,
                queue = wizardState.speakerQueue,
                onSetWayCount = onSetWayCount,
                onSetTopology = onSetCrossoverTopology,
                onSetHasSubwoofer = onSetHasSubwoofer,
                onNext = { onSelectWizardStep(WizardStep.DSP_CROSSOVERS) },
                modifier = Modifier.weight(1f)
            )

            WizardStep.DSP_CROSSOVERS -> DspCrossoversStep(
                config = wizardState.systemConfig,
                onUpdateChannelCrossover = onUpdateChannelCrossover,
                onResetDefaults = onResetDefaultCrossovers,
                onBack = { onSelectWizardStep(WizardStep.SYSTEM_CONFIG) },
                onNext = { onSelectWizardStep(WizardStep.SPEAKER_MEASUREMENT) },
                modifier = Modifier.weight(1f)
            )

            WizardStep.SPEAKER_MEASUREMENT -> SpeakerMeasurementStep(
                wizardState = wizardState,
                liveState = liveState,
                settings = settings,
                activeTargetCurve = activeTargetCurve,
                generatorState = generatorState,
                onSelectSlot = onSelectSpeakerSlot,
                onStartMeasure = onStartMeasureCurrentSpeaker,
                onTogglePinkNoise = onToggleWizardPinkNoise,
                onClearAll = onClearSpeakerMeasurements,
                onBack = { onSelectWizardStep(WizardStep.DSP_CROSSOVERS) },
                onNext = { onSelectWizardStep(WizardStep.RECOMMENDATIONS) },
                modifier = Modifier.weight(1f)
            )

            WizardStep.RECOMMENDATIONS -> WizardRecommendationsStep(
                wizardState = wizardState,
                liveState = liveState,
                activeTargetCurve = activeTargetCurve,
                snapshots = snapshots,
                aiAdvisorState = aiAdvisorState,
                selectedHeadUnitProfile = selectedHeadUnitProfile,
                completedAiSteps = completedAiSteps,
                onSelectHeadUnitProfile = onSelectHeadUnitProfile,
                onAnalyzeWizardWithAi = onAnalyzeWizardWithAi,
                onAnalyzeSnapshotWithAi = onAnalyzeSnapshotWithAi,
                onToggleAiStepCompleted = onToggleAiStepCompleted,
                onResetAiChecklist = onResetAiChecklist,
                onBackToMeasure = { onSelectWizardStep(WizardStep.SPEAKER_MEASUREMENT) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// STEP 1: System Composition (2-way / 3-way, Active / Passive, Subwoofer)
// ---------------------------------------------------------------------------

@Composable
private fun SystemConfigStep(
    config: AudioSystemConfig,
    queue: List<WizardSpeakerSlot>,
    onSetWayCount: (SystemWayCount) -> Unit,
    onSetTopology: (CrossoverTopology) -> Unit,
    onSetHasSubwoofer: (Boolean) -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rtaColors = LocalRtaColors.current

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            SectionCard(title = stringResource(R.string.wizard_way_count_title)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SystemWayCount.entries.forEach { way ->
                        SelectableOptionCard(
                            title = way.label,
                            subtitle = way.description,
                            selected = config.wayCount == way,
                            onClick = { onSetWayCount(way) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("wizard_way_${way.name.lowercase()}")
                        )
                    }
                }
            }
        }

        item {
            SectionCard(title = stringResource(R.string.wizard_topology_title)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CrossoverTopology.entries.forEach { topo ->
                        SelectableOptionCard(
                            title = topo.label,
                            subtitle = topo.description,
                            selected = config.crossoverTopology == topo,
                            onClick = { onSetTopology(topo) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("wizard_topo_${topo.name.lowercase()}")
                        )
                    }
                }
            }
        }

        item {
            SectionCard(title = stringResource(R.string.wizard_subwoofer_title)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (config.hasSubwoofer) "Сабвуфер установлен" else "Система без сабвуфера",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (config.hasSubwoofer) {
                                "Будет добавлен отдельный замер сабвуфера и проверка стыка ФНЧ/ФВЧ и фазы."
                            } else {
                                "Фронтальные мидбасы будут играть ниже; советы исключат перегрузку суббасом."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = config.hasSubwoofer,
                        onCheckedChange = onSetHasSubwoofer,
                        colors = SwitchDefaults.colors(checkedThumbColor = rtaColors.spectrumCyan),
                        modifier = Modifier.testTag("wizard_subwoofer_switch")
                    )
                }
            }
        }

        // Resulting measurement queue preview
        item {
            SectionCard(title = "Порядок замера динамиков (${queue.size} шагов)") {
                Text(
                    text = "Мастер проведёт вас по всем каналам по порядку:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    queue.forEach { slot ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "${slot.orderIndex}. ${slot.shortLabel}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        item {
            Button(
                onClick = onNext,
                colors = ButtonDefaults.buttonColors(
                    containerColor = rtaColors.spectrumCyan,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("wizard_step1_next_btn")
            ) {
                Text(
                    text = stringResource(R.string.wizard_next_to_dsp),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// STEP 2: DSP Crossovers Specification (HPF / LPF, Slope, Filter Type, Phase)
// ---------------------------------------------------------------------------

@Composable
private fun DspCrossoversStep(
    config: AudioSystemConfig,
    onUpdateChannelCrossover: (SpeakerGroupType, ChannelCrossoverConfig) -> Unit,
    onResetDefaults: () -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rtaColors = LocalRtaColors.current
    val groups = config.activeChannelGroups()

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, rtaColors.hairline, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.wizard_dsp_hint_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.wizard_dsp_hint_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(
                    onClick = onResetDefaults,
                    modifier = Modifier.testTag("wizard_reset_xovers_btn")
                ) {
                    Text("Авто", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        items(groups, key = { it.name }) { group ->
            val xover = config.crossoverFor(group) ?: return@items
            ChannelCrossoverEditorCard(
                config = xover,
                onChange = { updated -> onUpdateChannelCrossover(group, updated) }
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("wizard_step2_back_btn")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Назад")
                }
                Button(
                    onClick = onNext,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = rtaColors.spectrumCyan,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .weight(1.4f)
                        .height(48.dp)
                        .testTag("wizard_step2_next_btn")
                ) {
                    Text(
                        text = stringResource(R.string.wizard_next_to_measure),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun ChannelCrossoverEditorCard(
    config: ChannelCrossoverConfig,
    onChange: (ChannelCrossoverConfig) -> Unit
) {
    val rtaColors = LocalRtaColors.current
    val freqs = TuningWizardEngine.STANDARD_DSP_FREQS_HZ

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, rtaColors.hairline, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = rtaColors.targetAmber,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = config.group.label,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (config.group == SpeakerGroupType.SUBWOOFER) {
                FilterChip(
                    selected = config.phaseInverted,
                    onClick = { onChange(config.copy(phaseInverted = !config.phaseInverted)) },
                    label = {
                        Text(
                            text = if (config.phaseInverted) "Фаза 180°" else "Фаза 0°",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    modifier = Modifier.testTag("xover_sub_phase_chip")
                )
            }
        }

        // HPF Block (ФВЧ / Сабсоник)
        val hpfTitle = if (config.group == SpeakerGroupType.SUBWOOFER) "ФВЧ (Сабсоник)" else "ФВЧ (HPF — срез снизу)"
        CrossoverFilterRow(
            title = hpfTitle,
            enabled = config.hpfEnabled,
            freqHz = config.hpfHz,
            slope = config.hpfSlope,
            filterType = config.hpfType,
            freqs = freqs,
            tagPrefix = "hpf_${config.group.name.lowercase()}",
            onToggleEnabled = { onChange(config.copy(hpfEnabled = it)) },
            onFreqChange = { onChange(config.copy(hpfHz = it)) },
            onSlopeChange = { onChange(config.copy(hpfSlope = it)) },
            onTypeChange = { onChange(config.copy(hpfType = it)) }
        )

        // LPF Block (ФНЧ)
        val lpfTitle = "ФНЧ (LPF — срез сверху)"
        CrossoverFilterRow(
            title = lpfTitle,
            enabled = config.lpfEnabled,
            freqHz = config.lpfHz,
            slope = config.lpfSlope,
            filterType = config.lpfType,
            freqs = freqs,
            tagPrefix = "lpf_${config.group.name.lowercase()}",
            onToggleEnabled = { onChange(config.copy(lpfEnabled = it)) },
            onFreqChange = { onChange(config.copy(lpfHz = it)) },
            onSlopeChange = { onChange(config.copy(lpfSlope = it)) },
            onTypeChange = { onChange(config.copy(lpfType = it)) }
        )
    }
}

@Composable
private fun CrossoverFilterRow(
    title: String,
    enabled: Boolean,
    freqHz: Float,
    slope: FilterSlope,
    filterType: FilterType,
    freqs: List<Float>,
    tagPrefix: String,
    onToggleEnabled: (Boolean) -> Unit,
    onFreqChange: (Float) -> Unit,
    onSlopeChange: (FilterSlope) -> Unit,
    onTypeChange: (FilterType) -> Unit
) {
    val rtaColors = LocalRtaColors.current
    val currentIdx = freqs.indices.minByOrNull { abs(freqs[it] - freqHz) } ?: 0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Switch(
                checked = enabled,
                onCheckedChange = onToggleEnabled,
                colors = SwitchDefaults.colors(checkedThumbColor = rtaColors.spectrumCyan),
                modifier = Modifier.testTag("${tagPrefix}_toggle")
            )
        }

        if (enabled) {
            // Frequency stepper
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Частота среза:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = { if (currentIdx > 0) onFreqChange(freqs[currentIdx - 1]) },
                        enabled = currentIdx > 0,
                        modifier = Modifier.testTag("${tagPrefix}_freq_minus")
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Уменьшить частоту", tint = rtaColors.spectrumCyan)
                    }
                    Text(
                        text = ChannelCrossoverConfig.formatHz(freqHz),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = rtaColors.spectrumCyan
                    )
                    IconButton(
                        onClick = { if (currentIdx < freqs.lastIndex) onFreqChange(freqs[currentIdx + 1]) },
                        enabled = currentIdx < freqs.lastIndex,
                        modifier = Modifier.testTag("${tagPrefix}_freq_plus")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Увеличить частоту", tint = rtaColors.spectrumCyan)
                    }
                }
            }

            // Slope selector chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterSlope.entries.forEach { s ->
                    FilterChip(
                        selected = slope == s,
                        onClick = { onSlopeChange(s) },
                        label = { Text("${s.dbPerOct} дБ", style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = rtaColors.targetAmber.copy(alpha = 0.2f),
                            selectedLabelColor = rtaColors.targetAmber
                        )
                    )
                }
            }

            // Filter type selector chips (LR / BW / BS)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterType.entries.forEach { ft ->
                    FilterChip(
                        selected = filterType == ft,
                        onClick = { onTypeChange(ft) },
                        label = { Text(ft.label, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// STEP 3: Sequential Per-Speaker Measurement
// ---------------------------------------------------------------------------

@Composable
private fun SpeakerMeasurementStep(
    wizardState: TuningWizardUiState,
    liveState: RtaLiveUiState,
    settings: RtaSettings,
    activeTargetCurve: TargetCurvePreset,
    generatorState: GeneratorUiState,
    onSelectSlot: (Int) -> Unit,
    onStartMeasure: () -> Unit,
    onTogglePinkNoise: (OutputChannel) -> Unit,
    onClearAll: () -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rtaColors = LocalRtaColors.current
    val queue = wizardState.speakerQueue
    val activeSlot = queue.getOrNull(wizardState.activeSlotIndex) ?: queue.first()
    val activeMeasurement = wizardState.measurements[activeSlot.id]
    val activeXover = wizardState.systemConfig.crossoverFor(activeSlot.group)
    val isPinkPlayingOnSlotChannel = generatorState.isPlaying &&
        generatorState.outputChannel == activeSlot.side.generatorChannel

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        // Sequential Speaker Queue Strip
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Динамик ${activeSlot.orderIndex} из ${queue.size} • Замерено: ${wizardState.measurements.size}/${queue.size}",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = rtaColors.spectrumCyan
                    )
                    if (wizardState.measurements.isNotEmpty()) {
                        TextButton(
                            onClick = onClearAll,
                            modifier = Modifier.testTag("wizard_clear_measurements_btn")
                        ) {
                            Text("Сбросить", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    queue.forEachIndexed { idx, slot ->
                        val measured = wizardState.measurements.containsKey(slot.id)
                        val selected = idx == wizardState.activeSlotIndex
                        FilterChip(
                            selected = selected,
                            onClick = { onSelectSlot(idx) },
                            label = {
                                Text(
                                    text = "${slot.orderIndex}. ${slot.shortLabel}",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            leadingIcon = if (measured) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Замерено",
                                        tint = rtaColors.emeraldOk,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = rtaColors.spectrumCyanGlow,
                                selectedLabelColor = rtaColors.spectrumCyan
                            ),
                            modifier = Modifier.testTag("wizard_speaker_chip_${slot.id}")
                        )
                    }
                }
            }
        }

        // Active Speaker Instruction & Passband Card
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, rtaColors.spectrumCyan.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speaker,
                            contentDescription = null,
                            tint = rtaColors.spectrumCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = activeSlot.label,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(rtaColors.targetAmber.copy(alpha = 0.16f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = activeSlot.side.label,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = rtaColors.targetAmber
                        )
                    }
                }

                Text(
                    text = activeSlot.instruction,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "Полоса DSP: ${activeXover?.formatPassbandSummary() ?: "20 Гц – 20 кГц (вся система)"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = rtaColors.targetAmber
                )

                // Live RTA Canvas showing live mic + already-captured curve for this speaker (if any)
                RtaSpectrumCanvas(
                    bands31Db = liveState.bands31Db,
                    peakHold31Db = null,
                    trace128Db = liveState.trace128Db,
                    targetCurve = activeTargetCurve,
                    targetReferenceDb = liveState.eqAnalysis.alignedReferenceDb,
                    showTargetCurve = settings.showTargetCurve,
                    targetToleranceDb = settings.targetToleranceDb,
                    overlaySnapshotBands31 = activeMeasurement?.bands31Db,
                    displayMode = RtaDisplayMode.BOTH,
                    showPeakHold = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(155.dp)
                )

                if (wizardState.isMeasuringSpeaker) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Усредняем АЧХ «${activeSlot.shortLabel}»… (${(wizardState.measurementProgress * 100).toInt()}%)",
                            style = MaterialTheme.typography.labelMedium,
                            color = rtaColors.spectrumCyan
                        )
                        LinearProgressIndicator(
                            progress = { wizardState.measurementProgress },
                            color = rtaColors.spectrumCyan,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                    }
                }

                // Controls: Optional Phone Pink Noise (L / R / L+R) + 3-sec Averaged Capture (works with Head Unit pink noise too!)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onTogglePinkNoise(activeSlot.side.generatorChannel) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("wizard_pink_noise_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = if (isPinkPlayingOnSlotChannel) rtaColors.peakCoral else rtaColors.spectrumCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isPinkPlayingOnSlotChannel) "Стоп шум" else "Тел. ${activeSlot.side.generatorChannel.label}",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }

                    Button(
                        onClick = onStartMeasure,
                        enabled = !wizardState.isMeasuringSpeaker,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = rtaColors.spectrumCyan,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .weight(1.35f)
                            .height(48.dp)
                            .testTag("wizard_measure_speaker_btn")
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (activeMeasurement != null) "Перемерить (3 с)" else "Замерить (3 с)",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }

                Text(
                    text = "Подсказка: включите розовый шум на ГУ вручную (или кнопкой «Тел.» выше) и нажмите «Замерить (3 с)». Последовательность: от ВЧ к сабвуферу.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (activeMeasurement != null) {
                    MeasuredSpeakerSummaryPill(result = activeMeasurement)
                }
            }
        }

        // Navigation footer
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("wizard_step3_back_btn")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Срезы")
                }

                if (wizardState.activeSlotIndex < queue.lastIndex) {
                    OutlinedButton(
                        onClick = { onSelectSlot(wizardState.activeSlotIndex + 1) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("wizard_next_speaker_btn")
                    ) {
                        Text("След. дин.")
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }

                Button(
                    onClick = onNext,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = rtaColors.emeraldOk,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .weight(1.2f)
                        .height(48.dp)
                        .testTag("wizard_to_results_btn")
                ) {
                    Text(
                        text = "К советам",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun MeasuredSpeakerSummaryPill(result: SpeakerMeasurementResult) {
    val rtaColors = LocalRtaColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(rtaColors.emeraldOk.copy(alpha = 0.14f))
            .border(1.dp, rtaColors.emeraldOk.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = rtaColors.emeraldOk, modifier = Modifier.size(16.dp))
            Text(
                text = "Замер сохранён",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = rtaColors.emeraldOk
            )
        }
        Text(
            text = String.format(
                java.util.Locale.US,
                "Сред. %.1f дБ • RMS ±%.1f дБ",
                result.passbandAvgSplDb,
                result.passbandRmsErrorDb
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// ---------------------------------------------------------------------------
// STEP 4: Accurate System & Crossover-Aware Recommendations + AI Plan
// ---------------------------------------------------------------------------

@Composable
private fun WizardRecommendationsStep(
    wizardState: TuningWizardUiState,
    liveState: RtaLiveUiState,
    activeTargetCurve: TargetCurvePreset,
    snapshots: List<RtaSnapshotModel>,
    aiAdvisorState: AiAdvisorUiState,
    selectedHeadUnitProfile: HeadUnitEqProfile,
    completedAiSteps: Set<Int>,
    onSelectHeadUnitProfile: (HeadUnitEqProfile) -> Unit,
    onAnalyzeWizardWithAi: () -> Unit,
    onAnalyzeSnapshotWithAi: (RtaSnapshotModel) -> Unit,
    onToggleAiStepCompleted: (Int) -> Unit,
    onResetAiChecklist: () -> Unit,
    onBackToMeasure: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rtaColors = LocalRtaColors.current
    val report = wizardState.analysisReport
    val cfg = wizardState.systemConfig

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // System summary header pill
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, rtaColors.hairline, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${cfg.wayCount.shortLabel} • ${cfg.crossoverTopology.shortLabel} • ${if (cfg.hasSubwoofer) "+ Саб" else "Без саба"}",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = rtaColors.spectrumCyan
                )
                Text(
                    text = "Замерено динамиков: ${wizardState.measurements.size} из ${wizardState.speakerQueue.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(
                onClick = onBackToMeasure,
                modifier = Modifier.testTag("wizard_back_to_measure_btn")
            ) {
                Text("К замерам", style = MaterialTheme.typography.labelMedium)
            }
        }

        // Sub-view toggle: System & Per-Speaker Diagnostics vs AI Step-by-Step Plan
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 20.dp)
        ) {
            // 1. Crossover & Junction Diagnostics
            if (report != null && report.crossoverDiagnostics.isNotEmpty()) {
                item {
                    SectionCard(title = stringResource(R.string.wizard_xover_diag_title)) {
                        report.crossoverDiagnostics.forEach { diag ->
                            val accent = if (diag.isWarning) rtaColors.peakCoral else rtaColors.emeraldOk
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(accent.copy(alpha = 0.12f))
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = if (diag.isWarning) Icons.Default.Warning else Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = diag.title,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = accent
                                    )
                                    Text(
                                        text = diag.detail,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. Channel Gain & L/R Balance Matching
            if (report != null && report.levelMatchTips.isNotEmpty()) {
                item {
                    SectionCard(title = stringResource(R.string.wizard_level_match_title)) {
                        report.levelMatchTips.forEach { tip ->
                            val accent = when {
                                tip.isWarning -> rtaColors.peakCoral
                                abs(tip.recommendedGainDb) >= 1.5f -> rtaColors.targetAmber
                                else -> rtaColors.emeraldOk
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = tip.title,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = String.format(java.util.Locale.US, "%+.1f дБ", tip.recommendedGainDb),
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = accent
                                    )
                                }
                                Text(
                                    text = tip.detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // 3. Per-Speaker Passband EQ Fixes (strictly scoped to each driver's DSP HPF..LPF)
            if (report != null && report.perSpeakerRecommendations.isNotEmpty()) {
                item {
                    SectionCard(title = stringResource(R.string.wizard_per_speaker_eq_title)) {
                        report.perSpeakerRecommendations.forEach { rec ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = rec.slotLabel,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = String.format(java.util.Locale.US, "RMS ±%.1f дБ", rec.passbandRmsErrorDb),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = rtaColors.spectrumCyan
                                    )
                                }
                                Text(
                                    text = rec.passbandText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = rtaColors.targetAmber
                                )
                                if (rec.topBandFixes.isEmpty()) {
                                    Text(
                                        text = "В своей рабочей полосе динамик соответствует целевой кривой в пределах допуска.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = rtaColors.emeraldOk
                                    )
                                } else {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        rec.topBandFixes.forEach { fix ->
                                            val c = if (fix.deltaDb < 0f) rtaColors.peakCoral else rtaColors.spectrumCyan
                                            Column(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(c.copy(alpha = 0.14f))
                                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text(
                                                    text = "${fix.freqLabel} Гц",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = String.format(java.util.Locale.US, "%+.1f дБ", fix.deltaDb),
                                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = c
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                item {
                    SectionCard(title = "Индивидуальные замеры не выполнены") {
                        Text(
                            text = "Вы можете вернуться на шаг 3 и замерить каждый динамик по порядку для точного баланса Л/П и расчёта в полосах DSP, либо сразу сформировать общий пошаговый план AI ниже.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 4. Embedded Step-by-Step AI Tuning Plan (with Firebase notice banner & head unit EQ selector)
            item {
                AiTuningAdvisorEmbeddedBlock(
                    uiState = aiAdvisorState,
                    selectedProfile = selectedHeadUnitProfile,
                    activeTargetCurve = activeTargetCurve,
                    hasReceivedSignal = liveState.hasReceivedSignal || wizardState.measurements.isNotEmpty(),
                    snapshots = snapshots,
                    completedSteps = completedAiSteps,
                    onSelectProfile = onSelectHeadUnitProfile,
                    onAnalyzeLive = onAnalyzeWizardWithAi,
                    onAnalyzeSnapshot = onAnalyzeSnapshotWithAi,
                    onToggleStepCompleted = onToggleAiStepCompleted,
                    onResetChecklist = onResetAiChecklist
                )
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    val rtaColors = LocalRtaColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, rtaColors.hairline, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        content()
    }
}

@Composable
private fun SelectableOptionCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rtaColors = LocalRtaColors.current
    val borderColor = if (selected) rtaColors.spectrumCyan else rtaColors.hairline
    val bgColor = if (selected) rtaColors.spectrumCyanGlow else MaterialTheme.colorScheme.surfaceVariant

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(if (selected) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = if (selected) rtaColors.spectrumCyan else MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
