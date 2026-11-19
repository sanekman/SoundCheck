package com.example.ui

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AiAdvisorUiState
import com.example.ai.AiTuningRepository
import com.example.ai.AiTuningRequest
import com.example.ai.HeadUnitEqProfile
import com.example.ai.PlaceholderAiTuningRepository
import com.example.audio.AudioInputDeviceUi
import com.example.audio.AudioInputManager
import com.example.audio.AutoCalSignalSource
import com.example.audio.AutoCalStep
import com.example.audio.AutoCalUiState
import com.example.audio.AutoCalibrationAccumulator
import com.example.audio.AveragingMode
import com.example.audio.CalibrationParser
import com.example.audio.EqAnalysisSummary
import com.example.audio.FftProcessor
import com.example.audio.FrequencyRangeFilter
import com.example.audio.FrequencyWeighting
import com.example.audio.OutputChannel
import com.example.audio.RtaBands
import com.example.audio.RtaDisplayMode
import com.example.audio.SignalGeneratorEngine
import com.example.audio.SignalType
import com.example.audio.SnapshotExportPayload
import com.example.audio.SnapshotExporter
import com.example.audio.AudioSystemConfig
import com.example.audio.ChannelCrossoverConfig
import com.example.audio.CrossoverTopology
import com.example.audio.SpeakerGroupType
import com.example.audio.SystemWayCount
import com.example.audio.TargetCurveCsvParser
import com.example.audio.TargetCurvePreset
import com.example.audio.TargetCurves
import com.example.audio.TuningWizardEngine
import com.example.audio.TuningWizardUiState
import com.example.audio.WaterfallCapturePhase
import com.example.audio.WaterfallEngine
import com.example.audio.WaterfallFreqRange
import com.example.audio.WaterfallMode
import com.example.audio.WaterfallSignalSource
import com.example.audio.WaterfallSlice
import com.example.audio.WaterfallUiState
import com.example.audio.WizardStep
import com.example.data.CalibrationProfileModel
import com.example.data.RtaDatabase
import com.example.data.RtaPreferences
import com.example.data.RtaRepository
import com.example.data.RtaSettings
import com.example.data.RtaSnapshotModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SpeakerDistance(
    val id: String,
    val label: String,
    val distanceCm: Float
)

data class RtaLiveUiState(
    val bands31Db: FloatArray = FloatArray(31) { 25f },
    val peakHold31Db: FloatArray = FloatArray(31) { 25f },
    val trace128Db: FloatArray = FloatArray(RtaBands.TRACE_POINTS) { 25f },
    val overallSplDb: Float = 0f,
    val peakFreqHz: Float = 0f,
    val peakBandDb: Float = 0f,
    val inputPeakNormalized: Float = 0f,
    val isClipping: Boolean = false,
    val hasReceivedSignal: Boolean = false,
    val eqAnalysis: EqAnalysisSummary = TargetCurves.analyzeAgainstTarget(
        measuredBandsDb = FloatArray(31) { 25f },
        preset = TargetCurves.HARMAN,
        autoAlign = true,
        manualTargetRefDb = 75f
    )
)

data class GeneratorUiState(
    val isPlaying: Boolean = false,
    val signalType: SignalType = SignalType.PINK_NOISE,
    val outputChannel: OutputChannel = OutputChannel.BOTH,
    val gainDb: Float = -14f,
    val sineFreqHz: Float = 1000f
)

class RtaViewModel(application: Application) : AndroidViewModel(application) {

    private val database = RtaDatabase.getInstance(application)
    private val preferences = RtaPreferences(application)
    private val repository = RtaRepository(
        database.snapshotDao(),
        database.calibrationDao(),
        database.customTargetCurveDao(),
        preferences
    )

    private val audioInputManager = AudioInputManager(application)
    private val fftProcessor = FftProcessor(fftSize = 8192, sampleRate = 48000)
    private val signalGenerator = SignalGeneratorEngine()
    private val autoCalAccumulator = AutoCalibrationAccumulator()
    private val aiTuningRepository: AiTuningRepository = PlaceholderAiTuningRepository()

    val settings: StateFlow<RtaSettings> = preferences.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, RtaSettings())

    val snapshots: StateFlow<List<RtaSnapshotModel>> = repository.snapshotsFlow
        .map { list -> list.map { repository.toSnapshotModel(it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val calibrationProfiles: StateFlow<List<CalibrationProfileModel>> = repository.calibrationProfilesFlow
        .map { list -> list.map { repository.toCalibrationModel(it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTargetCurves: StateFlow<List<TargetCurvePreset>> = repository.customTargetCurvesFlow
        .map { customEntities ->
            TargetCurves.ALL_PRESETS + customEntities.map { repository.toTargetCurvePreset(it) }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, TargetCurves.ALL_PRESETS)

    val availableDevices: StateFlow<List<AudioInputDeviceUi>> = audioInputManager.availableDevices
    val selectedDeviceId: StateFlow<Int?> = audioInputManager.selectedDeviceId
    val isCapturing: StateFlow<Boolean> = audioInputManager.isCapturing
    val captureError: StateFlow<String?> = audioInputManager.captureError

    private val _liveState = MutableStateFlow(RtaLiveUiState())
    val liveState: StateFlow<RtaLiveUiState> = _liveState.asStateFlow()

    private val _generatorState = MutableStateFlow(GeneratorUiState())
    val generatorState: StateFlow<GeneratorUiState> = _generatorState.asStateFlow()

    private val _overlaySnapshotId = MutableStateFlow<Long?>(null)
    val overlaySnapshotId: StateFlow<Long?> = _overlaySnapshotId.asStateFlow()

    private val _eqRangeFilter = MutableStateFlow(FrequencyRangeFilter.ALL)
    val eqRangeFilter: StateFlow<FrequencyRangeFilter> = _eqRangeFilter.asStateFlow()

    private val _eqSortByPriority = MutableStateFlow(false)
    val eqSortByPriority: StateFlow<Boolean> = _eqSortByPriority.asStateFlow()

    private val _autoCalState = MutableStateFlow(AutoCalUiState())
    val autoCalState: StateFlow<AutoCalUiState> = _autoCalState.asStateFlow()

    private val _cabinTempC = MutableStateFlow(20f)
    val cabinTempC: StateFlow<Float> = _cabinTempC.asStateFlow()

    private val _speakerDistances = MutableStateFlow(
        listOf(
            SpeakerDistance("fl_tw", "Фронт Л (ВЧ/СЧ)", 92f),
            SpeakerDistance("fr_tw", "Фронт П (ВЧ/СЧ)", 136f),
            SpeakerDistance("fl_mb", "Фронт Л (Мидбас)", 115f),
            SpeakerDistance("fr_mb", "Фронт П (Мидбас)", 152f),
            SpeakerDistance("sub", "Сабвуфер (Багажник)", 235f)
        )
    )
    val speakerDistances: StateFlow<List<SpeakerDistance>> = _speakerDistances.asStateFlow()

    private val _selectedHeadUnitProfile = MutableStateFlow(HeadUnitEqProfile.EQ_13_BAND)
    val selectedHeadUnitProfile: StateFlow<HeadUnitEqProfile> = _selectedHeadUnitProfile.asStateFlow()

    private val _aiAdvisorState = MutableStateFlow<AiAdvisorUiState>(AiAdvisorUiState.Idle)
    val aiAdvisorState: StateFlow<AiAdvisorUiState> = _aiAdvisorState.asStateFlow()

    private val _completedAiSteps = MutableStateFlow<Set<Int>>(emptySet())
    val completedAiSteps: StateFlow<Set<Int>> = _completedAiSteps.asStateFlow()

    private val _wizardState = MutableStateFlow(TuningWizardUiState())
    val wizardState: StateFlow<TuningWizardUiState> = _wizardState.asStateFlow()

    private val _waterfallState = MutableStateFlow(WaterfallUiState())
    val waterfallState: StateFlow<WaterfallUiState> = _waterfallState.asStateFlow()

    private val _snackbarMessage = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val snackbarMessage: SharedFlow<String> = _snackbarMessage.asSharedFlow()

    // Smoothing buffers
    private val smoothedBands = FloatArray(31) { 25f }
    private val peakHoldBands = FloatArray(31) { 25f }
    private val smoothedTrace = FloatArray(RtaBands.TRACE_POINTS) { 25f }
    private var smoothedSpl = 30f
    private var avgFrameCounter = 0

    // Auto-calibration phase tracking
    @Volatile private var activeCalCaptureMode: AutoCalStep = AutoCalStep.IDLE
    private var autoCalJob: Job? = null

    // Sequential per-speaker wizard measurement accumulator
    @Volatile private var isAccumulatingWizardSpeaker: Boolean = false
    private val wizardAccLock = Any()
    private val wizardBandsSum = DoubleArray(31)
    private var wizardSplSum = 0.0
    private var wizardFrameCount = 0
    private var wizardMeasureJob: Job? = null

    // Waterfall (CSD & Live rolling) buffers
    private val waterfallLock = Any()
    @Volatile private var waterfallPhaseFlag: WaterfallCapturePhase = WaterfallCapturePhase.IDLE
    @Volatile private var waterfallLiveEnabled: Boolean = false
    private val waterfallLiveRing = ArrayDeque<WaterfallSlice>(22)
    private var waterfallLiveTick = 0
    private var waterfallSteadySpl = 0f
    private var waterfallSteadyFrames = 0
    private var waterfallPreBuffer = ShortArray(8192)
    private var waterfallDecayPcm = ShortArray(0)
    private var waterfallDecayWritePos = 0
    private var waterfallCutoffIdx = 0
    private var waterfallJob: Job? = null

    init {
        audioInputManager.registerDeviceListener()

        viewModelScope.launch {
            signalGenerator.isPlaying.collect { playing ->
                _generatorState.update { it.copy(isPlaying = playing) }
            }
        }

        // Recompute target curve recommendations whenever settings or custom target curves change
        viewModelScope.launch {
            settings.collect {
                recomputeTargetAnalysis()
            }
        }
        viewModelScope.launch {
            allTargetCurves.collect {
                recomputeTargetAnalysis()
            }
        }
    }

    fun resolveActiveTargetCurve(curveId: String = settings.value.targetCurveId): TargetCurvePreset {
        return allTargetCurves.value.firstOrNull { it.id == curveId } ?: TargetCurves.byId(curveId)
    }

    fun refreshAudioDevices() {
        audioInputManager.refreshInputDevices(autoPreferExternal = true)
    }

    fun selectInputDevice(deviceId: Int, hasMicPermission: Boolean) {
        val wasRunning = isCapturing.value
        audioInputManager.selectDevice(deviceId)
        if (wasRunning && hasMicPermission) {
            startRtaCapture()
        }
    }

    fun toggleRtaCapture(hasMicPermission: Boolean) {
        if (!hasMicPermission) {
            emitSnackbar("Необходимо разрешение на доступ к микрофону")
            return
        }
        if (isCapturing.value) {
            audioInputManager.stopCapture()
        } else {
            startRtaCapture()
        }
    }

    fun startRtaCapture() {
        resetAveragingInternal()
        audioInputManager.startCapture(viewModelScope) { pcm ->
            handleAudioFrame(pcm)
        }
    }

    fun stopRtaCapture() {
        audioInputManager.stopCapture()
    }

    fun resetPeaksAndAverage() {
        resetAveragingInternal()
        val currentBands = _liveState.value.bands31Db.copyOf()
        for (i in 0 until 31) peakHoldBands[i] = currentBands[i]
        _liveState.update { it.copy(peakHold31Db = peakHoldBands.copyOf()) }
        emitSnackbar("Пики и усреднение сброшены")
    }

    private fun resetAveragingInternal() {
        avgFrameCounter = 0
    }

    private fun getActiveCalibrationProfile(): CalibrationProfileModel? {
        val id = settings.value.activeCalibrationProfileId
        if (id < 0L) return null
        return calibrationProfiles.value.firstOrNull { it.id == id }
    }

    private fun handleAudioFrame(pcm: ShortArray) {
        val currentSettings = settings.value
        val activeCal = getActiveCalibrationProfile()

        // During Auto-Calibration, measure raw microphone response WITHOUT applying an old calibration profile
        val isAutoCalMeasuring = activeCalCaptureMode != AutoCalStep.IDLE
        val calPoints = if (isAutoCalMeasuring) emptyList() else (activeCal?.points ?: emptyList())
        val calSens = if (isAutoCalMeasuring) 0f else (activeCal?.sensitivityOffsetDb ?: 0f)
        val manualSpl = if (isAutoCalMeasuring) 0f else currentSettings.manualSplOffsetDb
        val weighting = if (isAutoCalMeasuring) FrequencyWeighting.Z else currentSettings.weighting

        val frame = fftProcessor.process(
            pcmSamples = pcm,
            weighting = weighting,
            calibrationPoints = calPoints,
            calibrationSensitivityOffsetDb = calSens,
            manualSplOffsetDb = manualSpl
        )

        // Feed auto-calibration accumulator if an auto-calibration step is actively measuring
        when (activeCalCaptureMode) {
            AutoCalStep.QUIET_CHECK -> {
                autoCalAccumulator.addAmbientFrame(frame.overallSplDb)
                _autoCalState.update { it.copy(currentAmbientDb = frame.overallSplDb) }
            }
            AutoCalStep.SENSITIVITY_MEASUREMENT -> {
                autoCalAccumulator.addSensitivityFrame(frame.bands31Db, frame.overallSplDb)
            }
            AutoCalStep.FR_SWEEP_MEASUREMENT -> {
                autoCalAccumulator.addFrFrame(frame.bands31Db)
            }
            else -> Unit
        }

        // Feed Tuning Wizard per-speaker averager if actively capturing a speaker
        if (isAccumulatingWizardSpeaker) {
            synchronized(wizardAccLock) {
                for (i in 0 until 31) {
                    wizardBandsSum[i] += frame.bands31Db[i].toDouble()
                }
                wizardSplSum += frame.overallSplDb.toDouble()
                wizardFrameCount++
            }
        }

        // Feed Waterfall Live rolling history and/or CSD decay recorder
        handleWaterfallFrame(pcm, frame)

        val mode = currentSettings.averagingMode
        avgFrameCounter++

        if (avgFrameCounter == 1) {
            System.arraycopy(frame.bands31Db, 0, smoothedBands, 0, 31)
            System.arraycopy(frame.bands31Db, 0, peakHoldBands, 0, 31)
            System.arraycopy(frame.trace128Db, 0, smoothedTrace, 0, RtaBands.TRACE_POINTS)
            smoothedSpl = frame.overallSplDb
        } else {
            val useCumulative = mode == AveragingMode.RTA_AVG
            val n = avgFrameCounter.coerceAtMost(120).toFloat()

            for (i in 0 until 31) {
                val v = frame.bands31Db[i]
                smoothedBands[i] = if (useCumulative) {
                    smoothedBands[i] + (v - smoothedBands[i]) / n
                } else {
                    val alpha = if (v > smoothedBands[i]) mode.attackAlpha else mode.releaseAlpha
                    smoothedBands[i] + alpha * (v - smoothedBands[i])
                }
                if (smoothedBands[i] > peakHoldBands[i]) {
                    peakHoldBands[i] = smoothedBands[i]
                }
            }
            for (i in 0 until RtaBands.TRACE_POINTS) {
                val v = frame.trace128Db[i]
                smoothedTrace[i] = if (useCumulative) {
                    smoothedTrace[i] + (v - smoothedTrace[i]) / n
                } else {
                    val alpha = if (v > smoothedTrace[i]) mode.attackAlpha else mode.releaseAlpha
                    smoothedTrace[i] + alpha * (v - smoothedTrace[i])
                }
            }
            val splAlpha = if (frame.overallSplDb > smoothedSpl) mode.attackAlpha else mode.releaseAlpha
            smoothedSpl += splAlpha * (frame.overallSplDb - smoothedSpl)
        }

        val bandsCopy = smoothedBands.copyOf()
        val traceCopy = smoothedTrace.copyOf()
        val preset = resolveActiveTargetCurve(currentSettings.targetCurveId)
        val analysis = TargetCurves.analyzeAgainstTarget(
            measuredBandsDb = bandsCopy,
            preset = preset,
            autoAlign = currentSettings.autoAlignTarget,
            manualTargetRefDb = currentSettings.manualTargetRefDb,
            toleranceDb = currentSettings.targetToleranceDb
        )

        _liveState.value = RtaLiveUiState(
            bands31Db = bandsCopy,
            peakHold31Db = peakHoldBands.copyOf(),
            trace128Db = traceCopy,
            overallSplDb = smoothedSpl,
            peakFreqHz = frame.peakFreqHz,
            peakBandDb = frame.peakBandDb,
            inputPeakNormalized = frame.inputPeakNormalized,
            isClipping = frame.isClipping,
            hasReceivedSignal = true,
            eqAnalysis = analysis
        )
    }

    private fun recomputeTargetAnalysis() {
        val current = _liveState.value
        val currentSettings = settings.value
        val preset = resolveActiveTargetCurve(currentSettings.targetCurveId)
        val analysis = TargetCurves.analyzeAgainstTarget(
            measuredBandsDb = current.bands31Db,
            preset = preset,
            autoAlign = currentSettings.autoAlignTarget,
            manualTargetRefDb = currentSettings.manualTargetRefDb,
            toleranceDb = currentSettings.targetToleranceDb
        )
        _liveState.value = current.copy(eqAnalysis = analysis)
    }

    // --- Settings Updates ---

    fun selectTargetCurve(id: String) = viewModelScope.launch {
        preferences.setTargetCurve(id)
        preferences.setShowTargetCurve(true)
    }
    fun setShowTargetCurve(show: Boolean) = viewModelScope.launch { preferences.setShowTargetCurve(show) }
    fun setTargetToleranceDb(db: Float) = viewModelScope.launch { preferences.setTargetToleranceDb(db) }
    fun selectWeighting(w: FrequencyWeighting) = viewModelScope.launch { preferences.setWeighting(w) }

    fun importTargetCurveCsvUri(contentResolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            try {
                val (fileName, text) = withContext(Dispatchers.IO) {
                    var name = "Custom_Target.csv"
                    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0 && cursor.moveToFirst()) {
                            name = cursor.getString(idx) ?: name
                        }
                    }
                    val content = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: throw IllegalStateException("Не удалось прочитать файл CSV")
                    name to content
                }
                importTargetCurveCsvTextInternal(fileName, text)
            } catch (e: Throwable) {
                emitSnackbar("Ошибка импорта CSV: ${e.message ?: "проверьте формат (Гц, дБ)"}")
            }
        }
    }

    fun importTargetCurveCsvText(name: String, csvText: String) {
        viewModelScope.launch {
            importTargetCurveCsvTextInternal(name.trim().ifEmpty { "Пользовательская CSV" }, csvText)
        }
    }

    private suspend fun importTargetCurveCsvTextInternal(name: String, csvText: String) {
        val parsed = TargetCurveCsvParser.parseCsv(csvText)
        if (parsed == null) {
            emitSnackbar("CSV должен содержать минимум 2 строки вида «Частота, дБ»")
            return
        }
        val cleanName = name.removeSuffix(".csv").removeSuffix(".txt").trim().ifEmpty { "CSV Кривая" }
        val desc = "Импорт CSV (${parsed.sourcePointCount} точек)" +
            if (parsed.normalizedTo1kHz) " • нормирована к 1 кГц" else ""
        val dbId = repository.saveCustomTargetCurve(
            name = cleanName,
            description = desc,
            sourcePointCount = parsed.sourcePointCount,
            relativeDb31 = parsed.relativeDb31
        )
        preferences.setTargetCurve("custom_$dbId")
        preferences.setShowTargetCurve(true)
        emitSnackbar("Целевая кривая «$cleanName» загружена и наложена на RTA")
    }

    fun deleteCustomTargetCurve(dbId: Long) {
        viewModelScope.launch {
            val targetId = "custom_$dbId"
            if (settings.value.targetCurveId == targetId) {
                preferences.setTargetCurve(TargetCurves.HARMAN.id)
            }
            repository.deleteCustomTargetCurve(dbId)
            emitSnackbar("Пользовательская кривая удалена")
        }
    }

    fun selectAveraging(m: AveragingMode) = viewModelScope.launch {
        preferences.setAveraging(m)
        resetAveragingInternal()
    }
    fun selectDisplayMode(m: RtaDisplayMode) = viewModelScope.launch { preferences.setDisplayMode(m) }
    fun setPeakHold(enabled: Boolean) = viewModelScope.launch { preferences.setPeakHold(enabled) }
    fun setAutoAlignTarget(enabled: Boolean) = viewModelScope.launch { preferences.setAutoAlignTarget(enabled) }
    fun setManualTargetRefDb(db: Float) = viewModelScope.launch { preferences.setManualTargetRefDb(db) }
    fun setManualSplOffsetDb(db: Float) = viewModelScope.launch { preferences.setManualSplOffsetDb(db) }
    fun setActiveCalibrationProfile(id: Long) = viewModelScope.launch { preferences.setActiveCalibrationProfileId(id) }

    fun setEqRangeFilter(filter: FrequencyRangeFilter) {
        _eqRangeFilter.value = filter
    }

    fun setEqSortByPriority(priority: Boolean) {
        _eqSortByPriority.value = priority
    }

    // --- Signal Generator ---

    fun toggleGenerator() {
        if (_generatorState.value.isPlaying) {
            signalGenerator.stop()
        } else {
            signalGenerator.signalType = _generatorState.value.signalType
            signalGenerator.outputChannel = _generatorState.value.outputChannel
            signalGenerator.gainDb = _generatorState.value.gainDb
            signalGenerator.sineFreqHz = _generatorState.value.sineFreqHz
            signalGenerator.start(viewModelScope)
        }
    }

    fun toggleQuickPinkNoise() {
        if (_generatorState.value.isPlaying && _generatorState.value.signalType == SignalType.PINK_NOISE) {
            signalGenerator.stop()
        } else {
            setSignalType(SignalType.PINK_NOISE)
            if (!_generatorState.value.isPlaying) {
                toggleGenerator()
            }
        }
    }

    fun setSignalType(type: SignalType) {
        signalGenerator.signalType = type
        _generatorState.update { it.copy(signalType = type) }
    }

    fun setOutputChannel(channel: OutputChannel) {
        signalGenerator.outputChannel = channel
        _generatorState.update { it.copy(outputChannel = channel) }
    }

    fun setGeneratorGainDb(gainDb: Float) {
        val clamped = gainDb.coerceIn(-40f, 0f)
        signalGenerator.gainDb = clamped
        _generatorState.update { it.copy(gainDb = clamped) }
    }

    fun setSineFrequencyHz(freqHz: Float) {
        val clamped = freqHz.coerceIn(20f, 20000f)
        signalGenerator.sineFreqHz = clamped
        _generatorState.update { it.copy(sineFreqHz = clamped) }
    }

    // --- Guided External RTA Microphone Auto-Calibration Wizard ---

    fun openAutoCalibrationWizard(hasMicPermission: Boolean = true) {
        cancelAutoCalJob()
        if (hasMicPermission && !isCapturing.value) {
            startRtaCapture()
        }
        _autoCalState.value = AutoCalUiState(
            step = AutoCalStep.QUIET_CHECK,
            signalSource = AutoCalSignalSource.HEAD_UNIT_MANUAL,
            statusMessage = "Убедитесь, что двигатель, климат-контроль и музыка выключены."
        )
    }

    fun closeAutoCalibrationWizard() {
        cancelAutoCalJob()
        signalGenerator.stop()
        _autoCalState.value = AutoCalUiState(step = AutoCalStep.IDLE)
    }

    fun setAutoCalSignalSource(source: AutoCalSignalSource) {
        if (source != AutoCalSignalSource.PHONE_GENERATOR) {
            signalGenerator.stop()
        }
        _autoCalState.update { state ->
            val newTargetSpl = when (source) {
                AutoCalSignalSource.ACOUSTIC_CALIBRATOR -> 94f
                else -> if (state.signalSource == AutoCalSignalSource.ACOUSTIC_CALIBRATOR) 75f else state.targetReferenceSplDb
            }
            val newMsg = when (state.step) {
                AutoCalStep.SENSITIVITY_MEASUREMENT -> sensitivityHintFor(source)
                AutoCalStep.FR_SWEEP_MEASUREMENT -> frSweepHintFor(source)
                else -> state.statusMessage
            }
            state.copy(
                signalSource = source,
                useExternalCalibrator = source == AutoCalSignalSource.ACOUSTIC_CALIBRATOR,
                targetReferenceSplDb = newTargetSpl,
                statusMessage = newMsg
            )
        }
    }

    fun toggleAutoCalPreviewNoise() {
        val st = _autoCalState.value
        if (st.isMeasuring) return
        if (_generatorState.value.isPlaying) {
            signalGenerator.stop()
            return
        }
        when (st.step) {
            AutoCalStep.SENSITIVITY_MEASUREMENT -> {
                signalGenerator.signalType = SignalType.SINE
                signalGenerator.sineFreqHz = 1000f
                signalGenerator.outputChannel = OutputChannel.BOTH
                signalGenerator.gainDb = -18f
                _generatorState.update {
                    it.copy(signalType = SignalType.SINE, sineFreqHz = 1000f, outputChannel = OutputChannel.BOTH)
                }
                signalGenerator.start(viewModelScope)
            }
            AutoCalStep.FR_SWEEP_MEASUREMENT -> {
                signalGenerator.signalType = SignalType.PINK_NOISE
                signalGenerator.outputChannel = OutputChannel.BOTH
                signalGenerator.gainDb = -16f
                _generatorState.update {
                    it.copy(signalType = SignalType.PINK_NOISE, outputChannel = OutputChannel.BOTH)
                }
                signalGenerator.start(viewModelScope)
            }
            else -> Unit
        }
    }

    private fun sensitivityHintFor(source: AutoCalSignalSource): String = when (source) {
        AutoCalSignalSource.HEAD_UNIT_MANUAL ->
            "Включите тестовый трек 1 кГц (синус или розовый шум) вручную на головном устройстве (ГУ) на уровне ~75 дБ по индикатору ниже, затем нажмите «Начать замер»."
        AutoCalSignalSource.PHONE_GENERATOR ->
            "Подключите телефон к системе (BT / AUX). Можно включить тон 1 кГц вручную для настройки громкости или сразу нажать «Измерить чувствительность»."
        AutoCalSignalSource.ACOUSTIC_CALIBRATOR ->
            "Наденьте акустический калибратор (94 дБ, 1 кГц) на капсюль микрофона и нажмите «Измерить чувствительность»."
    }

    private fun frSweepHintFor(source: AutoCalSignalSource): String = when (source) {
        AutoCalSignalSource.PHONE_GENERATOR ->
            "Готово к замеру АЧХ с генератора телефона (20 Гц – 20 кГц)."
        else ->
            "Включите розовый шум на головном устройстве (ГУ) на рабочей громкости, затем нажмите «Начать замер АЧХ»."
    }

    fun setAutoCalTargetSpl(splDb: Float) {
        _autoCalState.update { it.copy(targetReferenceSplDb = splDb.coerceIn(60f, 114f)) }
    }

    fun startQuietEnvironmentCheck(hasMicPermission: Boolean) {
        if (!hasMicPermission) {
            emitSnackbar("Разрешите доступ к микрофону для калибровки")
            return
        }
        cancelAutoCalJob()
        autoCalJob = viewModelScope.launch {
            signalGenerator.stop()
            if (!isCapturing.value) startRtaCapture()

            autoCalAccumulator.resetAmbient()
            activeCalCaptureMode = AutoCalStep.QUIET_CHECK
            _autoCalState.update {
                it.copy(
                    step = AutoCalStep.QUIET_CHECK,
                    isMeasuring = true,
                    progress = 0f,
                    statusMessage = "Измеряем фоновый шум в салоне (соблюдайте тишину)..."
                )
            }

            val totalSteps = 30
            for (step in 1..totalSteps) {
                delay(100)
                _autoCalState.update { it.copy(progress = step / totalSteps.toFloat()) }
            }

            activeCalCaptureMode = AutoCalStep.IDLE
            val floorDb = autoCalAccumulator.finishAmbient()
            val quiet = floorDb <= 55f
            val msg = if (quiet) {
                String.format(java.util.Locale.US, "Отлично! Шум фона %.1f дБ — тихая среда подтверждена.", floorDb)
            } else {
                String.format(java.util.Locale.US, "Фоновый шум повышен (%.1f дБ). Рекомендуется заглушить источники шума, но можно продолжить.", floorDb)
            }
            _autoCalState.update {
                it.copy(
                    isMeasuring = false,
                    progress = 1f,
                    measuredNoiseFloorDb = floorDb,
                    isEnvironmentQuiet = quiet,
                    statusMessage = msg
                )
            }
        }
    }

    fun proceedToSensitivityStep() {
        _autoCalState.update {
            it.copy(
                step = AutoCalStep.SENSITIVITY_MEASUREMENT,
                isMeasuring = false,
                progress = 0f,
                statusMessage = sensitivityHintFor(it.signalSource)
            )
        }
    }

    fun runSensitivityCalibration(hasMicPermission: Boolean) {
        if (!hasMicPermission) {
            emitSnackbar("Разрешите доступ к микрофону")
            return
        }
        cancelAutoCalJob()
        autoCalJob = viewModelScope.launch {
            if (!isCapturing.value) startRtaCapture()

            val stateNow = _autoCalState.value
            val usePhoneGen = stateNow.signalSource == AutoCalSignalSource.PHONE_GENERATOR
            val wasAlreadyPlaying = _generatorState.value.isPlaying
            if (usePhoneGen && !wasAlreadyPlaying) {
                signalGenerator.signalType = SignalType.SINE
                signalGenerator.sineFreqHz = 1000f
                signalGenerator.outputChannel = OutputChannel.BOTH
                signalGenerator.gainDb = -18f
                signalGenerator.start(viewModelScope)
                delay(400)
            }

            autoCalAccumulator.resetSensitivity()
            activeCalCaptureMode = AutoCalStep.SENSITIVITY_MEASUREMENT
            val activeMsg = when (stateNow.signalSource) {
                AutoCalSignalSource.HEAD_UNIT_MANUAL -> "Записываем сигнал 1 кГц с головного устройства (ГУ)..."
                AutoCalSignalSource.ACOUSTIC_CALIBRATOR -> "Записываем эталон калибратора 1 кГц..."
                AutoCalSignalSource.PHONE_GENERATOR -> "Измеряем чувствительность на частоте 1000 Гц..."
            }
            _autoCalState.update {
                it.copy(
                    isMeasuring = true,
                    progress = 0f,
                    statusMessage = activeMsg
                )
            }

            val totalSteps = 32
            for (step in 1..totalSteps) {
                delay(100)
                _autoCalState.update { it.copy(progress = step / totalSteps.toFloat()) }
            }

            activeCalCaptureMode = AutoCalStep.IDLE
            if (usePhoneGen) {
                signalGenerator.stop()
            }

            val measured1k = autoCalAccumulator.finishSensitivity()
            // Pistonphone only emits 1 kHz, so default Step 3 to Head Unit manual pink noise
            val nextSource = if (stateNow.signalSource == AutoCalSignalSource.ACOUSTIC_CALIBRATOR) {
                AutoCalSignalSource.HEAD_UNIT_MANUAL
            } else {
                stateNow.signalSource
            }
            _autoCalState.update {
                it.copy(
                    isMeasuring = false,
                    progress = 1f,
                    measured1kHzDb = measured1k,
                    signalSource = nextSource,
                    step = AutoCalStep.FR_SWEEP_MEASUREMENT,
                    statusMessage = String.format(
                        java.util.Locale.US,
                        "Уровень 1 кГц: %.1f дБ. %s",
                        measured1k,
                        frSweepHintFor(nextSource)
                    )
                )
            }
        }
    }

    fun runFrCorrectionSweep(hasMicPermission: Boolean) {
        if (!hasMicPermission) {
            emitSnackbar("Разрешите доступ к микрофону")
            return
        }
        cancelAutoCalJob()
        autoCalJob = viewModelScope.launch {
            if (!isCapturing.value) startRtaCapture()

            val stateNow = _autoCalState.value
            val usePhoneGen = stateNow.signalSource == AutoCalSignalSource.PHONE_GENERATOR
            val wasAlreadyPlaying = _generatorState.value.isPlaying
            if (usePhoneGen && !wasAlreadyPlaying) {
                signalGenerator.signalType = SignalType.PINK_NOISE
                signalGenerator.outputChannel = OutputChannel.BOTH
                signalGenerator.gainDb = -16f
                signalGenerator.start(viewModelScope)
                delay(450)
            }

            autoCalAccumulator.resetFrSweep()
            activeCalCaptureMode = AutoCalStep.FR_SWEEP_MEASUREMENT
            _autoCalState.update {
                it.copy(
                    isMeasuring = true,
                    progress = 0f,
                    statusMessage = if (usePhoneGen) {
                        "Замер АЧХ розовым шумом с телефона (20 Гц – 20 кГц)..."
                    } else {
                        "Замер АЧХ розовым шумом с ГУ (держите шум включённым 4.5 сек)..."
                    }
                )
            }

            val totalSteps = 45
            for (step in 1..totalSteps) {
                delay(100)
                _autoCalState.update { it.copy(progress = step / totalSteps.toFloat()) }
            }

            activeCalCaptureMode = AutoCalStep.IDLE
            if (usePhoneGen) {
                signalGenerator.stop()
            }

            val st = _autoCalState.value
            val micName = audioInputManager.getSelectedDevice()?.name ?: "USB RTA Mic"
            val result = autoCalAccumulator.buildCalibrationResult(
                micName = micName,
                noiseFloorDb = st.measuredNoiseFloorDb ?: 42f,
                measured1kHzDb = st.measured1kHzDb ?: 75f,
                targetReferenceSplDb = st.targetReferenceSplDb
            )

            _autoCalState.update {
                it.copy(
                    step = AutoCalStep.REVIEW_RESULT,
                    isMeasuring = false,
                    progress = 1f,
                    result = result,
                    statusMessage = "Автокалибровка завершена! Проверьте кривую коррекции и сохраните профиль."
                )
            }
        }
    }

    fun saveAndApplyAutoCalibration(customName: String? = null) {
        val res = _autoCalState.value.result ?: return
        viewModelScope.launch {
            val finalName = customName?.trim().takeIf { !it.isNullOrEmpty() } ?: res.profileName
            val newId = repository.saveCalibrationProfile(
                name = finalName,
                sensitivityOffsetDb = res.sensitivityOffsetDb,
                points = res.correctionPoints,
                sourceType = "AUTO_CAL"
            )
            preferences.setActiveCalibrationProfileId(newId)
            closeAutoCalibrationWizard()
            emitSnackbar("Калибровка «$finalName» применена к RTA")
        }
    }

    private fun cancelAutoCalJob() {
        autoCalJob?.cancel()
        autoCalJob = null
        activeCalCaptureMode = AutoCalStep.IDLE
        if (_autoCalState.value.isMeasuring && _autoCalState.value.signalSource == AutoCalSignalSource.PHONE_GENERATOR) {
            signalGenerator.stop()
        }
    }

    // --- Calibration File Import (.cal / .txt via SAF) ---

    fun importCalibrationUri(contentResolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            try {
                val (fileName, text) = withContext(Dispatchers.IO) {
                    var name = "RTA_Mic.cal"
                    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0 && cursor.moveToFirst()) {
                            name = cursor.getString(idx) ?: name
                        }
                    }
                    val content = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: throw IllegalStateException("Не удалось прочитать файл")
                    name to content
                }

                val parsed = CalibrationParser.parse(text)
                if (parsed.points.isEmpty()) {
                    emitSnackbar("Файл не содержит валидных точек частот (Hz dB)")
                    return@launch
                }

                val newId = repository.saveCalibrationProfile(
                    name = fileName,
                    sensitivityOffsetDb = parsed.sensitivityOffsetDb,
                    points = parsed.points,
                    sourceType = "IMPORTED_CAL"
                )
                preferences.setActiveCalibrationProfileId(newId)
                emitSnackbar("Загружена калибровка: $fileName (${parsed.points.size} точек)")
            } catch (e: Throwable) {
                emitSnackbar("Ошибка импорта: ${e.message ?: "проверьте формат .cal"}")
            }
        }
    }

    fun deleteCalibrationProfile(id: Long) {
        viewModelScope.launch {
            if (settings.value.activeCalibrationProfileId == id) {
                preferences.setActiveCalibrationProfileId(-1L)
            }
            repository.deleteCalibrationProfile(id)
            emitSnackbar("Профиль калибровки удалён")
        }
    }

    // --- Measurement Snapshots ---

    fun saveCurrentSnapshot(name: String, channelTag: String) {
        viewModelScope.launch {
            val live = _liveState.value
            val currentSettings = settings.value
            val micName = audioInputManager.getSelectedDevice()?.name ?: "Микрофон"
            val calName = getActiveCalibrationProfile()?.name ?: "Без калибровки"
            val trimmedName = name.trim().ifEmpty { "Замер $channelTag" }

            val id = repository.saveSnapshot(
                name = trimmedName,
                channelTag = channelTag,
                overallSplDb = live.overallSplDb,
                weightingCode = currentSettings.weighting.code,
                targetCurveId = currentSettings.targetCurveId,
                micName = micName,
                calibrationName = calName,
                bands31Db = live.bands31Db
            )
            _overlaySnapshotId.value = id
            emitSnackbar("Замер «$trimmedName» сохранён и наложен")
        }
    }

    fun toggleOverlaySnapshot(snapshotId: Long) {
        _overlaySnapshotId.value = if (_overlaySnapshotId.value == snapshotId) null else snapshotId
    }

    fun clearOverlaySnapshot() {
        _overlaySnapshotId.value = null
    }

    fun deleteSnapshot(id: Long) {
        viewModelScope.launch {
            if (_overlaySnapshotId.value == id) {
                _overlaySnapshotId.value = null
            }
            repository.deleteSnapshot(id)
            emitSnackbar("Замер удалён")
        }
    }

    // --- Export RTA Snapshots as PNG Image or CSV File ---

    @Volatile
    private var pendingExportPayload: SnapshotExportPayload? = null

    fun prepareCurrentSnapshotExport(name: String, channelTag: String, extension: String): String {
        val live = _liveState.value
        val currentSettings = settings.value
        val activeTarget = resolveActiveTargetCurve(currentSettings.targetCurveId)
        val micName = audioInputManager.getSelectedDevice()?.name ?: "Микрофон RTA"
        val calName = getActiveCalibrationProfile()?.name ?: "Без калибровки"
        val overlaySnap = _overlaySnapshotId.value?.let { id -> snapshots.value.firstOrNull { it.id == id } }
        val finalName = name.trim().ifEmpty { "Замер $channelTag" }
        val now = System.currentTimeMillis()

        pendingExportPayload = SnapshotExportPayload(
            name = finalName,
            channelTag = channelTag,
            timestampMs = now,
            overallSplDb = live.overallSplDb,
            weightingCode = currentSettings.weighting.code,
            micName = micName,
            calibrationName = calName,
            bands31Db = live.bands31Db.copyOf(),
            targetCurve = activeTarget,
            targetReferenceDb = live.eqAnalysis.alignedReferenceDb,
            showTargetCurve = currentSettings.showTargetCurve,
            targetToleranceDb = currentSettings.targetToleranceDb,
            overlayBands31Db = overlaySnap?.bands31Db?.copyOf(),
            overlayName = overlaySnap?.name
        )
        return SnapshotExporter.sanitizeFileName(finalName, extension, now)
    }

    fun prepareSavedSnapshotExport(snapshot: RtaSnapshotModel, extension: String): String {
        val currentSettings = settings.value
        val preset = resolveActiveTargetCurve(snapshot.targetCurveId)
        val analysis = TargetCurves.analyzeAgainstTarget(
            measuredBandsDb = snapshot.bands31Db,
            preset = preset,
            autoAlign = true,
            manualTargetRefDb = currentSettings.manualTargetRefDb,
            toleranceDb = currentSettings.targetToleranceDb
        )
        pendingExportPayload = SnapshotExportPayload(
            name = snapshot.name,
            channelTag = snapshot.channelTag,
            timestampMs = snapshot.timestampMs,
            overallSplDb = snapshot.overallSplDb,
            weightingCode = snapshot.weightingCode,
            micName = snapshot.micName,
            calibrationName = snapshot.calibrationName,
            bands31Db = snapshot.bands31Db.copyOf(),
            targetCurve = preset,
            targetReferenceDb = analysis.alignedReferenceDb,
            showTargetCurve = currentSettings.showTargetCurve,
            targetToleranceDb = currentSettings.targetToleranceDb
        )
        return SnapshotExporter.sanitizeFileName(snapshot.name, extension, snapshot.timestampMs)
    }

    fun exportPendingSnapshotAsCsv(contentResolver: ContentResolver, uri: Uri) {
        val payload = pendingExportPayload ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { out ->
                        SnapshotExporter.writeCsvToStream(payload, out)
                    } ?: throw IllegalStateException("Не удалось открыть файл для записи")
                }
                emitSnackbar("CSV-отчёт «${payload.name}» успешно сохранён")
            } catch (e: Throwable) {
                emitSnackbar("Ошибка экспорта CSV: ${e.message ?: "не удалось записать файл"}")
            }
        }
    }

    fun exportPendingSnapshotAsPng(contentResolver: ContentResolver, uri: Uri) {
        val payload = pendingExportPayload ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { out ->
                        SnapshotExporter.writePngToStream(payload, out)
                    } ?: throw IllegalStateException("Не удалось открыть файл для записи")
                }
                emitSnackbar("PNG-график «${payload.name}» успешно сохранён")
            } catch (e: Throwable) {
                emitSnackbar("Ошибка экспорта PNG: ${e.message ?: "не удалось сохранить изображение"}")
            }
        }
    }

    // --- Time Alignment Recommendations ---

    fun updateSpeakerDistance(id: String, cm: Float) {
        _speakerDistances.update { list ->
            list.map { if (it.id == id) it.copy(distanceCm = cm.coerceIn(20f, 400f)) else it }
        }
    }

    fun updateCabinTemp(tempC: Float) {
        _cabinTempC.value = tempC.coerceIn(-10f, 45f)
    }

    fun calculateDelayMs(speakerCm: Float): Float {
        val maxDistCm = _speakerDistances.value.maxOfOrNull { it.distanceCm } ?: speakerCm
        val speedOfSoundMps = 331.3f + 0.606f * _cabinTempC.value
        val deltaMeters = (maxDistCm - speakerCm).coerceAtLeast(0f) / 100f
        return (deltaMeters / speedOfSoundMps) * 1000f
    }

    // --- Guided Car Audio Tuning Wizard ("Мастер настройки") ---

    fun selectWizardStep(step: WizardStep) {
        if (step == WizardStep.RECOMMENDATIONS) {
            recomputeWizardReport()
        }
        _wizardState.update { it.copy(currentStep = step) }
    }

    fun setWizardWayCount(wayCount: SystemWayCount) {
        val currentCfg = _wizardState.value.systemConfig
        if (currentCfg.wayCount == wayCount) return
        applyNewSystemConfig(
            wayCount = wayCount,
            topology = currentCfg.crossoverTopology,
            hasSubwoofer = currentCfg.hasSubwoofer
        )
    }

    fun setWizardCrossoverTopology(topology: CrossoverTopology) {
        val currentCfg = _wizardState.value.systemConfig
        if (currentCfg.crossoverTopology == topology) return
        applyNewSystemConfig(
            wayCount = currentCfg.wayCount,
            topology = topology,
            hasSubwoofer = currentCfg.hasSubwoofer
        )
    }

    fun setWizardHasSubwoofer(hasSub: Boolean) {
        val currentCfg = _wizardState.value.systemConfig
        if (currentCfg.hasSubwoofer == hasSub) return
        applyNewSystemConfig(
            wayCount = currentCfg.wayCount,
            topology = currentCfg.crossoverTopology,
            hasSubwoofer = hasSub
        )
    }

    private fun applyNewSystemConfig(
        wayCount: SystemWayCount,
        topology: CrossoverTopology,
        hasSubwoofer: Boolean
    ) {
        wizardMeasureJob?.cancel()
        isAccumulatingWizardSpeaker = false
        val newCrossovers = TuningWizardEngine.defaultCrossoversFor(wayCount, topology, hasSubwoofer)
        val newConfig = AudioSystemConfig(
            wayCount = wayCount,
            crossoverTopology = topology,
            hasSubwoofer = hasSubwoofer,
            crossovers = newCrossovers
        )
        val newQueue = TuningWizardEngine.buildSpeakerQueue(newConfig)
        val validIds = newQueue.map { it.id }.toSet()
        _wizardState.update { state ->
            state.copy(
                systemConfig = newConfig,
                speakerQueue = newQueue,
                activeSlotIndex = 0,
                measurements = state.measurements.filterKeys { it in validIds },
                isMeasuringSpeaker = false,
                measurementProgress = 0f
            )
        }
        syncTimeAlignmentSpeakers(newConfig)
        recomputeWizardReport()
    }

    private fun syncTimeAlignmentSpeakers(config: AudioSystemConfig) {
        val list = mutableListOf<SpeakerDistance>()
        if (config.crossoverTopology == CrossoverTopology.PASSIVE_CROSSOVER) {
            list.add(SpeakerDistance("front_l", "Фронт Левый (кросс.)", 105f))
            list.add(SpeakerDistance("front_r", "Фронт Правый (кросс.)", 145f))
        } else {
            list.add(SpeakerDistance("fl_tw", "ВЧ Левый (Твитер)", 92f))
            list.add(SpeakerDistance("fr_tw", "ВЧ Правый (Твитер)", 136f))
            if (config.wayCount == SystemWayCount.THREE_WAY) {
                list.add(SpeakerDistance("fl_mid", "СЧ Левый", 98f))
                list.add(SpeakerDistance("fr_mid", "СЧ Правый", 140f))
            }
            list.add(SpeakerDistance("fl_mb", "Мидбас Левый", 115f))
            list.add(SpeakerDistance("fr_mb", "Мидбас Правый", 152f))
        }
        if (config.hasSubwoofer) {
            list.add(SpeakerDistance("sub", "Сабвуфер (Багажник)", 235f))
        }
        _speakerDistances.value = list
    }

    fun updateWizardChannelCrossover(group: SpeakerGroupType, updated: ChannelCrossoverConfig) {
        _wizardState.update { state ->
            val newMap = state.systemConfig.crossovers.toMutableMap().apply {
                put(group, updated)
            }
            state.copy(systemConfig = state.systemConfig.copy(crossovers = newMap))
        }
        recomputeWizardReport()
    }

    fun resetWizardDefaultCrossovers() {
        val cfg = _wizardState.value.systemConfig
        val defaults = TuningWizardEngine.defaultCrossoversFor(
            wayCount = cfg.wayCount,
            topology = cfg.crossoverTopology,
            hasSubwoofer = cfg.hasSubwoofer
        )
        _wizardState.update { it.copy(systemConfig = it.systemConfig.copy(crossovers = defaults)) }
        recomputeWizardReport()
        emitSnackbar("Установлены типовые срезы DSP для выбранной схемы")
    }

    fun selectWizardSpeakerSlot(index: Int) {
        val queueSize = _wizardState.value.speakerQueue.size
        if (queueSize == 0) return
        _wizardState.update { it.copy(activeSlotIndex = index.coerceIn(0, queueSize - 1)) }
    }

    fun toggleWizardPinkNoise(channel: OutputChannel) {
        val gen = _generatorState.value
        if (gen.isPlaying && gen.signalType == SignalType.PINK_NOISE && gen.outputChannel == channel) {
            signalGenerator.stop()
        } else {
            setSignalType(SignalType.PINK_NOISE)
            setOutputChannel(channel)
            if (!gen.isPlaying) {
                toggleGenerator()
            }
        }
    }

    fun startMeasureCurrentWizardSpeaker(hasMicPermission: Boolean) {
        if (!hasMicPermission) {
            emitSnackbar("Разрешите доступ к микрофону для замера динамика")
            return
        }
        val state = _wizardState.value
        val slot = state.speakerQueue.getOrNull(state.activeSlotIndex) ?: return

        wizardMeasureJob?.cancel()
        wizardMeasureJob = viewModelScope.launch {
            if (!isCapturing.value) {
                startRtaCapture()
                delay(250)
            }

            synchronized(wizardAccLock) {
                wizardBandsSum.fill(0.0)
                wizardSplSum = 0.0
                wizardFrameCount = 0
            }
            isAccumulatingWizardSpeaker = true

            _wizardState.update { it.copy(isMeasuringSpeaker = true, measurementProgress = 0f) }

            val totalSteps = 30 // 3.0 seconds averaged RTA capture
            for (step in 1..totalSteps) {
                delay(100)
                _wizardState.update { it.copy(measurementProgress = step / totalSteps.toFloat()) }
            }

            isAccumulatingWizardSpeaker = false

            val (avgBands, avgSpl) = synchronized(wizardAccLock) {
                if (wizardFrameCount > 0) {
                    val b = FloatArray(31) { idx -> (wizardBandsSum[idx] / wizardFrameCount).toFloat() }
                    val spl = (wizardSplSum / wizardFrameCount).toFloat()
                    b to spl
                } else {
                    _liveState.value.bands31Db.copyOf() to _liveState.value.overallSplDb
                }
            }

            val currentSettings = settings.value
            val targetCurve = resolveActiveTargetCurve(currentSettings.targetCurveId)
            val refDb = _liveState.value.eqAnalysis.alignedReferenceDb
            val measuredResult = TuningWizardEngine.computeSpeakerMeasurement(
                slot = slot,
                bands31Db = avgBands,
                overallSplDb = avgSpl,
                config = _wizardState.value.systemConfig,
                targetCurve = targetCurve,
                referenceDb = refDb,
                toleranceDb = currentSettings.targetToleranceDb
            )

            _wizardState.update { st ->
                val updatedMap = st.measurements + (slot.id to measuredResult)
                val nextIdx = if (st.activeSlotIndex < st.speakerQueue.lastIndex) {
                    st.activeSlotIndex + 1
                } else {
                    st.activeSlotIndex
                }
                st.copy(
                    measurements = updatedMap,
                    activeSlotIndex = nextIdx,
                    isMeasuringSpeaker = false,
                    measurementProgress = 1f
                )
            }
            recomputeWizardReport()
            emitSnackbar("Замер «${slot.shortLabel}» сохранён (${String.format(java.util.Locale.US, "%.1f", measuredResult.passbandAvgSplDb)} дБ)")
        }
    }

    fun clearWizardMeasurements() {
        wizardMeasureJob?.cancel()
        isAccumulatingWizardSpeaker = false
        _wizardState.update {
            it.copy(
                measurements = emptyMap(),
                activeSlotIndex = 0,
                isMeasuringSpeaker = false,
                measurementProgress = 0f
            )
        }
        recomputeWizardReport()
        emitSnackbar("Замеры динамиков мастера сброшены")
    }

    private fun recomputeWizardReport() {
        val st = _wizardState.value
        val currentSettings = settings.value
        val targetCurve = resolveActiveTargetCurve(currentSettings.targetCurveId)
        val fullSystemMeasurement = st.measurements["full_system"]
        val refDb = if (fullSystemMeasurement != null) {
            TargetCurves.analyzeAgainstTarget(
                measuredBandsDb = fullSystemMeasurement.bands31Db,
                preset = targetCurve,
                autoAlign = true,
                manualTargetRefDb = currentSettings.manualTargetRefDb,
                toleranceDb = currentSettings.targetToleranceDb
            ).alignedReferenceDb
        } else {
            _liveState.value.eqAnalysis.alignedReferenceDb
        }

        val report = TuningWizardEngine.analyzeWizardSystem(
            config = st.systemConfig,
            queue = st.speakerQueue,
            measurements = st.measurements,
            targetCurve = targetCurve,
            referenceDb = refDb,
            toleranceDb = currentSettings.targetToleranceDb
        )
        _wizardState.update { it.copy(analysisReport = report) }
    }

    // --- AI Recommendation Engine (Head Unit / DSP Step-by-Step EQ) ---

    private var aiJob: Job? = null

    fun selectHeadUnitProfile(profile: HeadUnitEqProfile) {
        _selectedHeadUnitProfile.value = profile
    }

    fun generateAiRecommendationsForLive() {
        recomputeWizardReport()
        val live = _liveState.value
        val wizard = _wizardState.value
        val currentSettings = settings.value
        val preset = resolveActiveTargetCurve(currentSettings.targetCurveId)
        val fullSystemMeas = wizard.measurements["full_system"]

        val bandsToUse = fullSystemMeas?.bands31Db?.copyOf() ?: live.bands31Db.copyOf()
        val splToUse = fullSystemMeas?.overallSplDb ?: live.overallSplDb
        val analysisToUse = if (fullSystemMeas != null) {
            TargetCurves.analyzeAgainstTarget(
                measuredBandsDb = bandsToUse,
                preset = preset,
                autoAlign = currentSettings.autoAlignTarget,
                manualTargetRefDb = currentSettings.manualTargetRefDb,
                toleranceDb = currentSettings.targetToleranceDb
            )
        } else {
            live.eqAnalysis
        }
        val sourceText = if (fullSystemMeas != null) {
            "Мастер настройки: общий замер (${wizard.measurements.size}/${wizard.speakerQueue.size} дин.)"
        } else if (wizard.measurements.isNotEmpty()) {
            "Мастер настройки (${wizard.measurements.size}/${wizard.speakerQueue.size} дин.) + текущий RTA"
        } else {
            "Текущий RTA-замер (${String.format(java.util.Locale.US, "%.1f", live.overallSplDb)} дБ)"
        }

        val request = AiTuningRequest(
            sourceLabel = sourceText,
            bands31Db = bandsToUse,
            targetCurve = preset,
            eqAnalysis = analysisToUse,
            overallSplDb = splToUse,
            weightingLabel = currentSettings.weighting.label,
            headUnitProfile = _selectedHeadUnitProfile.value,
            toleranceDb = currentSettings.targetToleranceDb,
            hasReceivedSignal = live.hasReceivedSignal || wizard.measurements.isNotEmpty(),
            systemConfig = wizard.systemConfig,
            speakerMeasurements = wizard.measurements,
            wizardReport = wizard.analysisReport
        )
        runAiTuningRequest(request)
    }

    fun generateAiRecommendationsForSnapshot(snapshot: RtaSnapshotModel) {
        val currentSettings = settings.value
        val wizard = _wizardState.value
        val preset = resolveActiveTargetCurve(currentSettings.targetCurveId)
        val analysis = TargetCurves.analyzeAgainstTarget(
            measuredBandsDb = snapshot.bands31Db,
            preset = preset,
            autoAlign = true,
            manualTargetRefDb = currentSettings.manualTargetRefDb,
            toleranceDb = currentSettings.targetToleranceDb
        )
        val request = AiTuningRequest(
            sourceLabel = "Замер «${snapshot.name}» (${snapshot.channelTag})",
            bands31Db = snapshot.bands31Db.copyOf(),
            targetCurve = preset,
            eqAnalysis = analysis,
            overallSplDb = snapshot.overallSplDb,
            weightingLabel = snapshot.weightingCode,
            headUnitProfile = _selectedHeadUnitProfile.value,
            toleranceDb = currentSettings.targetToleranceDb,
            hasReceivedSignal = true,
            systemConfig = wizard.systemConfig,
            speakerMeasurements = wizard.measurements,
            wizardReport = wizard.analysisReport
        )
        runAiTuningRequest(request)
    }

    private fun runAiTuningRequest(request: AiTuningRequest) {
        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            _aiAdvisorState.value = AiAdvisorUiState.Loading
            _completedAiSteps.value = emptySet()
            try {
                val plan = aiTuningRepository.generateTuningPlan(request)
                _aiAdvisorState.value = AiAdvisorUiState.Success(plan)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _aiAdvisorState.value = AiAdvisorUiState.Error(
                    e.message ?: "Неизвестная ошибка анализа АЧХ"
                )
            }
        }
    }

    fun toggleAiStepCompleted(stepNumber: Int) {
        _completedAiSteps.update { current ->
            if (stepNumber in current) current - stepNumber else current + stepNumber
        }
    }

    fun resetAiChecklist() {
        _completedAiSteps.value = emptySet()
    }

    // --- Waterfall (Cumulative Spectral Decay / CSD) Measurement ---

    fun setWaterfallMode(mode: WaterfallMode) {
        waterfallLiveEnabled = mode == WaterfallMode.LIVE_ROLLING
        _waterfallState.update { it.copy(mode = mode) }
    }

    fun setWaterfallSignalSource(source: WaterfallSignalSource) {
        _waterfallState.update {
            it.copy(
                signalSource = source,
                statusMessage = if (source == WaterfallSignalSource.HEAD_UNIT_MANUAL) {
                    "Включите розовый шум на ГУ на ~2 сек, затем резко поставьте ПАУЗУ / MUTE — затухание (CSD) запишется автоматически."
                } else {
                    "Телефон воспроизведёт короткий импульс розового шума (1.2 сек), остановит его и запишет затухание салона."
                }
            )
        }
    }

    fun setWaterfallWindowMs(windowMs: Int) {
        _waterfallState.update { it.copy(windowMs = windowMs.coerceIn(200, 500)) }
    }

    fun setWaterfallFreqRange(range: WaterfallFreqRange) {
        _waterfallState.update { it.copy(freqRange = range) }
    }

    fun startWaterfallCsdCapture(hasMicPermission: Boolean) {
        if (!hasMicPermission) {
            emitSnackbar("Разрешите доступ к микрофону для замера Waterfall")
            return
        }
        cancelWaterfallCapture()
        if (!isCapturing.value) startRtaCapture()

        val st = _waterfallState.value
        val source = st.signalSource
        val windowMs = st.windowMs

        if (source == WaterfallSignalSource.HEAD_UNIT_MANUAL) {
            synchronized(waterfallLock) {
                waterfallSteadySpl = 0f
                waterfallSteadyFrames = 0
                waterfallPhaseFlag = WaterfallCapturePhase.WAITING_SIGNAL
            }
            _waterfallState.update {
                it.copy(
                    mode = WaterfallMode.CSD_DECAY,
                    phase = WaterfallCapturePhase.WAITING_SIGNAL,
                    progress = 0.15f,
                    detectedSteadySplDb = null,
                    statusMessage = "Включите розовый шум на головном устройстве (ГУ) на ~2 сек, затем резко нажмите ПАУЗУ / MUTE (или кнопку «Захватить спад»)."
                )
            }
        } else {
            waterfallJob = viewModelScope.launch {
                synchronized(waterfallLock) {
                    waterfallSteadySpl = 0f
                    waterfallSteadyFrames = 0
                    waterfallPhaseFlag = WaterfallCapturePhase.PLAYING_PHONE_BURST
                }
                _waterfallState.update {
                    it.copy(
                        mode = WaterfallMode.CSD_DECAY,
                        phase = WaterfallCapturePhase.PLAYING_PHONE_BURST,
                        progress = 0.25f,
                        statusMessage = "Воспроизведение розового шума (1.2 сек) для возбуждения салона..."
                    )
                }

                signalGenerator.signalType = SignalType.PINK_NOISE
                signalGenerator.outputChannel = OutputChannel.BOTH
                signalGenerator.gainDb = -14f
                signalGenerator.start(viewModelScope)

                for (i in 1..12) {
                    delay(100)
                    _waterfallState.update { it.copy(progress = 0.1f + (i / 12f) * 0.45f) }
                }

                // Abrupt cutoff -> immediately begin recording post-cutoff PCM decay!
                signalGenerator.stop()
                delay(30)
                triggerManualWaterfallDecay()
            }
        }
    }

    fun triggerManualWaterfallDecay() {
        val windowMs = _waterfallState.value.windowMs
        val decaySamplesNeeded = ((windowMs + 140) / 1000.0 * 48000).toInt()
        val preLen = 4096
        synchronized(waterfallLock) {
            if (waterfallPhaseFlag == WaterfallCapturePhase.CAPTURING_DECAY) return
            waterfallDecayPcm = ShortArray(preLen + decaySamplesNeeded)
            // Seed t = 0 ms with the last 4096 steady-state PCM samples from preBuffer
            val srcStart = (waterfallPreBuffer.size - preLen).coerceAtLeast(0)
            System.arraycopy(waterfallPreBuffer, srcStart, waterfallDecayPcm, 0, preLen)
            waterfallCutoffIdx = 0
            waterfallDecayWritePos = preLen
            waterfallPhaseFlag = WaterfallCapturePhase.CAPTURING_DECAY
        }
        _waterfallState.update {
            it.copy(
                phase = WaterfallCapturePhase.CAPTURING_DECAY,
                progress = 0.6f,
                statusMessage = "Фиксируем акустическое затухание салона (${windowMs} мс)..."
            )
        }
    }

    fun cancelWaterfallCapture() {
        waterfallJob?.cancel()
        waterfallJob = null
        synchronized(waterfallLock) {
            if (waterfallPhaseFlag == WaterfallCapturePhase.PLAYING_PHONE_BURST) {
                signalGenerator.stop()
            }
            waterfallPhaseFlag = WaterfallCapturePhase.IDLE
        }
        _waterfallState.update {
            if (it.phase != WaterfallCapturePhase.COMPLETED) {
                it.copy(
                    phase = WaterfallCapturePhase.IDLE,
                    progress = 0f,
                    statusMessage = "Замер Waterfall отменён. Выберите источник и начните снова."
                )
            } else {
                it
            }
        }
    }

    private fun handleWaterfallFrame(pcm: ShortArray, frame: com.example.audio.RtaFrameResult) {
        // 1. Live rolling waterfall feed (every 2nd hop ~= 11.7 FPS)
        if (waterfallLiveEnabled) {
            waterfallLiveTick++
            if (waterfallLiveTick % 2 == 0) {
                val slice = WaterfallSlice(
                    timeMs = 0f,
                    bands31Db = frame.bands31Db.copyOf(),
                    trace128Db = frame.trace128Db.copyOf(),
                    overallSplDb = frame.overallSplDb
                )
                val snapshotList: List<WaterfallSlice>
                synchronized(waterfallLock) {
                    if (waterfallLiveRing.size >= 20) waterfallLiveRing.removeLast()
                    waterfallLiveRing.addFirst(slice)
                    snapshotList = waterfallLiveRing.mapIndexed { idx, s ->
                        s.copy(timeMs = idx * 85f)
                    }
                }
                _waterfallState.update { it.copy(liveRollingSlices = snapshotList) }
            }
        }

        // 2. CSD decay state machine
        val phase = waterfallPhaseFlag
        if (phase == WaterfallCapturePhase.IDLE || phase == WaterfallCapturePhase.COMPLETED) return

        var readyToComputeBuffer: ShortArray? = null
        var readyCutoffIdx = 0
        var decayProgress = 0f

        synchronized(waterfallLock) {
            when (waterfallPhaseFlag) {
                WaterfallCapturePhase.WAITING_SIGNAL -> {
                    System.arraycopy(pcm, 0, waterfallPreBuffer, 0, minOf(pcm.size, waterfallPreBuffer.size))
                    if (frame.overallSplDb >= 56f) {
                        waterfallSteadyFrames++
                        waterfallSteadySpl = if (waterfallSteadyFrames == 1) {
                            frame.overallSplDb
                        } else {
                            maxOf(waterfallSteadySpl * 0.85f + frame.overallSplDb * 0.15f, frame.overallSplDb)
                        }
                        if (waterfallSteadyFrames >= 3) {
                            waterfallPhaseFlag = WaterfallCapturePhase.SIGNAL_READY_WAIT_MUTE
                            val steady = waterfallSteadySpl
                            _waterfallState.update {
                                it.copy(
                                    phase = WaterfallCapturePhase.SIGNAL_READY_WAIT_MUTE,
                                    progress = 0.45f,
                                    detectedSteadySplDb = steady,
                                    statusMessage = String.format(
                                        java.util.Locale.US,
                                        "Шум с ГУ обнаружен (%.1f дБ)! Теперь резко нажмите ПАУЗУ / MUTE на ГУ (или «Захватить спад»).",
                                        steady
                                    )
                                )
                            }
                        }
                    } else {
                        waterfallSteadyFrames = 0
                    }
                }

                WaterfallCapturePhase.SIGNAL_READY_WAIT_MUTE -> {
                    if (frame.overallSplDb <= waterfallSteadySpl - 4.0f) {
                        // Sudden SPL drop detected -> Head Unit was paused/muted! Begin decay capture
                        val windowMs = _waterfallState.value.windowMs
                        val decaySamplesNeeded = ((windowMs + 140) / 1000.0 * 48000).toInt()
                        val preLen = 4096
                        waterfallDecayPcm = ShortArray(preLen + decaySamplesNeeded)
                        val srcStart = (waterfallPreBuffer.size - preLen).coerceAtLeast(0)
                        System.arraycopy(waterfallPreBuffer, srcStart, waterfallDecayPcm, 0, preLen)
                        // Also append the newest 2048 hop from the frame where drop started
                        val hopSize = minOf(2048, pcm.size)
                        val hopCopy = minOf(hopSize, waterfallDecayPcm.size - preLen)
                        System.arraycopy(pcm, pcm.size - hopSize, waterfallDecayPcm, preLen, hopCopy)
                        waterfallCutoffIdx = 0
                        waterfallDecayWritePos = preLen + hopCopy
                        waterfallPhaseFlag = WaterfallCapturePhase.CAPTURING_DECAY
                        _waterfallState.update {
                            it.copy(
                                phase = WaterfallCapturePhase.CAPTURING_DECAY,
                                progress = 0.65f,
                                statusMessage = "Пауза на ГУ зафиксирована! Записываем затухание (${windowMs} мс)..."
                            )
                        }
                    } else {
                        System.arraycopy(pcm, 0, waterfallPreBuffer, 0, minOf(pcm.size, waterfallPreBuffer.size))
                        waterfallSteadySpl = waterfallSteadySpl * 0.9f + frame.overallSplDb * 0.1f
                    }
                }

                WaterfallCapturePhase.PLAYING_PHONE_BURST -> {
                    System.arraycopy(pcm, 0, waterfallPreBuffer, 0, minOf(pcm.size, waterfallPreBuffer.size))
                    waterfallSteadySpl = frame.overallSplDb
                }

                WaterfallCapturePhase.CAPTURING_DECAY -> {
                    val hopSize = minOf(2048, pcm.size)
                    val remaining = waterfallDecayPcm.size - waterfallDecayWritePos
                    val toCopy = minOf(hopSize, remaining)
                    if (toCopy > 0) {
                        System.arraycopy(pcm, pcm.size - hopSize, waterfallDecayPcm, waterfallDecayWritePos, toCopy)
                        waterfallDecayWritePos += toCopy
                    }
                    decayProgress = 0.6f + 0.35f * (waterfallDecayWritePos.toFloat() / waterfallDecayPcm.size.coerceAtLeast(1))
                    if (waterfallDecayWritePos >= waterfallDecayPcm.size) {
                        readyToComputeBuffer = waterfallDecayPcm.copyOf()
                        readyCutoffIdx = waterfallCutoffIdx
                        waterfallPhaseFlag = WaterfallCapturePhase.COMPLETED
                    }
                }

                else -> Unit
            }
        }

        if (readyToComputeBuffer != null) {
            val captured = readyToComputeBuffer!!
            val winMs = _waterfallState.value.windowMs
            val curSettings = settings.value
            val activeCal = getActiveCalibrationProfile()
            viewModelScope.launch(Dispatchers.Default) {
                val result = WaterfallEngine.computeCsdFromPcm(
                    pcmBuffer = captured,
                    cutoffSampleIndex = readyCutoffIdx,
                    windowMs = winMs,
                    sliceCount = 20,
                    sampleRate = 48000,
                    weighting = curSettings.weighting,
                    calibrationPoints = activeCal?.points ?: emptyList(),
                    calibrationSensitivityOffsetDb = activeCal?.sensitivityOffsetDb ?: 0f,
                    manualSplOffsetDb = curSettings.manualSplOffsetDb
                )
                val summary = if (result.resonances.isEmpty()) {
                    "Замер Waterfall завершён (${winMs} мс). Критических резонансов не найдено."
                } else {
                    val worst = result.resonances.first()
                    "Найдено резонансов: ${result.resonances.size}. Главный пик: ${RtaBands.formatFrequencyHz(worst.freqHz)} (~${worst.estimatedDecayMs.toInt()} мс)."
                }
                _waterfallState.update {
                    it.copy(
                        phase = WaterfallCapturePhase.COMPLETED,
                        progress = 1f,
                        csdResult = result,
                        statusMessage = summary
                    )
                }
            }
        } else if (decayProgress > 0f) {
            _waterfallState.update { it.copy(progress = decayProgress) }
        }
    }

    private fun emitSnackbar(message: String) {
        _snackbarMessage.tryEmit(message)
    }

    override fun onCleared() {
        super.onCleared()
        cancelAutoCalJob()
        cancelWaterfallCapture()
        wizardMeasureJob?.cancel()
        isAccumulatingWizardSpeaker = false
        audioInputManager.stopCapture()
        audioInputManager.unregisterDeviceListener()
        signalGenerator.stop()
    }
}
