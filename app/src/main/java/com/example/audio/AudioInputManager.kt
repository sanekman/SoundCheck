package com.example.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class MicCategory(val label: String, val priority: Int) {
    USB_EXTERNAL("USB RTA-микрофон", 100),
    WIRED_TRRS("3.5 мм TRRS микрофон", 80),
    BLUETOOTH("Bluetooth аудиовход", 40),
    BUILTIN("Встроенный микрофон", 10),
    OTHER("Внешний аудиовход", 60)
}

data class AudioInputDeviceUi(
    val id: Int,
    val name: String,
    val category: MicCategory,
    val sampleRates: List<Int>,
    val isExternal: Boolean,
    val rawDevice: AudioDeviceInfo? = null
)

class AudioInputManager(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _availableDevices = MutableStateFlow<List<AudioInputDeviceUi>>(emptyList())
    val availableDevices: StateFlow<List<AudioInputDeviceUi>> = _availableDevices.asStateFlow()

    private val _selectedDeviceId = MutableStateFlow<Int?>(null)
    val selectedDeviceId: StateFlow<Int?> = _selectedDeviceId.asStateFlow()

    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()

    private val _captureError = MutableStateFlow<String?>(null)
    val captureError: StateFlow<String?> = _captureError.asStateFlow()

    private var captureJob: Job? = null
    private var deviceCallbackRegistered = false

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            refreshInputDevices(autoPreferExternal = true)
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            refreshInputDevices(autoPreferExternal = true)
        }
    }

    fun registerDeviceListener() {
        if (!deviceCallbackRegistered) {
            audioManager.registerAudioDeviceCallback(deviceCallback, Handler(Looper.getMainLooper()))
            deviceCallbackRegistered = true
        }
        refreshInputDevices(autoPreferExternal = true)
    }

    fun unregisterDeviceListener() {
        if (deviceCallbackRegistered) {
            audioManager.unregisterAudioDeviceCallback(deviceCallback)
            deviceCallbackRegistered = false
        }
    }

    fun refreshInputDevices(autoPreferExternal: Boolean = false) {
        val rawInputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        val mapped = rawInputs
            .filter { info ->
                info.type != AudioDeviceInfo.TYPE_TELEPHONY &&
                    info.type != AudioDeviceInfo.TYPE_FM_TUNER &&
                    info.type != AudioDeviceInfo.TYPE_REMOTE_SUBMIX
            }
            .map { info ->
                val category = classifyDevice(info.type)
                val productName = info.productName?.toString()?.trim().orEmpty()
                val friendlyName = when {
                    category == MicCategory.USB_EXTERNAL && productName.isNotEmpty() -> productName
                    category == MicCategory.USB_EXTERNAL -> "USB RTA-микрофон (ID ${info.id})"
                    category == MicCategory.WIRED_TRRS && productName.isNotEmpty() -> "$productName (TRRS)"
                    category == MicCategory.WIRED_TRRS -> "Внешний 3.5 мм TRRS микрофон"
                    category == MicCategory.BLUETOOTH && productName.isNotEmpty() -> "$productName (BT)"
                    category == MicCategory.BUILTIN -> "Встроенный микрофон устройства"
                    productName.isNotEmpty() -> productName
                    else -> "Аудиовход #${info.id}"
                }
                AudioInputDeviceUi(
                    id = info.id,
                    name = friendlyName,
                    category = category,
                    sampleRates = info.sampleRates.toList(),
                    isExternal = category != MicCategory.BUILTIN,
                    rawDevice = info
                )
            }
            .distinctBy { it.id }
            .sortedByDescending { it.category.priority }

        _availableDevices.value = mapped

        val currentId = _selectedDeviceId.value
        val currentStillExists = mapped.any { it.id == currentId }
        if (autoPreferExternal) {
            val bestExternal = mapped.firstOrNull { it.isExternal }
            if (bestExternal != null && (currentId == null || !currentStillExists || mapped.firstOrNull { it.id == currentId }?.isExternal == false)) {
                _selectedDeviceId.value = bestExternal.id
                return
            }
        }
        if (currentId == null || !currentStillExists) {
            _selectedDeviceId.value = mapped.firstOrNull()?.id
        }
    }

    fun selectDevice(deviceId: Int) {
        _selectedDeviceId.value = deviceId
    }

    fun getSelectedDevice(): AudioInputDeviceUi? {
        val id = _selectedDeviceId.value
        return _availableDevices.value.firstOrNull { it.id == id }
            ?: _availableDevices.value.firstOrNull()
    }

    @SuppressLint("MissingPermission")
    fun startCapture(
        scope: CoroutineScope,
        sampleRate: Int = 48000,
        fftSize: Int = 8192,
        onPcmFrame: (ShortArray) -> Unit
    ) {
        stopCapture()
        _captureError.value = null

        captureJob = scope.launch(Dispatchers.IO) {
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val minBufBytes = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            if (minBufBytes <= 0) {
                _captureError.value = "Аудиовход не поддерживает $sampleRate Гц"
                _isCapturing.value = false
                return@launch
            }

            // Try UNPROCESSED first for flat RTA frequency response (no Android AGC/HPF),
            // fallback to VOICE_RECOGNITION and then MIC if device rejects UNPROCESSED.
            val sourcesToTry = listOf(
                MediaRecorder.AudioSource.UNPROCESSED,
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                MediaRecorder.AudioSource.MIC
            )

            var recorder: AudioRecord? = null
            val bufferSizeBytes = maxOf(minBufBytes * 2, fftSize * 2)

            for (source in sourcesToTry) {
                try {
                    val candidate = AudioRecord(
                        source,
                        sampleRate,
                        channelConfig,
                        audioFormat,
                        bufferSizeBytes
                    )
                    if (candidate.state == AudioRecord.STATE_INITIALIZED) {
                        recorder = candidate
                        break
                    } else {
                        candidate.release()
                    }
                } catch (_: Throwable) {
                    // Try next fallback audio source
                }
            }

            if (recorder == null) {
                _captureError.value = "Не удалось инициализировать микрофон. Проверьте подключение USB RTA."
                _isCapturing.value = false
                return@launch
            }

            // Bind to selected external RTA microphone if available
            val preferred = getSelectedDevice()?.rawDevice
            if (preferred != null) {
                recorder.setPreferredDevice(preferred)
            }

            try {
                recorder.startRecording()
                _isCapturing.value = true

                // Overlap ring buffer: hop 2048 samples (~42.6 ms = ~23.4 FPS smooth updates) over 8192-sample FFT window
                val hopSize = 2048
                val hopBuffer = ShortArray(hopSize)
                val ringWindow = ShortArray(fftSize)
                var initialized = false

                while (isActive) {
                    val read = recorder.read(hopBuffer, 0, hopSize)
                    if (read > 0) {
                        // Shift ring window left and append newly captured samples
                        if (read >= fftSize) {
                            System.arraycopy(hopBuffer, read - fftSize, ringWindow, 0, fftSize)
                        } else {
                            System.arraycopy(ringWindow, read, ringWindow, 0, fftSize - read)
                            System.arraycopy(hopBuffer, 0, ringWindow, fftSize - read, read)
                        }
                        initialized = true
                        if (initialized) {
                            onPcmFrame(ringWindow.copyOf())
                        }
                    } else if (read < 0) {
                        _captureError.value = "Ошибка чтения потока микрофона (код $read)"
                        break
                    }
                }
            } catch (e: SecurityException) {
                _captureError.value = "Нет разрешения на доступ к микрофону"
            } catch (e: Throwable) {
                _captureError.value = "Сбой захвата аудио: ${e.message ?: "неизвестная ошибка"}"
            } finally {
                _isCapturing.value = false
                try {
                    if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        recorder.stop()
                    }
                } catch (_: Throwable) {
                }
                try {
                    recorder.release()
                } catch (_: Throwable) {
                }
            }
        }
    }

    fun stopCapture() {
        captureJob?.cancel()
        captureJob = null
        _isCapturing.value = false
    }

    private fun classifyDevice(type: Int): MicCategory {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && type == AudioDeviceInfo.TYPE_BLE_HEADSET) {
            return MicCategory.BLUETOOTH
        }
        return when (type) {
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_ACCESSORY -> MicCategory.USB_EXTERNAL
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_LINE_ANALOG,
            AudioDeviceInfo.TYPE_AUX_LINE -> MicCategory.WIRED_TRRS
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> MicCategory.BLUETOOTH
            AudioDeviceInfo.TYPE_BUILTIN_MIC -> MicCategory.BUILTIN
            else -> MicCategory.OTHER
        }
    }
}
