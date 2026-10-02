package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun AudioVisualizer(
    audioLevel: Float,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val barCount = 28
    val clampedLevel = audioLevel.coerceIn(0f, 1f)

    val infiniteTransition = rememberInfiniteTransition(label = "audio_wave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val liveDotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "live_dot"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("audio_visualizer_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isActive && clampedLevel > 0.05f) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            // Header Row: Visualizer Title & Real-time Stream Intensity
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = if (isActive && clampedLevel > 0.02f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Real-Time Audio Spectrum",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isActive) {
                        // Pulsing Live Indicator
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (clampedLevel > 0.02f) Color(0xFF10B981).copy(alpha = liveDotAlpha)
                                    else Color(0xFFF59E0B)
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (clampedLevel > 0.02f) "${(clampedLevel * 100).toInt()}% INTENSITY" else "STANDBY",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = if (clampedLevel > 0.02f) Color(0xFF10B981) else Color(0xFFF59E0B)
                        )
                    } else {
                        Text(
                            text = "OFFLINE",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Multi-bar Dynamic Equalizer Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0F172A).copy(alpha = 0.85f))
                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .testTag("audio_visualizer_canvas_container"),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    for (i in 0 until barCount) {
                        // Pseudo-frequency curve weighting (bass/mid/treble)
                        val freqWeight = remember(i) {
                            val normalizedIndex = i.toFloat() / barCount
                            // Bell curve peaking in mid-bass and mid frequencies
                            0.65f + 0.35f * sin(normalizedIndex * 3.14159f)
                        }
                        val randomOffset = remember(i) { 0.8f + Random(i * 19).nextFloat() * 0.4f }

                        val barHeightAnim = remember { Animatable(4f) }

                        LaunchedEffect(clampedLevel, isActive, phase) {
                            if (isActive && clampedLevel > 0.01f) {
                                val dynamicPhase = (phase * 6.28f + i * 0.35f)
                                val modulation = 0.8f + 0.2f * sin(dynamicPhase)
                                val target = (6f + (clampedLevel * 48f * freqWeight * randomOffset * modulation))
                                    .coerceIn(4f, 52f)

                                barHeightAnim.animateTo(
                                    targetValue = target,
                                    animationSpec = tween(55, easing = FastOutSlowInEasing)
                                )
                            } else if (isActive) {
                                // Subtle ambient breathing wave when active but silence
                                val idleWave = (6f + 3f * sin(phase * 6.28f + i * 0.35f)).coerceAtLeast(4f)
                                barHeightAnim.animateTo(idleWave, tween(90))
                            } else {
                                barHeightAnim.animateTo(3f, tween(120))
                            }
                        }

                        val barGradient = remember(clampedLevel, isActive) {
                            when {
                                !isActive -> listOf(Color(0xFF334155), Color(0xFF1E293B))
                                clampedLevel > 0.6f -> listOf(Color(0xFFF43F5E), Color(0xFF8B5CF6), Color(0xFF06B6D4))
                                clampedLevel > 0.25f -> listOf(Color(0xFF10B981), Color(0xFF06B6D4), Color(0xFF3B82F6))
                                else -> listOf(Color(0xFF38BDF8), Color(0xFF0284C7))
                            }
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom,
                            modifier = Modifier.height(54.dp)
                        ) {
                            // Floating peak cap dot for audio dynamics
                            if (isActive && clampedLevel > 0.05f) {
                                Box(
                                    modifier = Modifier
                                        .size(width = 5.dp, height = 2.dp)
                                        .clip(RoundedCornerShape(1.dp))
                                        .background(Color.White.copy(alpha = 0.85f))
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                            }

                            // Dynamic equalizer bar
                            Box(
                                modifier = Modifier
                                    .width(5.dp)
                                    .height(barHeightAnim.value.dp)
                                    .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                                    .background(Brush.verticalGradient(barGradient))
                            )
                        }
                    }
                }
            }
        }
    }
}
