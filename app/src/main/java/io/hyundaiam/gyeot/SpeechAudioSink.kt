package io.hyundaiam.gyeot

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import java.nio.ByteBuffer

/** Observes decoded samples without changing them or recording microphone/system audio. */
@UnstableApi
internal class SpeechAudioSink(sink: AudioSink) : ForwardingAudioSink(sink) {
    private val envelope = SpeechEnvelope()
    private var format: Format? = null
    @Volatile var level = 0f
        private set

    override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
        super.configure(inputFormat, specifiedBufferSize, outputChannels)
        format = inputFormat
    }

    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
        format?.let {
            if (it.pcmEncoding == C.ENCODING_PCM_16BIT || it.pcmEncoding == C.ENCODING_PCM_FLOAT) {
                envelope.append(buffer, presentationTimeUs, it.sampleRate, it.channelCount, it.pcmEncoding == C.ENCODING_PCM_FLOAT)
            }
        }
        return super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
    }

    override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
        val position = super.getCurrentPositionUs(sourceEnded)
        level = envelope.at(position)
        return position
    }

    override fun flush() { super.flush(); envelope.clear(); level = 0f }
    override fun reset() { super.reset(); envelope.clear(); level = 0f }
    override fun pause() { super.pause(); level = 0f }
}
