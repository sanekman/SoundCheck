package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.RtaViewModel
import com.example.ui.components.AutoCalibrationDialog
import com.example.ui.components.TargetCurveManagerDialog
import com.example.ui.screens.DspEqScreen
import com.example.ui.screens.MicSetupScreen
import com.example.ui.screens.RtaAnalyzerScreen
import com.example.ui.screens.SignalGeneratorScreen
import com.example.ui.theme.AutoRtaTheme
import com.example.ui.theme.LocalRtaColors

private enum class AutoRtaTab(
    val routeId: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    RTA("rta_analyzer", R.string.nav_rta, Icons.Filled.Equalizer, Icons.Outlined.Equalizer),
    DSP_EQ("dsp_eq", R.string.nav_eq, Icons.Filled.Tune, Icons.Outlined.Tune),
    GENERATOR("signal_generator", R.string.nav_generator, Icons.Filled.GraphicEq, Icons.Outlined.GraphicEq),
    MIC_SETUP("mic_setup", R.string.nav_setup, Icons.Filled.Mic, Icons.Outlined.MicNone)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AutoRtaTheme {
                AutoRtaApp()
            }
        }
    }
}

@Composable
private fun AutoRtaApp(
    viewModel: RtaViewModel = viewModel()
) {
    val context = LocalContext.current
    val rtaColors = LocalRtaColors.current
    val snackbarHostState = remember { SnackbarHostState() }

    var currentTab by rememberSaveable { mutableStateOf(AutoRtaTab.RTA) }
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionDeniedOnce by remember { mutableStateOf(false) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (granted) {
            permissionDeniedOnce = false
            viewModel.startRtaCapture()
        } else {
            permissionDeniedOnce = true
        }
    }

    val calFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importCalibrationUri(context.contentResolver, uri)
        }
    }

    val targetCsvPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importTargetCurveCsvUri(context.contentResolver, uri)
        }
    }

    val exportPngLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/png")
    ) { uri ->
        if (uri != null) {
            viewModel.exportPendingSnapshotAsPng(context.contentResolver, uri)
        }
    }

    val exportCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            viewModel.exportPendingSnapshotAsCsv(context.contentResolver, uri)
        }
    }

    var showTargetManagerDialog by remember { mutableStateOf(false) }

    // Collect ViewModel state flows
    val liveState by viewModel.liveState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val allTargetCurves by viewModel.allTargetCurves.collectAsStateWithLifecycle()
    val generatorState by viewModel.generatorState.collectAsStateWithLifecycle()
    val devices by viewModel.availableDevices.collectAsStateWithLifecycle()
    val selectedDeviceId by viewModel.selectedDeviceId.collectAsStateWithLifecycle()
    val isCapturing by viewModel.isCapturing.collectAsStateWithLifecycle()
    val captureError by viewModel.captureError.collectAsStateWithLifecycle()
    val calibrationProfiles by viewModel.calibrationProfiles.collectAsStateWithLifecycle()
    val snapshots by viewModel.snapshots.collectAsStateWithLifecycle()
    val overlaySnapshotId by viewModel.overlaySnapshotId.collectAsStateWithLifecycle()
    val eqRangeFilter by viewModel.eqRangeFilter.collectAsStateWithLifecycle()
    val eqSortByPriority by viewModel.eqSortByPriority.collectAsStateWithLifecycle()
    val autoCalState by viewModel.autoCalState.collectAsStateWithLifecycle()
    val speakerDistances by viewModel.speakerDistances.collectAsStateWithLifecycle()
    val cabinTempC by viewModel.cabinTempC.collectAsStateWithLifecycle()
    val aiAdvisorState by viewModel.aiAdvisorState.collectAsStateWithLifecycle()
    val selectedHeadUnitProfile by viewModel.selectedHeadUnitProfile.collectAsStateWithLifecycle()
    val completedAiSteps by viewModel.completedAiSteps.collectAsStateWithLifecycle()
    val wizardState by viewModel.wizardState.collectAsStateWithLifecycle()
    val waterfallState by viewModel.waterfallState.collectAsStateWithLifecycle()

    val activeDevice = remember(devices, selectedDeviceId) {
        devices.firstOrNull { it.id == selectedDeviceId } ?: devices.firstOrNull()
    }
    val activeCalibration = remember(calibrationProfiles, settings.activeCalibrationProfileId) {
        calibrationProfiles.firstOrNull { it.id == settings.activeCalibrationProfileId }
    }
    val overlaySnapshot = remember(snapshots, overlaySnapshotId) {
        snapshots.firstOrNull { it.id == overlaySnapshotId }
    }
    val activeTargetCurve = remember(allTargetCurves, settings.targetCurveId) {
        allTargetCurves.firstOrNull { it.id == settings.targetCurveId } ?: allTargetCurves.first()
    }

    // Auto-start RTA capture on launch if permission is already granted
    LaunchedEffect(hasMicPermission) {
        if (hasMicPermission && !isCapturing) {
            viewModel.startRtaCapture()
        }
    }

    // Snackbar messages
    LaunchedEffect(Unit) {
        viewModel.snackbarMessage.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    // Handle back press on non-home tabs by returning to RTA screen
    BackHandler(enabled = currentTab != AutoRtaTab.RTA) {
        currentTab = AutoRtaTab.RTA
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        AutoRtaTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = {
                                    Text(
                                        text = stringResource(tab.labelRes),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = rtaColors.spectrumCyan,
                                    selectedTextColor = rtaColors.spectrumCyan,
                                    indicatorColor = rtaColors.spectrumCyanGlow,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.testTag("nav_tab_${tab.routeId}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        AutoRtaTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = {
                                    Text(
                                        text = stringResource(tab.labelRes),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = rtaColors.spectrumCyan,
                                    selectedTextColor = rtaColors.spectrumCyan,
                                    indicatorColor = rtaColors.spectrumCyanGlow,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.testTag("nav_rail_${tab.routeId}")
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    when (currentTab) {
                        AutoRtaTab.RTA -> RtaAnalyzerScreen(
                            liveState = liveState,
                            settings = settings,
                            allTargetCurves = allTargetCurves,
                            activeTargetCurve = activeTargetCurve,
                            generatorState = generatorState,
                            activeDevice = activeDevice,
                            activeCalibration = activeCalibration,
                            overlaySnapshot = overlaySnapshot,
                            isCapturing = isCapturing,
                            captureError = captureError,
                            hasMicPermission = hasMicPermission,
                            permissionDeniedOnce = permissionDeniedOnce,
                            onRequestMicPermission = {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            },
                            onToggleCapture = {
                                if (!hasMicPermission) {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                } else {
                                    viewModel.toggleRtaCapture(true)
                                }
                            },
                            onResetPeaks = viewModel::resetPeaksAndAverage,
                            onTogglePinkNoise = viewModel::toggleQuickPinkNoise,
                            onSaveSnapshot = viewModel::saveCurrentSnapshot,
                            onExportCurrentPng = { name, channel ->
                                val fileName = viewModel.prepareCurrentSnapshotExport(name, channel, "png")
                                exportPngLauncher.launch(fileName)
                            },
                            onExportCurrentCsv = { name, channel ->
                                val fileName = viewModel.prepareCurrentSnapshotExport(name, channel, "csv")
                                exportCsvLauncher.launch(fileName)
                            },
                            onClearOverlay = viewModel::clearOverlaySnapshot,
                            onOpenMicSetup = { currentTab = AutoRtaTab.MIC_SETUP },
                            onOpenAutoCal = {
                                if (!hasMicPermission) {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                } else {
                                    viewModel.openAutoCalibrationWizard(true)
                                }
                            },
                            onOpenTargetManager = { showTargetManagerDialog = true },
                            onToggleShowTargetCurve = viewModel::setShowTargetCurve,
                            onSelectTargetCurve = viewModel::selectTargetCurve,
                            onSelectWeighting = viewModel::selectWeighting,
                            onSelectAveraging = viewModel::selectAveraging,
                            onSelectDisplayMode = viewModel::selectDisplayMode,
                            onTogglePeakHold = viewModel::setPeakHold,
                            waterfallState = waterfallState,
                            onSelectWaterfallMode = viewModel::setWaterfallMode,
                            onSelectWaterfallSource = viewModel::setWaterfallSignalSource,
                            onSelectWaterfallWindowMs = viewModel::setWaterfallWindowMs,
                            onSelectWaterfallFreqRange = viewModel::setWaterfallFreqRange,
                            onStartWaterfallCsd = {
                                if (!hasMicPermission) {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                } else {
                                    viewModel.startWaterfallCsdCapture(true)
                                }
                            },
                            onTriggerWaterfallManualDecay = viewModel::triggerManualWaterfallDecay,
                            onCancelWaterfallCapture = viewModel::cancelWaterfallCapture
                        )

                        AutoRtaTab.DSP_EQ -> DspEqScreen(
                            liveState = liveState,
                            settings = settings,
                            activeTargetCurve = activeTargetCurve,
                            generatorState = generatorState,
                            wizardState = wizardState,
                            isCapturing = isCapturing,
                            rangeFilter = eqRangeFilter,
                            sortByPriority = eqSortByPriority,
                            speakerDistances = speakerDistances,
                            cabinTempC = cabinTempC,
                            snapshots = snapshots,
                            aiAdvisorState = aiAdvisorState,
                            selectedHeadUnitProfile = selectedHeadUnitProfile,
                            completedAiSteps = completedAiSteps,
                            calculateDelayMs = viewModel::calculateDelayMs,
                            onToggleCapture = {
                                if (!hasMicPermission) {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                } else {
                                    viewModel.toggleRtaCapture(true)
                                }
                            },
                            onToggleAutoAlign = viewModel::setAutoAlignTarget,
                            onManualTargetRefChange = viewModel::setManualTargetRefDb,
                            onSelectRangeFilter = viewModel::setEqRangeFilter,
                            onToggleSortPriority = viewModel::setEqSortByPriority,
                            onUpdateSpeakerDistance = viewModel::updateSpeakerDistance,
                            onUpdateCabinTemp = viewModel::updateCabinTemp,
                            onSelectWizardStep = viewModel::selectWizardStep,
                            onSetWizardWayCount = viewModel::setWizardWayCount,
                            onSetWizardTopology = viewModel::setWizardCrossoverTopology,
                            onSetWizardHasSubwoofer = viewModel::setWizardHasSubwoofer,
                            onUpdateWizardCrossover = viewModel::updateWizardChannelCrossover,
                            onResetWizardCrossovers = viewModel::resetWizardDefaultCrossovers,
                            onSelectWizardSpeakerSlot = viewModel::selectWizardSpeakerSlot,
                            onStartMeasureWizardSpeaker = {
                                if (!hasMicPermission) {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                } else {
                                    viewModel.startMeasureCurrentWizardSpeaker(true)
                                }
                            },
                            onToggleWizardPinkNoise = viewModel::toggleWizardPinkNoise,
                            onClearWizardMeasurements = viewModel::clearWizardMeasurements,
                            onSelectHeadUnitProfile = viewModel::selectHeadUnitProfile,
                            onAnalyzeLiveWithAi = viewModel::generateAiRecommendationsForLive,
                            onAnalyzeSnapshotWithAi = viewModel::generateAiRecommendationsForSnapshot,
                            onToggleAiStepCompleted = viewModel::toggleAiStepCompleted,
                            onResetAiChecklist = viewModel::resetAiChecklist
                        )

                        AutoRtaTab.GENERATOR -> SignalGeneratorScreen(
                            state = generatorState,
                            onTogglePlay = viewModel::toggleGenerator,
                            onSelectSignalType = viewModel::setSignalType,
                            onSelectChannel = viewModel::setOutputChannel,
                            onGainChange = viewModel::setGeneratorGainDb,
                            onSineFreqChange = viewModel::setSineFrequencyHz
                        )

                        AutoRtaTab.MIC_SETUP -> MicSetupScreen(
                            devices = devices,
                            selectedDeviceId = selectedDeviceId,
                            calibrationProfiles = calibrationProfiles,
                            settings = settings,
                            snapshots = snapshots,
                            overlaySnapshotId = overlaySnapshotId,
                            onRefreshDevices = viewModel::refreshAudioDevices,
                            onSelectDevice = { devId -> viewModel.selectInputDevice(devId, hasMicPermission) },
                            onOpenAutoCalWizard = {
                                if (!hasMicPermission) {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                } else {
                                    viewModel.openAutoCalibrationWizard(true)
                                }
                            },
                            onImportCalFile = {
                                calFilePickerLauncher.launch(arrayOf("text/*", "application/octet-stream", "*/*"))
                            },
                            onSelectCalibrationProfile = viewModel::setActiveCalibrationProfile,
                            onDeleteCalibrationProfile = viewModel::deleteCalibrationProfile,
                            onManualSplOffsetChange = viewModel::setManualSplOffsetDb,
                            onToggleOverlaySnapshot = viewModel::toggleOverlaySnapshot,
                            onExportSnapshotPng = { snap ->
                                val fileName = viewModel.prepareSavedSnapshotExport(snap, "png")
                                exportPngLauncher.launch(fileName)
                            },
                            onExportSnapshotCsv = { snap ->
                                val fileName = viewModel.prepareSavedSnapshotExport(snap, "csv")
                                exportCsvLauncher.launch(fileName)
                            },
                            onDeleteSnapshot = viewModel::deleteSnapshot
                        )
                    }
                }
            }
        }
    }

    // Guided 4-Step Auto-Calibration Wizard Modal
    AutoCalibrationDialog(
        state = autoCalState,
        activeMicName = activeDevice?.name ?: "Микрофон RTA",
        isExternalMic = activeDevice?.isExternal == true,
        liveSplDb = liveState.overallSplDb,
        live1kHzDb = liveState.bands31Db.getOrElse(17) { liveState.overallSplDb },
        isPhoneNoisePlaying = generatorState.isPlaying,
        onDismiss = viewModel::closeAutoCalibrationWizard,
        onSetSignalSource = viewModel::setAutoCalSignalSource,
        onToggleManualPhoneNoise = viewModel::toggleAutoCalPreviewNoise,
        onSetTargetSpl = viewModel::setAutoCalTargetSpl,
        onRunQuietCheck = { viewModel.startQuietEnvironmentCheck(hasMicPermission) },
        onProceedToSensitivity = viewModel::proceedToSensitivityStep,
        onRunSensitivity = { viewModel.runSensitivityCalibration(hasMicPermission) },
        onRunFrSweep = { viewModel.runFrCorrectionSweep(hasMicPermission) },
        onSaveProfile = { customName -> viewModel.saveAndApplyAutoCalibration(customName) }
    )

    // Target Curve Overlay Manager & Custom CSV Importer Modal
    if (showTargetManagerDialog) {
        TargetCurveManagerDialog(
            allCurves = allTargetCurves,
            activeCurve = activeTargetCurve,
            settings = settings,
            alignedReferenceDb = liveState.eqAnalysis.alignedReferenceDb,
            onDismiss = { showTargetManagerDialog = false },
            onToggleShowTarget = viewModel::setShowTargetCurve,
            onToggleAutoAlign = viewModel::setAutoAlignTarget,
            onManualTargetRefChange = viewModel::setManualTargetRefDb,
            onToleranceChange = viewModel::setTargetToleranceDb,
            onSelectCurve = viewModel::selectTargetCurve,
            onImportCsvFile = {
                targetCsvPickerLauncher.launch(
                    arrayOf("text/csv", "text/comma-separated-values", "text/plain", "*/*")
                )
            },
            onImportCsvText = viewModel::importTargetCurveCsvText,
            onDeleteCustomCurve = viewModel::deleteCustomTargetCurve
        )
    }
}
