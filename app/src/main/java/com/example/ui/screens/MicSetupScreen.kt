package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.audio.AudioInputDeviceUi
import com.example.data.CalibrationProfileModel
import com.example.data.RtaSettings
import com.example.data.RtaSnapshotModel
import com.example.ui.components.CalibrationCurveCanvas
import com.example.ui.theme.LocalRtaColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MicSetupScreen(
    devices: List<AudioInputDeviceUi>,
    selectedDeviceId: Int?,
    calibrationProfiles: List<CalibrationProfileModel>,
    settings: RtaSettings,
    snapshots: List<RtaSnapshotModel>,
    overlaySnapshotId: Long?,
    onRefreshDevices: () -> Unit,
    onSelectDevice: (Int) -> Unit,
    onOpenAutoCalWizard: () -> Unit,
    onImportCalFile: () -> Unit,
    onSelectCalibrationProfile: (Long) -> Unit,
    onDeleteCalibrationProfile: (Long) -> Unit,
    onManualSplOffsetChange: (Float) -> Unit,
    onToggleOverlaySnapshot: (Long) -> Unit,
    onExportSnapshotPng: (RtaSnapshotModel) -> Unit,
    onExportSnapshotCsv: (RtaSnapshotModel) -> Unit,
    onDeleteSnapshot: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val rtaColors = LocalRtaColors.current
    val activeCal = remember(calibrationProfiles, settings.activeCalibrationProfileId) {
        calibrationProfiles.firstOrNull { it.id == settings.activeCalibrationProfileId }
    }
    val dateFormatter = remember { SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // 1. Hardware External RTA Microphone Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.setup_inputs_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                IconButton(
                    onClick = onRefreshDevices,
                    modifier = Modifier.testTag("setup_refresh_devices_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.setup_refresh_devices),
                        tint = rtaColors.spectrumCyan
                    )
                }
            }
        }

        if (devices.none { it.isExternal }) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, rtaColors.targetAmber.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Usb,
                        contentDescription = null,
                        tint = rtaColors.targetAmber
                    )
                    Text(
                        text = stringResource(R.string.setup_usb_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        items(devices, key = { it.id }) { dev ->
            val selected = dev.id == selectedDeviceId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) rtaColors.spectrumCyanGlow else MaterialTheme.colorScheme.surface)
                    .border(
                        1.dp,
                        if (selected) rtaColors.spectrumCyan else rtaColors.hairline,
                        RoundedCornerShape(12.dp)
                    )
                    .clickable { onSelectDevice(dev.id) }
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .testTag("mic_device_item_${dev.id}"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (dev.isExternal) rtaColors.emeraldDim else MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (dev.isExternal) Icons.Default.Usb else Icons.Default.Mic,
                            contentDescription = null,
                            tint = if (dev.isExternal) rtaColors.emeraldOk else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = dev.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = dev.category.label + if (dev.sampleRates.isNotEmpty()) " • ${dev.sampleRates.max() / 1000} кГц" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (dev.isExternal) rtaColors.emeraldOk else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (selected) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Активный микрофон",
                        tint = rtaColors.spectrumCyan
                    )
                }
            }
        }

        // 2. Automatic External Mic Calibration + .cal File Import Section
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.setup_cal_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, rtaColors.hairline, RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Prominent Auto-Calibration Card CTA
                Button(
                    onClick = onOpenAutoCalWizard,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("setup_start_autocal_btn"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = rtaColors.spectrumCyan)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoFixHigh,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Автокалибровка RTA-микрофона",
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Text(
                    text = "Пошаговый замер в тихой среде: определение шума фона, чувствительности на 1 кГц и автоматическая коррекция АЧХ микрофона (20 Гц – 20 кГц).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedButton(
                    onClick = onImportCalFile,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("setup_import_cal_btn"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FileUpload,
                        contentDescription = null,
                        tint = rtaColors.emeraldOk,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${stringResource(R.string.setup_cal_import)} (UMIK-1 / EMM-6 / iMM-6)",
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                // Active calibration curve preview
                if (activeCal != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Активная кривая: ${activeCal.name}",
                                style = MaterialTheme.typography.labelMedium,
                                color = rtaColors.emeraldOk
                            )
                            Text(
                                text = String.format(Locale.US, "Sens %+.1f дБ", activeCal.sensitivityOffsetDb),
                                style = MaterialTheme.typography.labelMedium,
                                color = rtaColors.spectrumCyan
                            )
                        }
                        CalibrationCurveCanvas(
                            points = activeCal.points,
                            height = 118.dp,
                            lineColor = rtaColors.emeraldOk
                        )
                    }
                }

                // Manual SPL Calibrator Offset
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.setup_spl_offset_label),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = String.format(Locale.US, "%+.1f дБ", settings.manualSplOffsetDb),
                            style = MaterialTheme.typography.labelLarge,
                            color = rtaColors.targetAmber
                        )
                    }
                    Slider(
                        value = settings.manualSplOffsetDb,
                        onValueChange = onManualSplOffsetChange,
                        valueRange = -25f..25f,
                        modifier = Modifier.testTag("setup_spl_offset_slider"),
                        colors = SliderDefaults.colors(
                            thumbColor = rtaColors.targetAmber,
                            activeTrackColor = rtaColors.targetAmber
                        )
                    )
                }
            }
        }

        // Calibration profile selector list
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (settings.activeCalibrationProfileId < 0L) rtaColors.spectrumCyanGlow else MaterialTheme.colorScheme.surface)
                    .border(
                        1.dp,
                        if (settings.activeCalibrationProfileId < 0L) rtaColors.spectrumCyan else rtaColors.hairline,
                        RoundedCornerShape(10.dp)
                    )
                    .clickable { onSelectCalibrationProfile(-1L) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.setup_cal_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (settings.activeCalibrationProfileId < 0L) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = rtaColors.spectrumCyan)
                }
            }
        }

        items(calibrationProfiles, key = { it.id }) { profile ->
            val selected = profile.id == settings.activeCalibrationProfileId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) rtaColors.emeraldDim else MaterialTheme.colorScheme.surface)
                    .border(
                        1.dp,
                        if (selected) rtaColors.emeraldOk else rtaColors.hairline,
                        RoundedCornerShape(10.dp)
                    )
                    .clickable { onSelectCalibrationProfile(profile.id) }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .testTag("cal_profile_item_${profile.id}"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val tag = if (profile.sourceType == "AUTO_CAL") "Автокалибровка" else "Файл .cal"
                    Text(
                        text = "$tag • ${profile.points.size} точек • Sens ${String.format(Locale.US, "%+.1f", profile.sensitivityOffsetDb)} дБ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { onDeleteCalibrationProfile(profile.id) }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.action_delete),
                        tint = rtaColors.peakCoral,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // 3. Saved RTA Measurement Snapshots (Before / After Comparison)
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.setup_snapshots_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        if (snapshots.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, rtaColors.hairline, RoundedCornerShape(14.dp))
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bookmark,
                        contentDescription = null,
                        tint = rtaColors.snapshotViolet,
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        text = stringResource(R.string.setup_snapshots_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(snapshots, key = { it.id }) { snap ->
                val isOverlay = snap.id == overlaySnapshotId
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isOverlay) rtaColors.snapshotVioletGlow else MaterialTheme.colorScheme.surface)
                        .border(
                            1.dp,
                            if (isOverlay) rtaColors.snapshotViolet else rtaColors.hairline,
                            RoundedCornerShape(14.dp)
                        )
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = snap.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${snap.channelTag} • ${String.format(Locale.US, "%.1f дБ%s", snap.overallSplDb, snap.weightingCode)} • ${dateFormatter.format(Date(snap.timestampMs))}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { onDeleteSnapshot(snap.id) }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.action_delete),
                                tint = rtaColors.peakCoral,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Mini sparkline preview of captured 31-band АЧХ
                    SnapshotSparkline(bands = snap.bands31Db)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onToggleOverlaySnapshot(snap.id) },
                            modifier = Modifier
                                .weight(1.2f)
                                .height(46.dp)
                                .testTag("snapshot_overlay_btn_${snap.id}"),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isOverlay) rtaColors.snapshotViolet else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (isOverlay) MaterialTheme.colorScheme.onTertiary else rtaColors.snapshotViolet
                            )
                        ) {
                            Icon(Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (isOverlay) stringResource(R.string.setup_overlay_active_btn) else stringResource(R.string.setup_overlay_btn),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }

                        OutlinedButton(
                            onClick = { onExportSnapshotPng(snap) },
                            modifier = Modifier
                                .weight(0.75f)
                                .height(46.dp)
                                .testTag("snapshot_card_png_${snap.id}"),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) {
                            Text(
                                text = "PNG",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = rtaColors.spectrumCyan
                            )
                        }

                        OutlinedButton(
                            onClick = { onExportSnapshotCsv(snap) },
                            modifier = Modifier
                                .weight(0.75f)
                                .height(46.dp)
                                .testTag("snapshot_card_csv_${snap.id}"),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) {
                            Text(
                                text = "CSV",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = rtaColors.emeraldOk
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SnapshotSparkline(bands: FloatArray) {
    val rtaColors = LocalRtaColors.current
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        if (bands.isEmpty()) return@Canvas
        val minVal = (bands.minOrNull() ?: 25f) - 4f
        val maxVal = (bands.maxOrNull() ?: 95f) + 4f
        val span = (maxVal - minVal).coerceAtLeast(10f)
        val path = Path()
        bands.forEachIndexed { i, db ->
            val x = (i.toFloat() / (bands.size - 1).coerceAtLeast(1)) * size.width
            val y = size.height * (1f - ((db - minVal) / span).coerceIn(0f, 1f))
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = rtaColors.snapshotViolet,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}
