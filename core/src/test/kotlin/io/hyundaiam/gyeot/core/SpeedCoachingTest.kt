package io.hyundaiam.gyeot.core

import org.junit.Assert.*
import org.junit.Test

class SpeedCoachingTest {
    @Test fun speedingDoesNotWaitForANonSpeedWarningCooldown() {
        val gate = SpeechGate()
        gate.markPlayed(Tone.CAUTION, 1_000)
        assertFalse(gate.canSpeak(Tone.CAUTION, 2_000))
        assertTrue(gate.canSpeak(Tone.CAUTION, 2_000, urgent = true))
        gate.markPlayed(Tone.CAUTION, 2_000, urgent = true)
        assertFalse(gate.canSpeak(Tone.CAUTION, 3_000, urgent = true))
    }
    private fun frame(t: Long, speed: Double, limit: Double? = 60.0) = SignalFrame(t, SignalSource.EMULATOR, speedKph = speed, speedLimitKph = limit, speedTimeMs = t, speedLimitTimeMs = t)
    @Test fun oneKphAboveSixtyTriggersAfterTwoSecondsButSixtyDoesNot() {
        val engine = CoachEngine()
        assertFalse(engine.evaluate(frame(0, 60.0)).any { it.scenarioId == "B02" })
        assertFalse(engine.evaluate(frame(1_000, 61.0)).any { it.scenarioId == "B02" })
        assertTrue(engine.evaluate(frame(3_000, 61.0)).any { it.scenarioId == "B02" })
    }
    @Test fun slowingDownGetsRecoveryFeedbackAndRearmsTheNextWarning() {
        val engine = CoachEngine()
        engine.evaluate(frame(0, 75.0)); engine.evaluate(frame(2_000, 75.0))
        engine.evaluate(frame(3_000, 58.0))
        assertTrue(engine.evaluate(frame(6_000, 58.0)).any { it.scenarioId == "C06" })
        engine.evaluate(frame(7_000, 75.0))
        assertTrue(engine.evaluate(frame(9_000, 75.0)).any { it.scenarioId == "B02" })
    }
    @Test fun anUnknownRoadLimitIsReportedRatherThanDefaultedToSixty() {
        assertEquals(SpeedState.WAITING_LIMIT, SpeedAssessment.from(frame(0, 75.0, null), 0).state)
        val engine = CoachEngine(); engine.evaluate(frame(0, 75.0, null))
        assertFalse(engine.evaluate(frame(5_000, 75.0, null)).any { it.scenarioId == "B02" })
    }
    @Test fun testRoadLimitCannotMakeAnOldVehicleSpeedFresh() {
        val stale = frame(10_000, 75.0).copy(speedTimeMs = 0)
        assertEquals(SpeedState.WAITING_SPEED, SpeedAssessment.from(stale, 10_000).state)
    }
    @Test fun cautionPreemptsPraiseAndCheckingAvailabilityDoesNotConsumeTheGate() {
        val gate = SpeechGate()
        gate.markPlayed(Tone.GOOD, 0)
        assertTrue(gate.canSpeak(Tone.CAUTION, 1_000))
        assertTrue(gate.canSpeak(Tone.CAUTION, 1_000))
        gate.markPlayed(Tone.CAUTION, 1_000)
        assertFalse(gate.canSpeak(Tone.CAUTION, 2_000))
    }
    @Test fun changingToAHigherRoadLimitDoesNotInventAnImprovement() {
        val engine = CoachEngine()
        engine.evaluate(frame(0, 75.0)); engine.evaluate(frame(2_000, 75.0))
        engine.evaluate(frame(3_000, 75.0, 80.0))
        assertFalse(engine.evaluate(frame(6_000, 75.0, 80.0)).any { it.scenarioId == "C06" })
    }
}
