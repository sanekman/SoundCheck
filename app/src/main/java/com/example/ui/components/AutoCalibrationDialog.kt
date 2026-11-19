package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.audio.AutoCalSignalSource
import com.example.audio.AutoCalStep
import com.example.audio.AutoCalUiState
import com.example.ui.theme.LocalRtaColors

@Composable
fun AutoCalibrationDialog(
    state: AutoCalUiState,
    activeMicName: String,
    isExternalMic: Boolean,
    liveSplDb: Float,
    live1kHzDb: Float,
    isPhoneNoisePlaying: Boolean,
    onDismiss: () -> Unit,
    onSetSignalSource: (AutoCalSignalSource) -> Unit,
    onToggleManualPhoneNoise: () -> Unit,
    onSetTargetSpl: (Float) -> Unit,
    onRunQuietCheck: () -> Unit,
    onProceedToSensitivity: () -> Unit,
    onRunSensitivity: () -> Unit,
    onRunFrSweep: () -> Unit,
    onSaveProfile: (String) -> Unit
) {
    if (state.step == AutoCalStep.IDLE) return

    val rtaColors = LocalRtaColors.current
    var profileName by remember(state.result?.profileName) {
        mutableStateOf(state.result?.profileName ?: "Автокалибровка RTA")
    }

    Dialog(
        onDismissRequest = { if (!state.isMeasuring) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .border(1.dp, rtaColors.hairline, RoundedCornerShape(20.dp))
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(rtaColors.spectrumCyanGlow),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoFixHigh,
                                contentDescription = "Автокалибровка",
                                tint = rtaColors.spectrumCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Автокалибровка RTA",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = activeMicName,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isExternalMic) rtaColors.emeraldOk else rtaColors.targetAmber
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        enabled = !state.isMeasuring,
                        modifier = Modifier.testTag("autocal_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Закрыть мастер калибровки",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 4-step progress stepper
                AutoCalStepper(currentStep = state.step)

                // Step content
                when (state.step) {
                    AutoCalStep.QUIET_CHECK -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VolumeOff,
                                    contentDescription = null,
                                    tint = rtaColors.spectrumCyan
                                )
                                Text(
                                    text = "Шаг 1: Проверка тихой среды",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = state.statusMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            val displayDb = state.measuredNoiseFloorDb ?: if (state.isMeasuring) state.currentAmbientDb else liveSplDb
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (state.measuredNoiseFloorDb != null) "Замеренный шум фона:" else "Текущий уровень фона:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = String.format(java.util.Locale.US, "%.1f дБ", displayDb),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = if (displayDb <= 55f) rtaColors.emeraldOk else rtaColors.targetAmber
                                )
                            }

                            if (state.isMeasuring) {
                                LinearProgressIndicator(
                                    progress = { state.progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = rtaColors.spectrumCyan
                                )
                            }

                            if (!state.isMeasuring && state.measuredNoiseFloorDb != null) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (state.isEnvironmentQuiet) Icons.Default.CheckCircle else Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = if (state.isEnvironmentQuiet) rtaColors.emeraldOk else rtaColors.targetAmber,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = if (state.isEnvironmentQuiet) "Тихая среда — готово к калибровке" else "Уровень шума повышен, но можно продолжить",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (state.isEnvironmentQuiet) rtaColors.emeraldOk else rtaColors.targetAmber
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onRunQuietCheck,
                                enabled = !state.isMeasuring,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("autocal_measure_quiet_btn")
                            ) {
                                Text(if (state.measuredNoiseFloorDb == null) "Замерить тишину" else "Повторить")
                            }
                            if (state.measuredNoiseFloorDb != null && !state.isMeasuring) {
                                Button(
                                    onClick = onProceedToSensitivity,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("autocal_next_sens_btn"),
                                    colors = ButtonDefaults.buttonColors(containerColor = rtaColors.spectrumCyan)
                                ) {
                                    Text("Далее →", color = MaterialTheme.colorScheme.onPrimary)
                                }
                            }
                        }
                    }

                    AutoCalStep.SENSITIVITY_MEASUREMENT -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = null,
                                    tint = rtaColors.targetAmber
                                )
                                Text(
                                    text = "Шаг 2: Чувствительность (1 кГц)",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Text(
                                text = "Источник тестового сигнала:",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            // 3-way Signal Source Selector: Head Unit Manual (default), Phone Generator, Pistonphone 94 dB
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    FilterChip(
                                        selected = state.signalSource == AutoCalSignalSource.HEAD_UNIT_MANUAL,
                                        onClick = { onSetSignalSource(AutoCalSignalSource.HEAD_UNIT_MANUAL) },
                                        enabled = !state.isMeasuring,
                                        label = { Text(AutoCalSignalSource.HEAD_UNIT_MANUAL.shortLabel) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("autocal_src_head_unit"),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = rtaColors.spectrumCyanGlow,
                                            selectedLabelColor = rtaColors.spectrumCyan
                                        )
                                    )
                                    FilterChip(
                                        selected = state.signalSource == AutoCalSignalSource.PHONE_GENERATOR,
                                        onClick = { onSetSignalSource(AutoCalSignalSource.PHONE_GENERATOR) },
                                        enabled = !state.isMeasuring,
                                        label = { Text(AutoCalSignalSource.PHONE_GENERATOR.shortLabel) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("autocal_src_phone"),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = rtaColors.emeraldDim,
                                            selectedLabelColor = rtaColors.emeraldOk
                                        )
                                    )
                                }
                                FilterChip(
                                    selected = state.signalSource == AutoCalSignalSource.ACOUSTIC_CALIBRATOR,
                                    onClick = { onSetSignalSource(AutoCalSignalSource.ACOUSTIC_CALIBRATOR) },
                                    enabled = !state.isMeasuring,
                                    label = { Text(AutoCalSignalSource.ACOUSTIC_CALIBRATOR.shortLabel) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("autocal_src_calibrator"),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = rtaColors.targetAmberGlow,
                                        selectedLabelColor = rtaColors.targetAmber
                                    )
                                )
                            }

                            Text(
                                text = state.statusMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Live signal level readout so user can dial Head Unit volume before capturing
                            LiveMicMonitorRow(
                                label = "Микрофон сейчас (1 кГц):",
                                primaryDb = live1kHzDb,
                                secondaryLabel = "Общий: ${String.format(java.util.Locale.US, "%.1f", liveSplDb)} дБ"
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Эталонный уровень SPL:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = String.format(java.util.Locale.US, "%.0f дБ SPL", state.targetReferenceSplDb),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = rtaColors.targetAmber
                                )
                            }
                            Slider(
                                value = state.targetReferenceSplDb,
                                onValueChange = onSetTargetSpl,
                                valueRange = 65f..114f,
                                enabled = !state.isMeasuring,
                                colors = SliderDefaults.colors(
                                    thumbColor = rtaColors.targetAmber,
                                    activeTrackColor = rtaColors.targetAmber
                                )
                            )

                            if (state.isMeasuring) {
                                LinearProgressIndicator(
                                    progress = { state.progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = rtaColors.targetAmber
                                )
                            }
                        }

                        // Optional manual test-tone trigger when using Phone Generator
                        if (state.signalSource == AutoCalSignalSource.PHONE_GENERATOR) {
                            OutlinedButton(
                                onClick = onToggleManualPhoneNoise,
                                enabled = !state.isMeasuring,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("autocal_manual_tone_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VolumeUp,
                                    contentDescription = null,
                                    tint = if (isPhoneNoisePlaying) rtaColors.peakCoral else rtaColors.spectrumCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isPhoneNoisePlaying) "Остановить тон 1 кГц (телефон)" else "Включить тон 1 кГц вручную (телефон)"
                                )
                            }
                        }

                        Button(
                            onClick = onRunSensitivity,
                            enabled = !state.isMeasuring,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("autocal_run_sens_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = rtaColors.spectrumCyan)
                        ) {
                            Text(
                                text = when {
                                    state.isMeasuring -> "Идёт замер 1 кГц..."
                                    state.signalSource == AutoCalSignalSource.HEAD_UNIT_MANUAL -> "Шум 1 кГц включён на ГУ — Начать замер"
                                    else -> "Измерить чувствительность"
                                },
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    AutoCalStep.FR_SWEEP_MEASUREMENT -> {
                        val isPhoneMode = state.signalSource == AutoCalSignalSource.PHONE_GENERATOR
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = null,
                                    tint = rtaColors.emeraldOk
                                )
                                Text(
                                    text = "Шаг 3: Коррекция АЧХ (20 Гц – 20 кГц)",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Text(
                                text = "Источник розового шума:",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = !isPhoneMode,
                                    onClick = { onSetSignalSource(AutoCalSignalSource.HEAD_UNIT_MANUAL) },
                                    enabled = !state.isMeasuring,
                                    label = { Text("ГУ (вручную)") },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("autocal_fr_src_head_unit"),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = rtaColors.spectrumCyanGlow,
                                        selectedLabelColor = rtaColors.spectrumCyan
                                    )
                                )
                                FilterChip(
                                    selected = isPhoneMode,
                                    onClick = { onSetSignalSource(AutoCalSignalSource.PHONE_GENERATOR) },
                                    enabled = !state.isMeasuring,
                                    label = { Text("Телефон (BT/AUX)") },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("autocal_fr_src_phone"),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = rtaColors.emeraldDim,
                                        selectedLabelColor = rtaColors.emeraldOk
                                    )
                                )
                            }

                            Text(
                                text = state.statusMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (isPhoneMode) {
                                    "Можно включить розовый шум вручную кнопкой ниже (чтобы выставить громкость), либо нажать «Запустить замер АЧХ» — приложение подаст шум (4.5 сек) автоматически."
                                } else {
                                    "Включите трек розового шума (20 Гц – 20 кГц) вручную на головном устройстве автомобиля (USB / CD / BT) на рабочей громкости, затем нажмите «Начать замер АЧХ» — телефон запишет 4.5 сек без запуска собственного генератора."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            LiveMicMonitorRow(
                                label = "Текущий уровень шума:",
                                primaryDb = liveSplDb,
                                secondaryLabel = "Цель: ~${state.targetReferenceSplDb.toInt()} дБ"
                            )

                            if (state.isMeasuring) {
                                LinearProgressIndicator(
                                    progress = { state.progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = rtaColors.emeraldOk
                                )
                            }
                        }

                        if (isPhoneMode) {
                            OutlinedButton(
                                onClick = onToggleManualPhoneNoise,
                                enabled = !state.isMeasuring,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("autocal_manual_pink_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VolumeUp,
                                    contentDescription = null,
                                    tint = if (isPhoneNoisePlaying) rtaColors.peakCoral else rtaColors.emeraldOk,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isPhoneNoisePlaying) "Остановить розовый шум (телефон)" else "Включить розовый шум вручную (телефон)"
                                )
                            }
                        }

                        Button(
                            onClick = onRunFrSweep,
                            enabled = !state.isMeasuring,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("autocal_run_fr_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = rtaColors.emeraldOk)
                        ) {
                            Text(
                                text = when {
                                    state.isMeasuring -> "Анализ спектра розового шума..."
                                    !isPhoneMode -> "Шум включён на ГУ — Начать замер АЧХ"
                                    else -> "Запустить замер АЧХ"
                                },
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    AutoCalStep.REVIEW_RESULT -> {
                        val res = state.result
                        if (res != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CalMetricBox(
                                    label = "Чувствит.",
                                    value = String.format(java.util.Locale.US, "%+.1f дБ", res.sensitivityOffsetDb),
                                    accent = rtaColors.spectrumCyan,
                                    modifier = Modifier.weight(1f)
                                )
                                CalMetricBox(
                                    label = "Шум фона",
                                    value = String.format(java.util.Locale.US, "%.1f дБ", res.noiseFloorDb),
                                    accent = rtaColors.targetAmber,
                                    modifier = Modifier.weight(1f)
                                )
                                CalMetricBox(
                                    label = "SNR",
                                    value = String.format(java.util.Locale.US, "%.0f дБ", res.estimatedSnrDb),
                                    accent = rtaColors.emeraldOk,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Text(
                                text = "Вычисленная кривая коррекции АЧХ (31 полоса)",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            CalibrationCurveCanvas(
                                points = res.correctionPoints,
                                height = 135.dp,
                                lineColor = rtaColors.emeraldOk
                            )

                            OutlinedTextField(
                                value = profileName,
                                onValueChange = { profileName = it },
                                label = { Text("Название профиля калибровки") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("autocal_profile_name_input")
                            )

                            Spacer(modifier = Modifier.height(2.dp))
                            Button(
                                onClick = { onSaveProfile(profileName) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("autocal_save_apply_btn"),
                                colors = ButtonDefaults.buttonColors(containerColor = rtaColors.emeraldOk)
                            ) {
                                Text(
                                    text = "Сохранить и применить калибровку",
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    AutoCalStep.IDLE -> Unit
                }
            }
        }
    }
}

@Composable
private fun LiveMicMonitorRow(
    label: String,
    primaryDb: Float,
    secondaryLabel: String
) {
    val rtaColors = LocalRtaColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, rtaColors.hairline, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = secondaryLabel,
                style = MaterialTheme.typography.labelSmall,
                color = rtaColors.targetAmber
            )
        }
        Text(
            text = String.format(java.util.Locale.US, "%.1f дБ", primaryDb),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = if (primaryDb >= 60f) rtaColors.emeraldOk else rtaColors.spectrumCyan
        )
    }
}

@Composable
private fun AutoCalStepper(currentStep: AutoCalStep) {
    val rtaColors = LocalRtaColors.current
    val steps = listOf(
        AutoCalStep.QUIET_CHECK to "1. Тишина",
        AutoCalStep.SENSITIVITY_MEASUREMENT to "2. 1 кГц",
        AutoCalStep.FR_SWEEP_MEASUREMENT to "3. АЧХ",
        AutoCalStep.REVIEW_RESULT to "4. Готово"
    )
    val currentIdx = steps.indexOfFirst { it.first == currentStep }.coerceAtLeast(0)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        steps.forEachIndexed { idx, (_, title) ->
            val active = idx == currentIdx
            val done = idx < currentIdx
            val barColor = when {
                done -> rtaColors.emeraldOk
                active -> rtaColors.spectrumCyan
                else -> rtaColors.hairline
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(barColor)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (active || done) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CalMetricBox(
    label: String,
    value: String,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = accent
        )
    }
}
