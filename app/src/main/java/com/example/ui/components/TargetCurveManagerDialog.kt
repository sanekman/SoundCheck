package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.audio.RtaBands
import com.example.audio.TargetCurvePreset
import com.example.data.RtaSettings
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalRtaColors
import kotlin.math.log10

@Composable
fun TargetCurveManagerDialog(
    allCurves: List<TargetCurvePreset>,
    activeCurve: TargetCurvePreset,
    settings: RtaSettings,
    alignedReferenceDb: Float,
    onDismiss: () -> Unit,
    onToggleShowTarget: (Boolean) -> Unit,
    onToggleAutoAlign: (Boolean) -> Unit,
    onManualTargetRefChange: (Float) -> Unit,
    onToleranceChange: (Float) -> Unit,
    onSelectCurve: (String) -> Unit,
    onImportCsvFile: () -> Unit,
    onImportCsvText: (String, String) -> Unit,
    onDeleteCustomCurve: (Long) -> Unit
) {
    val rtaColors = LocalRtaColors.current
    var showPasteEditor by remember { mutableStateOf(false) }
    var pasteName by remember { mutableStateOf("Моя кривая CSV") }
    var pasteCsvContent by remember {
        mutableStateOf(
            "Frequency,dB\n20, 6.5\n40, 7.0\n80, 4.5\n200, 1.0\n1000, 0.0\n4000, -1.5\n10000, -3.5\n20000, -5.0"
        )
    }

    val customCurves = remember(allCurves) { allCurves.filter { it.isCustom } }
    val builtinCurves = remember(allCurves) { allCurves.filter { !it.isCustom } }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 20.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .border(1.dp, rtaColors.hairline, RoundedCornerShape(20.dp))
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
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
                                .background(rtaColors.targetAmberGlow),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ShowChart,
                                contentDescription = null,
                                tint = rtaColors.targetAmber,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = stringResource(R.string.target_manager_title),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = activeCurve.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = rtaColors.targetAmber
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("target_manager_close_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Закрыть",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Live Preview Canvas of Active Target Curve
                TargetCurvePreviewCanvas(
                    curve = activeCurve,
                    toleranceDb = settings.targetToleranceDb,
                    isEnabled = settings.showTargetCurve
                )

                // Overlay Visibility + Auto-Align + Tolerance Controls
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.target_overlay_toggle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = settings.showTargetCurve,
                            onCheckedChange = onToggleShowTarget,
                            modifier = Modifier.testTag("target_overlay_switch"),
                            colors = SwitchDefaults.colors(checkedThumbColor = rtaColors.targetAmber)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${stringResource(R.string.eq_auto_align)} (${String.format(java.util.Locale.US, "%.1f дБ", alignedReferenceDb)})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = settings.autoAlignTarget,
                            onCheckedChange = onToggleAutoAlign,
                            colors = SwitchDefaults.colors(checkedThumbColor = rtaColors.spectrumCyan)
                        )
                    }

                    if (!settings.autoAlignTarget) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Опорный уровень (1 кГц):",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = String.format(java.util.Locale.US, "%.0f дБ SPL", settings.manualTargetRefDb),
                                style = MaterialTheme.typography.labelMedium,
                                color = rtaColors.targetAmber
                            )
                        }
                        Slider(
                            value = settings.manualTargetRefDb,
                            onValueChange = onManualTargetRefChange,
                            valueRange = 45f..100f,
                            colors = SliderDefaults.colors(
                                thumbColor = rtaColors.targetAmber,
                                activeTrackColor = rtaColors.targetAmber
                            )
                        )
                    }

                    // Tolerance ribbon selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.target_tolerance_label),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(1.0f, 1.5f, 2.0f, 3.0f).forEach { tol ->
                                FilterChip(
                                    selected = kotlin.math.abs(settings.targetToleranceDb - tol) < 0.1f,
                                    onClick = { onToleranceChange(tol) },
                                    label = {
                                        Text(
                                            text = "±${String.format(java.util.Locale.US, "%.1f", tol)}",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = rtaColors.targetAmberGlow,
                                        selectedLabelColor = rtaColors.targetAmber
                                    )
                                )
                            }
                        }
                    }
                }

                // Import Custom CSV File & Paste CSV actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onImportCsvFile,
                        modifier = Modifier
                            .weight(1.25f)
                            .height(48.dp)
                            .testTag("target_import_csv_file_btn"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = rtaColors.targetAmber)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileUpload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.target_import_csv_btn),
                            color = MaterialTheme.colorScheme.onSecondary,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    OutlinedButton(
                        onClick = { showPasteEditor = !showPasteEditor },
                        modifier = Modifier
                            .weight(0.95f)
                            .height(48.dp)
                            .testTag("target_paste_csv_toggle_btn"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = null,
                            tint = rtaColors.spectrumCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.target_paste_csv_btn),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                // Optional Quick CSV Text Importer
                if (showPasteEditor) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, rtaColors.spectrumCyan.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Формат CSV: Частота(Гц), Уровень(дБ)",
                            style = MaterialTheme.typography.labelMedium,
                            color = rtaColors.spectrumCyan
                        )
                        OutlinedTextField(
                            value = pasteName,
                            onValueChange = { pasteName = it },
                            label = { Text("Название кривой") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("target_csv_name_input")
                        )
                        OutlinedTextField(
                            value = pasteCsvContent,
                            onValueChange = { pasteCsvContent = it },
                            label = { Text("Данные CSV (Гц, дБ)") },
                            minLines = 4,
                            maxLines = 6,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("target_csv_text_input")
                        )
                        Button(
                            onClick = {
                                onImportCsvText(pasteName, pasteCsvContent)
                                showPasteEditor = false
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("target_csv_apply_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = rtaColors.emeraldOk)
                        ) {
                            Text(
                                text = "Сохранить и наложить CSV-кривую",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Custom Imported CSV Curves Section
                if (customCurves.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.target_custom_section),
                        style = MaterialTheme.typography.titleSmall,
                        color = rtaColors.emeraldOk
                    )
                    customCurves.forEach { curve ->
                        TargetCurveSelectionRow(
                            curve = curve,
                            selected = curve.id == activeCurve.id,
                            onSelect = { onSelectCurve(curve.id) },
                            onDelete = curve.customDbId?.let { dbId -> { onDeleteCustomCurve(dbId) } }
                        )
                    }
                }

                // Pre-Defined Target Curves Section
                Text(
                    text = stringResource(R.string.target_builtin_section),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                builtinCurves.forEach { curve ->
                    TargetCurveSelectionRow(
                        curve = curve,
                        selected = curve.id == activeCurve.id,
                        onSelect = { onSelectCurve(curve.id) },
                        onDelete = null
                    )
                }
            }
        }
    }
}

@Composable
private fun TargetCurveSelectionRow(
    curve: TargetCurvePreset,
    selected: Boolean,
    onSelect: () -> Unit,
    onDelete: (() -> Unit)?
) {
    val rtaColors = LocalRtaColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) rtaColors.targetAmberGlow else MaterialTheme.colorScheme.background)
            .border(
                width = 1.dp,
                color = if (selected) rtaColors.targetAmber else rtaColors.hairline,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onSelect() }
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("target_curve_row_${curve.id}"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = curve.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (selected) rtaColors.targetAmber else MaterialTheme.colorScheme.onSurface
                )
                if (curve.isCustom) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(rtaColors.emeraldDim)
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "CSV",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = rtaColors.emeraldOk
                        )
                    }
                }
            }
            Text(
                text = curve.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onDelete != null) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("delete_target_curve_${curve.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.action_delete),
                        tint = rtaColors.peakCoral,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Активная кривая",
                    tint = rtaColors.targetAmber,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun TargetCurvePreviewCanvas(
    curve: TargetCurvePreset,
    toleranceDb: Float,
    isEnabled: Boolean
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
        modifier = Modifier
            .fillMaxWidth()
            .height(126.dp)
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

        // Horizontal dB lines
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
        listOf(20f to "20", 63f to "63", 250f to "250", 1000f to "1k", 4000f to "4k", 16000f to "16k").forEach { (f, lbl) ->
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

        // Tolerance ribbon + dashed curve
        val curvePath = Path()
        val ribbonPath = Path()
        for (i in 0 until 31) {
            val x = freqToX(RtaBands.ISO_31_FREQS[i])
            val valDb = curve.relativeDb31[i]
            val y = dbToY(valDb)
            val yTop = dbToY(valDb + toleranceDb)
            if (i == 0) {
                curvePath.moveTo(x, y)
                ribbonPath.moveTo(x, yTop)
            } else {
                curvePath.lineTo(x, y)
                ribbonPath.lineTo(x, yTop)
            }
        }
        for (i in 30 downTo 0) {
            val x = freqToX(RtaBands.ISO_31_FREQS[i])
            val yBot = dbToY(curve.relativeDb31[i] - toleranceDb)
            ribbonPath.lineTo(x, yBot)
        }
        ribbonPath.close()

        val alphaMul = if (isEnabled) 1f else 0.35f
        drawPath(path = ribbonPath, color = rtaColors.targetAmberGlow.copy(alpha = 0.35f * alphaMul))
        drawPath(
            path = curvePath,
            color = rtaColors.targetAmber.copy(alpha = alphaMul),
            style = Stroke(
                width = 2.2.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
            )
        )
    }
}
