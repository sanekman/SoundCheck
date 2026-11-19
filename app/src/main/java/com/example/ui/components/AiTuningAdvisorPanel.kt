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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ai.AiAdvisorUiState
import com.example.ai.AiEqStep
import com.example.ai.AiTuningPlan
import com.example.ai.HeadUnitEqProfile
import com.example.audio.TargetCurvePreset
import com.example.data.RtaSnapshotModel
import com.example.ui.theme.LocalRtaColors

/**
 * Non-scrolling Column variant of [AiTuningAdvisorPanel] designed to be embedded inside
 * the 4th step of [TuningWizardPanel] without nesting scrollable containers.
 */
@Composable
fun AiTuningAdvisorEmbeddedBlock(
    uiState: AiAdvisorUiState,
    selectedProfile: HeadUnitEqProfile,
    activeTargetCurve: TargetCurvePreset,
    hasReceivedSignal: Boolean,
    snapshots: List<RtaSnapshotModel>,
    completedSteps: Set<Int>,
    onSelectProfile: (HeadUnitEqProfile) -> Unit,
    onAnalyzeLive: () -> Unit,
    onAnalyzeSnapshot: (RtaSnapshotModel) -> Unit,
    onToggleStepCompleted: (Int) -> Unit,
    onResetChecklist: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rtaColors = LocalRtaColors.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Firebase notice banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(rtaColors.targetAmber.copy(alpha = 0.14f))
                .border(1.dp, rtaColors.targetAmber.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .testTag("ai_firebase_notice_banner"),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = stringResource(R.string.ai_firebase_notice_title),
                tint = rtaColors.targetAmber,
                modifier = Modifier.size(18.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.ai_firebase_notice_title),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = rtaColors.targetAmber
                )
                Text(
                    text = stringResource(R.string.ai_firebase_notice_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Control card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, rtaColors.hairline, RoundedCornerShape(14.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = rtaColors.spectrumCyan,
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        text = stringResource(R.string.ai_advisor_title),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Цель: ${activeTargetCurve.name}",
                        style = MaterialTheme.typography.labelSmall,
                        color = rtaColors.targetAmber
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.ai_hu_eq_selector_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HeadUnitEqProfile.entries.forEach { profile ->
                        FilterChip(
                            selected = selectedProfile == profile,
                            onClick = { onSelectProfile(profile) },
                            label = { Text(profile.shortLabel, style = MaterialTheme.typography.labelMedium) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = rtaColors.spectrumCyanGlow,
                                selectedLabelColor = rtaColors.spectrumCyan
                            ),
                            modifier = Modifier.testTag("ai_eq_profile_${profile.id}")
                        )
                    }
                }
            }

            Button(
                onClick = onAnalyzeLive,
                enabled = uiState !is AiAdvisorUiState.Loading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = rtaColors.spectrumCyan,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("ai_analyze_live_button")
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (hasReceivedSignal) {
                        stringResource(R.string.ai_analyze_live_btn)
                    } else {
                        stringResource(R.string.ai_analyze_sample_btn)
                    },
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }

            if (snapshots.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.ai_analyze_snapshot_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        snapshots.take(6).forEach { snap ->
                            FilterChip(
                                selected = false,
                                onClick = { onAnalyzeSnapshot(snap) },
                                label = { Text("${snap.name} (${snap.channelTag})", style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.testTag("ai_analyze_snapshot_${snap.id}")
                            )
                        }
                    }
                }
            }
        }

        when (uiState) {
            is AiAdvisorUiState.Idle -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, rtaColors.hairline, RoundedCornerShape(14.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(26.dp))
                    Text(stringResource(R.string.ai_idle_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    Text(stringResource(R.string.ai_idle_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            is AiAdvisorUiState.Loading -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, rtaColors.hairline, RoundedCornerShape(14.dp))
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(color = rtaColors.spectrumCyan, modifier = Modifier.size(30.dp), strokeWidth = 3.dp)
                    Text(stringResource(R.string.ai_loading_message), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                }
            }

            is AiAdvisorUiState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(rtaColors.peakCoralDim)
                        .border(1.dp, rtaColors.peakCoral, RoundedCornerShape(14.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = rtaColors.peakCoral)
                        Text(stringResource(R.string.ai_error_title), style = MaterialTheme.typography.titleSmall, color = rtaColors.peakCoral)
                    }
                    Text(uiState.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                    OutlinedButton(onClick = onAnalyzeLive, modifier = Modifier.testTag("ai_retry_button")) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.ai_retry_btn))
                    }
                }
            }

            is AiAdvisorUiState.Success -> {
                val plan = uiState.plan
                AiPlanSummaryCard(plan = plan)

                if (plan.steps.isNotEmpty()) {
                    val doneCount = plan.steps.count { it.stepNumber in completedSteps }
                    val progress = doneCount.toFloat() / plan.steps.size.toFloat()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, rtaColors.hairline, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Пошаговая инструкция ГУ/DSP ($doneCount/${plan.steps.size})",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (doneCount > 0) {
                                TextButton(onClick = onResetChecklist, modifier = Modifier.testTag("ai_reset_checklist_btn")) {
                                    Text("Сбросить", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                        LinearProgressIndicator(
                            progress = { progress },
                            color = if (doneCount == plan.steps.size) rtaColors.emeraldOk else rtaColors.spectrumCyan,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                    }

                    plan.steps.forEach { step ->
                        AiStepCard(
                            step = step,
                            isCompleted = step.stepNumber in completedSteps,
                            onToggleCompleted = { onToggleStepCompleted(step.stepNumber) }
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, rtaColors.emeraldOk.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = rtaColors.emeraldOk)
                        Text(stringResource(R.string.ai_no_steps_needed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                    }
                }

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
                        text = stringResource(R.string.ai_crossover_tips_title),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    plan.crossoverAndGainTips.forEach { tip ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 6.dp)
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(rtaColors.targetAmber)
                            )
                            Text(tip, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(10.dp)
                    ) {
                        Text(plan.expectedOutcome, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

/**
 * Interactive AI Recommendation Engine panel that analyzes live RTA data (or a saved snapshot)
 * against the active Target Curve and generates a step-by-step head unit EQ adjustment checklist.
 */
@Composable
fun AiTuningAdvisorPanel(
    uiState: AiAdvisorUiState,
    selectedProfile: HeadUnitEqProfile,
    activeTargetCurve: TargetCurvePreset,
    hasReceivedSignal: Boolean,
    snapshots: List<RtaSnapshotModel>,
    completedSteps: Set<Int>,
    onSelectProfile: (HeadUnitEqProfile) -> Unit,
    onAnalyzeLive: () -> Unit,
    onAnalyzeSnapshot: (RtaSnapshotModel) -> Unit,
    onToggleStepCompleted: (Int) -> Unit,
    onResetChecklist: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rtaColors = LocalRtaColors.current

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 20.dp)
    ) {
        // Notice banner explaining Firebase AI Logic requirement
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(rtaColors.targetAmber.copy(alpha = 0.14f))
                    .border(1.dp, rtaColors.targetAmber.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .testTag("ai_firebase_notice_banner"),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = stringResource(R.string.ai_firebase_notice_title),
                    tint = rtaColors.targetAmber,
                    modifier = Modifier.size(18.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.ai_firebase_notice_title),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = rtaColors.targetAmber
                    )
                    Text(
                        text = stringResource(R.string.ai_firebase_notice_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Control card: Head Unit EQ profile selector + Analyze CTA + optional snapshot quick-analyze
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, rtaColors.hairline, RoundedCornerShape(14.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
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
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = rtaColors.spectrumCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = stringResource(R.string.ai_advisor_title),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Цель: ${activeTargetCurve.name}",
                                style = MaterialTheme.typography.labelSmall,
                                color = rtaColors.targetAmber
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.ai_hu_eq_selector_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        HeadUnitEqProfile.entries.forEach { profile ->
                            FilterChip(
                                selected = selectedProfile == profile,
                                onClick = { onSelectProfile(profile) },
                                label = {
                                    Text(
                                        text = profile.shortLabel,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = rtaColors.spectrumCyanGlow,
                                    selectedLabelColor = rtaColors.spectrumCyan
                                ),
                                modifier = Modifier.testTag("ai_eq_profile_${profile.id}")
                            )
                        }
                    }
                }

                Button(
                    onClick = onAnalyzeLive,
                    enabled = uiState !is AiAdvisorUiState.Loading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = rtaColors.spectrumCyan,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("ai_analyze_live_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (hasReceivedSignal) {
                            stringResource(R.string.ai_analyze_live_btn)
                        } else {
                            stringResource(R.string.ai_analyze_sample_btn)
                        },
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }

                if (snapshots.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringResource(R.string.ai_analyze_snapshot_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            snapshots.take(6).forEach { snap ->
                                FilterChip(
                                    selected = false,
                                    onClick = { onAnalyzeSnapshot(snap) },
                                    label = {
                                        Text(
                                            text = "${snap.name} (${snap.channelTag})",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    modifier = Modifier.testTag("ai_analyze_snapshot_${snap.id}")
                                )
                            }
                        }
                    }
                }
            }
        }

        // Dynamic State Content
        when (uiState) {
            is AiAdvisorUiState.Idle -> {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, rtaColors.hairline, RoundedCornerShape(14.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = stringResource(R.string.ai_idle_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.ai_idle_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            is AiAdvisorUiState.Loading -> {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, rtaColors.hairline, RoundedCornerShape(14.dp))
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            color = rtaColors.spectrumCyan,
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 3.dp
                        )
                        Text(
                            text = stringResource(R.string.ai_loading_message),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            is AiAdvisorUiState.Error -> {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(rtaColors.peakCoralDim)
                            .border(1.dp, rtaColors.peakCoral, RoundedCornerShape(14.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = rtaColors.peakCoral
                            )
                            Text(
                                text = stringResource(R.string.ai_error_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = rtaColors.peakCoral
                            )
                        }
                        Text(
                            text = uiState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        OutlinedButton(
                            onClick = onAnalyzeLive,
                            modifier = Modifier.testTag("ai_retry_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.ai_retry_btn))
                        }
                    }
                }
            }

            is AiAdvisorUiState.Success -> {
                val plan = uiState.plan

                // Plan Overview & Priority Issues Card
                item {
                    AiPlanSummaryCard(plan = plan)
                }

                // Step-by-Step Progress Header
                if (plan.steps.isNotEmpty()) {
                    item {
                        val doneCount = plan.steps.count { it.stepNumber in completedSteps }
                        val progress = doneCount.toFloat() / plan.steps.size.toFloat()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, rtaColors.hairline, RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Пошаговая инструкция ГУ ($doneCount/${plan.steps.size})",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (doneCount > 0) {
                                    TextButton(
                                        onClick = onResetChecklist,
                                        modifier = Modifier.testTag("ai_reset_checklist_btn")
                                    ) {
                                        Text("Сбросить", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                            LinearProgressIndicator(
                                progress = { progress },
                                color = if (doneCount == plan.steps.size) rtaColors.emeraldOk else rtaColors.spectrumCyan,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                            )
                        }
                    }

                    items(plan.steps, key = { "${plan.headUnitProfileLabel}_${it.stepNumber}_${it.bandFreqLabel}" }) { step ->
                        AiStepCard(
                            step = step,
                            isCompleted = step.stepNumber in completedSteps,
                            onToggleCompleted = { onToggleStepCompleted(step.stepNumber) }
                        )
                    }
                } else {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, rtaColors.emeraldOk.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = rtaColors.emeraldOk
                            )
                            Text(
                                text = stringResource(R.string.ai_no_steps_needed),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Crossover, Gain & Head Unit Checklist Card
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
                            text = stringResource(R.string.ai_crossover_tips_title),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        plan.crossoverAndGainTips.forEach { tip ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 6.dp)
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(rtaColors.targetAmber)
                                )
                                Text(
                                    text = tip,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(10.dp)
                        ) {
                            Text(
                                text = plan.expectedOutcome,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AiPlanSummaryCard(plan: AiTuningPlan) {
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (plan.isPlaceholder) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(rtaColors.targetAmber.copy(alpha = 0.14f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = stringResource(R.string.ai_placeholder_badge),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = rtaColors.targetAmber
                    )
                }
            }
            Text(
                text = "SQ ${plan.matchScoreBefore}% • ±${String.format(java.util.Locale.US, "%.1f", plan.rmsDeviationBefore)} дБ",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = rtaColors.spectrumCyan
            )
        }

        Text(
            text = "${plan.sourceLabel} → ${plan.targetCurveName}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = plan.overallAssessment,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )

        if (plan.priorityIssues.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = stringResource(R.string.ai_priority_issues_title),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                plan.priorityIssues.forEach { issue ->
                    Text(
                        text = "• $issue",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun AiStepCard(
    step: AiEqStep,
    isCompleted: Boolean,
    onToggleCompleted: () -> Unit
) {
    val rtaColors = LocalRtaColors.current
    val isCut = step.recommendedGainDb < 0f
    val accentColor = when {
        isCompleted -> rtaColors.emeraldOk
        isCut -> rtaColors.peakCoral
        else -> rtaColors.spectrumCyan
    }
    val gainBadgeBg = when {
        isCompleted -> rtaColors.emeraldOk.copy(alpha = 0.16f)
        isCut -> rtaColors.peakCoralDim
        else -> rtaColors.spectrumCyanGlow
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = if (isCompleted) rtaColors.emeraldOk.copy(alpha = 0.5f) else rtaColors.hairline,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onToggleCompleted)
            .padding(12.dp)
            .testTag("ai_step_card_${step.stepNumber}"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(gainBadgeBg),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${step.stepNumber}",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = accentColor
                    )
                }
                Column {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = step.bandFreqLabel,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(gainBadgeBg)
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = String.format(java.util.Locale.US, "%+.1f дБ", step.recommendedGainDb),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = accentColor
                            )
                        }
                    }
                    Text(
                        text = step.recommendedQ,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Checkbox(
                checked = isCompleted,
                onCheckedChange = { onToggleCompleted() },
                colors = CheckboxDefaults.colors(
                    checkedColor = rtaColors.emeraldOk,
                    uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.testTag("ai_step_checkbox_${step.stepNumber}")
            )
        }

        Text(
            text = step.actionTitle,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
                textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None
            ),
            color = if (isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = step.reason,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = "Проверка: ${step.verificationTip}",
            style = MaterialTheme.typography.labelSmall,
            color = rtaColors.spectrumCyan.copy(alpha = 0.9f)
        )
    }
}
