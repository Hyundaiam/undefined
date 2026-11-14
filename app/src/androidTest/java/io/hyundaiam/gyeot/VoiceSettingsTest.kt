package io.hyundaiam.gyeot

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class VoiceSettingsTest {
    @get:Rule val ui = createAndroidComposeRule<VoiceSettingsActivity>()

    @Test fun voiceChoicePersistsAndPreviewUsesSelectedVoice() = runBlocking<Unit> {
        assumeTrue(InstrumentationRegistry.getArguments().getString("companionUi") == "true")
        val app = ui.activity.application as GyeotApplication
        app.ready.await()
        withTimeout(10000) { while (!app.ux.state.value.known || app.ux.state.value.restricted) delay(100) }
        app.settings.voice(true)
        val driver = app.dao.allDrivers().first().id
        app.settings.driver(driver)
        ui.onNodeWithText("걱정 많은 엄마").performClick()
        withTimeout(3000) { while (app.dao.driver(driver)?.persona != "mom") delay(50) }
        ui.onNodeWithText("걱정 많은 엄마").assertIsDisplayed()
        ui.onNodeWithText("미리 듣기").performClick()
        withTimeout(10000) { while (app.voicePlayer.state.value.phase != VoicePhase.PLAYING) delay(50) }
        assertEquals("mom", app.voicePlayer.state.value.persona)
        ui.onNodeWithContentDescription("음성 안내").performClick()
        withTimeout(3000) { while (app.settings.flow.first().voice) delay(50) }
        assertEquals(0f, app.voicePlayer.amplitude.value, 0f)
        ui.onNodeWithContentDescription("음성 안내").performClick()
        withTimeout(3000) { while (!app.settings.flow.first().voice) delay(50) }
        ui.onNodeWithContentDescription("음성 안내").assertIsOn()
        val done = CompletableDeferred<Int>()
        ui.runOnUiThread {
            val view = ui.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            PixelCopy.request(ui.activity.window, bitmap, { result ->
                if (result == PixelCopy.SUCCESS) File(app.filesDir, "companion-settings.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle(); done.complete(result)
            }, Handler(Looper.getMainLooper()))
        }
        assertEquals(PixelCopy.SUCCESS, withTimeout(5000) { done.await() })
        ui.activityRule.scenario.recreate()
        ui.onNodeWithText("걱정 많은 엄마").assertIsSelected()
        assertEquals("mom", app.dao.driver(driver)?.persona)
    }
}
