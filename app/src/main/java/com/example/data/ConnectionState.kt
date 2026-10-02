package com.example.data

enum class SpeakerMode {
    WIFI_RECEIVER,    // This phone acts as the wireless speaker (Server)
    DESKTOP_AUDIO,    // Discover & stream audio from PC/Mac Desktop Audio Services via NSD
    WIFI_SENDER,      // This phone streams audio to another speaker phone
    BLUETOOTH_CHECK   // Checks Bluetooth A2DP Sink capability
}

enum class ConnectionStatus {
    DISCONNECTED,
    INITIALIZING,
    LISTENING,        // Server is running and waiting for sender
    CONNECTING,       // Establishing connection
    RECONNECTING,     // Periodically attempting to reconnect after signal drop
    CONNECTED,        // Audio streaming actively
    PAUSED,           // Paused (audio focus lost e.g., call)
    ERROR
}

enum class AudioSourceType {
    MICROPHONE,       // Real-time microphone / megaphone
    TEST_SYNTH,       // High-quality rhythmic synth beat & melody
    AUDIO_FILE        // User picked audio file
}

data class DiscoveredSpeaker(
    val name: String,
    val ip: String,
    val port: Int,
    val pin: String? = null
)

data class DiscoveredDesktopService(
    val name: String,
    val ip: String,
    val port: Int,
    val serviceType: String,
    val os: String = "Desktop",
    val audioFormat: String = "PCM 16-bit",
    val sampleRate: Int = 44100,
    val isStereo: Boolean = false
)

data class NetworkQualityMetrics(
    val latencyMs: Int = 12,
    val jitterMs: Float = 1.8f,
    val signalBars: Int = 4, // 1 to 4
    val qualityText: String = "Excellent",
    val wifiSsid: String? = null,
    val wifiFrequencyMhz: Int = 5000,
    val wifiLinkSpeedMbps: Int = 300,
    val packetsTransferred: Long = 0L
)

data class NetworkDevice(
    val ip: String,
    val hostName: String,
    val isReachable: Boolean,
    val isPhoneSpeakerService: Boolean = false,
    val isWebAudioService: Boolean = false,
    val responseTimeMs: Long = 0L,
    val deviceType: String = "Generic Device" // "PC / Laptop", "Phone", "Router", "Generic Device"
)

data class SpeakerUiState(
    val mode: SpeakerMode = SpeakerMode.WIFI_RECEIVER,
    val status: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val statusMessage: String = "Ready to start",
    val localIp: String = "Detecting...",
    val port: Int = 9876,
    val pairingPin: String = "1234",
    val connectedDeviceName: String? = null,
    val connectedDeviceIp: String? = null,
    val volume: Float = 0.85f,
    val isMuted: Boolean = false,
    val isAudioPaused: Boolean = false,
    val audioLevel: Float = 0f, // 0.0 to 1.0 for live VU visualizer
    val streamDurationSeconds: Long = 0L,
    val bytesReceived: Long = 0L,
    val sampleRate: Int = 44100,
    val bufferLatencyMs: Int = 50,
    val currentAudioRoute: String = "Phone Speaker",

    // Visual Connection Status Telemetry
    val activeProtocol: String = "Wi-Fi", // "Wi-Fi" or "Bluetooth"
    val currentBitrateKbps: Int = 1411, // Real-time transmission bitrate in kbps
    val pingMs: Int = 12, // Round-trip latency (ping) to PC source in ms

    // Network Quality & Jitter Monitoring
    val networkQuality: NetworkQualityMetrics = NetworkQualityMetrics(),

    // Connection Retry & Reconnect Mechanism
    val isAutoReconnecting: Boolean = false,
    val retryAttempt: Int = 0,
    val maxRetryAttempts: Int = 5,
    val retryCountdownSeconds: Int = 0,

    // Desktop Web Browser Streamer
    val isWebStreamerActive: Boolean = false,
    val webStreamerUrl: String = "",
    val webStreamerPort: Int = 8080,

    // Subnet Network Device Scanner (All devices on same Wi-Fi)
    val isScanningSubnet: Boolean = false,
    val subnetDevices: List<NetworkDevice> = emptyList(),
    val subnetScanProgress: Float = 0f,

    // Desktop NSD Discovery & Streaming
    val discoveredDesktops: List<DiscoveredDesktopService> = emptyList(),
    val isSearchingDesktops: Boolean = false,
    val selectedDesktop: DiscoveredDesktopService? = null,
    val desktopManualIp: String = "",
    val desktopManualPort: Int = 9876,

    // Bluetooth Sink capability information
    val isBluetoothSupported: Boolean = true,
    val isBluetoothEnabled: Boolean = false,
    val isBluetoothSinkSupported: Boolean = false,
    val bluetoothSinkExplanation: String = "Checking Bluetooth capability...",
    val bluetoothDeviceName: String = "Unknown Device",

    // Sender mode state
    val targetIp: String = "",
    val targetPort: Int = 9876,
    val targetPin: String = "",
    val selectedSource: AudioSourceType = AudioSourceType.MICROPHONE,
    val isMicMuted: Boolean = false,
    val micGain: Float = 1.0f,
    val selectedAudioFileName: String? = null,
    val discoveredSpeakers: List<DiscoveredSpeaker> = emptyList(),
    val isSearchingSpeakers: Boolean = false
)
