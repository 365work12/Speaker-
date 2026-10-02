package com.example.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object BluetoothCapabilityChecker {

    // BluetoothProfile.A2DP_SINK constant in Android Framework is 11
    const val A2DP_SINK_PROFILE = 11

    data class BluetoothCheckResult(
        val isBluetoothSupported: Boolean,
        val isBluetoothEnabled: Boolean,
        val isA2dpSinkSupported: Boolean,
        val deviceName: String,
        val message: String
    )

    suspend fun checkCapability(context: Context): BluetoothCheckResult = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val hasBtFeature = pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)
        if (!hasBtFeature) {
            return@withContext BluetoothCheckResult(
                isBluetoothSupported = false,
                isBluetoothEnabled = false,
                isA2dpSinkSupported = false,
                deviceName = "No Bluetooth",
                message = "This device does not have Bluetooth hardware."
            )
        }

        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = btManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()

        if (adapter == null) {
            return@withContext BluetoothCheckResult(
                isBluetoothSupported = false,
                isBluetoothEnabled = false,
                isA2dpSinkSupported = false,
                deviceName = "No Bluetooth",
                message = "Bluetooth adapter is unavailable on this device."
            )
        }

        val isEnabled = adapter.isEnabled
        var deviceName = "Unknown Device"
        try {
            deviceName = adapter.name ?: "${Build.MANUFACTURER} ${Build.MODEL}"
        } catch (_: SecurityException) {
            deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
        }

        if (!isEnabled) {
            return@withContext BluetoothCheckResult(
                isBluetoothSupported = true,
                isBluetoothEnabled = false,
                isA2dpSinkSupported = false,
                deviceName = deviceName,
                message = "Bluetooth is currently turned off. Please turn on Bluetooth in settings."
            )
        }

        // Test A2DP Sink support
        val isA2dpSinkSupported = probeA2dpSinkSupport(context, adapter)

        val explanation = if (isA2dpSinkSupported) {
            "Bluetooth A2DP Sink is supported on this device. You can accept incoming Bluetooth audio from other devices."
        } else {
            "Bluetooth Speaker Mode is not supported on this phone. Standard Android phones only operate as Bluetooth audio transmitters (A2DP Source). The receiver role (A2DP Sink) is compiled out of standard phone firmware by manufacturers and restricted to Automotive/custom devices. Please use Wi-Fi Speaker Mode instead."
        }

        BluetoothCheckResult(
            isBluetoothSupported = true,
            isBluetoothEnabled = true,
            isA2dpSinkSupported = isA2dpSinkSupported,
            deviceName = deviceName,
            message = explanation
        )
    }

    private fun probeA2dpSinkSupport(context: Context, adapter: BluetoothAdapter): Boolean {
        // First check if the BluetoothA2dpSink class exists in this runtime
        val a2dpSinkClassExists = try {
            Class.forName("android.bluetooth.BluetoothA2dpSink")
            true
        } catch (_: Throwable) {
            false
        }

        if (!a2dpSinkClassExists) {
            return false
        }

        val latch = CountDownLatch(1)
        val supported = AtomicBoolean(false)

        try {
            val listener = object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
                    if (profile == A2DP_SINK_PROFILE && proxy != null) {
                        supported.set(true)
                        try {
                            adapter.closeProfileProxy(profile, proxy)
                        } catch (_: Throwable) {}
                    }
                    latch.countDown()
                }

                override fun onServiceDisconnected(profile: Int) {
                    latch.countDown()
                }
            }

            val proxyRequested = adapter.getProfileProxy(context, listener, A2DP_SINK_PROFILE)
            if (!proxyRequested) {
                return false
            }

            // Wait briefly for callback
            latch.await(700, TimeUnit.MILLISECONDS)
        } catch (_: SecurityException) {
            return false
        } catch (_: Throwable) {
            return false
        }

        return supported.get()
    }
}
