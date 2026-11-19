package com.example.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.audio.OutputChannel
import com.example.audio.RtaBands
import com.example.audio.SignalType
import com.example.ui.GeneratorUiState
import com.example.ui.theme.LocalRtaColors
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin

@Composable
fun SignalGeneratorScreen(
    state: GeneratorUiState,
    onTogglePlay: () -> Unit,
    onSelectSignalType: (SignalType) -> Unit,
    onSelectChannel: (OutputChannel) -> Unit,
    onGainChange: (Float) -> Unit,
    onSineFreqChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val rtaColors = LocalRtaColors.current
    val infiniteTransition = rememberInfiniteTransition(label = "gen_wave")
    val phaseShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 20.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.gen_screen_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Подача тестового сигнала на ГУ через Bluetooth / AUX / USB",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Animated Waveform Preview + Giant Tactile Play/Stop Button
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        width = 1.dp,
                        color = if (state.isPlaying) rtaColors.spectrumCyan else rtaColors.hairline,
                        shape = RoundedCornerShape(18.dp)
                    )
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Live Signal Oscilloscope Preview
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(82.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    val midY = size.height / 2f
                    drawLine(
                        color = rtaColors.gridMajor,
                        start = Offset(0f, midY),
                        end = Offset(size.width, midY),
                        strokeWidth = 1.dp.toPx()
                    )

                    val amp = if (state.isPlaying) {
                        val normGain = ((state.gainDb + 40f) / 40f).coerceIn(0.15f, 1f)
                        size.height * 0.36f * normGain
                    } else {
                        size.height * 0.08f
                    }

                    val path = Path()
                    val steps = 140
                    val activePhase = if (state.isPlaying) phaseShift else 0f
                    for (i in 0..steps) {
                        val t = i.toFloat() / steps
                        val x = t * size.width
                        val yOffset = when (state.signalType) {
                            SignalType.SINE -> sin(t * 6f * PI.toFloat() + activePhase) * amp
                            SignalType.LOG_SWEEP -> sin(t * t * 18f * PI.toFloat() + activePhase) * amp
                            SignalType.PINK_NOISE -> (
                                sin(t * 17f + activePhase * 3f) * 0.45f +
                                    sin(t * 41f - activePhase * 5f) * 0.35f +
                                    sin(t * 83f + activePhase * 7f) * 0.2f
                                ) * amp
                            SignalType.WHITE_NOISE -> (
                                sin(t * 53f + activePhase * 6f) * 0.4f +
                                    sin(t * 127f - activePhase * 9f) * 0.35f +
                                    sin(t * 211f + activePhase * 4f) * 0.25f
                                ) * amp
                            SignalType.POLARITY -> {
                                val mod = ((t * 3f + activePhase / (2f * PI.toFloat())) % 1f)
                                when {
                                    mod < 0.08f -> -sin(mod / 0.08f * PI.toFloat()) * amp * 1.1f
                                    mod < 0.22f -> sin((mod - 0.08f) / 0.14f * PI.toFloat()) * amp * 0.25f
                                    else -> 0f
                                }
                            }
                        }
                        val y = midY + yOffset
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }

                    drawPath(
                        path = path,
                        color = if (state.isPlaying) rtaColors.spectrumCyan else rtaColors.spectrumCyanDim,
                        style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                Button(
                    onClick = onTogglePlay,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("generator_play_stop_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.isPlaying) rtaColors.peakCoral else rtaColors.spectrumCyan
                    )
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (state.isPlaying) stringResource(R.string.gen_stop) else stringResource(R.string.gen_play),
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }

        // Channel Routing Selector (L+R, Left, Right)
        item {
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
                    text = stringResource(R.string.gen_channel_label),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutputChannel.entries.forEach { ch ->
                        FilterChip(
                            selected = state.outputChannel == ch,
                            onClick = { onSelectChannel(ch) },
                            label = {
                                Text(
                                    text = ch.label,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("gen_channel_${ch.name.lowercase()}"),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = rtaColors.spectrumCyanGlow,
                                selectedLabelColor = rtaColors.spectrumCyan
                            )
                        )
                    }
                }
            }
        }

        // Safe Output Level Attenuator (-40 dB to 0 dB)
        item {
            val isLoud = state.gainDb > -6f
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        1.dp,
                        if (isLoud) rtaColors.peakCoral else rtaColors.hairline,
                        RoundedCornerShape(14.dp)
                    )
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.gen_level_label),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = String.format(java.util.Locale.US, "%.1f дБ FS", state.gainDb),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (isLoud) rtaColors.peakCoral else rtaColors.targetAmber
                    )
                }
                Slider(
                    value = state.gainDb,
                    onValueChange = onGainChange,
                    valueRange = -40f..0f,
                    modifier = Modifier.testTag("gen_gain_slider"),
                    colors = SliderDefaults.colors(
                        thumbColor = if (isLoud) rtaColors.peakCoral else rtaColors.targetAmber,
                        activeTrackColor = if (isLoud) rtaColors.peakCoral else rtaColors.targetAmber
                    )
                )
                if (isLoud) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = rtaColors.peakCoral,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(R.string.gen_level_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = rtaColors.peakCoral
                        )
                    }
                }
            }
        }

        // Sine Frequency Control (shown when SINE is active)
        if (state.signalType == SignalType.SINE) {
            item {
                val logMin = log10(20.0).toFloat()
                val logMax = log10(20000.0).toFloat()
                val sliderVal = ((log10(state.sineFreqHz.toDouble()).toFloat() - logMin) / (logMax - logMin)).coerceIn(0f, 1f)
                val presets = listOf(40f, 63f, 80f, 125f, 250f, 500f, 1000f, 3150f, 10000f)

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
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.gen_freq_label),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = RtaBands.formatFrequencyHz(state.sineFreqHz),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = rtaColors.spectrumCyan
                        )
                    }
                    Slider(
                        value = sliderVal,
                        onValueChange = { t ->
                            val hz = 10.0.pow((logMin + t * (logMax - logMin)).toDouble()).toFloat()
                            onSineFreqChange(hz)
                        },
                        valueRange = 0f..1f,
                        modifier = Modifier.testTag("gen_sine_freq_slider"),
                        colors = SliderDefaults.colors(
                            thumbColor = rtaColors.spectrumCyan,
                            activeTrackColor = rtaColors.spectrumCyan
                        )
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        presets.forEach { hz ->
                            FilterChip(
                                selected = kotlin.math.abs(state.sineFreqHz - hz) < hz * 0.05f,
                                onClick = { onSineFreqChange(hz) },
                                label = { Text("${RtaBands.formatFrequencyShort(hz)} Гц", style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            }
        }

        // Signal Type Selection Cards
        item {
            Text(
                text = "Тип тестового сигнала",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        items(SignalType.entries.size) { index ->
            val type = SignalType.entries[index]
            val selected = state.signalType == type
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) rtaColors.spectrumCyanGlow else MaterialTheme.colorScheme.surface)
                    .border(
                        width = 1.dp,
                        color = if (selected) rtaColors.spectrumCyan else rtaColors.hairline,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable { onSelectSignalType(type) }
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .testTag("gen_signal_type_${type.name.lowercase()}"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = if (selected) rtaColors.spectrumCyan else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column {
                        Text(
                            text = type.label,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (selected) rtaColors.spectrumCyan else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = type.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (selected) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(rtaColors.spectrumCyan)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "Выбран",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}
