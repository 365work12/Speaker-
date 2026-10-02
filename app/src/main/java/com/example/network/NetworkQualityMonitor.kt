package com.example.network

import android.content.Context
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import com.example.data.NetworkQualityMetrics
import kotlin.math.abs
import kotlin.math.roundToInt

class NetworkQualityMonitor(private val context: Context) {

    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private var currentJitter = 1.5f
    private var lastTransitTime = 0L
    private var lastRtt = 10L
    private var packetsCount = 0L

    /**
     * Updates latency from an RTT measurement and computes RFC 3550 statistical jitter.
     */
    fun recordRtt(rttMs: Long): NetworkQualityMetrics {
        packetsCount++
        val safeRtt = rttMs.coerceIn(1L, 1000L)
        val d = abs(safeRtt - lastRtt).toFloat()
        // RFC 3550 Jitter estimate formula: J = J + (|D| - J) / 16
        currentJitter += (d - currentJitter) / 16.0f
        lastRtt = safeRtt

        return buildMetrics(safeRtt.toInt(), currentJitter)
    }

    /**
     * Updates packet arrival time on receiver to measure inter-arrival jitter.
     */
    fun recordPacketArrival(): NetworkQualityMetrics {
        packetsCount++
        val now = System.currentTimeMillis()
        if (lastTransitTime != 0L) {
            val delta = (now - lastTransitTime).toFloat()
            // Expected packet delta is ~23ms (chunk interval for 1024 samples at 44.1kHz)
            val deviation = abs(delta - 23f)
            currentJitter += (deviation - currentJitter) / 16.0f
        }
        lastTransitTime = now

        return buildMetrics(lastRtt.toInt(), currentJitter)
    }

    fun getInitialMetrics(): NetworkQualityMetrics {
        return buildMetrics(12, 1.8f)
    }

    private fun buildMetrics(latencyMs: Int, jitterMs: Float): NetworkQualityMetrics {
        val safeJitter = (jitterMs * 10).roundToInt() / 10f
        val (bars, quality) = when {
            latencyMs < 25 && safeJitter < 6.0f -> 4 to "Excellent"
            latencyMs < 50 && safeJitter < 15.0f -> 3 to "Good"
            latencyMs < 100 && safeJitter < 30.0f -> 2 to "Fair"
            else -> 1 to "Poor"
        }

        var ssid: String? = null
        var freq = 5000
        var linkSpeed = 300

        try {
            val connectionInfo: WifiInfo? = wifiManager?.connectionInfo
            if (connectionInfo != null) {
                ssid = connectionInfo.ssid?.replace("\"", "")
                if (ssid == "<unknown ssid>") ssid = null
                linkSpeed = connectionInfo.linkSpeed.coerceAtLeast(10)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    freq = connectionInfo.frequency
                }
            }
        } catch (_: Exception) {}

        return NetworkQualityMetrics(
            latencyMs = latencyMs,
            jitterMs = safeJitter,
            signalBars = bars,
            qualityText = quality,
            wifiSsid = ssid,
            wifiFrequencyMhz = freq,
            wifiLinkSpeedMbps = linkSpeed,
            packetsTransferred = packetsCount
        )
    }
}
