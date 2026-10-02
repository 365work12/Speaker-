package com.example.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.NetworkInterface
import kotlin.random.Random

object NetworkUtils {

    /**
     * Retrieves the best available local IPv4 address for Wi-Fi or Hotspot interfaces.
     */
    fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            val candidates = mutableListOf<String>()

            for (networkInterface in interfaces) {
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                val name = networkInterface.name.lowercase()

                // Look for common wifi/hotspot interfaces (wlan0, ap0, swlan0, rndis0, etc.)
                for (address in networkInterface.inetAddresses) {
                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        val hostAddress = address.hostAddress ?: continue
                        if (hostAddress.startsWith("127.")) continue

                        if (name.contains("wlan") || name.contains("ap") || name.contains("swlan")) {
                            return hostAddress // Prioritize direct wifi/hotspot interface
                        }
                        candidates.add(hostAddress)
                    }
                }
            }

            return candidates.firstOrNull()
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Checks if the device is currently connected to a Wi-Fi network or Hotspot.
     */
    fun isWifiConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false

        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    /**
     * Generates an easy-to-read 4-digit pairing PIN.
     */
    fun generatePairingPin(): String {
        return Random.nextInt(1000, 9999).toString()
    }
}
