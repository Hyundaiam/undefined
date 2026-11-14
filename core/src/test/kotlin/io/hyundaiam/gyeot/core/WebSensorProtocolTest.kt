package io.hyundaiam.gyeot.core

import org.junit.Assert.*
import org.junit.Test

class WebSensorProtocolTest {
    @Test fun rawInputGetsAnAppOwnedClockAndSource() {
        val f = WebSensorProtocol.parse("""{"speedKph":75,"speedLimitKph":60,"handsOnWheel":2,"gear":"GEAR_DRIVE"}""",1234)
        assertEquals("GEAR_DRIVE",f.gear)
        assertEquals(SignalSource.WEB_SIM,f.source); assertEquals(1234L,f.timeMs)
        assertEquals(1234L,f.speedTimeMs); assertEquals(1234L,f.speedLimitTimeMs)
        assertTrue(f.occupiedSeats.isEmpty()); assertTrue(f.beltBySeat.isEmpty())
    }
    @Test fun callerCannotSendAVerdictClockOrDifferentSource() {
        for(json in listOf("""{"scenarioId":"B02"}""","""{"source":"PLEOS"}""","""{"timeMs":123}""","""{"speedKph":"80"}""","""{"doorsOpen":"false"}""","""{"speedKph":-1}""")) {
            assertTrue(json,runCatching { WebSensorProtocol.parse(json,1000) }.isFailure)
        }
    }
    @Test fun webInputsUseRealHoldTimesAndDoNotBecomeFreshOnTheirOwn() {
        val engine = CoachEngine(); val json="""{"speedKph":75,"speedLimitKph":60}"""
        assertTrue(engine.evaluate(WebSensorProtocol.parse(json,0)).isEmpty())
        assertTrue(engine.evaluate(WebSensorProtocol.parse(json,1000)).isEmpty())
        assertEquals("B02",engine.evaluate(WebSensorProtocol.parse(json,2100)).single().scenarioId)
        assertEquals(SpeedState.WAITING_SPEED,SpeedAssessment.from(WebSensorProtocol.parse(json,0),4000).state)
    }
    @Test fun aResolvedConditionCanCancelQueuedSpeech() {
        val engine=CoachEngine()
        for(t in listOf(0L,3000L))engine.evaluate(WebSensorProtocol.parse("""{"speedKph":40,"occupiedSeats":["driver"],"beltBySeat":{"driver":false}}""",t))
        assertTrue(engine.isActive("B14"))
        engine.evaluate(WebSensorProtocol.parse("""{"speedKph":40,"occupiedSeats":["driver"],"beltBySeat":{"driver":true}}""",4000))
        assertFalse(engine.isActive("B14"))
    }
}
