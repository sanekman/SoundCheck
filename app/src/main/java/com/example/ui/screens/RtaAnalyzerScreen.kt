package com.example.ui.screens

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.audio.AudioInputDeviceUi
import com.example.audio.AveragingMode
import com.example.audio.FrequencyWeighting
import com.example.audio.RtaBands
import com.example.audio.RtaDisplayMode
import com.example.audio.TargetCurvePreset
import com.example.audio.WaterfallFreqRange
import com.example.audio.WaterfallMode
import com.example.audio.WaterfallSignalSource
import com.example.audio.WaterfallUiState
import com.example.data.CalibrationProfileModel
import com.example.data.RtaSettings
import com.example.data.RtaSnapshotModel
import com.example.ui.GeneratorUiState
import com.example.ui.RtaLiveUiState
import com.example.ui.components.RtaSpectrumCanvas
import com.example.ui.components.WaterfallCanvas
import com.example.ui.components.WaterfallControlPanel
import com.example.ui.theme.LocalRtaColors

@Composable
fun RtaAnalyzerScreen(
    liveState: RtaLiveUiState,
    settings: RtaSettings,
    allTargetCurves: List<TargetCurvePreset>,
    activeTargetCurve: TargetCurvePreset,
    generatorState: GeneratorUiState,
    activeDevice: AudioInputDeviceUi?,
    activeCalibration: CalibrationProfileModel?,
    overlaySnapshot: RtaSnapshotModel?,
    isCapturing: Boolean,
    captureError: String?,
    hasMicPermission: Boolean,
    permissionDeniedOnce: Boolean,
    onRequestMicPermission: () -> Unit,
    onToggleCapture: () -> Unit,
    onResetPeaks: () -> Unit,
    onTogglePinkNoise: () -> Unit,
    onSaveSnapshot: (String, String) -> Unit,
    onExportCurrentPng: (String, String) -> Unit,
    onExportCurrentCsv: (String, String) -> Unit,
    onClearOverlay: () -> Unit,
    onOpenMicSetup: () -> Unit,
    onOpenAutoCal: () -> Unit,
    onOpenTargetManager: () -> Unit,
    onToggleShowTargetCurve: (Boolean) -> Unit,
    onSelectTargetCurve: (String) -> Unit,
    onSelectWeighting: (FrequencyWeighting) -> Unit,
    onSelectAveraging: (AveragingMode) -> Unit,
    onSelectDisplayMode: (RtaDisplayMode) -> Unit,
    onTogglePeakHold: (Boolean) -> Unit,
    waterfallState: WaterfallUiState,
    onSelectWaterfallMode: (WaterfallMode) -> Unit,
    onSelectWaterfallSource: (WaterfallSignalSource) -> Unit,
    onSelectWaterfallWindowMs: (Int) -> Unit,
    onSelectWaterfallFreqRange: (WaterfallFreqRange) -> Unit,
    onStartWaterfallCsd: () -> Unit,
    onTriggerWaterfallManualDecay: () -> Unit,
    onCancelWaterfallCapture: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rtaColors = LocalRtaColors.current
    var showSnapshotDialog by remember { mutableStateOf(false) }
    var showWaterfallView by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. Compact Studio Telemetry Header (Active Mic + Giant SPL Readout + Peak + Cal Badge)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, rtaColors.hairline, RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Mic Status Pill + Calibration Status
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (activeDevice?.isExternal == true) rtaColors.emeraldDim
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable { onOpenMicSetup() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("rta_mic_status_pill"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = if (activeDevice?.isExternal == true) Icons.Default.Usb else Icons.Default.Mic,
                        contentDescription = "Выбрать микрофон",
                        tint = if (activeDevice?.isExternal == true) rtaColors.emeraldOk else rtaColors.targetAmber,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = activeDevice?.name ?: "Микрофон не найден",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val calText = activeCalibration?.name ?: "Нет калибровки"
                    Text(
                        text = if (activeCalibration != null) "CAL: $calText" else "CAL: $calText",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (activeCalibration != null) rtaColors.emeraldOk else rtaColors.targetAmber,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clickable { onOpenAutoCal() }
                            .testTag("rta_cal_status_chip")
                    )
                    if (liveState.isClipping) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(rtaColors.peakCoral)
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.rta_clip_warning),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onError
                            )
                        }
                    }
                }
            }

            // Right: Live SPL + Peak Freq Readout
            Column(horizontalAlignment = Alignment.End) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (liveState.hasReceivedSignal) {
                            String.format(java.util.Locale.US, "%.1f", liveState.overallSplDb)
                        } else {
                            "--.-"
                        },
                        style = MaterialTheme.typography.displayMedium,
                        color = if (isCapturing) rtaColors.spectrumCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("rta_spl_readout")
                    )
                    Text(
                        text = "дБ${settings.weighting.code}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 5.dp)
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (liveState.peakFreqHz > 0f) {
                            "${stringResource(R.string.rta_peak_label)}: ${RtaBands.formatFrequencyHz(liveState.peakFreqHz)}"
                        } else {
                            "SQ: --%"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (liveState.hasReceivedSignal) {
                        Text(
                            text = "SQ ${liveState.eqAnalysis.matchScorePercent}%",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = when {
                                liveState.eqAnalysis.matchScorePercent >= 75 -> rtaColors.emeraldOk
                                liveState.eqAnalysis.matchScorePercent >= 50 -> rtaColors.targetAmber
                                else -> rtaColors.peakCoral
                            }
                        )
                    }
                }
            }
        }

        // 2. Permission or Error Banner (if mic permission not granted)
        if (!hasMicPermission) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(rtaColors.peakCoralDim)
                    .border(1.dp, rtaColors.peakCoral.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.MicOff,
                        contentDescription = null,
                        tint = rtaColors.peakCoral
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.permission_mic_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (permissionDeniedOnce) {
                                stringResource(R.string.permission_mic_denied_hint)
                            } else {
                                stringResource(R.string.permission_mic_rationale)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onRequestMicPermission,
                    colors = ButtonDefaults.buttonColors(containerColor = rtaColors.spectrumCyan),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("grant_mic_permission_button")
                ) {
                    Text(
                        text = "Разрешить",
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        } else if (captureError != null) {
            Text(
                text = captureError,
                style = MaterialTheme.typography.bodySmall,
                color = rtaColors.peakCoral,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(rtaColors.peakCoralDim)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        } else if (activeCalibration == null) {
            // Helpful one-tap Auto-Calibration prompt when external mic is uncalibrated
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, rtaColors.targetAmber.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                    .clickable { onOpenAutoCal() }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .testTag("rta_autocal_banner"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoFixHigh,
                        contentDescription = null,
                        tint = rtaColors.targetAmber,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Выполните автокалибровку внешнего RTA-микрофона в тихой среде",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "Старт →",
                    style = MaterialTheme.typography.labelMedium,
                    color = rtaColors.targetAmber
                )
            }
        }

        // 3. Viewport Mode Switcher (RTA АЧХ | Waterfall CSD) + Active Overlay Legend
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, rtaColors.hairline, RoundedCornerShape(10.dp))
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (!showWaterfallView) rtaColors.spectrumCyanGlow else MaterialTheme.colorScheme.surface)
                        .clickable { showWaterfallView = false }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                        .testTag("viewport_mode_rta")
                ) {
                    Text(
                        text = "RTA АЧХ",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (!showWaterfallView) rtaColors.spectrumCyan else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (showWaterfallView) rtaColors.spectrumCyanGlow else MaterialTheme.colorScheme.surface)
                        .clickable {
                            showWaterfallView = true
                            onSelectWaterfallMode(waterfallState.mode)
                        }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                        .testTag("viewport_mode_waterfall")
                ) {
                    Text(
                        text = "Waterfall",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (showWaterfallView) rtaColors.spectrumCyan else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!showWaterfallView) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LegendDot(color = rtaColors.spectrumCyan, label = "RTA")
                    if (settings.showTargetCurve) {
                        LegendDot(color = rtaColors.targetAmber, label = activeTargetCurve.shortName.take(8))
                    }
                    if (overlaySnapshot != null) {
                        LegendDot(color = rtaColors.snapshotViolet, label = overlaySnapshot.name.take(8))
                        IconButton(
                            onClick = onClearOverlay,
                            modifier = Modifier
                                .size(24.dp)
                                .testTag("rta_clear_overlay_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.rta_clear_overlay),
                                tint = rtaColors.snapshotViolet,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = if (waterfallState.mode == WaterfallMode.CSD_DECAY) "CSD 3D • ${waterfallState.windowMs} мс" else "Live 3D",
                    style = MaterialTheme.typography.labelSmall,
                    color = rtaColors.targetAmber
                )
            }
        }

        // 4. Prime Viewport: Real-Time Zoomable RTA Spectrum OR 3D Waterfall (CSD)
        if (!showWaterfallView) {
            RtaSpectrumCanvas(
                bands31Db = liveState.bands31Db,
                peakHold31Db = liveState.peakHold31Db,
                trace128Db = liveState.trace128Db,
                targetCurve = activeTargetCurve,
                targetReferenceDb = liveState.eqAnalysis.alignedReferenceDb,
                showTargetCurve = settings.showTargetCurve,
                targetToleranceDb = settings.targetToleranceDb,
                displayMode = settings.displayMode,
                showPeakHold = settings.peakHoldEnabled,
                overlaySnapshotBands31 = overlaySnapshot?.bands31Db,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        } else {
            val activeSlices = if (waterfallState.mode == WaterfallMode.CSD_DECAY) {
                waterfallState.csdResult?.slices ?: emptyList()
            } else {
                waterfallState.liveRollingSlices
            }
            WaterfallCanvas(
                slices = activeSlices,
                freqRange = waterfallState.freqRange,
                windowMs = if (waterfallState.mode == WaterfallMode.CSD_DECAY) {
                    waterfallState.csdResult?.windowMs ?: waterfallState.windowMs
                } else {
                    1600
                },
                bandDecayMs31 = if (waterfallState.mode == WaterfallMode.CSD_DECAY) {
                    waterfallState.csdResult?.bandDecayMs31
                } else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }

        if (showWaterfallView) {
            WaterfallControlPanel(
                state = waterfallState,
                liveSplDb = liveState.overallSplDb,
                onSelectMode = onSelectWaterfallMode,
                onSelectSignalSource = onSelectWaterfallSource,
                onSelectWindowMs = onSelectWaterfallWindowMs,
                onSelectFreqRange = onSelectWaterfallFreqRange,
                onStartCsdCapture = onStartWaterfallCsd,
                onTriggerManualDecay = onTriggerWaterfallManualDecay,
                onCancelCapture = onCancelWaterfallCapture
            )
        } else {
        // 5. Primary Action Bar (Start/Pause, Quick Pink Noise, Reset Peaks, Save Snapshot)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onToggleCapture,
                modifier = Modifier
                    .weight(1.35f)
                    .height(48.dp)
                    .testTag("rta_start_pause_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isCapturing) rtaColors.peakCoral else rtaColors.spectrumCyan
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = if (isCapturing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isCapturing) stringResource(R.string.rta_pause) else stringResource(R.string.rta_start),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.titleSmall
                )
            }

            OutlinedButton(
                onClick = onTogglePinkNoise,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("rta_quick_pink_noise_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (generatorState.isPlaying) rtaColors.targetAmberGlow else MaterialTheme.colorScheme.surface,
                    contentColor = if (generatorState.isPlaying) rtaColors.targetAmber else MaterialTheme.colorScheme.onSurface
                ),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.VolumeUp,
                    contentDescription = "Розовый шум",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (generatorState.isPlaying) "Стоп" else stringResource(R.string.rta_pink_quick),
                    style = MaterialTheme.typography.labelLarge
                )
            }

            OutlinedButton(
                onClick = onResetPeaks,
                modifier = Modifier
                    .height(48.dp)
                    .testTag("rta_reset_peaks_button"),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.rta_reset_peaks),
                    modifier = Modifier.size(18.dp)
                )
            }

            OutlinedButton(
                onClick = { showSnapshotDialog = true },
                modifier = Modifier
                    .height(48.dp)
                    .testTag("rta_save_snapshot_button"),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.BookmarkAdd,
                    contentDescription = stringResource(R.string.rta_save_snapshot),
                    tint = rtaColors.snapshotViolet,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // 6. Scrollable Target Curve Chips (Toggle + CSV Manager + Built-in & Custom CSV Curves)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = false,
                onClick = onOpenTargetManager,
                label = {
                    Text(
                        text = "Кривые / CSV",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = rtaColors.targetAmberGlow,
                    labelColor = rtaColors.targetAmber
                ),
                modifier = Modifier.testTag("open_target_curve_manager_chip")
            )

            FilterChip(
                selected = settings.showTargetCurve,
                onClick = { onToggleShowTargetCurve(!settings.showTargetCurve) },
                label = {
                    Text(
                        text = if (settings.showTargetCurve) "Цель: ВКЛ" else "Цель: ВЫКЛ",
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = rtaColors.emeraldDim,
                    selectedLabelColor = rtaColors.emeraldOk
                ),
                modifier = Modifier.testTag("toggle_target_curve_overlay_chip")
            )

            allTargetCurves.forEach { preset ->
                FilterChip(
                    selected = settings.showTargetCurve && settings.targetCurveId == preset.id,
                    onClick = { onSelectTargetCurve(preset.id) },
                    label = {
                        Text(
                            text = if (preset.isCustom) "${preset.shortName} (CSV)" else preset.shortName,
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = rtaColors.targetAmberGlow,
                        selectedLabelColor = rtaColors.targetAmber
                    ),
                    modifier = Modifier.testTag("target_curve_chip_${preset.id}")
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FrequencyWeighting.entries.forEach { w ->
                FilterChip(
                    selected = settings.weighting == w,
                    onClick = { onSelectWeighting(w) },
                    label = { Text("dB${w.code}", style = MaterialTheme.typography.labelMedium) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = rtaColors.spectrumCyanGlow,
                        selectedLabelColor = rtaColors.spectrumCyan
                    )
                )
            }
            AveragingMode.entries.forEach { mode ->
                FilterChip(
                    selected = settings.averagingMode == mode,
                    onClick = { onSelectAveraging(mode) },
                    label = { Text(mode.label, style = MaterialTheme.typography.labelMedium) }
                )
            }
            RtaDisplayMode.entries.forEach { dm ->
                FilterChip(
                    selected = settings.displayMode == dm,
                    onClick = { onSelectDisplayMode(dm) },
                    label = { Text(dm.label, style = MaterialTheme.typography.labelMedium) }
                )
            }
            FilterChip(
                selected = settings.peakHoldEnabled,
                onClick = { onTogglePeakHold(!settings.peakHoldEnabled) },
                label = { Text("Пики", style = MaterialTheme.typography.labelMedium) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = rtaColors.peakCoralDim,
                    selectedLabelColor = rtaColors.peakCoral
                )
            )
        }
        }
    }

    if (showSnapshotDialog) {
        SaveSnapshotDialog(
            onDismiss = { showSnapshotDialog = false },
            onSave = { name, channel ->
                onSaveSnapshot(name, channel)
                showSnapshotDialog = false
            },
            onExportPng = { name, channel ->
                onExportCurrentPng(name, channel)
                showSnapshotDialog = false
            },
            onExportCsv = { name, channel ->
                onExportCurrentCsv(name, channel)
                showSnapshotDialog = false
            }
        )
    }
}

@Composable
private fun LegendDot(color: androidx.compose.ui.graphics.Color, label: String) {
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

@Composable
private fun SaveSnapshotDialog(
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    onExportPng: (String, String) -> Unit,
    onExportCsv: (String, String) -> Unit
) {
    val rtaColors = LocalRtaColors.current
    val channels = listOf("Салон L+R", "Фронт Л", "Фронт П", "Сабвуфер", "Твитеры")
    var selectedChannel by remember { mutableStateOf(channels.first()) }
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.GraphicEq, contentDescription = null, tint = rtaColors.snapshotViolet)
                Text(stringResource(R.string.snapshot_dialog_title))
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.snapshot_name_label)) },
                    placeholder = { Text(stringResource(R.string.snapshot_name_placeholder)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("snapshot_name_input")
                )
                Text(
                    text = stringResource(R.string.snapshot_channel_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    channels.forEach { ch ->
                        FilterChip(
                            selected = selectedChannel == ch,
                            onClick = { selectedChannel = ch },
                            label = { Text(ch, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.export_section_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onExportPng(name, selectedChannel) },
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .testTag("snapshot_export_png_btn"),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.export_png_btn),
                            style = MaterialTheme.typography.labelMedium,
                            color = rtaColors.spectrumCyan
                        )
                    }
                    OutlinedButton(
                        onClick = { onExportCsv(name, selectedChannel) },
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .testTag("snapshot_export_csv_btn"),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.export_csv_btn),
                            style = MaterialTheme.typography.labelMedium,
                            color = rtaColors.emeraldOk
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, selectedChannel) },
                modifier = Modifier.testTag("snapshot_confirm_save_btn")
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
