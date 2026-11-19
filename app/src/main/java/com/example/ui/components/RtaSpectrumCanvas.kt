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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.RtaBands
import com.example.audio.RtaDisplayMode
import com.example.audio.TargetCurvePreset
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalRtaColors
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow

@Composable
fun RtaSpectrumCanvas(
    bands31Db: FloatArray,
    peakHold31Db: FloatArray?,
    trace128Db: FloatArray,
    targetCurve: TargetCurvePreset,
    targetReferenceDb: Float,
    showTargetCurve: Boolean = true,
    targetToleranceDb: Float = 1.5f,
    displayMode: RtaDisplayMode,
    showPeakHold: Boolean,
    overlaySnapshotBands31: FloatArray? = null,
    minDb: Float = 25f,
    maxDb: Float = 105f,
    modifier: Modifier = Modifier
) {
    val rtaColors = LocalRtaColors.current
    val surfaceColor = MaterialTheme.colorScheme.surface
    val textMuted = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()

    var inspectedBandIdx by remember { mutableStateOf<Int?>(null) }

    // Pinch-to-zoom (1x..8x) and horizontal panning along the logarithmic frequency axis
    var freqZoom by remember { mutableFloatStateOf(1f) }
    var panFraction by remember { mutableFloatStateOf(0f) }

    val latestZoom by rememberUpdatedState(freqZoom)
    val latestPan by rememberUpdatedState(panFraction)

    val fullLogMin = remember { log10(20.0).toFloat() }
    val fullLogMax = remember { log10(20000.0).toFloat() }
    val fullLogSpan = remember { fullLogMax - fullLogMin }

    val visibleLogSpan = fullLogSpan / freqZoom
    val visibleLogMin = fullLogMin + panFraction * fullLogSpan
    val visibleLogMax = visibleLogMin + visibleLogSpan
    val visibleMinHz = 10.0.pow(visibleLogMin.toDouble()).toFloat().coerceIn(20f, 20000f)
    val visibleMaxHz = 10.0.pow(visibleLogMax.toDouble()).toFloat().coerceIn(20f, 20000f)

    val defaultGridFreqs = remember {
        listOf(
            20f to "20", 31.5f to "31", 63f to "63", 125f to "125",
            250f to "250", 500f to "500", 1000f to "1k", 2000f to "2k",
            4000f to "4k", 8000f to "8k", 16000f to "16k"
        )
    }

    val labelStyle = remember(textMuted) {
        TextStyle(
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 9.sp,
            color = textMuted
        )
    }

    fun selectClosestBandAt(tapX: Float, widthPx: Float, leftPadPx: Float, rightPadPx: Float, toggleIfSame: Boolean) {
        val plotW = (widthPx - leftPadPx - rightPadPx).coerceAtLeast(1f)
        val relX = ((tapX - leftPadPx) / plotW).coerceIn(0f, 1f)
        val curSpan = fullLogSpan / latestZoom
        val curMin = fullLogMin + latestPan * fullLogSpan
        val freq = 10.0.pow((curMin + relX * curSpan).toDouble()).toFloat()
        val closestIdx = RtaBands.ISO_31_FREQS.indices.minByOrNull {
            abs(RtaBands.ISO_31_FREQS[it] - freq)
        }
        inspectedBandIdx = if (toggleIfSame && inspectedBandIdx == closestIdx) null else closestIdx
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(surfaceColor, MaterialTheme.colorScheme.background)
                )
            )
            .border(1.dp, rtaColors.hairline, RoundedCornerShape(16.dp))
            .testTag("rta_spectrum_canvas")
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { offset ->
                            selectClosestBandAt(
                                tapX = offset.x,
                                widthPx = size.width.toFloat(),
                                leftPadPx = 32.dp.toPx(),
                                rightPadPx = 10.dp.toPx(),
                                toggleIfSame = true
                            )
                        },
                        onDoubleTap = {
                            if (latestZoom > 1.02f) {
                                freqZoom = 1f
                                panFraction = 0f
                            } else {
                                // Quick 2.5x zoom into tapped region on double-tap
                                val leftPadPx = 32.dp.toPx()
                                val rightPadPx = 10.dp.toPx()
                                val plotW = (size.width - leftPadPx - rightPadPx).coerceAtLeast(1f)
                                val relX = ((it.x - leftPadPx) / plotW).coerceIn(0f, 1f)
                                val newZoom = 2.5f
                                val newSpan = 1f / newZoom
                                freqZoom = newZoom
                                panFraction = (relX - newSpan * 0.5f).coerceIn(0f, 1f - newSpan)
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    val leftPadPx = 32.dp.toPx()
                    val rightPadPx = 10.dp.toPx()
                    val touchSlop = viewConfiguration.touchSlop

                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var zoomAccum = 1f
                        var panAccum = Offset.Zero
                        var pastSlop = false
                        var isHorizontalGesture = false

                        do {
                            val event = awaitPointerEvent()
                            val activeChanges = event.changes.filter { it.pressed }
                            if (activeChanges.isEmpty()) break

                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            val pointerCount = activeChanges.size

                            if (!pastSlop) {
                                zoomAccum *= zoomChange
                                panAccum += panChange
                                val centroidSize = if (pointerCount > 1) abs(1f - zoomAccum) * 180f else 0f
                                val panDist = panAccum.getDistance()
                                if (pointerCount > 1 && (centroidSize > touchSlop || panDist > touchSlop)) {
                                    pastSlop = true
                                    isHorizontalGesture = true
                                } else if (panDist > touchSlop) {
                                    // Allow parent vertical scroll when single-finger gesture is mostly vertical
                                    if (abs(panAccum.x) >= abs(panAccum.y) * 0.85f) {
                                        pastSlop = true
                                        isHorizontalGesture = true
                                    } else {
                                        // Yield to parent vertical scroll container
                                        break
                                    }
                                }
                            }

                            if (pastSlop && isHorizontalGesture) {
                                val plotW = (size.width - leftPadPx - rightPadPx).coerceAtLeast(1f)

                                if (pointerCount > 1 || abs(zoomChange - 1f) > 0.002f) {
                                    val centroid = event.calculateCentroid(useCurrent = true)
                                    val anchorRelX = ((centroid.x - leftPadPx) / plotW).coerceIn(0f, 1f)

                                    val oldZoom = latestZoom
                                    val oldPan = latestPan
                                    val oldSpan = 1f / oldZoom
                                    val anchorFrac = oldPan + anchorRelX * oldSpan

                                    val newZoom = (oldZoom * zoomChange).coerceIn(1f, 8f)
                                    val newSpan = 1f / newZoom
                                    val panDeltaFrac = -panChange.x / plotW * newSpan
                                    val maxPan = (1f - newSpan).coerceAtLeast(0f)
                                    val newPan = (anchorFrac - anchorRelX * newSpan + panDeltaFrac).coerceIn(0f, maxPan)

                                    freqZoom = newZoom
                                    panFraction = newPan
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                } else if (latestZoom > 1.02f) {
                                    // 1-finger horizontal pan when zoomed in
                                    val span = 1f / latestZoom
                                    val maxPan = (1f - span).coerceAtLeast(0f)
                                    val deltaFrac = -panChange.x / plotW * span
                                    panFraction = (latestPan + deltaFrac).coerceIn(0f, maxPan)
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                } else {
                                    // 1-finger horizontal scrub inspects crosshair band at 1x zoom
                                    val pos = activeChanges.first().position
                                    selectClosestBandAt(
                                        tapX = pos.x,
                                        widthPx = size.width.toFloat(),
                                        leftPadPx = leftPadPx,
                                        rightPadPx = rightPadPx,
                                        toggleIfSame = false
                                    )
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
        ) {
            val leftPad = 32.dp.toPx()
            val rightPad = 10.dp.toPx()
            val topPad = 12.dp.toPx()
            val bottomPad = 22.dp.toPx()
            val plotW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
            val plotH = (size.height - topPad - bottomPad).coerceAtLeast(1f)

            fun freqToX(hz: Float): Float {
                val f = hz.coerceIn(20f, 20000f)
                val t = (log10(f.toDouble()).toFloat() - visibleLogMin) / visibleLogSpan
                return leftPad + t * plotW
            }

            fun dbToY(db: Float): Float {
                val t = ((db - minDb) / (maxDb - minDb)).coerceIn(0f, 1f)
                return topPad + plotH * (1f - t)
            }

            // 1. Horizontal dB grid lines (every 10 dB)
            var dbTick = (minDb.toInt() / 10) * 10
            if (dbTick < minDb) dbTick += 10
            while (dbTick <= maxDb) {
                val y = dbToY(dbTick.toFloat())
                val isMajor = dbTick % 20 == 0
                drawLine(
                    color = if (isMajor) rtaColors.gridMajor else rtaColors.gridMinor,
                    start = Offset(leftPad, y),
                    end = Offset(leftPad + plotW, y),
                    strokeWidth = if (isMajor) 1.dp.toPx() else 0.7.dp.toPx()
                )
                val measured = textMeasurer.measure("$dbTick", style = labelStyle)
                drawText(
                    textLayoutResult = measured,
                    topLeft = Offset(
                        x = (leftPad - measured.size.width - 4.dp.toPx()).coerceAtLeast(2f),
                        y = y - measured.size.height / 2f
                    )
                )
                dbTick += 10
            }

            // 2. Dynamic vertical logarithmic frequency grid lines (unfolds ISO bands when zoomed)
            val tickCandidates = if (freqZoom <= 1.25f) {
                defaultGridFreqs
            } else {
                RtaBands.ISO_31_BANDS.map { b ->
                    val shortLbl = when {
                        b.nominalHz >= 1000f -> {
                            val khz = b.nominalHz / 1000f
                            if (khz == khz.toInt().toFloat()) "${khz.toInt()}k"
                            else String.format(java.util.Locale.US, "%.1fk", khz)
                        }
                        b.nominalHz == 31.5f -> "31.5"
                        else -> "${b.nominalHz.toInt()}"
                    }
                    b.nominalHz to shortLbl
                }
            }

            var lastLabelRightX = -1000f
            val minLabelGapPx = 6.dp.toPx()
            tickCandidates.forEach { (freq, lbl) ->
                val x = freqToX(freq)
                if (x >= leftPad - 1f && x <= leftPad + plotW + 1f) {
                    drawLine(
                        color = rtaColors.gridMinor,
                        start = Offset(x, topPad),
                        end = Offset(x, topPad + plotH),
                        strokeWidth = 0.8.dp.toPx()
                    )
                    val measured = textMeasurer.measure(lbl, style = labelStyle)
                    val textX = (x - measured.size.width / 2f)
                        .coerceIn(leftPad, size.width - measured.size.width - 2f)
                    if (textX >= lastLabelRightX + minLabelGapPx) {
                        drawText(
                            textLayoutResult = measured,
                            topLeft = Offset(
                                x = textX,
                                y = topPad + plotH + 4.dp.toPx()
                            )
                        )
                        lastLabelRightX = textX + measured.size.width
                    }
                }
            }

            // Clip all spectrum plots to the active plot viewport so zoomed paths stay razor-sharp
            clipRect(
                left = leftPad,
                top = topPad,
                right = leftPad + plotW,
                bottom = topPad + plotH
            ) {
                // 3. Target curve tolerance ribbon (±targetToleranceDb) & Amber target curve path
                val ribbonPath = Path()
                val targetPath = Path()
                if (showTargetCurve) {
                    for (i in 0 until 31) {
                        val x = freqToX(RtaBands.ISO_31_FREQS[i])
                        val y = dbToY(targetReferenceDb + targetCurve.relativeDb31[i])
                        val yTop = dbToY(targetReferenceDb + targetCurve.relativeDb31[i] + targetToleranceDb)
                        if (i == 0) {
                            targetPath.moveTo(x, y)
                            ribbonPath.moveTo(x, yTop)
                        } else {
                            targetPath.lineTo(x, y)
                            ribbonPath.lineTo(x, yTop)
                        }
                    }
                    for (i in 30 downTo 0) {
                        val x = freqToX(RtaBands.ISO_31_FREQS[i])
                        val yBot = dbToY(targetReferenceDb + targetCurve.relativeDb31[i] - targetToleranceDb)
                        ribbonPath.lineTo(x, yBot)
                    }
                    ribbonPath.close()
                    drawPath(path = ribbonPath, color = rtaColors.targetAmberGlow)
                }

                // 4. Live 31-band RTA bars (width scales proportionally with zoom)
                if (displayMode == RtaDisplayMode.BARS || displayMode == RtaDisplayMode.BOTH) {
                    val barSlotWidth = (plotW * freqZoom) / 31f
                    val barW = (barSlotWidth * 0.66f).coerceIn(3.dp.toPx(), 42.dp.toPx())
                    val barBrush = Brush.verticalGradient(
                        colors = listOf(
                            rtaColors.spectrumCyan,
                            rtaColors.spectrumCyan.copy(alpha = 0.55f),
                            rtaColors.spectrumCyanDim.copy(alpha = 0.25f)
                        ),
                        startY = topPad,
                        endY = topPad + plotH
                    )
                    for (i in 0 until 31) {
                        val cx = freqToX(RtaBands.ISO_31_FREQS[i])
                        if (cx + barW < leftPad || cx - barW > leftPad + plotW) continue
                        val topY = dbToY(bands31Db[i])
                        val barH = (topPad + plotH - topY).coerceAtLeast(1.5.dp.toPx())
                        val isInspected = inspectedBandIdx == i
                        drawRoundRect(
                            brush = barBrush,
                            topLeft = Offset(cx - barW / 2f, topY),
                            size = Size(barW, barH),
                            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                            alpha = if (isInspected) 1f else 0.85f
                        )
                    }
                }

                // 5. High-resolution live mic trace curve
                if (displayMode == RtaDisplayMode.LINE || displayMode == RtaDisplayMode.BOTH) {
                    val linePath = Path()
                    val fillPath = Path()
                    for (i in trace128Db.indices) {
                        val x = freqToX(RtaBands.LOG_TRACE_FREQS[i])
                        val y = dbToY(trace128Db[i])
                        if (i == 0) {
                            linePath.moveTo(x, y)
                            fillPath.moveTo(x, topPad + plotH)
                            fillPath.lineTo(x, y)
                        } else {
                            linePath.lineTo(x, y)
                            fillPath.lineTo(x, y)
                        }
                    }
                    fillPath.lineTo(freqToX(RtaBands.LOG_TRACE_FREQS.last()), topPad + plotH)
                    fillPath.close()

                    if (displayMode == RtaDisplayMode.LINE) {
                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(rtaColors.spectrumCyanGlow, Color.Transparent),
                                startY = topPad,
                                endY = topPad + plotH
                            )
                        )
                    }
                    drawPath(
                        path = linePath,
                        color = rtaColors.spectrumCyan,
                        style = Stroke(
                            width = 2.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }

                // 6. Violet Reference Snapshot Overlay (Before / After comparison)
                if (overlaySnapshotBands31 != null && overlaySnapshotBands31.size == 31) {
                    val snapPath = Path()
                    for (i in 0 until 31) {
                        val x = freqToX(RtaBands.ISO_31_FREQS[i])
                        val y = dbToY(overlaySnapshotBands31[i])
                        if (i == 0) snapPath.moveTo(x, y) else snapPath.lineTo(x, y)
                    }
                    drawPath(
                        path = snapPath,
                        color = rtaColors.snapshotViolet,
                        style = Stroke(
                            width = 2.2.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }

                // 7. Target Curve Stroke on top
                if (showTargetCurve) {
                    drawPath(
                        path = targetPath,
                        color = rtaColors.targetAmber,
                        style = Stroke(
                            width = 2.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
                        )
                    )
                }

                // 8. Peak Hold caps
                if (showPeakHold && peakHold31Db != null) {
                    val capW = ((plotW * freqZoom) / 31f * 0.62f).coerceIn(3.dp.toPx(), 40.dp.toPx())
                    for (i in 0 until 31) {
                        val cx = freqToX(RtaBands.ISO_31_FREQS[i])
                        if (cx + capW < leftPad || cx - capW > leftPad + plotW) continue
                        val py = dbToY(peakHold31Db[i])
                        drawLine(
                            color = rtaColors.peakCoral,
                            start = Offset(cx - capW / 2f, py),
                            end = Offset(cx + capW / 2f, py),
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }

                // 9. Touch Crosshair Inspector
                inspectedBandIdx?.let { idx ->
                    val freq = RtaBands.ISO_31_FREQS[idx]
                    val x = freqToX(freq)
                    if (x in leftPad..(leftPad + plotW)) {
                        val activeVal = bands31Db[idx]
                        val y = dbToY(activeVal)
                        drawLine(
                            color = Color.White.copy(alpha = 0.55f),
                            start = Offset(x, topPad),
                            end = Offset(x, topPad + plotH),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                        )
                        drawCircle(
                            color = rtaColors.spectrumCyan,
                            radius = 5.dp.toPx(),
                            center = Offset(x, y)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 2.5.dp.toPx(),
                            center = Offset(x, y)
                        )
                    }
                }
            }
        }

        // Active Zoom Range HUD Pill (top-left inside plot when zoomed > 1x)
        if (freqZoom > 1.02f) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 38.dp, top = 8.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.88f))
                    .border(1.dp, rtaColors.spectrumCyan.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${RtaBands.formatFrequencyHz(visibleMinHz)} – ${RtaBands.formatFrequencyHz(visibleMaxHz)} • ${
                        String.format(java.util.Locale.US, "%.1f×", freqZoom)
                    }",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
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
                        .testTag("rta_zoom_reset_btn")
                ) {
                    Text(
                        text = "1×",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = rtaColors.spectrumCyan
                    )
                }
            }
        }

        // Crosshair readout HUD badge in top-right corner when user touches the graph
        inspectedBandIdx?.let { idx ->
            val band = RtaBands.ISO_31_BANDS[idx]
            val activeVal = bands31Db[idx]
            val targetVal = targetReferenceDb + targetCurve.relativeDb31[idx]
            val delta = targetVal - activeVal
            val deltaSign = if (delta >= 0f) "+" else ""
            val deltaColor = when {
                abs(delta) <= targetToleranceDb -> rtaColors.emeraldOk
                delta > 0f -> rtaColors.spectrumCyan
                else -> rtaColors.peakCoral
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 10.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.88f))
                    .border(1.dp, rtaColors.hairline, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = RtaBands.formatFrequencyHz(band.nominalHz),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = String.format(java.util.Locale.US, "Замер %.1f дБ • Цель %.1f", activeVal, targetVal),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = String.format(java.util.Locale.US, "%s%.1f дБ", deltaSign, delta),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = deltaColor
                )
            }
        }
    }
}
