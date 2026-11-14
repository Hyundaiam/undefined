package io.hyundaiam.gyeot

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.ArrayDeque
import kotlin.math.ln
import kotlin.math.sqrt

/** Small PCM windows indexed by presentation time, rather than by decoder arrival time. */
internal class SpeechEnvelope {
    private data class Window(val startUs: Long, val endUs: Long, val level: Float)
    private val windows = ArrayDeque<Window>()
    private var lastBufferTime = Long.MIN_VALUE

    fun clear() { windows.clear(); lastBufferTime = Long.MIN_VALUE }

    fun append(input: ByteBuffer, timeUs: Long, sampleRate: Int, channels: Int, floatPcm: Boolean = false) {
        // A sink may receive the same partially consumed buffer more than once.
        if (timeUs == lastBufferTime || sampleRate <= 0 || channels <= 0) return
        lastBufferTime = timeUs
        val data = input.asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN)
        val bytesPerSample = if (floatPcm) 4 else 2
        val totalFrames = data.remaining() / (bytesPerSample * channels)
        val windowFrames = (sampleRate / 50).coerceAtLeast(1)
        var frame = 0
        while (frame < totalFrames) {
            val count = minOf(windowFrames, totalFrames - frame)
            var energy = 0.0
            repeat(count * channels) {
                val sample = if (floatPcm) data.float.toDouble().takeIf(Double::isFinite) ?: 0.0 else data.short / 32768.0
                energy += sample * sample
            }
            val rms = sqrt(energy / (count * channels))
            // Quiet gaps stay quiet; logarithmic gain gives ordinary speech visible articulation.
            val level = if (rms < .003) 0f else (ln(1 + rms * 24) / ln(13.0)).toFloat().coerceIn(0f, 1f)
            windows.addLast(Window(timeUs + frame * 1_000_000L / sampleRate, timeUs + (frame + count) * 1_000_000L / sampleRate, level))
            frame += count
        }
        while (windows.size > 2000) windows.removeFirst()
    }

    fun at(positionUs: Long): Float {
        while (windows.isNotEmpty() && windows.first.endUs <= positionUs) windows.removeFirst()
        return windows.firstOrNull()?.takeIf { positionUs in it.startUs until it.endUs }?.level ?: 0f
    }
}
