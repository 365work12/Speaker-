package com.example.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import com.example.data.DiscoveredDesktopService
import com.example.data.DiscoveredSpeaker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.charset.StandardCharsets

class NetworkDiscoveryManager(
    private val context: Context,
    private val onSpeakerFound: (DiscoveredSpeaker) -> Unit,
    private val onDesktopServiceFound: (DiscoveredDesktopService) -> Unit = {}
) {
    private val TAG = "NetworkDiscoveryManager"

    companion object {
        const val SERVICE_TYPE_PHONE_SPEAKER = "_phonespeaker._tcp."
        const val SERVICE_TYPE_DESKTOP_AUDIO = "_desktopaudio._tcp."
        const val SERVICE_TYPE_AUDIO_STREAM = "_audiostream._tcp."
        const val UDP_BROADCAST_PORT = 9877
    }

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var httpRegistrationListener: NsdManager.RegistrationListener? = null

    // Multiple discovery listeners for phone and desktop services
    private val activeDiscoveryListeners = mutableListOf<NsdManager.DiscoveryListener>()

    private var broadcastJob: Job? = null
    private var listenJob: Job? = null
    private var resolveQueueJob: Job? = null

    // Channel queue for sequential NSD service resolution to prevent FAILURE_ALREADY_ACTIVE
    private val resolveChannel = Channel<NsdServiceInfo>(Channel.UNLIMITED)
    private val resolveMutex = Mutex()

    // ----------------------------------------------------
    // ADVERTISING THIS PHONE AS A SPEAKER SERVICE
    // ----------------------------------------------------

    fun registerService(port: Int, pin: String, scope: CoroutineScope) {
        unregisterService()

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "PhoneSpeaker_${Build.MODEL.replace(" ", "_")}"
            serviceType = SERVICE_TYPE_PHONE_SPEAKER
            setPort(port)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                setAttribute("pin", pin)
                setAttribute("model", Build.MODEL)
                setAttribute("os", "Android")
                setAttribute("rate", "44100")
            }
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo?) {
                Log.d(TAG, "Phone speaker registered on NSD: ${serviceInfo?.serviceName}")
            }
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                Log.e(TAG, "NSD registration failed: $errorCode")
            }
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo?) {
                Log.d(TAG, "NSD service unregistered")
            }
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                Log.e(TAG, "NSD unregistration failed: $errorCode")
            }
        }

        try {
            nsdManager?.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            Log.e(TAG, "NSD register error", e)
        }

        // Register HTTP web service for phonespeaker.local:8080
        val httpServiceInfo = NsdServiceInfo().apply {
            serviceName = "phonespeaker"
            serviceType = "_http._tcp."
            setPort(8080)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                setAttribute("path", "/")
            }
        }
        httpRegistrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo?) {
                Log.d(TAG, "HTTP mDNS registered: phonespeaker.local:8080")
            }
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {}
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo?) {}
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {}
        }
        try {
            nsdManager?.registerService(httpServiceInfo, NsdManager.PROTOCOL_DNS_SD, httpRegistrationListener)
        } catch (e: Exception) {
            Log.e(TAG, "HTTP NSD register error", e)
        }

        startUdpBeacon(port, pin, scope)
    }

    private fun startUdpBeacon(port: Int, pin: String, scope: CoroutineScope) {
        broadcastJob = scope.launch(Dispatchers.IO) {
            val message = "PSPK_DISCOVER:${Build.MODEL}:$port:$pin"
            val bytes = message.toByteArray(StandardCharsets.UTF_8)
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket()
                socket.broadcast = true
                val broadcastAddress = InetAddress.getByName("255.255.255.255")

                while (isActive) {
                    val packet = DatagramPacket(bytes, bytes.size, broadcastAddress, UDP_BROADCAST_PORT)
                    socket.send(packet)
                    delay(3000)
                }
            } catch (e: Exception) {
                Log.d(TAG, "UDP Beacon ended: ${e.message}")
            } finally {
                socket?.close()
            }
        }
    }

    fun unregisterService() {
        broadcastJob?.cancel()
        broadcastJob = null

        registrationListener?.let {
            try {
                nsdManager?.unregisterService(it)
            } catch (_: Exception) {}
            registrationListener = null
        }

        httpRegistrationListener?.let {
            try {
                nsdManager?.unregisterService(it)
            } catch (_: Exception) {}
            httpRegistrationListener = null
        }
    }

    // ----------------------------------------------------
    // DESKTOP AUDIO STREAMING SERVICE DISCOVERY (NSD)
    // ----------------------------------------------------

    fun startDesktopDiscovery(scope: CoroutineScope) {
        stopAllDiscovery()
        startResolveWorker(scope)

        // Discover standard desktop audio streaming service types
        val desktopTypes = listOf(
            SERVICE_TYPE_DESKTOP_AUDIO,
            SERVICE_TYPE_AUDIO_STREAM,
            SERVICE_TYPE_PHONE_SPEAKER
        )

        for (serviceType in desktopTypes) {
            createAndStartListener(serviceType, isDesktopSearch = true)
        }

        // Secondary UDP listener for desktop UDP beacons
        startUdpListener(scope, isDesktopSearch = true)
    }

    fun stopDesktopDiscovery() {
        stopAllDiscovery()
    }

    // ----------------------------------------------------
    // PHONE-TO-PHONE SPEAKER DISCOVERY
    // ----------------------------------------------------

    fun startDiscovery(scope: CoroutineScope) {
        stopAllDiscovery()
        startResolveWorker(scope)

        createAndStartListener(SERVICE_TYPE_PHONE_SPEAKER, isDesktopSearch = false)
        startUdpListener(scope, isDesktopSearch = false)
    }

    fun stopDiscovery() {
        stopAllDiscovery()
    }

    private fun createAndStartListener(serviceType: String, isDesktopSearch: Boolean) {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String?) {
                Log.d(TAG, "NSD Discovery started for: $regType")
            }

            override fun onServiceFound(service: NsdServiceInfo?) {
                if (service == null) return
                val matched = service.serviceType.contains("desktopaudio") ||
                              service.serviceType.contains("audiostream") ||
                              service.serviceType.contains("phonespeaker")
                if (matched) {
                    Log.d(TAG, "Found candidate service: ${service.serviceName} (${service.serviceType})")
                    resolveChannel.trySend(service)
                }
            }

            override fun onServiceLost(service: NsdServiceInfo?) {
                Log.d(TAG, "Service lost: ${service?.serviceName}")
            }

            override fun onDiscoveryStopped(serviceType: String?) {
                Log.d(TAG, "Discovery stopped for: $serviceType")
            }

            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Log.e(TAG, "Start discovery failed ($errorCode) for $serviceType")
            }

            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Log.e(TAG, "Stop discovery failed ($errorCode) for $serviceType")
            }
        }

        synchronized(activeDiscoveryListeners) {
            activeDiscoveryListeners.add(listener)
        }

        try {
            nsdManager?.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting NSD discovery for $serviceType", e)
        }
    }

    private fun startResolveWorker(scope: CoroutineScope) {
        resolveQueueJob?.cancel()
        resolveQueueJob = scope.launch(Dispatchers.IO) {
            for (service in resolveChannel) {
                if (!isActive) break
                resolveMutex.withLock {
                    resolveServiceSynchronously(service)
                }
                delay(120) // Brief delay between resolves to keep Android NSD daemon happy
            }
        }
    }

    private suspend fun resolveServiceSynchronously(serviceInfo: NsdServiceInfo) {
        val doneSignal = kotlinx.coroutines.CompletableDeferred<Unit>()

        try {
            nsdManager?.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                    Log.w(TAG, "NSD resolve failed for ${serviceInfo?.serviceName}: error $errorCode")
                    doneSignal.complete(Unit)
                }

                override fun onServiceResolved(resolvedService: NsdServiceInfo?) {
                    if (resolvedService != null) {
                        handleResolvedService(resolvedService)
                    }
                    doneSignal.complete(Unit)
                }
            })

            // Timeout after 2.5s per service so queue doesn't hang
            kotlinx.coroutines.withTimeoutOrNull(2500) {
                doneSignal.await()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during resolveService", e)
        }
    }

    private fun handleResolvedService(service: NsdServiceInfo) {
        val host = service.host?.hostAddress ?: return
        val port = service.port
        if (port <= 0) return

        var osName = "Desktop"
        var format = "PCM 16-bit"
        var sampleRate = 44100
        var isStereo = false
        var pin: String? = null
        var model = service.serviceName

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            service.attributes?.let { attrs ->
                attrs["os"]?.let { osName = String(it, StandardCharsets.UTF_8) }
                attrs["format"]?.let { format = String(it, StandardCharsets.UTF_8) }
                attrs["rate"]?.let { rateStr ->
                    String(rateStr, StandardCharsets.UTF_8).toIntOrNull()?.let { sampleRate = it }
                }
                attrs["channels"]?.let { chStr ->
                    isStereo = String(chStr, StandardCharsets.UTF_8).contains("2") ||
                               String(chStr, StandardCharsets.UTF_8).contains("stereo")
                }
                attrs["pin"]?.let { pin = String(it, StandardCharsets.UTF_8) }
                attrs["model"]?.let { model = String(it, StandardCharsets.UTF_8) }
            }
        }

        val type = service.serviceType
        val isDesktop = type.contains("desktopaudio") ||
                        type.contains("audiostream") ||
                        osName.lowercase().contains("windows") ||
                        osName.lowercase().contains("mac") ||
                        osName.lowercase().contains("linux") ||
                        model.lowercase().contains("pc") ||
                        model.lowercase().contains("mac") ||
                        model.lowercase().contains("desktop") ||
                        model.lowercase().contains("streamer")

        if (isDesktop) {
            val desktopService = DiscoveredDesktopService(
                name = model.replace('_', ' '),
                ip = host,
                port = port,
                serviceType = type,
                os = osName,
                audioFormat = format,
                sampleRate = sampleRate,
                isStereo = isStereo
            )
            onDesktopServiceFound(desktopService)
        }

        // Also notify general speaker callback
        onSpeakerFound(
            DiscoveredSpeaker(
                name = model.replace('_', ' '),
                ip = host,
                port = port,
                pin = pin
            )
        )
    }

    private fun startUdpListener(scope: CoroutineScope, isDesktopSearch: Boolean) {
        listenJob = scope.launch(Dispatchers.IO) {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket(UDP_BROADCAST_PORT).apply {
                    broadcast = true
                    reuseAddress = true
                }
                val buffer = ByteArray(1024)

                while (isActive) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    val senderIp = packet.address.hostAddress ?: continue
                    val text = String(packet.data, 0, packet.length, StandardCharsets.UTF_8)

                    if (text.startsWith("PSPK_DISCOVER:") || text.startsWith("DESKTOP_AUDIO:")) {
                        val parts = text.split(":")
                        if (parts.size >= 4) {
                            val model = parts[1]
                            val port = parts[2].toIntOrNull() ?: 9876
                            val pin = parts[3]
                            val os = if (parts.size >= 5) parts[4] else "Desktop"

                            if (isDesktopSearch) {
                                onDesktopServiceFound(
                                    DiscoveredDesktopService(
                                        name = model,
                                        ip = senderIp,
                                        port = port,
                                        serviceType = SERVICE_TYPE_DESKTOP_AUDIO,
                                        os = os
                                    )
                                )
                            }
                            onSpeakerFound(DiscoveredSpeaker(name = model, ip = senderIp, port = port, pin = pin))
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "UDP listener closed: ${e.message}")
            } finally {
                socket?.close()
            }
        }
    }

    private fun stopAllDiscovery() {
        listenJob?.cancel()
        listenJob = null

        resolveQueueJob?.cancel()
        resolveQueueJob = null

        synchronized(activeDiscoveryListeners) {
            for (listener in activeDiscoveryListeners) {
                try {
                    nsdManager?.stopServiceDiscovery(listener)
                } catch (_: Exception) {}
            }
            activeDiscoveryListeners.clear()
        }
    }
}
