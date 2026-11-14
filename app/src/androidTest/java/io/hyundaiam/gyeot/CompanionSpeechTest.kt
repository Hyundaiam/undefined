package io.hyundaiam.gyeot

import android.content.Intent
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.Socket

/** Isolated QA AVD only: real sensor verdict -> real decoded PCM -> visible companion. */
@RunWith(AndroidJUnit4::class)
class CompanionSpeechTest {
    @Test fun realSpeechDrivesAmplitudeAndStopsWithThePlayer() = runBlocking<Unit> {
        assumeTrue(InstrumentationRegistry.getArguments().getString("companionUi") == "true")
        val app = ApplicationProvider.getApplicationContext<GyeotApplication>()
        app.ready.await()
        app.settings.web(true); app.settings.voice(true)
        val screen = ActivityScenario.launch<DriveActivity>(Intent(app, DriveActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            withTimeout(10000) { while (!app.monitor.value.running) delay(100) }
            assertEquals(0f, app.voicePlayer.amplitude.value, 0f)
            capture(screen, "companion-idle.png")
            val levels = mutableListOf<Float>()
            val feeder = launch(Dispatchers.IO) {
                while (isActive) {
                    Socket("127.0.0.1", 8765).use { socket ->
                        socket.soTimeout = 3000
                        val body = """{"speedKph":78,"speedLimitKph":60}"""
                        socket.getOutputStream().write(("POST /api/frame HTTP/1.1\r\nHost: 127.0.0.1:8765\r\nContent-Type: application/json\r\nContent-Length: ${body.length}\r\nConnection: close\r\n\r\n" + body).toByteArray())
                        socket.getInputStream().bufferedReader().readText()
                    }
                    delay(200)
                }
            }
            try {
                withTimeout(15000) {
                    while (app.voicePlayer.state.value.lastRenderedScenario != "B02") delay(50)
                    repeat(90) { levels += app.voicePlayer.amplitude.value; delay(32) }
                }
                assertTrue("Actual speech PCM must reach the companion: $levels", levels.maxOrNull()!! > .1f)
                assertTrue("Speech level must follow the utterance, not a constant playing flag", levels.map { (it * 100).toInt() }.distinct().size > 5)
                capture(screen, "companion-speaking.png")
                withContext(Dispatchers.Main) { app.voicePlayer.stop() }
                assertEquals(0f, app.voicePlayer.amplitude.value, 0f)
            } finally { feeder.cancelAndJoin() }
        } finally {
            app.startService(Intent(app, DrivingService::class.java).setAction(DrivingService.STOP))
            screen.close()
        }
    }

    private suspend fun capture(screen: ActivityScenario<DriveActivity>, name: String) {
        val done = CompletableDeferred<Int>()
        screen.onActivity { activity ->
            val view = activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            PixelCopy.request(activity.window, bitmap, { result ->
                if (result == PixelCopy.SUCCESS) File(activity.filesDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle(); done.complete(result)
            }, Handler(Looper.getMainLooper()))
        }
        assertEquals(PixelCopy.SUCCESS, withTimeout(5000) { done.await() })
    }
}
