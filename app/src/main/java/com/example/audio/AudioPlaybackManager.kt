package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.util.Log
import kotlin.math.abs
import kotlin.math.sqrt

class AudioPlaybackManager(
    private val context: Context,
    private val onAudioLevelChanged: (Float) -> Unit = {},
    private val onAudioFocusChanged: (Boolean) -> Unit = {}
) {
    private val TAG = "AudioPlaybackManager"

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioTrack: AudioTrack? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    private var currentSampleRate = 44100
    private var isPlaying = false
    private var volume = 0.85f
    private var isMuted = false

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Log.d(TAG, "Audio focus lost")
                pause()
                onAudioFocusChanged(false)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.d(TAG, "Audio focus gained")
                resume()
                onAudioFocusChanged(true)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                audioTrack?.setVolume(volume * 0.3f)
            }
        }
    }

    @Synchronized
    fun init(sampleRate: Int = 44100, isStereo: Boolean = false): Boolean {
        currentSampleRate = sampleRate
        release()

        val channelMask = if (isStereo) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelMask, encoding)
        if (minBufferSize <= 0) {
            Log.e(TAG, "Invalid min buffer size: $minBufferSize")
            return false
        }

        val bufferSize = minBufferSize * 3

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        val format = AudioFormat.Builder()
            .setEncoding(encoding)
            .setSampleRate(sampleRate)
            .setChannelMask(channelMask)
            .build()

        return try {
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            applyVolume()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create AudioTrack", e)
            false
        }
    }

    @Synchronized
    fun start(): Boolean {
        if (audioTrack == null) {
            if (!init(currentSampleRate)) return false
        }

        if (!requestAudioFocus()) {
            Log.w(TAG, "Failed to obtain audio focus")
        }

        return try {
            audioTrack?.play()
            isPlaying = true
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error starting AudioTrack", e)
            false
        }
    }

    @Synchronized
    fun writePcm(buffer: ByteArray, offset: Int, size: Int): Int {
        val track = audioTrack ?: return 0
        if (!isPlaying) return 0

        // Calculate RMS audio level for visualizer
        computeAndEmitAudioLevel(buffer, offset, size)

        return try {
            track.write(buffer, offset, size, AudioTrack.WRITE_BLOCKING)
        } catch (e: Exception) {
            Log.e(TAG, "Error writing to AudioTrack", e)
            0
        }
    }

    private fun computeAndEmitAudioLevel(buffer: ByteArray, offset: Int, size: Int) {
        if (size <= 0) return
        var sumSquares = 0.0
        val sampleCount = size / 2
        var i = offset
        while (i < offset + size - 1) {
            // 16-bit little-endian PCM sample
            val sample = ((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)).toShort()
            sumSquares += sample * sample
            i += 2
        }

        if (sampleCount > 0) {
            val rms = sqrt(sumSquares / sampleCount)
            // Normalize roughly against max 16-bit value (32768)
            val normalized = (rms / 12000.0).coerceIn(0.0, 1.0).toFloat()
            onAudioLevelChanged(if (isMuted) 0f else normalized)
        }
    }

    fun setVolume(vol: Float) {
        volume = vol.coerceIn(0f, 1f)
        applyVolume()
    }

    fun setMuted(muted: Boolean) {
        isMuted = muted
        applyVolume()
    }

    private fun applyVolume() {
        val actualVol = if (isMuted) 0f else volume
        try {
            audioTrack?.setVolume(actualVol)
        } catch (_: Exception) {}
    }

    @Synchronized
    fun pause() {
        try {
            audioTrack?.pause()
            isPlaying = false
        } catch (_: Exception) {}
    }

    @Synchronized
    fun resume() {
        try {
            if (audioTrack != null && !isPlaying) {
                audioTrack?.play()
                isPlaying = true
            }
        } catch (_: Exception) {}
    }

    @Synchronized
    fun stop() {
        isPlaying = false
        try {
            audioTrack?.stop()
            audioTrack?.flush()
        } catch (_: Exception) {}
        abandonAudioFocus()
        onAudioLevelChanged(0f)
    }

    @Synchronized
    fun release() {
        stop()
        try {
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null
    }

    private fun requestAudioFocus(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener(focusChangeListener)
                .setAcceptsDelayedFocusGain(true)
                .build()
            audioFocusRequest = req
            audioManager.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                focusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            audioFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusChangeListener)
        }
    }

    fun getActiveAudioRoute(): String {
        return try {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            for (device in devices) {
                when (device.type) {
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> return "Bluetooth (${device.productName})"
                    AudioDeviceInfo.TYPE_WIRED_HEADSET,
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                    AudioDeviceInfo.TYPE_USB_HEADSET -> return "Headphones"
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> return "Phone Speaker"
                }
            }
            "Phone Speaker"
        } catch (_: Exception) {
            "Phone Speaker"
        }
    }
}
