package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.ResonanceHotspot
import com.example.audio.RtaBands
import com.example.audio.WaterfallCapturePhase
import com.example.audio.WaterfallFreqRange
import com.example.audio.WaterfallMode
import com.example.audio.WaterfallSignalSource
import com.example.audio.WaterfallSlice
import com.example.audio.WaterfallUiState
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalRtaColors
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * 3D Isometric Cumulative Spectral Decay (Waterfall / CSD) Canvas with pinch-to-zoom,
 * painter's-algorithm slice occlusion, and interactive frequency decay inspection.
 */
@Composable
fun WaterfallCanvas(
    slices: List<WaterfallSlice>,
    freqRange: WaterfallFreqRange,
    windowMs: Int,
    bandDecayMs31: FloatArray?,
    minDb: Float = 30f,
    maxDb: Float = 100f,
    modifier: Modifier = Modifier
) {
    val rtaColors = LocalRtaColors.current
    val surfaceColor = MaterialTheme.colorScheme.surface
    val bgColor = MaterialTheme.colorScheme.background
    val textMuted = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()

    var inspectedBandIdx by remember { mutableStateOf<Int?>(null) }
    var freqZoom by remember(freqRange) { mutableFloatStateOf(1f) }
    var panFraction by remember(freqRange) { mutableFloatStateOf(0f) }

    val latestZoom by rememberUpdatedState(freqZoom)
    val latestPan by rememberUpdatedState(panFraction)

    val baseLogMin = remember(freqRange) { log10(freqRange.minHz.toDouble()).toFloat() }
    val baseLogMax = remember(freqRange) { log10(freqRange.maxHz.toDouble()).toFloat() }
    val baseLogSpan = remember(freqRange) { (baseLogMax - baseLogMin).coerceAtLeast(0.1f) }

    val visibleLogSpan = baseLogSpan / freqZoom
    val visibleLogMin = baseLogMin + panFraction * baseLogSpan
    val visibleLogMax = visibleLogMin + visibleLogSpan
    val visibleMinHz = 10.0.pow(visibleLogMin.toDouble()).toFloat()
    val visibleMaxHz = 10.0.pow(visibleLogMax.toDouble()).toFloat()

    val labelStyle = remember(textMuted) {
        TextStyle(
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 9.sp,
            color = textMuted
        )
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.verticalGradient(colors = listOf(surfaceColor, bgColor)))
            .border(1.dp, rtaColors.hairline, RoundedCornerShape(16.dp))
            .testTag("waterfall_canvas")
    ) {
        if (slices.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Waves,
                    contentDescription = null,
                    tint = rtaColors.spectrumCyan.copy(alpha = 0.6f),
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Нет данных Waterfall (CSD)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Запустите замер CSD (с ГУ или телефона) либо включите Live Водопад",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(freqRange) {
                        detectTapGestures(
                            onTap = { offset ->
                                val leftPad = 30.dp.toPx()
                                val rightPad = 24.dp.toPx()
                                val plotW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
                                val relX = ((offset.x - leftPad) / plotW).coerceIn(0f, 1f)
                                val curSpan = baseLogSpan / latestZoom
                                val curMin = baseLogMin + latestPan * baseLogSpan
                                val freq = 10.0.pow((curMin + relX * curSpan).toDouble()).toFloat()
                                val idx = RtaBands.ISO_31_FREQS.indices.minByOrNull {
                                    abs(RtaBands.ISO_31_FREQS[it] - freq)
                                }
                                inspectedBandIdx = if (inspectedBandIdx == idx) null else idx
                            },
                            onDoubleTap = {
                                freqZoom = 1f
                                panFraction = 0f
                            }
                        )
                    }
                    .pointerInput(freqRange) {
                        val leftPadPx = 30.dp.toPx()
                        val rightPadPx = 24.dp.toPx()
                        val touchSlop = viewConfiguration.touchSlop

                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            var zoomAccum = 1f
                            var panAccum = Offset.Zero
                            var pastSlop = false

                            do {
                                val event = awaitPointerEvent()
                                val active = event.changes.filter { it.pressed }
                                if (active.isEmpty()) break

                                val z = event.calculateZoom()
                                val p = event.calculatePan()
                                val count = active.size

                                if (!pastSlop) {
                                    zoomAccum *= z
                                    panAccum += p
                                    if (count > 1 && (abs(1f - zoomAccum) * 180f > touchSlop || panAccum.getDistance() > touchSlop)) {
                                        pastSlop = true
                                    } else if (panAccum.getDistance() > touchSlop) {
                                        if (abs(panAccum.x) >= abs(panAccum.y) * 0.9f && latestZoom > 1.02f) {
                                            pastSlop = true
                                        } else {
                                            break
                                        }
                                    }
                                }

                                if (pastSlop) {
                                    val plotW = (size.width - leftPadPx - rightPadPx).coerceAtLeast(1f)
                                    if (count > 1 || abs(z - 1f) > 0.002f) {
                                        val centroid = event.calculateCentroid(useCurrent = true)
                                        val anchorRelX = ((centroid.x - leftPadPx) / plotW).coerceIn(0f, 1f)
                                        val oldZoom = latestZoom
                                        val oldPan = latestPan
                                        val oldSpan = 1f / oldZoom
                                        val anchorFrac = oldPan + anchorRelX * oldSpan

                                        val newZoom = (oldZoom * z).coerceIn(1f, 6f)
                                        val newSpan = 1f / newZoom
                                        val panDeltaFrac = -p.x / plotW * newSpan
                                        val maxPan = (1f - newSpan).coerceAtLeast(0f)
                                        freqZoom = newZoom
                                        panFraction = (anchorFrac - anchorRelX * newSpan + panDeltaFrac).coerceIn(0f, maxPan)
                                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                                    } else if (latestZoom > 1.02f) {
                                        val span = 1f / latestZoom
                                        val maxPan = (1f - span).coerceAtLeast(0f)
                                        panFraction = (latestPan - p.x / plotW * span).coerceIn(0f, maxPan)
                                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                                    }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
            ) {
                val leftPad = 30.dp.toPx()
                val rightPad = 24.dp.toPx()
                val topPad = 14.dp.toPx()
                val bottomPad = 24.dp.toPx()
                val totalW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
                val totalH = (size.height - topPad - bottomPad).coerceAtLeast(1f)

                // Isometric 3D perspective depth offsets:
                // Back slice (t = 0 ms) sits top-right; front slice (t = windowMs) sits bottom-left
                val depthShiftX = (totalW * 0.10f).coerceAtMost(36.dp.toPx())
                val depthShiftY = (totalH * 0.42f).coerceAtLeast(24.dp.toPx())
                val sliceW = (totalW - depthShiftX).coerceAtLeast(1f)
                val ampH = (totalH - depthShiftY).coerceAtLeast(1f)

                val sliceCount = slices.size.coerceAtLeast(1)

                fun projectX(hz: Float, depthFrac: Float): Float {
                    // depthFrac: 0f = back (0 ms), 1f = front (windowMs)
                    val f = hz.coerceIn(20f, 20000f)
                    val t = (log10(f.toDouble()).toFloat() - visibleLogMin) / visibleLogSpan
                    val xOrigin = leftPad + (1f - depthFrac) * depthShiftX
                    return xOrigin + t * sliceW
                }

                fun projectY(db: Float, depthFrac: Float): Float {
                    val norm = ((db - minDb) / (maxDb - minDb)).coerceIn(0f, 1f)
                    val baselineY = topPad + ampH + depthFrac * depthShiftY
                    return baselineY - norm * ampH
                }

                // 1. Back-wall amplitude grid lines (at depthFrac = 0)
                val backLeft = leftPad + depthShiftX
                val backRight = backLeft + sliceW
                var dbTick = (minDb.toInt() / 10) * 10
                if (dbTick < minDb) dbTick += 10
                while (dbTick <= maxDb) {
                    val y = projectY(dbTick.toFloat(), 0f)
                    drawLine(
                        color = if (dbTick % 20 == 0) rtaColors.gridMajor else rtaColors.gridMinor,
                        start = Offset(backLeft, y),
                        end = Offset(backRight, y),
                        strokeWidth = 0.7.dp.toPx()
                    )
                    val measured = textMeasurer.measure("$dbTick", style = labelStyle)
                    drawText(
                        textLayoutResult = measured,
                        topLeft = Offset(
                            x = (leftPad - measured.size.width - 3.dp.toPx()).coerceAtLeast(2f),
                            y = y - measured.size.height / 2f
                        )
                    )
                    dbTick += 10
                }

                // 2. Side depth time axis (right edge: 0 ms at back -> windowMs at front)
                val timeLabels = listOf(0f, 0.5f, 1f)
                timeLabels.forEach { d ->
                    val x = leftPad + (1f - d) * depthShiftX + sliceW
                    val y = topPad + ampH + d * depthShiftY
                    val msLabel = "${(d * windowMs).roundToInt()}мс"
                    val measured = textMeasurer.measure(msLabel, style = labelStyle)
                    drawText(
                        textLayoutResult = measured,
                        topLeft = Offset(
                            x = (x + 2.dp.toPx()).coerceAtMost(size.width - measured.size.width - 2f),
                            y = y - measured.size.height / 2f
                        )
                    )
                }

                // 3. Front frequency ticks (along depthFrac = 1f baseline)
                var lastLabelRightX = -1000f
                val minGap = 6.dp.toPx()
                RtaBands.ISO_31_BANDS.forEach { b ->
                    val xFront = projectX(b.nominalHz, 1f)
                    if (xFront >= leftPad - 1f && xFront <= leftPad + sliceW + 1f) {
                        // Subtle floor grid line receding from front to back
                        val xBack = projectX(b.nominalHz, 0f)
                        drawLine(
                            color = rtaColors.gridMinor.copy(alpha = 0.45f),
                            start = Offset(xFront, topPad + ampH + depthShiftY),
                            end = Offset(xBack, topPad + ampH),
                            strokeWidth = 0.6.dp.toPx()
                        )
                        val shortLbl = when {
                            b.nominalHz >= 1000f -> {
                                val k = b.nominalHz / 1000f
                                if (k == k.toInt().toFloat()) "${k.toInt()}k"
                                else String.format(java.util.Locale.US, "%.1fk", k)
                            }
                            b.nominalHz == 31.5f -> "31"
                            else -> "${b.nominalHz.toInt()}"
                        }
                        val measured = textMeasurer.measure(shortLbl, style = labelStyle)
                        val tx = (xFront - measured.size.width / 2f)
                            .coerceIn(leftPad, leftPad + sliceW - measured.size.width)
                        if (tx >= lastLabelRightX + minGap) {
                            drawText(
                                textLayoutResult = measured,
                                topLeft = Offset(tx, topPad + ampH + depthShiftY + 4.dp.toPx())
                            )
                            lastLabelRightX = tx + measured.size.width
                        }
                    }
                }

                // 4. Painter's Algorithm Back-to-Front 3D Waterfall Slices
                clipRect(
                    left = leftPad - 1f,
                    top = topPad - 2f,
                    right = leftPad + totalW + 2f,
                    bottom = topPad + totalH + 2f
                ) {
                    val backColor = rtaColors.targetAmber
                    val midColor = rtaColors.spectrumCyan
                    val frontColor = rtaColors.snapshotViolet

                    slices.forEachIndexed { idx, slice ->
                        val depthFrac = if (sliceCount > 1) idx.toFloat() / (sliceCount - 1) else 0f
                        val baselineY = topPad + ampH + depthFrac * depthShiftY

                        val strokeColor = if (depthFrac < 0.45f) {
                            lerp(backColor, midColor, depthFrac / 0.45f)
                        } else {
                            lerp(midColor, frontColor, (depthFrac - 0.45f) / 0.55f)
                        }

                        val linePath = Path()
                        val maskPath = Path()
                        val trace = slice.trace128Db
                        var firstX = 0f
                        var lastX = 0f

                        for (i in trace.indices) {
                            val freq = RtaBands.LOG_TRACE_FREQS[i]
                            val x = projectX(freq, depthFrac)
                            val y = projectY(trace[i], depthFrac)
                            if (i == 0) {
                                firstX = x
                                linePath.moveTo(x, y)
                                maskPath.moveTo(x, baselineY)
                                maskPath.lineTo(x, y)
                            } else {
                                linePath.lineTo(x, y)
                                maskPath.lineTo(x, y)
                            }
                            lastX = x
                        }
                        maskPath.lineTo(lastX, baselineY)
                        maskPath.close()

                        // Opaque dark occlusion fill hides ridges behind this time slice
                        drawPath(
                            path = maskPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    strokeColor.copy(alpha = 0.24f),
                                    bgColor.copy(alpha = 0.94f)
                                ),
                                startY = baselineY - ampH,
                                endY = baselineY
                            )
                        )

                        // Crisp glowing ridge line
                        drawPath(
                            path = linePath,
                            color = strokeColor.copy(alpha = (1f - depthFrac * 0.22f).coerceIn(0.65f, 1f)),
                            style = Stroke(
                                width = if (idx == 0 || idx == sliceCount - 1) 1.8.dp.toPx() else 1.25.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }

                    // Inspected frequency guide ridge from back (0 ms) to front (windowMs)
                    inspectedBandIdx?.let { bandIdx ->
                        val freq = RtaBands.ISO_31_FREQS[bandIdx]
                        val ridgePath = Path()
                        slices.forEachIndexed { idx, slice ->
                            val d = if (sliceCount > 1) idx.toFloat() / (sliceCount - 1) else 0f
                            val x = projectX(freq, d)
                            val y = projectY(slice.bands31Db[bandIdx], d)
                            if (idx == 0) ridgePath.moveTo(x, y) else ridgePath.lineTo(x, y)
                        }
                        drawPath(
                            path = ridgePath,
                            color = Color.White,
                            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }
            }
        }

        // Top-left Zoom HUD pill when zoomed
        if (freqZoom > 1.02f) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 34.dp, top = 8.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(bgColor.copy(alpha = 0.88f))
                    .border(1.dp, rtaColors.spectrumCyan.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${RtaBands.formatFrequencyHz(visibleMinHz)}–${RtaBands.formatFrequencyHz(visibleMaxHz)} • ${
                        String.format(java.util.Locale.US, "%.1f×", freqZoom)
                    }",
                    style = MaterialTheme.typography.labelSmall,
                    color = rtaColors.spectrumCyan
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(rtaColors.spectrumCyanGlow)
                        .clickable {
                            freqZoom = 1f
                            panFraction = 0f
                        }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .testTag("waterfall_zoom_reset_btn")
                ) {
                    Text(
                        text = "1×",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = rtaColors.spectrumCyan
                    )
                }
            }
        }

        // Top-right inspected frequency decay HUD
        inspectedBandIdx?.let { idx ->
            if (slices.isNotEmpty()) {
                val freq = RtaBands.ISO_31_FREQS[idx]
                val startDb = slices.first().bands31Db[idx]
                val endDb = slices.last().bands31Db[idx]
                val dropDb = (startDb - endDb).coerceAtLeast(0f)
                val decayMs = bandDecayMs31?.getOrNull(idx)

                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, end = 10.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(bgColor.copy(alpha = 0.90f))
                        .border(1.dp, rtaColors.hairline, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Waterfall • ${RtaBands.formatFrequencyHz(freq)}",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = String.format(
                            java.util.Locale.US,
                            "Спад за %d мс: -%.1f дБ",
                            windowMs,
                            dropDb
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = rtaColors.spectrumCyan
                    )
                    if (decayMs != null && decayMs > 0f) {
                        Text(
                            text = "Затухание ~${decayMs.roundToInt()} мс",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (decayMs > 210f) rtaColors.peakCoral else rtaColors.emeraldOk
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WaterfallControlPanel(
    state: WaterfallUiState,
    liveSplDb: Float,
    onSelectMode: (WaterfallMode) -> Unit,
    onSelectSignalSource: (WaterfallSignalSource) -> Unit,
    onSelectWindowMs: (Int) -> Unit,
    onSelectFreqRange: (WaterfallFreqRange) -> Unit,
    onStartCsdCapture: () -> Unit,
    onTriggerManualDecay: () -> Unit,
    onCancelCapture: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rtaColors = LocalRtaColors.current
    val isBusy = state.phase == WaterfallCapturePhase.WAITING_SIGNAL ||
        state.phase == WaterfallCapturePhase.SIGNAL_READY_WAIT_MUTE ||
        state.phase == WaterfallCapturePhase.PLAYING_PHONE_BURST ||
        state.phase == WaterfallCapturePhase.CAPTURING_DECAY

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Primary Action Bar for CSD Mode or Live indicator
        if (state.mode == WaterfallMode.CSD_DECAY) {
            // Status / Progress instruction card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        1.dp,
                        when (state.phase) {
                            WaterfallCapturePhase.SIGNAL_READY_WAIT_MUTE -> rtaColors.emeraldOk.copy(alpha = 0.65f)
                            WaterfallCapturePhase.CAPTURING_DECAY -> rtaColors.targetAmber.copy(alpha = 0.65f)
                            else -> rtaColors.hairline
                        },
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when (state.phase) {
                            WaterfallCapturePhase.WAITING_SIGNAL -> "Ожидание шума с ГУ..."
                            WaterfallCapturePhase.SIGNAL_READY_WAIT_MUTE -> "Сигнал найден! Нажмите ПАУЗУ на ГУ"
                            WaterfallCapturePhase.PLAYING_PHONE_BURST -> "Генерация шума с телефона..."
                            WaterfallCapturePhase.CAPTURING_DECAY -> "Запись затухания (${state.windowMs} мс)..."
                            WaterfallCapturePhase.COMPLETED -> "Замер Waterfall (CSD) готов"
                            WaterfallCapturePhase.IDLE -> "Замер затухания (CSD)"
                        },
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = when (state.phase) {
                            WaterfallCapturePhase.SIGNAL_READY_WAIT_MUTE -> rtaColors.emeraldOk
                            WaterfallCapturePhase.CAPTURING_DECAY -> rtaColors.targetAmber
                            else -> rtaColors.spectrumCyan
                        }
                    )
                    Text(
                        text = String.format(java.util.Locale.US, "%.1f дБ", liveSplDb),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = state.statusMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (isBusy) {
                    LinearProgressIndicator(
                        progress = { state.progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = rtaColors.spectrumCyan
                    )
                }
            }

            // Capture / Trigger / Stop buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!isBusy) {
                    Button(
                        onClick = onStartCsdCapture,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("waterfall_start_csd_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = rtaColors.spectrumCyan),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (state.signalSource == WaterfallSignalSource.HEAD_UNIT_MANUAL) {
                                "Замер CSD (с ГУ)"
                            } else {
                                "Замер CSD (Авто)"
                            },
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                } else {
                    if (state.phase == WaterfallCapturePhase.WAITING_SIGNAL ||
                        state.phase == WaterfallCapturePhase.SIGNAL_READY_WAIT_MUTE
                    ) {
                        Button(
                            onClick = onTriggerManualDecay,
                            modifier = Modifier
                                .weight(1.3f)
                                .height(48.dp)
                                .testTag("waterfall_manual_trigger_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = rtaColors.emeraldOk),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Захватить спад",
                                color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = onCancelCapture,
                        modifier = Modifier
                            .weight(0.8f)
                            .height(48.dp)
                            .testTag("waterfall_cancel_btn"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Отмена",
                            tint = rtaColors.peakCoral,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Стоп", color = rtaColors.peakCoral)
                    }
                }
            }
        }

        // Scrollable controls strip: Mode, Signal Source, Range, Window ms
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WaterfallMode.entries.forEach { m ->
                FilterChip(
                    selected = state.mode == m,
                    onClick = { onSelectMode(m) },
                    label = { Text(m.label, style = MaterialTheme.typography.labelMedium) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = rtaColors.spectrumCyanGlow,
                        selectedLabelColor = rtaColors.spectrumCyan
                    ),
                    modifier = Modifier.testTag("waterfall_mode_${m.name.lowercase()}")
                )
            }

            if (state.mode == WaterfallMode.CSD_DECAY) {
                WaterfallSignalSource.entries.forEach { src ->
                    FilterChip(
                        selected = state.signalSource == src,
                        onClick = { onSelectSignalSource(src) },
                        enabled = !isBusy,
                        label = { Text(src.label, style = MaterialTheme.typography.labelMedium) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = rtaColors.targetAmberGlow,
                            selectedLabelColor = rtaColors.targetAmber
                        ),
                        modifier = Modifier.testTag("waterfall_source_${src.name.lowercase()}")
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WaterfallFreqRange.entries.forEach { r ->
                FilterChip(
                    selected = state.freqRange == r,
                    onClick = { onSelectFreqRange(r) },
                    label = { Text(r.label, style = MaterialTheme.typography.labelMedium) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = rtaColors.emeraldDim,
                        selectedLabelColor = rtaColors.emeraldOk
                    ),
                    modifier = Modifier.testTag("waterfall_range_${r.name.lowercase()}")
                )
            }

            if (state.mode == WaterfallMode.CSD_DECAY) {
                listOf(200, 300, 500).forEach { ms ->
                    FilterChip(
                        selected = state.windowMs == ms,
                        onClick = { onSelectWindowMs(ms) },
                        enabled = !isBusy,
                        label = { Text("$ms мс", style = MaterialTheme.typography.labelMedium) },
                        modifier = Modifier.testTag("waterfall_win_${ms}")
                    )
                }
            }
        }

        // Detected Cabin Resonances Summary (when CSD measurement completed)
        val result = state.csdResult
        if (state.mode == WaterfallMode.CSD_DECAY && result != null) {
            if (result.resonances.isEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(rtaColors.emeraldDim)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Быстрое ровное затухание: критических резонансов салона в окне ${result.windowMs} мс не выявлено.",
                        style = MaterialTheme.typography.bodySmall,
                        color = rtaColors.emeraldOk
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    result.resonances.forEach { spot ->
                        ResonanceHotspotChipCard(spot = spot)
                    }
                }
            }
        }
    }
}

@Composable
private fun ResonanceHotspotChipCard(spot: ResonanceHotspot) {
    val rtaColors = LocalRtaColors.current
    Column(
        modifier = Modifier
            .width(235.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, rtaColors.peakCoral.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
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
                text = "${RtaBands.formatFrequencyHz(spot.freqHz)} • ~${spot.estimatedDecayMs.roundToInt()} мс",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = rtaColors.peakCoral
            )
        }
        Text(
            text = spot.advice,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3
        )
    }
}
