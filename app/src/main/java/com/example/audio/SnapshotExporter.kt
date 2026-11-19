package com.example.audio

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.log10

data class SnapshotExportPayload(
    val name: String,
    val channelTag: String,
    val timestampMs: Long,
    val overallSplDb: Float,
    val weightingCode: String,
    val micName: String,
    val calibrationName: String,
    val bands31Db: FloatArray,
    val targetCurve: TargetCurvePreset,
    val targetReferenceDb: Float,
    val showTargetCurve: Boolean,
    val targetToleranceDb: Float,
    val overlayBands31Db: FloatArray? = null,
    val overlayName: String? = null
)

object SnapshotExporter {

    private val isoDateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private val fileTimestampFormatter = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US)

    fun sanitizeFileName(baseName: String, extension: String, timestampMs: Long = System.currentTimeMillis()): String {
        val cleaned = baseName
            .trim()
            .replace(Regex("""[^\p{L}\p{N}_\-]+"""), "_")
            .trim('_')
            .take(32)
            .ifEmpty { "AutoRTA_Snapshot" }
        val stamp = fileTimestampFormatter.format(Date(timestampMs))
        return "${cleaned}_${stamp}.${extension.lowercase(Locale.US)}"
    }

    /**
     * Generates a standards-compliant CSV file containing metadata comments and all 31 ISO 1/3-octave
     * frequency bands with Measured dB, Target dB, and Delta dB (compatible with REW, Excel, and AutoRTA).
     */
    fun buildCsvContent(payload: SnapshotExportPayload): String {
        val sb = StringBuilder()
        val dateStr = isoDateFormatter.format(Date(payload.timestampMs))
        sb.appendLine("# AutoRTA Measurement Snapshot")
        sb.appendLine("# Name: ${payload.name}")
        sb.appendLine("# Channel: ${payload.channelTag}")
        sb.appendLine("# Timestamp: $dateStr")
        sb.appendLine(String.format(Locale.US, "# Overall_SPL: %.1f dB%s", payload.overallSplDb, payload.weightingCode))
        sb.appendLine("# Microphone: ${payload.micName}")
        sb.appendLine("# Calibration: ${payload.calibrationName}")
        sb.appendLine(String.format(Locale.US, "# Target_Curve: %s (Ref %.1f dB)", payload.targetCurve.name, payload.targetReferenceDb))

        val hasOverlay = payload.overlayBands31Db != null && payload.overlayBands31Db.size == 31
        if (hasOverlay) {
            sb.appendLine("# Overlay_Reference: ${payload.overlayName ?: "Snapshot"}")
            sb.appendLine("Frequency_Hz,Measured_dB,Target_dB,Delta_dB,Overlay_dB")
        } else {
            sb.appendLine("Frequency_Hz,Measured_dB,Target_dB,Delta_dB")
        }

        for (i in 0 until 31) {
            val freq = RtaBands.ISO_31_FREQS[i]
            val measured = payload.bands31Db.getOrElse(i) { 25f }
            val target = payload.targetReferenceDb + payload.targetCurve.relativeDb31[i]
            val delta = target - measured
            if (hasOverlay) {
                val overlayVal = payload.overlayBands31Db!![i]
                sb.appendLine(
                    String.format(
                        Locale.US,
                        "%.1f,%.2f,%.2f,%.2f,%.2f",
                        freq, measured, target, delta, overlayVal
                    )
                )
            } else {
                sb.appendLine(
                    String.format(
                        Locale.US,
                        "%.1f,%.2f,%.2f,%.2f",
                        freq, measured, target, delta
                    )
                )
            }
        }
        return sb.toString()
    }

    fun writeCsvToStream(payload: SnapshotExportPayload, outputStream: OutputStream) {
        val csv = buildCsvContent(payload)
        outputStream.write(csv.toByteArray(Charsets.UTF_8))
        outputStream.flush()
    }

    /**
     * Renders a crisp 1600x960 high-resolution dark-theme RTA measurement report PNG bitmap.
     */
    fun renderRtaPngBitmap(
        payload: SnapshotExportPayload,
        width: Int = 1600,
        height: Int = 960
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Colors matching AutoRTA Precision Dark Acoustic Lab theme
        val bgObsidian = Color.parseColor("#0A0D12")
        val surfaceCarbon = Color.parseColor("#11161F")
        val borderHairline = Color.parseColor("#2B3749")
        val gridMajor = Color.parseColor("#263345")
        val gridMinor = Color.parseColor("#17202D")
        val textPrimary = Color.parseColor("#F1F5FA")
        val textMuted = Color.parseColor("#94A3B8")
        val cyan = Color.parseColor("#00E5FF")
        val cyanMid = Color.parseColor("#8800E5FF")
        val cyanLow = Color.parseColor("#33007B8A")
        val amber = Color.parseColor("#FFB300")
        val amberRibbon = Color.parseColor("#2CFFB300")
        val violet = Color.parseColor("#B388FF")

        canvas.drawColor(bgObsidian)

        // Header panel
        val headerRect = RectF(36f, 28f, width - 36f, 152f)
        val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = surfaceCarbon }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = borderHairline
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawRoundRect(headerRect, 20f, 20f, panelPaint)
        canvas.drawRoundRect(headerRect, 20f, 20f, borderPaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textPrimary
            textSize = 36f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textMuted
            textSize = 22f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val splPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = cyan
            textSize = 48f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        val metaRightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textMuted
            textSize = 20f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            textAlign = Paint.Align.RIGHT
        }

        val dateStr = isoDateFormatter.format(Date(payload.timestampMs))
        canvas.drawText("${payload.name}  •  ${payload.channelTag}", 64f, 82f, titlePaint)
        canvas.drawText(
            "Микрофон: ${payload.micName}  |  Калибровка: ${payload.calibrationName}",
            64f,
            122f,
            subtitlePaint
        )
        canvas.drawText(
            String.format(Locale.US, "%.1f dB%s", payload.overallSplDb, payload.weightingCode),
            width - 64f,
            88f,
            splPaint
        )
        canvas.drawText(dateStr, width - 64f, 124f, metaRightPaint)

        // Plot container
        val plotCard = RectF(36f, 174f, width - 36f, height - 86f)
        canvas.drawRoundRect(plotCard, 20f, 20f, panelPaint)
        canvas.drawRoundRect(plotCard, 20f, 20f, borderPaint)

        val leftPad = 112f
        val rightPad = 76f
        val topPad = 216f
        val bottomPad = height - 146f
        val plotW = (width - leftPad - rightPad).coerceAtLeast(1f)
        val plotH = (bottomPad - topPad).coerceAtLeast(1f)

        val minDb = 25f
        val maxDb = 105f
        val logMin = log10(20.0).toFloat()
        val logMax = log10(20000.0).toFloat()
        val logSpan = logMax - logMin

        fun freqToX(hz: Float): Float {
            val t = ((log10(hz.coerceIn(20f, 20000f).toDouble()).toFloat() - logMin) / logSpan).coerceIn(0f, 1f)
            return leftPad + t * plotW
        }

        fun dbToY(db: Float): Float {
            val t = ((db - minDb) / (maxDb - minDb)).coerceIn(0f, 1f)
            return topPad + plotH * (1f - t)
        }

        val axisLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textMuted
            textSize = 20f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        }
        val gridMajorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = gridMajor
            strokeWidth = 2f
        }
        val gridMinorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = gridMinor
            strokeWidth = 1.4f
        }

        // Horizontal dB lines
        var dbTick = 30
        while (dbTick <= 100) {
            val y = dbToY(dbTick.toFloat())
            canvas.drawLine(leftPad, y, leftPad + plotW, y, if (dbTick % 20 == 0) gridMajorPaint else gridMinorPaint)
            axisLabelPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("$dbTick", leftPad - 14f, y + 7f, axisLabelPaint)
            dbTick += 10
        }

        // Vertical log frequency lines
        val freqTicks = listOf(
            20f to "20", 31.5f to "31.5", 63f to "63", 125f to "125",
            250f to "250", 500f to "500", 1000f to "1k", 2000f to "2k",
            4000f to "4k", 8000f to "8k", 16000f to "16k", 20000f to "20k"
        )
        axisLabelPaint.textAlign = Paint.Align.CENTER
        freqTicks.forEach { (freq, lbl) ->
            val x = freqToX(freq)
            canvas.drawLine(x, topPad, x, bottomPad, gridMinorPaint)
            canvas.drawText(lbl, x, bottomPad + 32f, axisLabelPaint)
        }

        // Target curve tolerance ribbon
        val targetPath = Path()
        if (payload.showTargetCurve) {
            val ribbonPath = Path()
            for (i in 0 until 31) {
                val x = freqToX(RtaBands.ISO_31_FREQS[i])
                val y = dbToY(payload.targetReferenceDb + payload.targetCurve.relativeDb31[i])
                val yTop = dbToY(payload.targetReferenceDb + payload.targetCurve.relativeDb31[i] + payload.targetToleranceDb)
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
                val yBot = dbToY(payload.targetReferenceDb + payload.targetCurve.relativeDb31[i] - payload.targetToleranceDb)
                ribbonPath.lineTo(x, yBot)
            }
            ribbonPath.close()
            val ribbonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = amberRibbon
                style = Paint.Style.FILL
            }
            canvas.drawPath(ribbonPath, ribbonPaint)
        }

        // 31-band RTA bars
        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, topPad, 0f, bottomPad,
                intArrayOf(cyan, cyanMid, cyanLow),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
            style = Paint.Style.FILL
        }
        val barW = (plotW / 31f * 0.62f).coerceAtLeast(12f)
        val tracePath = Path()

        for (i in 0 until 31) {
            val cx = freqToX(RtaBands.ISO_31_FREQS[i])
            val valDb = payload.bands31Db.getOrElse(i) { 25f }
            val topY = dbToY(valDb)
            val rect = RectF(cx - barW / 2f, topY, cx + barW / 2f, bottomPad)
            canvas.drawRoundRect(rect, 5f, 5f, barPaint)
            if (i == 0) tracePath.moveTo(cx, topY) else tracePath.lineTo(cx, topY)
        }

        // Smooth measured trace line
        val tracePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = cyan
            style = Paint.Style.STROKE
            strokeWidth = 4.5f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawPath(tracePath, tracePaint)

        // Optional comparison overlay snapshot trace
        if (payload.overlayBands31Db != null && payload.overlayBands31Db.size == 31) {
            val ovPath = Path()
            for (i in 0 until 31) {
                val cx = freqToX(RtaBands.ISO_31_FREQS[i])
                val cy = dbToY(payload.overlayBands31Db[i])
                if (i == 0) ovPath.moveTo(cx, cy) else ovPath.lineTo(cx, cy)
            }
            val ovPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = violet
                style = Paint.Style.STROKE
                strokeWidth = 4f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            canvas.drawPath(ovPath, ovPaint)
        }

        // Target curve dashed stroke on top
        if (payload.showTargetCurve) {
            val targetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = amber
                style = Paint.Style.STROKE
                strokeWidth = 4.5f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                pathEffect = DashPathEffect(floatArrayOf(18f, 10f), 0f)
            }
            canvas.drawPath(targetPath, targetPaint)
        }

        // Footer legend
        val legendY = height - 34f
        var cursorX = 56f
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val legendTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textMuted
            textSize = 21f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }

        fun drawLegendItem(color: Int, label: String) {
            dotPaint.color = color
            canvas.drawCircle(cursorX, legendY - 7f, 8f, dotPaint)
            canvas.drawText(label, cursorX + 18f, legendY, legendTextPaint)
            cursorX += 26f + legendTextPaint.measureText(label) + 34f
        }

        drawLegendItem(cyan, "Замер RTA (1/3 окт)")
        if (payload.showTargetCurve) {
            drawLegendItem(amber, "Цель: ${payload.targetCurve.name} (±${String.format(Locale.US, "%.1f", payload.targetToleranceDb)} дБ)")
        }
        if (payload.overlayBands31Db != null) {
            drawLegendItem(violet, "Сравнение: ${payload.overlayName ?: "Снимок"}")
        }

        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textMuted
            textSize = 20f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("AutoRTA • 20 Hz – 20 kHz", width - 48f, legendY, brandPaint)

        return bitmap
    }

    fun writePngToStream(payload: SnapshotExportPayload, outputStream: OutputStream) {
        val bitmap = renderRtaPngBitmap(payload)
        try {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            outputStream.flush()
        } finally {
            bitmap.recycle()
        }
    }
}
