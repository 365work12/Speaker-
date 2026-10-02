package com.example.network

import android.os.Build
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket

class WifiSpeakerClient(
    private val onConnected: (speakerDeviceName: String) -> Unit,
    private val onDisconnected: () -> Unit,
    private val onError: (String) -> Unit
) {
    private val TAG = "WifiSpeakerClient"

    private var clientSocket: Socket? = null
    private var dataOutputStream: DataOutputStream? = null
    private var isConnected = false

    suspend fun connect(targetIp: String, targetPort: Int, pin: String): Boolean = withContext(Dispatchers.IO) {
        disconnect()

        try {
            val socket = Socket()
            socket.tcpNoDelay = true
            socket.connect(InetSocketAddress(targetIp, targetPort), 5000)

            val output = DataOutputStream(socket.getOutputStream())
            val input = DataInputStream(socket.getInputStream())

            // 1. Send Handshake
            output.write(WifiSpeakerServer.MAGIC_BYTES)
            output.writeByte(WifiSpeakerServer.MSG_HANDSHAKE_REQ.toInt())
            output.writeUTF(pin)
            val myDeviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
            output.writeUTF(myDeviceName)
            output.flush()

            // 2. Read Response
            val magic = ByteArray(4)
            input.readFully(magic)
            if (!magic.contentEquals(WifiSpeakerServer.MAGIC_BYTES)) {
                socket.close()
                onError("Invalid response from speaker.")
                return@withContext false
            }

            val msgType = input.readByte()
            if (msgType != WifiSpeakerServer.MSG_HANDSHAKE_RESP) {
                socket.close()
                onError("Unexpected response from speaker.")
                return@withContext false
            }

            val status = input.readByte()
            if (status != WifiSpeakerServer.STATUS_OK) {
                val errorMsg = input.readUTF()
                socket.close()
                onError(errorMsg)
                return@withContext false
            }

            val speakerName = input.readUTF()
            clientSocket = socket
            dataOutputStream = output
            isConnected = true

            onConnected(speakerName)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Connection failed", e)
            onError("Connection failed: ${e.localizedMessage ?: "Device unreachable"}")
            false
        }
    }

    suspend fun sendAudioFrame(buffer: ByteArray, size: Int): Boolean = withContext(Dispatchers.IO) {
        val output = dataOutputStream ?: return@withContext false
        if (!isConnected) return@withContext false

        return@withContext try {
            output.write(WifiSpeakerServer.MAGIC_BYTES)
            output.writeByte(WifiSpeakerServer.MSG_AUDIO_DATA.toInt())
            output.writeInt(size)
            output.write(buffer, 0, size)
            output.flush()
            true
        } catch (e: CancellationException) {
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error sending audio frame", e)
            disconnect()
            false
        }
    }

    fun disconnect() {
        if (!isConnected && clientSocket == null) return
        isConnected = false

        try {
            dataOutputStream?.let { output ->
                output.write(WifiSpeakerServer.MAGIC_BYTES)
                output.writeByte(WifiSpeakerServer.MSG_DISCONNECT.toInt())
                output.flush()
            }
        } catch (_: Exception) {}

        try {
            clientSocket?.close()
        } catch (_: Exception) {}

        clientSocket = null
        dataOutputStream = null
        onDisconnected()
    }
}
