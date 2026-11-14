package io.hyundaiam.gyeot

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import io.hyundaiam.gyeot.core.SignalSource
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.Socket

@RunWith(AndroidJUnit4::class)
class WebSimulatorTest {
    @Test fun changingWebSensorsCausesNativeSpeechAndDisconnectsSafely() = runBlocking<Unit> {
        assumeTrue(InstrumentationRegistry.getArguments().getString("webSimulator") == "true")
        val app=ApplicationProvider.getApplicationContext<GyeotApplication>()
        app.ready.await()
        val screen=ActivityScenario.launch<DriveActivity>(Intent(app,DriveActivity::class.java).putExtra("webSimulator",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            withTimeout(10000) { while(!app.monitor.value.running||app.monitor.value.source!=SignalSource.WEB_SIM)delay(100) }
            assertEquals(403,request("POST","/api/frame","{}",origin="https://example.com").first)
            assertEquals(400,request("POST","/api/frame","""{"scenarioId":"B02"}""").first)
            assertEquals(200,request("POST","/api/settings","""{"persona":"friend"}""").first)
            val before=app.voicePlayer.state.value.renderedCount
            withTimeout(12000) {
                while(app.voicePlayer.state.value.let { it.renderedCount<=before||it.lastRenderedScenario!="B02" }) {
                    assertEquals(200,request("POST","/api/frame","""{"speedKph":75,"speedLimitKph":60}""").first); delay(200)
                }
            }
            val trip=requireNotNull(app.monitor.value.tripId)
            assertEquals("WEB_SIM",app.dao.trip(trip)?.source)
            assertEquals("B02",app.monitor.value.lastScenarioId)
            withTimeout(12000) {
                while(app.voicePlayer.state.value.lastRenderedScenario!="C06") {
                    request("POST","/api/frame","""{"speedKph":45,"speedLimitKph":60}""");delay(200)
                }
            }
            val status=JsonParser.parseString(request("GET","/api/status").second).asJsonObject
            assertTrue(status["receiving"].asBoolean); assertEquals("WEB_SIM",status["source"].asString)
            withTimeout(4000) { while(app.monitor.value.frame!=null)delay(100) }
            assertTrue(app.monitor.value.status.contains("대기"))
            assertEquals(VoicePhase.IDLE,app.voicePlayer.state.value.phase)
            assertEquals(200,request("POST","/api/reset","{}").first)
            withTimeout(3000) { while(app.monitor.value.lastScenarioId!=null)delay(100) }
        } finally {
            if(app.monitor.value.running)app.startService(Intent(app,DrivingService::class.java).setAction(DrivingService.STOP))
            screen.close()
        }
    }
    private fun request(method:String,path:String,body:String="",origin:String?=null):Pair<Int,String> {
        return Socket("127.0.0.1",8765).use { s ->
            s.soTimeout=5000
            val bytes=body.toByteArray()
            val headers="$method $path HTTP/1.1\r\nHost: 127.0.0.1:8765\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\n"+(origin?.let{"Origin: $it\r\n"}?:"")+"Connection: close\r\n\r\n"
            s.getOutputStream().apply{write(headers.toByteArray());write(bytes);flush()}
            val text=s.getInputStream().bufferedReader().readText()
            text.substringBefore("\r\n").split(' ')[1].toInt() to text.substringAfter("\r\n\r\n")
        }
    }
}
