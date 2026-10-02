package com.example.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SpeakerUiState
import com.example.ui.components.SpeakerHeroCard
import com.example.ui.components.VolumeControl

@Composable
fun BluetoothScreen(
    uiState: SpeakerUiState,
    onCheckAgain: () -> Unit,
    onSwitchToWifiMode: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onToggleMute: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Bluetooth Diagnostic Header Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("bluetooth_diagnostic_card"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (uiState.isBluetoothSinkSupported) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                }
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(
                            if (uiState.isBluetoothSinkSupported) Color(0xFF10B981)
                            else if (!uiState.isBluetoothEnabled) Color(0xFFF59E0B)
                            else MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (!uiState.isBluetoothEnabled) Icons.Default.BluetoothDisabled
                        else if (uiState.isBluetoothSinkSupported) Icons.Default.Bluetooth
                        else Icons.Default.ErrorOutline,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = if (uiState.isBluetoothSinkSupported) Color.White
                        else if (!uiState.isBluetoothEnabled) Color.White
                        else MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Bluetooth Speaker",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (uiState.isBluetoothSinkSupported) {
                        "Ready to pair! Phone supports receiving Bluetooth audio."
                    } else if (!uiState.isBluetoothEnabled) {
                        "Bluetooth is currently switched off."
                    } else {
                        "Bluetooth audio sink is disabled on standard Android phone ROMs."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            try {
                                context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                            } catch (_: Exception) {}
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("BT Settings", fontSize = 12.sp)
                    }

                    Button(
                        onClick = onCheckAgain,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Recheck", fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // If NOT supported: Prominent CTA to Wi-Fi Speaker Mode
        if (!uiState.isBluetoothSinkSupported) {
            Button(
                onClick = onSwitchToWifiMode,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("use_wifi_speaker_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Wifi,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "USE WI-FI SPEAKER (RECOMMENDED)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Educational / Technical Details Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Why doesn't Android support BT Sink?",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "• Android smartphones are designed as audio sources (A2DP Source) to transmit sound to headphones, earbuds, and car stereos.\n\n" +
                                "• The A2DP Sink (receiver) role is disabled in standard Android phone ROMs by Google and OEMs to prevent audio routing conflicts, and is reserved for Android Automotive/Car head units.\n\n" +
                                "• Wi-Fi Speaker mode in this app bypasses this restriction completely using local Wi-Fi with higher bitrates (CD-quality uncompressed PCM) and lower latency!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            }
        } else {
            // Supported device (e.g. specialized hardware or Android Automotive)
            SpeakerHeroCard(
                status = uiState.status,
                audioLevel = uiState.audioLevel,
                connectedDeviceName = uiState.bluetoothDeviceName,
                audioRouteName = "Bluetooth A2DP Sink"
            )

            Spacer(modifier = Modifier.height(16.dp))

            VolumeControl(
                volume = uiState.volume,
                isMuted = uiState.isMuted,
                onVolumeChange = onVolumeChange,
                onToggleMute = onToggleMute
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
