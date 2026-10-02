package com.example.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log

class WifiNetworkMonitor(
    private val context: Context,
    private val onWifiAvailable: () -> Unit,
    private val onWifiLost: () -> Unit
) {
    private val TAG = "WifiNetworkMonitor"
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var isRegistered = false

    fun startMonitoring() {
        if (isRegistered || connectivityManager == null) return

        try {
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()

            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.d(TAG, "Wi-Fi network connection acquired")
                    onWifiAvailable()
                }

                override fun onLost(network: Network) {
                    Log.d(TAG, "Wi-Fi network connection lost")
                    onWifiLost()
                }
            }

            connectivityManager.registerNetworkCallback(request, networkCallback!!)
            isRegistered = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback", e)
        }
    }

    fun stopMonitoring() {
        if (!isRegistered) return
        try {
            networkCallback?.let { connectivityManager?.unregisterNetworkCallback(it) }
        } catch (_: Exception) {}
        networkCallback = null
        isRegistered = false
    }
}
