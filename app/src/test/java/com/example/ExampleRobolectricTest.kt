package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.ConnectionStatus
import com.example.data.DiscoveredDesktopService
import com.example.data.NetworkDevice
import com.example.data.NetworkQualityMetrics
import com.example.data.SpeakerMode
import com.example.data.SpeakerUiState
import com.example.utils.NetworkUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("SoundLink", appName)
  }

  @Test
  fun `verify initial speaker state`() {
    val state = SpeakerUiState()
    assertEquals(SpeakerMode.WIFI_RECEIVER, state.mode)
    assertEquals(ConnectionStatus.DISCONNECTED, state.status)
    assertEquals(9876, state.port)
    assertEquals(8080, state.webStreamerPort)
    assertFalse(state.isAutoReconnecting)
    assertEquals(0, state.retryAttempt)
    assertEquals(5, state.maxRetryAttempts)
    assertTrue(state.discoveredDesktops.isEmpty())
  }

  @Test
  fun `verify pairing pin generation`() {
    val pin = NetworkUtils.generatePairingPin()
    assertEquals(4, pin.length)
    assertTrue(pin.toInt() in 1000..9999)
  }

  @Test
  fun `verify desktop service data model`() {
    val desktop = DiscoveredDesktopService(
      name = "Windows PC Audio",
      ip = "192.168.1.150",
      port = 9876,
      serviceType = "_desktopaudio._tcp.",
      os = "Windows 11",
      sampleRate = 48000
    )
    assertEquals("Windows PC Audio", desktop.name)
    assertEquals("192.168.1.150", desktop.ip)
    assertEquals(9876, desktop.port)
    assertEquals(48000, desktop.sampleRate)
  }

  @Test
  fun `verify network quality and device models`() {
    val quality = NetworkQualityMetrics(
      latencyMs = 15,
      jitterMs = 2.1f,
      signalBars = 4,
      qualityText = "Excellent"
    )
    assertEquals(15, quality.latencyMs)
    assertEquals(2.1f, quality.jitterMs, 0.01f)
    assertEquals(4, quality.signalBars)

    val device = NetworkDevice(
      ip = "192.168.1.105",
      hostName = "DESKTOP-WIN11",
      isReachable = true,
      isWebAudioService = true,
      deviceType = "Windows PC / Desktop"
    )
    assertEquals("DESKTOP-WIN11", device.hostName)
    assertTrue(device.isReachable)
    assertTrue(device.isWebAudioService)
  }

  @Test
  fun `verify auto reconnecting state`() {
    val state = SpeakerUiState(
      status = ConnectionStatus.RECONNECTING,
      isAutoReconnecting = true,
      retryAttempt = 2,
      maxRetryAttempts = 5,
      retryCountdownSeconds = 3
    )
    assertEquals(ConnectionStatus.RECONNECTING, state.status)
    assertTrue(state.isAutoReconnecting)
    assertEquals(2, state.retryAttempt)
    assertEquals(5, state.maxRetryAttempts)
    assertEquals(3, state.retryCountdownSeconds)
  }

  @Test
  fun `verify QR code generation for web link`() {
    val url = "http://soundlink.local:8080"
    val bitmap = com.example.ui.components.generateQrCodeBitmap(url, 256)
    assertNotNull(bitmap)
    assertEquals(256, bitmap?.width)
    assertEquals(256, bitmap?.height)
  }

  @Test
  fun `verify audio level visualizer state`() {
    val state = SpeakerUiState(
      status = ConnectionStatus.CONNECTED,
      audioLevel = 0.75f,
      isAudioPaused = false
    )
    assertEquals(ConnectionStatus.CONNECTED, state.status)
    assertEquals(0.75f, state.audioLevel, 0.01f)
    assertFalse(state.isAudioPaused)
  }

  @Test
  fun `verify visual connection status indicator telemetry defaults`() {
    val state = SpeakerUiState(
      activeProtocol = "Wi-Fi",
      currentBitrateKbps = 1411,
      pingMs = 12
    )
    assertEquals("Wi-Fi", state.activeProtocol)
    assertEquals(1411, state.currentBitrateKbps)
    assertEquals(12, state.pingMs)

    val bluetoothState = state.copy(
      activeProtocol = "Bluetooth",
      currentBitrateKbps = 328,
      pingMs = 38
    )
    assertEquals("Bluetooth", bluetoothState.activeProtocol)
    assertEquals(328, bluetoothState.currentBitrateKbps)
    assertEquals(38, bluetoothState.pingMs)
  }
}
