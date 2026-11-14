package io.hyundaiam.gyeot

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.Log
import android.view.PixelCopy
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.hyundaiam.gyeot.core.SignalSource
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Run only on an isolated Pleos AVD; drive its official console when prompted. */
@RunWith(AndroidJUnit4::class)
class SpeedFeedbackTest {
    @Test fun sixtyRoadSpeaksWhileMovingAndPraisesAfterSlowingDown() = runBlocking<Unit> {
        assumeTrue(InstrumentationRegistry.getArguments().getString("pleosSpeedFeedback") == "true")
        val app = ApplicationProvider.getApplicationContext<GyeotApplication>()
        app.ready.await()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        for (permission in listOf("android.car.permission.CAR_SPEED", "android.permission.POST_NOTIFICATIONS")) {
            ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("pm grant --user current ${app.packageName} $permission")).bufferedReader().use { it.readText() }
        }
        val driver = app.dao.allDrivers().first().id
        app.settings.driver(driver); app.dao.setPersona(driver, "friend")
        val scenario = ActivityScenario.launch<DriveActivity>(Intent(app, DriveActivity::class.java).putExtra("speedTest", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            withTimeout(45_000) { while (app.monitor.value.let { !it.running || it.source != SignalSource.EMULATOR || it.frame?.speedKph == null } || !app.ux.state.value.known) delay(200) }
            assertEquals(60.0, app.monitor.value.frame?.speedLimitKph)
            val info = app.packageManager.getActivityInfo(ComponentName(app, DriveActivity::class.java), PackageManager.GET_META_DATA)
            assertTrue(info.metaData.getBoolean("distractionOptimized"))
            Log.i("GyeotSpeedTest", "READY_FOR_ACCEL: select D and hold ACCEL past 60 on this AVD's console")
            withTimeout(120_000) {
                while (app.voicePlayer.state.value.lastRenderedScenario != "B02" || (app.monitor.value.frame?.speedKph ?: 0.0) <= 60) delay(100)
            }
            assertTrue(app.ux.state.value.restricted)
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
            scenario.onActivity { assertTrue("Driving view must remain focused under real UX restrictions", it.hasWindowFocus()) }
            assertTrue(app.dao.recentEvents(driver, "EMULATOR").any { it.scenarioId == "B02" })
            assertTrue(app.dao.recentEvents(driver, "PLEOS").isEmpty())
            delay(300) // Let Compose display the observed player state before capturing it.
            saveWindow(scenario, "speed-warning.png")
            Log.i("GyeotSpeedTest", "SPEED_WARNING_RENDERED speed=${app.monitor.value.frame?.speedKph} phase=${app.voicePlayer.state.value.phase} uxRestricted=${app.ux.state.value.restricted}")
            Log.i("GyeotSpeedTest", "READY_FOR_BRAKE: slow below 60, then return to P")
            withTimeout(90_000) { while (app.voicePlayer.state.value.lastRenderedScenario != "C06") delay(100) }
            assertTrue(app.dao.recentEvents(driver, "EMULATOR").any { it.scenarioId == "C06" })
            Log.i("GyeotSpeedTest", "RECOVERY_VOICE_RENDERED speed=${app.monitor.value.frame?.speedKph}")
            saveWindow(scenario, "speed-recovered.png")
            val renderedBeforeBackground = app.voicePlayer.state.value.renderedCount
            // Finish the UI entirely; the user-started foreground service must survive it.
            scenario.close()
            assertEquals(Lifecycle.State.DESTROYED, scenario.state)
            Log.i("GyeotSpeedTest", "READY_FOR_BACKGROUND_ACCEL: exceed 60 again with the app in the background")
            withTimeout(120_000) {
                while (app.voicePlayer.state.value.let { it.renderedCount <= renderedBeforeBackground || it.lastRenderedScenario != "B02" } || (app.monitor.value.frame?.speedKph ?: 0.0) <= 60) delay(100)
            }
            assertTrue(app.monitor.value.running)
            assertTrue(app.ux.state.value.restricted)
            assertTrue(scenario.state != Lifecycle.State.RESUMED)
            Log.i("GyeotSpeedTest", "BACKGROUND_WARNING_RENDERED speed=${app.monitor.value.frame?.speedKph} lifecycle=${scenario.state}")
        } finally {
            if (app.monitor.value.running) app.startService(Intent(app, DrivingService::class.java).setAction(DrivingService.STOP))
            scenario.close()
        }
    }
    private suspend fun saveWindow(scenario: ActivityScenario<DriveActivity>, name: String) {
        val done = CompletableDeferred<Int>()
        scenario.onActivity { activity ->
            val view = activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            PixelCopy.request(activity.window, bitmap, { result ->
                if (result == PixelCopy.SUCCESS) {
                    val file = File(activity.filesDir, name)
                    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    Log.i("GyeotSpeedTest", "SCREENSHOT=${file.absolutePath}")
                }
                bitmap.recycle(); done.complete(result)
            }, Handler(Looper.getMainLooper()))
        }
        assertEquals(PixelCopy.SUCCESS, withTimeout(5_000) { done.await() })
    }
}
