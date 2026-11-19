package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

enum class SignalType(val label: String, val subtitle: String) {
    PINK_NOISE("Розовый шум", "-3 дБ/окт — эталон для RTA"),
    WHITE_NOISE("Белый шум", "Равномерная энергия по Гц"),
    SINE("Синус", "Точный тон 20 Гц – 20 кГц"),
    LOG_SWEEP("Лог-свип", "Плавная развёртка 20 Гц – 20 кГц"),
    POLARITY("Полярность", "Импульсный тест фазы динамиков")
}

enum class OutputChannel(val label: String) {
    BOTH("L + R"),
    LEFT("Левый (L)"),
    RIGHT("Правый (R)")
}

/**
 * Pure, uncolored real-time 48 kHz 16-bit Stereo AudioTrack test signal generator
 * for feeding reference signals into the car head unit (via Bluetooth / AUX / USB)
 * while measuring acoustic response with an external RTA microphone.
 */
class SignalGeneratorEngine {

    private val sampleRate = 48000

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    @Volatile var signalType: SignalType = SignalType.PINK_NOISE
    @Volatile var outputChannel: OutputChannel = OutputChannel.BOTH
    @Volatile var gainDb: Float = -12f
    @Volatile var sineFreqHz: Float = 1000f

    private var playbackJob: Job? = null

    fun start(scope: CoroutineScope) {
        if (_isPlaying.value) return
        stop()

        playbackJob = scope.launch(Dispatchers.IO) {
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferBytes = maxOf(minBuf * 2, 4096 * 4)

            val track = try {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(sampleRate)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferBytes)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            } catch (_: Throwable) {
                _isPlaying.value = false
                return@launch
            }

            // Paul Kellet's accurate -3dB/oct pink noise filter state
            var b0 = 0.0; var b1 = 0.0; var b2 = 0.0
            var b3 = 0.0; var b4 = 0.0; var b5 = 0.0; var b6 = 0.0
            var phase = 0.0
            var sweepTime = 0.0
            val sweepDurationSec = 6.0
            var polarityCounter = 0

            val framesPerChunk = 1024
            val stereoPcm = ShortArray(framesPerChunk * 2)

            try {
                track.play()
                _isPlaying.value = true

                while (isActive) {
                    val currentType = signalType
                    val currentChan = outputChannel
                    val currentGain = 10.0.pow(gainDb.coerceIn(-45f, 0f) / 20.0)
                    val currentSineHz = sineFreqHz.coerceIn(20f, 20000f).toDouble()

                    for (i in 0 until framesPerChunk) {
                        val sample = when (currentType) {
                            SignalType.WHITE_NOISE -> Random.nextDouble(-1.0, 1.0) * 0.55
                            SignalType.PINK_NOISE -> {
                                val white = Random.nextDouble(-1.0, 1.0)
                                b0 = 0.99886 * b0 + white * 0.0555179
                                b1 = 0.99332 * b1 + white * 0.0750759
                                b2 = 0.96900 * b2 + white * 0.1538520
                                b3 = 0.86650 * b3 + white * 0.3104856
                                b4 = 0.55000 * b4 + white * 0.5329522
                                b5 = -0.7616 * b5 - white * 0.0168980
                                val pink = (b0 + b1 + b2 + b3 + b4 + b5 + b6 + white * 0.5362) * 0.11
                                b6 = white * 0.115926
                                pink
                            }
                            SignalType.SINE -> {
                                val s = sin(phase)
                                phase += 2.0 * PI * currentSineHz / sampleRate
                                if (phase > 2.0 * PI) phase -= 2.0 * PI
                                s
                            }
                            SignalType.LOG_SWEEP -> {
                                val t = (sweepTime % sweepDurationSec) / sweepDurationSec
                                val instFreq = 20.0 * 1000.0.pow(t)
                                val s = sin(phase)
                                phase += 2.0 * PI * instFreq / sampleRate
                                if (phase > 2.0 * PI) phase -= 2.0 * PI
                                sweepTime += 1.0 / sampleRate
                                s * 0.85
                            }
                            SignalType.POLARITY -> {
                                val period = sampleRate / 4
                                val pos = polarityCounter % period
                                polarityCounter++
                                when {
                                    pos < 32 -> sin(PI * pos / 32.0)
                                    pos < 128 -> -0.22 * sin(PI * (pos - 32) / 96.0)
                                    else -> 0.0
                                }
                            }
                        }

                        val scaled = (sample * currentGain).coerceIn(-0.98, 0.98)
                        val pcmVal = (scaled * 32767.0).toInt().toShort()

                        stereoPcm[i * 2] = if (currentChan == OutputChannel.RIGHT) 0 else pcmVal
                        stereoPcm[i * 2 + 1] = if (currentChan == OutputChannel.LEFT) 0 else pcmVal
                    }

                    track.write(stereoPcm, 0, stereoPcm.size)
                }
            } catch (_: Throwable) {
                // Stop gracefully on cancellation or disconnect
            } finally {
                _isPlaying.value = false
                try {
                    track.stop()
                } catch (_: Throwable) {
                }
                try {
                    track.release()
                } catch (_: Throwable) {
                }
            }
        }
    }

    fun stop() {
        playbackJob?.cancel()
        playbackJob = null
        _isPlaying.value = false
    }
}
