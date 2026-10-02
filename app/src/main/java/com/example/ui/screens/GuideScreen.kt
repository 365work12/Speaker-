package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.CellWifi
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun GuideScreen(
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Text(
            text = "How to Use Phone Speaker",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Stream real-time audio from another phone or PC/Mac desktop over your local Wi-Fi network.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Desktop Audio with NSD
        GuideCard(
            stepNumber = "🖥️",
            title = "Desktop Audio Streamer (NSD)",
            icon = Icons.Default.Laptop,
            content = "Go to the 'Desktop NSD' tab. When a desktop audio streamer (e.g., Python sounddevice script, PulseAudio, or AudioRelay) is running on your PC or Mac, Android's Network Service Discovery (NSD) automatically detects it. Tap 'Connect' to stream your PC sound directly to your phone speaker!"
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Method 1: Same Wi-Fi
        GuideCard(
            stepNumber = "1",
            title = "Connect to the Same Network",
            icon = Icons.Default.Wifi,
            content = "Ensure both devices (Phone A and Phone B, or Phone and PC) are connected to the same Wi-Fi router, or turn on 'Mobile Hotspot' on either phone and connect the other device to it."
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Method 2: Start Receiver
        GuideCard(
            stepNumber = "2",
            title = "Phone A: Tap 'START SPEAKER'",
            icon = Icons.Default.VolumeUp,
            content = "Open the app on Phone A (the phone you want to use as a speaker). Tap 'START SPEAKER'. Note the displayed IP Address (e.g. 192.168.1.100) and the 4-digit PIN code."
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Method 3: Connect Sender
        GuideCard(
            stepNumber = "3",
            title = "Phone B: Connect & Stream",
            icon = Icons.Default.MusicNote,
            content = "Open the app on Phone B. Switch to 'Stream Audio' tab. Phone B will automatically discover Phone A! Tap 'Connect', select your audio source (Microphone or Synth Beats), and tap 'START STREAMING'."
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Method 4: No Router? Mobile Hotspot Tip
        GuideCard(
            stepNumber = "💡",
            title = "No Wi-Fi? Use Mobile Hotspot",
            icon = Icons.Default.CellWifi,
            content = "You don't need an internet connection! Simply turn on 'Personal Hotspot' on your phone, connect your laptop or second phone to it over Wi-Fi, and launch Phone Speaker. Audio streams 100% locally with ultra-low latency."
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun GuideCard(
    stepNumber: String,
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stepNumber,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )
        }
    }
}
