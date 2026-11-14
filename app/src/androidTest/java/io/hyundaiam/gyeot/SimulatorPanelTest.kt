package io.hyundaiam.gyeot

import android.content.Intent
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SimulatorPanelTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    @Test fun selectingASituationDoesNotSpeakUntilItsSensorsAreReplayed() = runBlocking<Unit> {
        assumeTrue(InstrumentationRegistry.getArguments().getString("scenarioVoices") == "true")
        val app = ui.activity.application as GyeotApplication
        app.ready.await()
        app.settings.demo(true); app.settings.voice(true); app.settings.praise(true)
        val driver = app.dao.allDrivers().first().id
        app.settings.driver(driver); app.dao.setPersona(driver,"friend")
        ui.waitUntil(10_000) { ui.onAllNodesWithText("50가지 상황, 직접 확인해요.").fetchSemanticsNodes().isNotEmpty() }
        ui.runOnUiThread { ContextCompat.startForegroundService(ui.activity, Intent(app, DrivingService::class.java).setAction(DrivingService.START).putExtra("driver", driver).putExtra("demo", true)) }
        try {
            withTimeout(10_000) { while (!app.monitor.value.running) delay(100) }
            val count = app.voicePlayer.state.value.renderedCount
            ui.onNodeWithText("주의 상황 25").performScrollTo().performClick()
            ui.onNodeWithText("B17  정지선 넘어 진행").performScrollTo().performClick()
            assertEquals(count, app.voicePlayer.state.value.renderedCount)
            ui.onNodeWithText("B17 센서 상황 재현").performScrollTo().performClick()
            withTimeout(15_000) { while (app.monitor.value.lastScenarioId != "B17" || app.voicePlayer.state.value.let { it.renderedCount <= count || it.lastRenderedScenario != "B17" }) delay(100) }
            ui.onNodeWithText("50가지 상황, 직접 확인해요.").performScrollTo()
            delay(300)
            val done = CompletableDeferred<Int>()
            ui.runOnUiThread {
                val bitmap = Bitmap.createBitmap(ui.activity.window.decorView.width, ui.activity.window.decorView.height, Bitmap.Config.ARGB_8888)
                PixelCopy.request(ui.activity.window, bitmap, { result ->
                    if(result == PixelCopy.SUCCESS) File(app.filesDir,"scenario-panel.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
                    bitmap.recycle(); done.complete(result)
                }, Handler(Looper.getMainLooper()))
            }
            assertEquals(PixelCopy.SUCCESS,withTimeout(5_000) { done.await() })
        } finally {
            if(app.monitor.value.running) app.startService(Intent(app,DrivingService::class.java).setAction(DrivingService.STOP))
            withTimeout(5_000) { while(app.monitor.value.running) delay(100) }
        }
    }
}
