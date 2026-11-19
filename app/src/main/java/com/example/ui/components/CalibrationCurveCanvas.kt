package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.CalibrationParser
import com.example.audio.CalibrationPoint
import com.example.audio.RtaBands
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalRtaColors
import kotlin.math.log10

@Composable
fun CalibrationCurveCanvas(
    points: List<CalibrationPoint>,
    modifier: Modifier = Modifier,
    height: Dp = 130.dp,
    lineColor: Color = LocalRtaColors.current.emeraldOk
) {
    val rtaColors = LocalRtaColors.current
    val textMuted = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()

    val labelStyle = remember(textMuted) {
        TextStyle(
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 9.sp,
            color = textMuted
        )
    }

    val logMin = remember { log10(20.0).toFloat() }
    val logMax = remember { log10(20000.0).toFloat() }
    val logSpan = remember { logMax - logMin }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, rtaColors.hairline, RoundedCornerShape(12.dp))
    ) {
        val leftPad = 30.dp.toPx()
        val rightPad = 10.dp.toPx()
        val topPad = 10.dp.toPx()
        val bottomPad = 18.dp.toPx()
        val plotW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
        val plotH = (size.height - topPad - bottomPad).coerceAtLeast(1f)

        val minDb = -12f
        val maxDb = 12f

        fun freqToX(hz: Float): Float {
            val t = ((log10(hz.coerceIn(20f, 20000f).toDouble()).toFloat() - logMin) / logSpan).coerceIn(0f, 1f)
            return leftPad + t * plotW
        }

        fun dbToY(db: Float): Float {
            val t = ((db.coerceIn(minDb, maxDb) - minDb) / (maxDb - minDb)).coerceIn(0f, 1f)
            return topPad + plotH * (1f - t)
        }

        // Horizontal dB grid (-12, -6, 0, +6, +12)
        listOf(-12, -6, 0, 6, 12).forEach { db ->
            val y = dbToY(db.toFloat())
            drawLine(
                color = if (db == 0) rtaColors.gridMajor else rtaColors.gridMinor,
                start = Offset(leftPad, y),
                end = Offset(leftPad + plotW, y),
                strokeWidth = if (db == 0) 1.2.dp.toPx() else 0.7.dp.toPx()
            )
            val txt = if (db > 0) "+$db" else "$db"
            val measured = textMeasurer.measure(txt, style = labelStyle)
            drawText(
                textLayoutResult = measured,
                topLeft = Offset(
                    x = (leftPad - measured.size.width - 4.dp.toPx()).coerceAtLeast(2f),
                    y = y - measured.size.height / 2f
                )
            )
        }

        // Frequency markers
        listOf(20f to "20", 100f to "100", 500f to "500", 1000f to "1k", 5000f to "5k", 20000f to "20k").forEach { (f, lbl) ->
            val x = freqToX(f)
            drawLine(
                color = rtaColors.gridMinor,
                start = Offset(x, topPad),
                end = Offset(x, topPad + plotH),
                strokeWidth = 0.7.dp.toPx()
            )
            val measured = textMeasurer.measure(lbl, style = labelStyle)
            drawText(
                textLayoutResult = measured,
                topLeft = Offset(
                    x = (x - measured.size.width / 2f).coerceIn(leftPad, size.width - measured.size.width - 2f),
                    y = topPad + plotH + 2.dp.toPx()
                )
            )
        }

        if (points.isNotEmpty()) {
            val curvePath = Path()
            val fillPath = Path()
            val zeroY = dbToY(0f)

            RtaBands.LOG_TRACE_FREQS.forEachIndexed { idx, freq ->
                val corrDb = CalibrationParser.interpolateCorrectionDb(points, freq)
                val x = freqToX(freq)
                val y = dbToY(corrDb)
                if (idx == 0) {
                    curvePath.moveTo(x, y)
                    fillPath.moveTo(x, zeroY)
                    fillPath.lineTo(x, y)
                } else {
                    curvePath.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
            }
            fillPath.lineTo(freqToX(RtaBands.LOG_TRACE_FREQS.last()), zeroY)
            fillPath.close()

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(lineColor.copy(alpha = 0.25f), Color.Transparent),
                    startY = topPad,
                    endY = topPad + plotH
                )
            )
            drawPath(
                path = curvePath,
                color = lineColor,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}
