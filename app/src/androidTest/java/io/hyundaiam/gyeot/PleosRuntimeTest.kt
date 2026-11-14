package io.hyundaiam.gyeot

import android.content.pm.PackageManager
import android.content.Intent
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.core.app.ActivityScenario
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in: official Pleos emulator only; grants CAR_SPEED to the active driver. */
@RunWith(AndroidJUnit4::class)
class PleosRuntimeTest {
    @Test fun receivesVehicleSignalsFromThePleosRuntime() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("pleosIntegration") == "true")
        val app = ApplicationProvider.getApplicationContext<GyeotApplication>()
        assertTrue(app.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE))
        grantSpeed(app)
        app.ready.await()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val signals = PleosSignals(app, scope, app.dao.allDrivers().first(), false)
        try {
            withContext(Dispatchers.Main) { signals.start() }
            withTimeout(45_000) {
                while (signals.frames.value.let { it.speedKph == null || it.gear == null || it.windowPositions.isEmpty() || it.beltBySeat.isEmpty() }) delay(250)
            }
            val frame = signals.frames.value
            assertNotNull(frame.speedKph)
            assertNotNull(frame.gear)
            assertTrue(frame.windowPositions.isNotEmpty())
            assertTrue(frame.beltBySeat.isNotEmpty())
            // The SDK must never fabricate the unavailable cabin/ADAS inputs.
            assertNull(frame.handsOnWheel)
            assertNull(frame.bothFeetOnPedals)
            assertNull(frame.distanceM)
            assertNull(frame.tunnel)
            if (InstrumentationRegistry.getArguments().getString("pleosObserveChange") == "true") {
                val initial = requireNotNull(frame.windowPositions["ROW_1_LEFT"])
                Log.i("GyeotPleosTest", "READY_FOR_WINDOW_CHANGE initial=$initial")
                withTimeout(60_000) {
                    while (signals.frames.value.windowPositions["ROW_1_LEFT"] == initial) delay(250)
                }
                Log.i("GyeotPleosTest", "WINDOW_CHANGE_RECEIVED=${signals.frames.value.windowPositions["ROW_1_LEFT"]}")
            }
        } finally {
            Log.i("GyeotPleosTest", "FRAME=" + Gson().toJson(signals.frames.value))
            Log.i("GyeotPleosTest", "CAPABILITIES=" + Gson().toJson(app.monitor.value.capabilities))
            withContext(Dispatchers.Main) { scope.cancel(); signals.close() }
        }
    }

    @Test fun liveVehicleSignalsContinueAndAreStoredWithTheActivityInBackground() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("pleosIntegration") == "true")
        val app = ApplicationProvider.getApplicationContext<GyeotApplication>()
        grantSpeed(app)
        app.ready.await()
        val prefs = app.settings.flow.first()
        app.settings.voice(false); app.settings.demo(false)
        val driver = app.dao.allDrivers().first().id
        val activity = ActivityScenario.launch(MainActivity::class.java)
        try {
            activity.onActivity { ContextCompat.startForegroundService(it, Intent(it, DrivingService::class.java).setAction(DrivingService.START).putExtra("driver", driver).putExtra("demo", false)) }
            withTimeout(45_000) {
                while (app.monitor.value.let { !it.running || it.frame?.speedKph == null || it.frame?.windowPositions.isNullOrEmpty() }) delay(250)
            }
            val trip = requireNotNull(app.monitor.value.tripId)
            val before = requireNotNull(app.monitor.value.frame?.speedTimeMs)
            activity.onActivity { it.moveTaskToBack(true) }
            withTimeout(10_000) { while ((app.monitor.value.frame?.speedTimeMs ?: 0) <= before + 2_000) delay(250) }
            assertTrue(app.monitor.value.running)
            assertEquals("PLEOS", app.dao.trip(trip)?.source)
            val saved = withContext(Dispatchers.IO) {
                app.database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM observations WHERE tripId = ? AND source = 'PLEOS'", arrayOf(trip)).use { it.moveToFirst(); it.getInt(0) }
            }
            assertTrue("Vehicle frames must be persisted while the screen is in the background.", saved > 0)
            app.startService(Intent(app, DrivingService::class.java).setAction(DrivingService.STOP))
            withTimeout(8_000) { while (app.dao.trip(trip)?.endedAt == null) delay(100) }
        } finally {
            if (app.monitor.value.running) app.startService(Intent(app, DrivingService::class.java).setAction(DrivingService.STOP))
            activity.close()
            app.settings.voice(prefs.voice); app.settings.demo(prefs.demo)
        }
    }

    private suspend fun grantSpeed(app: GyeotApplication) {
        // Gradle installs a fresh app for instrumentation, so a grant made before
        // connectedDebugAndroidTest does not survive that installation.
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        // AAOS uses a headless system user; grant to the active driver, not user 0.
        ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("pm grant --user current ${app.packageName} android.car.permission.CAR_SPEED")).bufferedReader().use { it.readText() }
        withTimeout(5_000) { while (app.checkSelfPermission("android.car.permission.CAR_SPEED") != PackageManager.PERMISSION_GRANTED) delay(100) }
    }
}
