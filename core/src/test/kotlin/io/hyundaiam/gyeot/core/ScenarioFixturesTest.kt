package io.hyundaiam.gyeot.core

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Every replay must traverse sensor rules; an unrelated warning must not steal its voice. */
@RunWith(Parameterized::class)
class ScenarioFixturesTest(private val id: String) {
    @Test fun sensorReplayProducesOnlyTheExpectedSituation() {
        val engine = CoachEngine()
        val frames = ScenarioFixtures.frames(id)
        val detected = frames.flatMap { engine.evaluate(it) }.map { it.scenarioId }
        assertEquals(id, listOf(id), detected)
        assertTrue(frames.all { it.source == SignalSource.DEMO })
    }
    companion object {
        @JvmStatic @Parameterized.Parameters(name="{0}") fun cases() = ScenarioFixtures.ids.map { arrayOf(it) }
    }
}
