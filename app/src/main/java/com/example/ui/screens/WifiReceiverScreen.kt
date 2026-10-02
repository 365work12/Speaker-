package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ConnectionStatus
import com.example.data.SpeakerUiState
import com.example.ui.components.AudioVisualizer
import com.example.ui.components.QrCodeDialog
import com.example.ui.components.QrCodeImage
import com.example.ui.components.VolumeControl

@Composable
fun WifiReceiverScreen(
    uiState: SpeakerUiState,
    onStartReceiver: () -> Unit,
    onStopReceiver: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onToggleMute: () -> Unit,
    onOpenDevicesSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val isRunning = uiState.status == ConnectionStatus.LISTENING || uiState.status == ConnectionStatus.CONNECTED || uiState.status == ConnectionStatus.RECONNECTING
    val isStreaming = uiState.status == ConnectionStatus.CONNECTED
    val isReconnecting = uiState.status == ConnectionStatus.RECONNECTING

    val webUrl = "http://${uiState.localIp}:${uiState.webStreamerPort}"
    val localDomainUrl = "http://soundlink.local:${uiState.webStreamerPort}"

    var showQrDialog by remember { mutableStateOf(false) }
    var showInlineQr by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isStreaming) 1.12f else if (isReconnecting) 1.09f else if (isRunning) 1.06f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isStreaming) 500 else if (isReconnecting) 800 else 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "waves"
    )

    val activeGlowColor by animateColorAsState(
        targetValue = when {
            isStreaming -> Color(0xFF10B981) // Emerald Green
            isReconnecting -> Color(0xFFF59E0B) // Amber Reconnecting
            isRunning -> Color(0xFF38BDF8) // Electric Cyan
            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
        },
        label = "glowColor"
    )

    if (showQrDialog) {
        QrCodeDialog(
            url = localDomainUrl,
            onDismiss = { showQrDialog = false },
            onCopy = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("SoundLink Link", localDomainUrl))
                Toast.makeText(context, "Link copied!", Toast.LENGTH_SHORT).show()
            },
            onShare = {
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    putExtra(Intent.EXTRA_TEXT, "Open this link on your PC to stream audio to SoundLink: $localDomainUrl (or $webUrl)")
                    type = "text/plain"
                }
                context.startActivity(Intent.createChooser(sendIntent, "Share SoundLink Link"))
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        // Center Big Glowing Power / Speaker Button
        Box(
            modifier = Modifier
                .size(200.dp)
                .testTag("speaker_main_power_button"),
            contentAlignment = Alignment.Center
        ) {
            // Concentric radiating sound waves when active
            if (isRunning) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val maxRadius = size.minDimension / 2
                    for (ring in 1..3) {
                        val ringOffset = (wavePhase + ring * 0.33f) % 1.0f
                        val currentRadius = maxRadius * (0.6f + ringOffset * 0.4f)
                        val alpha = (1.0f - ringOffset) * (if (isStreaming) 0.7f else 0.35f)

                        drawCircle(
                            color = activeGlowColor.copy(alpha = alpha),
                            radius = currentRadius,
                            center = center,
                            style = Stroke(width = 2.5.dp.toPx())
                        )
                    }
                }
            }

            // Central Touch Circle
            Box(
                modifier = Modifier
                    .size(136.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                if (isRunning) activeGlowColor.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.colorScheme.surface
                            )
                        )
                    )
                    .border(
                        width = 3.dp,
                        brush = Brush.linearGradient(
                            listOf(activeGlowColor, MaterialTheme.colorScheme.primary)
                        ),
                        shape = CircleShape
                    )
                    .clickable {
                        if (isRunning) onStopReceiver() else onStartReceiver()
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (isStreaming) Icons.Default.GraphicEq else if (isReconnecting) Icons.Default.GraphicEq else if (isRunning) Icons.Default.VolumeUp else Icons.Default.PowerSettingsNew,
                        contentDescription = "Activate Speaker",
                        modifier = Modifier.size(46.dp),
                        tint = activeGlowColor
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isReconnecting) "RECONNECTING" else if (isStreaming) "STREAMING" else if (isRunning) "ACTIVE" else "TAP TO START",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = activeGlowColor,
                        letterSpacing = 1.sp
                    )
                }
            }
        }

        // Subtitle status
        Text(
            text = when {
                isReconnecting -> "Signal dropped • Retrying in ${uiState.retryCountdownSeconds}s (Attempt ${uiState.retryAttempt}/${uiState.maxRetryAttempts})"
                isStreaming -> "Playing sound from ${uiState.connectedDeviceName ?: "Desktop PC"}"
                isRunning -> "Speaker is on • Waiting for audio from PC or phone"
                else -> "Tap the button to start receiving audio on this phone"
            },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (isRunning) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Real-Time Audio Visualization Component (Prominently placed on Main Screen)
        AudioVisualizer(
            audioLevel = uiState.audioLevel,
            isActive = isStreaming || (isRunning && uiState.audioLevel > 0.005f)
        )

        // Volume Control Slider
        VolumeControl(
            volume = uiState.volume,
            isMuted = uiState.isMuted,
            onVolumeChange = onVolumeChange,
            onToggleMute = onToggleMute
        )

        // PC Browser Stream Link Card (Clean & Prominent)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("pc_connect_link_card"),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (isRunning) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else Color.Transparent
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Laptop,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Stream from Windows PC or Mac",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color(0xFF10B981).copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "NO APP REQUIRED",
                            color = Color(0xFF10B981),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Scan the QR code or open this link in Chrome/Edge on your PC:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Link Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = localDomainUrl,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "or IP: $webUrl",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row {
                        IconButton(
                            onClick = { showQrDialog = true },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("show_qr_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCode2,
                                contentDescription = "Show QR Code",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("SoundLink Link", localDomainUrl))
                                Toast.makeText(context, "Link copied! Open on your PC.", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy Link",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        IconButton(
                            onClick = {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    putExtra(Intent.EXTRA_TEXT, "Open this link on your PC to stream audio to SoundLink: $localDomainUrl (or $webUrl)")
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share PC Link"))
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Inline QR Code Toggle & Preview
                OutlinedButton(
                    onClick = { showInlineQr = !showInlineQr },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("toggle_inline_qr_button"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = if (showInlineQr) Icons.Default.QrCode else Icons.Default.QrCode2,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (showInlineQr) "Hide QR Code" else "Scan QR Code on Screen",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                AnimatedVisibility(
                    visible = showInlineQr,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        QrCodeImage(
                            url = localDomainUrl,
                            modifier = Modifier
                                .size(200.dp)
                                .clickable { showQrDialog = true }
                                .testTag("inline_qr_image"),
                            sizePx = 400
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Point your PC webcam, laptop camera, or phone at this screen to open the SoundLink controller page.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }
            }
        }

        // Minimal "More Devices & Options" link button
        OutlinedButton(
            onClick = onOpenDevicesSheet,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Explore Other Devices & Tools", fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
