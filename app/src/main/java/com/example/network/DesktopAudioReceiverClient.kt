package com.example.network

import android.os.Build
import android.util.Log
import com.example.audio.AudioPlaybackManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket

class DesktopAudioReceiverClient(
    private val audioPlaybackManager: AudioPlaybackManager,
    private val onConnected: (desktopName: String, sampleRate: Int) -> Unit,
    private val onReconnecting: (attempt: Int, maxAttempts: Int, countdownSec: Int) -> Unit,
    private val onDisconnected: () -> Unit,
    private val onStatsUpdated: (bytesReceived: Long, durationSec: Long) -> Unit,
    private val onError: (String) -> Unit
) {
    private val TAG = "DesktopAudioReceiverClient"

    companion object {
        const val DEFAULT_MAX_RETRIES = 5
        val RETRY_DELAYS_SEC = listOf(2, 3, 5, 7, 10)
    }

    private var clientSocket: Socket? = null
    private var streamJob: Job? = null
    private var isConnected = false
    private var isUserDisconnect = false

    // Target parameters for auto-reconnect
    private var lastTargetIp: String = ""
    private var lastTargetPort: Int = 9876
    private var lastTargetName: String = ""
    private var lastSampleRate: Int = 44100
    private var lastIsStereo: Boolean = false
    private var coroutineScope: CoroutineScope? = null

    fun connectToDesktop(
        desktopIp: String,
        desktopPort: Int,
        desktopName: String,
        sampleRate: Int = 44100,
        isStereo: Boolean = false,
        scope: CoroutineScope
    ) {
        lastTargetIp = desktopIp
        lastTargetPort = desktopPort
        lastTargetName = desktopName
        lastSampleRate = sampleRate
        lastIsStereo = isStereo
        coroutineScope = scope
        isUserDisconnect = false

        streamJob?.cancel()
        streamJob = scope.launch(Dispatchers.IO) {
            connectWithRetryLoop()
        }
    }

    private suspend fun connectWithRetryLoop() {
        var attempt = 0
        var totalBytes = 0L
        val startTime = System.currentTimeMillis()

        while (coroutineScope?.isActive == true && !isUserDisconnect) {
            var connectedThisRound = false
            try {
                Log.d(TAG, "Attempting connection to desktop audio service at $lastTargetIp:$lastTargetPort")
                val socket = Socket()
                socket.tcpNoDelay = true
                // 4-second connect timeout
                socket.connect(InetSocketAddress(lastTargetIp, lastTargetPort), 4000)
                clientSocket = socket

                val output = DataOutputStream(socket.getOutputStream())
                val rawInput = BufferedInputStream(socket.getInputStream(), 16384)
                val input = DataInputStream(rawInput)

                // 1. Send Handshake
                output.write(WifiSpeakerServer.MAGIC_BYTES)
                output.writeByte(WifiSpeakerServer.MSG_HANDSHAKE_REQ.toInt())
                output.writeUTF("")
                val clientName = "SoundLink_${Build.MANUFACTURER}_${Build.MODEL}"
                output.writeUTF(clientName)
                output.flush()

                // 2. Initialize AudioTrack
                audioPlaybackManager.init(sampleRate = lastSampleRate, isStereo = lastIsStereo)
                audioPlaybackManager.start()

                isConnected = true
                connectedThisRound = true
                attempt = 0 // Reset retry count upon successful connection
                onConnected(lastTargetName, lastSampleRate)

                val buffer = ByteArray(4096)

                // 3. Receive & Play Audio Stream
                while (coroutineScope?.isActive == true && isConnected && !socket.isClosed) {
                    val bytesRead = rawInput.read(buffer, 0, buffer.size)
                    if (bytesRead <= 0) {
                        Log.d(TAG, "Desktop audio stream closed or signal dropped (EOF)")
                        break
                    }

                    // Check if stream wraps in PhoneSpeaker protocol packets
                    if (bytesRead >= 9 &&
                        buffer[0] == 'P'.code.toByte() &&
                        buffer[1] == 'S'.code.toByte() &&
                        buffer[2] == 'P'.code.toByte() &&
                        buffer[3] == 'K'.code.toByte()
                    ) {
                        val msgType = buffer[4]
                        if (msgType == WifiSpeakerServer.MSG_AUDIO_DATA) {
                            val payloadLen = ((buffer[5].toInt() and 0xFF) shl 24) or
                                             ((buffer[6].toInt() and 0xFF) shl 16) or
                                             ((buffer[7].toInt() and 0xFF) shl 8) or
                                             (buffer[8].toInt() and 0xFF)
                            val actualPayload = minOf(payloadLen, bytesRead - 9)
                            if (actualPayload > 0) {
                                audioPlaybackManager.writePcm(buffer, 9, actualPayload)
                                totalBytes += actualPayload
                            }
                        }
                    } else {
                        // Raw PCM audio stream
                        audioPlaybackManager.writePcm(buffer, 0, bytesRead)
                        totalBytes += bytesRead
                    }

                    val duration = (System.currentTimeMillis() - startTime) / 1000L
                    onStatsUpdated(totalBytes, duration)
                }
            } catch (e: CancellationException) {
                break
            } catch (e: Exception) {
                Log.w(TAG, "Connection failed or dropped: ${e.message}")
            } finally {
                cleanUpSocket()
            }

            if (isUserDisconnect) {
                break
            }

            // Connection lost / signal dropped — start retry backoff
            attempt++
            if (attempt > DEFAULT_MAX_RETRIES) {
                Log.w(TAG, "Exceeded maximum retry attempts ($DEFAULT_MAX_RETRIES). Stopping auto-reconnect.")
                onError("Signal dropped: unable to reconnect to $lastTargetName after $DEFAULT_MAX_RETRIES attempts.")
                onDisconnected()
                break
            }

            val delaySec = RETRY_DELAYS_SEC[(attempt - 1).coerceAtMost(RETRY_DELAYS_SEC.size - 1)]
            Log.d(TAG, "Wi-Fi signal dropped or desktop service lost. Retrying in ${delaySec}s (Attempt $attempt/$DEFAULT_MAX_RETRIES)...")

            // Countdown ticker for responsive UI
            for (sec in delaySec downTo 1) {
                if (isUserDisconnect || coroutineScope?.isActive != true) break
                onReconnecting(attempt, DEFAULT_MAX_RETRIES, sec)
                delay(1000L)
            }
        }
    }

    fun retryNow() {
        if (lastTargetIp.isNotEmpty() && !isConnected) {
            isUserDisconnect = false
            coroutineScope?.let { scope ->
                connectToDesktop(lastTargetIp, lastTargetPort, lastTargetName, lastSampleRate, lastIsStereo, scope)
            }
        }
    }

    private fun cleanUpSocket() {
        isConnected = false
        try {
            audioPlaybackManager.stop()
        } catch (_: Exception) {}

        try {
            clientSocket?.close()
        } catch (_: Exception) {}
        clientSocket = null
    }

    fun disconnect() {
        isUserDisconnect = true
        cleanUpSocket()
        streamJob?.cancel()
        streamJob = null
        onDisconnected()
    }
}
