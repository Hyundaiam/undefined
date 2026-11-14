package io.hyundaiam.gyeot

import android.content.Intent
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.hyundaiam.gyeot.core.ScenarioFixtures
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Opt-in on a disposable QA AVD: sensor replay -> stored verdict -> real voice progress. */
@RunWith(Parameterized::class)
class ScenarioVoiceTest(private val persona: String, private val id: String) {
    @Test fun matchingVoiceFollowsTheSensorVerdict() = runBlocking<Unit> {
        assumeTrue(InstrumentationRegistry.getArguments().getString("scenarioVoices") == "true")
        val app = ApplicationProvider.getApplicationContext<GyeotApplication>()
        app.ready.await()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("pm grant --user current ${app.packageName} android.permission.POST_NOTIFICATIONS")).bufferedReader().use { it.readText() }
        val driver = app.dao.allDrivers().first().id
        app.settings.driver(driver); app.settings.demo(true); app.settings.voice(true); app.settings.praise(true)
        app.dao.setPersona(driver, persona)
        val screen = ActivityScenario.launch(MainActivity::class.java)
        var trip: Long? = null
        try {
            screen.onActivity { ContextCompat.startForegroundService(it, Intent(it, DrivingService::class.java).setAction(DrivingService.START).putExtra("driver", driver).putExtra("demo", true)) }
            withTimeout(10_000) { while (!app.monitor.value.running) delay(100) }
            trip = app.monitor.value.tripId
            val before = app.voicePlayer.state.value.renderedCount
            assertTrue("Every situation must have its own bundled clip", app.voicePlayer.hasScenarioVoice(persona, id))
            app.startService(Intent(app, DrivingService::class.java).setAction(DrivingService.SCENARIO).putExtra("scenario", id))
            withTimeout(20_000) {
                while (app.monitor.value.lastScenarioId != id || app.voicePlayer.state.value.let { it.renderedCount <= before || it.lastRenderedScenario != id }) delay(100)
            }
            assertEquals("DEMO", app.dao.trip(requireNotNull(trip))?.source)
            val events = app.dao.recentEvents(driver, "DEMO").filter { it.tripId == trip }
            assertEquals(listOf(id), events.map { it.scenarioId })
            assertTrue(events.single().reason.startsWith("시험 센서"))
            assertEquals(app.voicePlayer.feedbackText(persona, requireNotNull(app.catalog.find(id))), app.monitor.value.feedback)
            Log.i("GyeotScenarioTest", "PASS $persona/$id sensor->${events.single().scenarioId}->voice position advanced")
        } finally {
            if (app.monitor.value.running) app.startService(Intent(app, DrivingService::class.java).setAction(DrivingService.STOP))
            if (trip != null) withTimeout(8_000) { while (app.dao.trip(trip)?.endedAt == null) delay(100) }
            screen.close()
            delay(200)
        }
    }
    companion object {
        @JvmStatic @Parameterized.Parameters(name="{0}/{1}") fun cases(): List<Array<String>> =
            ScenarioFixtures.ids.map { arrayOf("friend", it) } + listOf("grandma", "mom", "sibling").map { arrayOf(it, "B16") }
    }
}
