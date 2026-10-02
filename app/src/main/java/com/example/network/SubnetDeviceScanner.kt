package com.example.network

import android.util.Log
import com.example.data.NetworkDevice
import com.example.utils.NetworkUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

class SubnetDeviceScanner {

    private val TAG = "SubnetDeviceScanner"

    suspend fun scanSubnet(
        onProgress: (Float) -> Unit,
        onDeviceFound: (NetworkDevice) -> Unit
    ): List<NetworkDevice> = withContext(Dispatchers.IO) {
        val myIp = NetworkUtils.getLocalIpAddress() ?: return@withContext emptyList()
        val parts = myIp.split(".")
        if (parts.size != 4) return@withContext emptyList()

        val subnetPrefix = "${parts[0]}.${parts[1]}.${parts[2]}."
        val myLastOctet = parts[3].toIntOrNull() ?: -1

        val discoveredDevices = Collections.synchronizedList(mutableListOf<NetworkDevice>())
        val completedCount = AtomicInteger(0)
        val totalHosts = 254
        val semaphore = Semaphore(30) // Concurrent probes

        coroutineScope {
            val jobs = (1..totalHosts).map { hostIndex ->
                async {
                    if (!isActive) return@async
                    val targetIp = "$subnetPrefix$hostIndex"

                    semaphore.withPermit {
                        if (!isActive) return@withPermit
                        val device = probeHost(targetIp, isSelf = hostIndex == myLastOctet, isGateway = hostIndex == 1)
                        if (device != null) {
                            discoveredDevices.add(device)
                            onDeviceFound(device)
                        }

                        val done = completedCount.incrementAndGet()
                        onProgress(done.toFloat() / totalHosts)
                    }
                }
            }

            try {
                jobs.awaitAll()
            } catch (_: CancellationException) {}
        }

        discoveredDevices.sortedBy { device ->
            // Sort: Self/PhoneSpeaker first, then active gateways/PCs, then others
            when {
                device.isPhoneSpeakerService -> 0
                device.isWebAudioService -> 1
                device.deviceType.contains("Router") -> 2
                else -> 3
            }
        }
    }

    private fun probeHost(targetIp: String, isSelf: Boolean, isGateway: Boolean): NetworkDevice? {
        val startTime = System.currentTimeMillis()
        var isAlive = false
        var isPhoneSpeaker = false
        var isWebAudio = false

        // 1. Probe PhoneSpeaker Port 9876
        isPhoneSpeaker = checkPortOpen(targetIp, 9876, 200)

        // 2. Probe Web Audio Port 8080
        isWebAudio = checkPortOpen(targetIp, 8080, 200)

        // 3. If neither, probe other common web/service ports (80, 443, 445, 5353) or reachability
        if (isPhoneSpeaker || isWebAudio) {
            isAlive = true
        } else {
            isAlive = checkPortOpen(targetIp, 80, 150) ||
                      checkPortOpen(targetIp, 443, 150) ||
                      checkPortOpen(targetIp, 445, 150) ||
                      checkPortOpen(targetIp, 22, 150)
        }

        if (!isAlive && !isSelf) {
            // Fallback ICMP reachability check
            try {
                val addr = InetAddress.getByName(targetIp)
                isAlive = addr.isReachable(180)
            } catch (_: Exception) {}
        }

        if (!isAlive && !isSelf) return null

        val responseTime = System.currentTimeMillis() - startTime

        // Resolve Host Name
        var hostName = targetIp
        try {
            val addr = InetAddress.getByName(targetIp)
            val canonical = addr.canonicalHostName
            if (canonical != targetIp && canonical.isNotBlank()) {
                hostName = canonical
            } else {
                val rawHost = addr.hostName
                if (rawHost != targetIp && rawHost.isNotBlank()) {
                    hostName = rawHost
                }
            }
        } catch (_: Exception) {}

        val lowerHost = hostName.lowercase()
        val deviceType = when {
            isSelf -> "This Phone (Speaker)"
            isGateway -> "Wi-Fi Router / Gateway"
            isPhoneSpeaker -> "Phone Speaker Device"
            isWebAudio -> "Desktop Web Streamer"
            lowerHost.contains("pc") || lowerHost.contains("desktop") || lowerHost.contains("laptop") || lowerHost.contains("windows") -> "Windows PC / Desktop"
            lowerHost.contains("mac") || lowerHost.contains("apple") || lowerHost.contains("darwin") -> "Mac / Apple Device"
            lowerHost.contains("android") || lowerHost.contains("phone") || lowerHost.contains("pixel") || lowerHost.contains("samsung") -> "Mobile Device"
            lowerHost.contains("router") || lowerHost.contains("gateway") -> "Router / Access Point"
            else -> "Network Device"
        }

        return NetworkDevice(
            ip = targetIp,
            hostName = if (isSelf) "This Device" else hostName,
            isReachable = true,
            isPhoneSpeakerService = isPhoneSpeaker,
            isWebAudioService = isWebAudio,
            responseTimeMs = responseTime,
            deviceType = deviceType
        )
    }

    private fun checkPortOpen(ip: String, port: Int, timeoutMs: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), timeoutMs)
                true
            }
        } catch (_: Exception) {
            false
        }
    }
}
