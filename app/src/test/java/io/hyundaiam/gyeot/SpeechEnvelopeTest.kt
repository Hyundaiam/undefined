package io.hyundaiam.gyeot

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SpeechEnvelopeTest {
    private fun pcm(vararg samples: Int) = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN).apply {
        samples.forEach { putShort(it.toShort()) }; flip()
    }

    @Test fun amplitudeFollowsPlaybackTimeInsteadOfDecodedAheadAudio() {
        val envelope = SpeechEnvelope()
        val silent = pcm(*IntArray(1600))
        val speech = pcm(*IntArray(1600) { if (it % 2 == 0) 12000 else -12000 })
        envelope.append(silent, 0, 16000, 1)
        envelope.append(speech, 100_000, 16000, 1)
        assertEquals(0f, envelope.at(50_000), 0f)
        assertTrue(envelope.at(110_000) > .5f)
        assertEquals(0f, envelope.at(250_000), 0f)
        assertEquals(0, silent.position())
        assertEquals(0, speech.position())
    }

    @Test fun partialSinkRetriesDoNotDuplicateAndResetRemovesOldSpeech() {
        val envelope = SpeechEnvelope()
        val speech = pcm(*IntArray(320) { 15000 })
        envelope.append(speech, 10_000, 16000, 1)
        envelope.append(speech, 10_000, 16000, 1)
        assertTrue(envelope.at(15_000) > 0)
        assertEquals(0f, envelope.at(40_000), 0f)
        envelope.clear()
        assertEquals(0f, envelope.at(15_000), 0f)
        envelope.append(pcm(*IntArray(320)), 10_000, 16000, 1)
        assertEquals(0f, envelope.at(15_000), 0f)
    }

    @Test fun floatStereoUsesBothChannelsAndIgnoresNonFiniteSamples() {
        val envelope = SpeechEnvelope()
        val buffer = ByteBuffer.allocate(4 * 2 * 320).order(ByteOrder.LITTLE_ENDIAN).apply {
            repeat(320) { putFloat(Float.NaN); putFloat(.4f) }; flip()
        }
        envelope.append(buffer, 0, 16000, 2, true)
        assertTrue(envelope.at(10_000).isFinite())
        assertTrue(envelope.at(10_000) > .5f)
    }
}
