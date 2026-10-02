package com.example.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

class AudioCaptureManager(
    private val context: Context,
    private val onAudioLevelChanged: (Float) -> Unit = {}
) {
    private val TAG = "AudioCaptureManager"
    private var audioRecord: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var isRecording = false

    var isMuted: Boolean = false
    var micGain: Float = 1.0f

    @SuppressLint("MissingPermission")
    suspend fun startCapture(
        sampleRate: Int = 44100,
        onAudioData: suspend (ByteArray, Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

        if (minBufferSize <= 0) {
            Log.e(TAG, "Invalid minBufferSize: $minBufferSize")
            return@withContext
        }

        val bufferSize = minBufferSize * 2
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            val audioSessionId = audioRecord?.audioSessionId ?: 0
            if (audioSessionId != 0) {
                if (AcousticEchoCanceler.isAvailable()) {
                    echoCanceler = AcousticEchoCanceler.create(audioSessionId)?.apply {
                        enabled = true
                    }
                }
                if (NoiseSuppressor.isAvailable()) {
                    noiseSuppressor = NoiseSuppressor.create(audioSessionId)?.apply {
                        enabled = true
                    }
                }
            }

            audioRecord?.startRecording()
            isRecording = true

            val buffer = ByteArray(2048)

            while (isActive && isRecording) {
                val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                if (bytesRead > 0) {
                    if (isMuted) {
                        buffer.fill(0, 0, bytesRead)
                        onAudioLevelChanged(0f)
                    } else {
                        // Apply mic gain and compute level
                        applyGainAndComputeLevel(buffer, bytesRead, micGain)
                    }
                    onAudioData(buffer, bytesRead)
                }
            }
        } catch (e: CancellationException) {
            // Normal coroutine cancellation
        } catch (e: Exception) {
            Log.e(TAG, "Audio capture failed", e)
        } finally {
            stopCapture()
        }
    }

    private fun applyGainAndComputeLevel(buffer: ByteArray, size: Int, gain: Float) {
        var sumSquares = 0.0
        val sampleCount = size / 2

        var i = 0
        while (i < size - 1) {
            var sample = ((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)).toShort()
            if (gain != 1.0f) {
                val multiplied = (sample * gain).toInt().coerceIn(-32768, 32767)
                sample = multiplied.toShort()
                buffer[i] = (sample.toInt() and 0xFF).toByte()
                buffer[i + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
            }
            sumSquares += sample * sample
            i += 2
        }

        if (sampleCount > 0) {
            val rms = sqrt(sumSquares / sampleCount)
            val normalized = (rms / 12000.0).coerceIn(0.0, 1.0).toFloat()
            onAudioLevelChanged(normalized)
        }
    }

    fun stopCapture() {
        isRecording = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null

        try {
            echoCanceler?.release()
            noiseSuppressor?.release()
        } catch (_: Exception) {}
        echoCanceler = null
        noiseSuppressor = null

        onAudioLevelChanged(0f)
    }
}
