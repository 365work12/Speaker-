package com.example.audio

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.sin

class TestAudioGenerator(
    private val onAudioLevelChanged: (Float) -> Unit = {}
) {
    private var isGenerating = false

    suspend fun startGenerating(
        sampleRate: Int = 44100,
        onAudioData: suspend (ByteArray, Int) -> Unit
    ) = withContext(Dispatchers.Default) {
        isGenerating = true

        // Pentatonic frequencies in Hz (C4, D4, E4, G4, A4, C5, D5, E5)
        val melodyNotes = doubleArrayOf(
            261.63, 293.66, 329.63, 392.00, 440.00, 523.25, 587.33, 659.25
        )
        // Arpeggio sequence indices
        val melodyPattern = intArrayOf(0, 2, 4, 7, 5, 4, 2, 0, 1, 3, 5, 6, 5, 3, 1, 0)

        val chunkSize = 2048
        val buffer = ByteArray(chunkSize)
        var phaseMelody = 0.0
        var phaseBass = 0.0
        var totalSamplesGenerated = 0L

        val samplesPerNote = (sampleRate * 0.22).toLong() // ~136 BPM sixteenth/eighth rhythm

        try {
            while (isActive && isGenerating) {
                var bufferIdx = 0
                val samplesInChunk = chunkSize / 2

                for (s in 0 until samplesInChunk) {
                    val currentSampleIndex = totalSamplesGenerated + s
                    val noteIndex = ((currentSampleIndex / samplesPerNote) % melodyPattern.size).toInt()
                    val targetFreq = melodyNotes[melodyPattern[noteIndex]]
                    val bassFreq = targetFreq * 0.5 // Sub-octave warm bass

                    // Note envelope (decay towards end of note)
                    val noteProgress = (currentSampleIndex % samplesPerNote).toDouble() / samplesPerNote
                    val envelope = (1.0 - noteProgress * 0.85)

                    phaseMelody += 2.0 * PI * targetFreq / sampleRate
                    if (phaseMelody > 2.0 * PI) phaseMelody -= 2.0 * PI

                    phaseBass += 2.0 * PI * bassFreq / sampleRate
                    if (phaseBass > 2.0 * PI) phaseBass -= 2.0 * PI

                    // Wave combination: sine lead + warm triangle bass + soft harmonic
                    val lead = sin(phaseMelody) * 0.45 * envelope
                    val harmonic = sin(phaseMelody * 2.0) * 0.15 * envelope
                    val bass = sin(phaseBass) * 0.3

                    val mixed = (lead + harmonic + bass).coerceIn(-1.0, 1.0)
                    val sampleShort = (mixed * 24000.0).toInt().toShort()

                    buffer[bufferIdx] = (sampleShort.toInt() and 0xFF).toByte()
                    buffer[bufferIdx + 1] = ((sampleShort.toInt() shr 8) and 0xFF).toByte()
                    bufferIdx += 2
                }

                totalSamplesGenerated += samplesInChunk

                // Compute level for UI
                onAudioLevelChanged(0.65f)

                onAudioData(buffer, buffer.size)

                // Sleep appropriate amount of time to maintain 44.1kHz real-time rate
                val chunkDurationMs = (samplesInChunk * 1000L) / sampleRate
                kotlinx.coroutines.delay(chunkDurationMs)
            }
        } catch (_: CancellationException) {
        } finally {
            stopGenerating()
        }
    }

    fun stopGenerating() {
        isGenerating = false
        onAudioLevelChanged(0f)
    }
}
