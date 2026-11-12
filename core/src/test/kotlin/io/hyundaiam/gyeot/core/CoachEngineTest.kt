package io.hyundaiam.gyeot.core

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class CoachEngineTest {
    @Test fun recordingCatalogContainsExactlyFifteenGoodAndFifteenCautionSituations() {
        val catalog = ScenarioCatalog.parse(File(System.getProperty("catalogPath")).readText())
        assertEquals(15, catalog.scenarios.count { it.tone == Tone.GOOD })
        assertEquals(15, catalog.scenarios.count { it.tone == Tone.CAUTION })
        assertEquals(8, catalog.scenarios.count { it.tone == Tone.COMMON })
        assertTrue(catalog.scenarios.all { it.recordingText.isNotBlank() && it.trigger.isNotBlank() })
    }
    @Test fun missingSignalsDoNotProduceDrivingFeedback() {
        val engine = CoachEngine()
        repeat(100) { i -> assertTrue(engine.evaluate(SignalFrame(i * 1000L, SignalSource.PLEOS)).isEmpty()) }
    }
    @Test fun speedingRequiresAFreshSpeedAndANavigationLimit() {
        val engine = CoachEngine()
        fun frame(t: Long) = SignalFrame(t, SignalSource.PLEOS, speedKph = 74.0, speedLimitKph = 60.0, speedTimeMs = t, speedLimitTimeMs = t)
        assertTrue(engine.evaluate(frame(0)).isEmpty())
        assertEquals("B02", engine.evaluate(frame(3_000)).single().scenarioId)
        assertTrue(engine.evaluate(frame(4_000)).isEmpty())
        assertTrue(CoachEngine().evaluate(frame(0).copy(speedLimitKph = null)).isEmpty())
    }
    @Test fun oldMotionDataCannotBeRefreshedByOtherSensors() {
        val engine = CoachEngine()
        engine.evaluate(SignalFrame(0, SignalSource.PLEOS, speedKph = 80.0, speedLimitKph = 60.0, speedTimeMs = 0, speedLimitTimeMs = 0))
        val staleSpeed = SignalFrame(10_000, SignalSource.PLEOS, speedKph = 80.0, speedLimitKph = 60.0, speedTimeMs = 0, speedLimitTimeMs = 10_000)
        assertTrue(engine.evaluate(staleSpeed).isEmpty())
    }
    @Test fun unoccupiedSeatsNeverTriggerBeltWarnings() {
        val engine = CoachEngine()
        for (t in listOf(0L, 5_000L)) assertTrue(engine.evaluate(SignalFrame(t, SignalSource.DEMO, speedKph = 40.0, beltBySeat = mapOf("passenger" to false))).isEmpty())
    }
    @Test fun unknownBeltStateDoesNotCountAsUnbuckled() {
        val engine = CoachEngine()
        for (t in listOf(0L, 5_000L)) assertTrue(engine.evaluate(SignalFrame(t, SignalSource.DEMO, speedKph = 40.0, occupiedSeats = setOf("driver"))).isEmpty())
    }
    @Test fun confirmedOccupiedUnbuckledSeatProducesWarning() {
        val engine = CoachEngine()
        fun frame(t: Long) = SignalFrame(t, SignalSource.DEMO, speedKph = 40.0, occupiedSeats = setOf("driver"), beltBySeat = mapOf("driver" to false))
        engine.evaluate(frame(0))
        assertEquals("B14", engine.evaluate(frame(2_000)).single().scenarioId)
    }
    @Test fun emergencyOrUnknownBrakingIsNotPunished() {
        for (emergency in listOf(true, null)) {
            val engine = CoachEngine()
            engine.evaluate(SignalFrame(0, SignalSource.DEMO, speedKph = 80.0, emergencyBraking = emergency))
            assertFalse(engine.evaluate(SignalFrame(1_000, SignalSource.DEMO, speedKph = 30.0, emergencyBraking = emergency)).any { it.scenarioId == "B04" })
        }
    }
    @Test fun switchingFromDemoToVehicleResetsConditionTimers() {
        val engine = CoachEngine()
        engine.evaluate(SignalFrame(0, SignalSource.DEMO, speedKph = 74.0, speedLimitKph = 60.0))
        assertTrue(engine.evaluate(SignalFrame(4_000, SignalSource.PLEOS, speedKph = 74.0, speedLimitKph = 60.0, speedTimeMs = 4_000, speedLimitTimeMs = 4_000)).isEmpty())
    }
    @Test fun speechWaitsDuringNavigationAndAvoidsRapidRepeats() {
        val gate = SpeechGate()
        assertFalse(gate.allow(Tone.GOOD, 0, true))
        assertTrue(gate.allow(Tone.GOOD, 0, false))
        assertFalse(gate.allow(Tone.GOOD, 5_000, false))
        assertTrue(gate.allow(Tone.CAUTION, 15_000, false))
    }
    @Test fun noObservationDoesNotInventADrivingScore() { assertNull(Counts(0, 0).goodRate) }
}
