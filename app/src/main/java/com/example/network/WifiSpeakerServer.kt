package com.example.network

import android.os.Build
import android.util.Log
import com.example.audio.AudioPlaybackManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException

class WifiSpeakerServer(
    private val audioPlaybackManager: AudioPlaybackManager,
    private val onClientConnected: (deviceName: String, deviceIp: String) -> Unit,
    private val onClientDisconnected: () -> Unit,
    private val onStatsUpdated: (bytesReceived: Long, durationSec: Long) -> Unit,
    private val onError: (String) -> Unit
) {
    private val TAG = "WifiSpeakerServer"

    companion object {
        val MAGIC_BYTES = byteArrayOf('P'.code.toByte(), 'S'.code.toByte(), 'P'.code.toByte(), 'K'.code.toByte())
        const val MSG_HANDSHAKE_REQ: Byte = 1
        const val MSG_HANDSHAKE_RESP: Byte = 2
        const val MSG_AUDIO_DATA: Byte = 3
        const val MSG_DISCONNECT: Byte = 4
        const val MSG_PING: Byte = 5
        const val MSG_PONG: Byte = 6

        const val STATUS_OK: Byte = 0
        const val STATUS_INVALID_PIN: Byte = 1
    }

    private var serverSocket: ServerSocket? = null
    private var activeClientSocket: Socket? = null
    private var serverJob: Job? = null
    private var isRunning = false
    private var expectedPin: String = ""

    fun start(port: Int, pairingPin: String, scope: CoroutineScope) {
        if (isRunning) return
        expectedPin = pairingPin
        isRunning = true

        serverJob = scope.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(port).apply {
                    reuseAddress = true
                }
                Log.d(TAG, "Server listening on port $port")

                while (isActive && isRunning) {
                    val clientSocket = try {
                        serverSocket?.accept() ?: break
                    } catch (e: SocketException) {
                        break // Server closed
                    }

                    handleClient(clientSocket)
                }
            } catch (e: CancellationException) {
                // Expected on cancellation
            } catch (e: Exception) {
                Log.e(TAG, "Server error", e)
                onError("Server error: ${e.localizedMessage ?: "Unknown"}")
            } finally {
                stop()
            }
        }
    }

    private suspend fun handleClient(clientSocket: Socket) = withContext(Dispatchers.IO) {
        val clientIp = clientSocket.inetAddress.hostAddress ?: "Unknown"
        activeClientSocket = clientSocket
        clientSocket.tcpNoDelay = true // Low latency streaming

        var inputStream: DataInputStream? = null
        var outputStream: DataOutputStream? = null

        try {
            inputStream = DataInputStream(clientSocket.getInputStream())
            outputStream = DataOutputStream(clientSocket.getOutputStream())

            // 1. Handshake verification
            val magic = ByteArray(4)
            inputStream.readFully(magic)
            if (!magic.contentEquals(MAGIC_BYTES)) {
                Log.w(TAG, "Invalid magic bytes from $clientIp")
                clientSocket.close()
                return@withContext
            }

            val msgType = inputStream.readByte()
            if (msgType != MSG_HANDSHAKE_REQ) {
                Log.w(TAG, "Unexpected message type: $msgType")
                clientSocket.close()
                return@withContext
            }

            val pin = inputStream.readUTF()
            val senderDeviceName = inputStream.readUTF()

            // Verify PIN
            if (expectedPin.isNotEmpty() && pin != expectedPin) {
                Log.w(TAG, "Invalid PIN entered: $pin vs expected $expectedPin")
                outputStream.write(MAGIC_BYTES)
                outputStream.writeByte(MSG_HANDSHAKE_RESP.toInt())
                outputStream.writeByte(STATUS_INVALID_PIN.toInt())
                outputStream.writeUTF("Invalid pairing PIN code.")
                outputStream.flush()
                clientSocket.close()
                onError("Connection rejected from $senderDeviceName: incorrect PIN.")
                return@withContext
            }

            // Accept connection
            outputStream.write(MAGIC_BYTES)
            outputStream.writeByte(MSG_HANDSHAKE_RESP.toInt())
            outputStream.writeByte(STATUS_OK.toInt())
            val myDeviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
            outputStream.writeUTF(myDeviceName)
            outputStream.flush()

            // Start audio playback
            audioPlaybackManager.start()
            onClientConnected(senderDeviceName, clientIp)

            var totalBytes = 0L
            val startTime = System.currentTimeMillis()
            val readBuffer = ByteArray(4096)

            // 2. Stream audio packets
            while (isActive && isRunning && !clientSocket.isClosed) {
                val packetMagic = ByteArray(4)
                inputStream.readFully(packetMagic)
                if (!packetMagic.contentEquals(MAGIC_BYTES)) {
                    break
                }

                val packetType = inputStream.readByte()
                when (packetType) {
                    MSG_AUDIO_DATA -> {
                        val payloadLength = inputStream.readInt()
                        if (payloadLength in 1..65536) {
                            var bytesLeft = payloadLength
                            while (bytesLeft > 0) {
                                val toRead = minOf(bytesLeft, readBuffer.size)
                                inputStream.readFully(readBuffer, 0, toRead)
                                audioPlaybackManager.writePcm(readBuffer, 0, toRead)
                                bytesLeft -= toRead
                                totalBytes += toRead
                            }

                            val durationSec = (System.currentTimeMillis() - startTime) / 1000L
                            onStatsUpdated(totalBytes, durationSec)
                        }
                    }
                    MSG_DISCONNECT -> {
                        Log.d(TAG, "Client sent disconnect signal")
                        break
                    }
                    MSG_PING -> {
                        outputStream.write(MAGIC_BYTES)
                        outputStream.writeByte(MSG_PONG.toInt())
                        outputStream.flush()
                    }
                }
            }
        } catch (e: CancellationException) {
            // Cancelled
        } catch (e: Exception) {
            Log.d(TAG, "Client communication closed: ${e.message}")
        } finally {
            try {
                audioPlaybackManager.stop()
            } catch (_: Exception) {}
            try {
                clientSocket.close()
            } catch (_: Exception) {}
            activeClientSocket = null
            onClientDisconnected()
        }
    }

    fun stop() {
        isRunning = false
        try {
            activeClientSocket?.close()
        } catch (_: Exception) {}
        activeClientSocket = null

        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null

        serverJob?.cancel()
        serverJob = null
    }
}
