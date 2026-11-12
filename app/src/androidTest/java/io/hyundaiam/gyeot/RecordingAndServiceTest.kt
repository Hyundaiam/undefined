package io.hyundaiam.gyeot

import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(AndroidJUnit4::class)
class RecordingAndServiceTest {
    private val app get() = ApplicationProvider.getApplicationContext<GyeotApplication>()

    @Test fun invalidReplacementPreservesThePreviouslyRegisteredRecording() = runBlocking {
        app.ready.await()
        val previous = app.dao.clip("friend", "C08")
        val good = File(app.cacheDir, "recording-test.wav")
        val bad = File(app.cacheDir, "invalid-recording-test.wav")
        var registered: ClipEntity? = null
        try {
            good.writeBytes(wav(800))
            app.voicePlayer.importClip(Uri.fromFile(good), "friend", "C08")
            registered = requireNotNull(app.dao.clip("friend", "C08"))
            assertTrue(registered.durationMs in 750..850)
            bad.writeText("This is not audio.")
            assertTrue(runCatching { app.voicePlayer.importClip(Uri.fromFile(bad), "friend", "C08") }.isFailure)
            assertEquals(registered, app.dao.clip("friend", "C08"))
            assertTrue(File(app.filesDir, "recordings/${registered.fileName}").isFile)
        } finally {
            if (previous != null) app.dao.putClip(previous) else app.dao.removeClip("friend", "C08")
            registered?.let { File(app.filesDir, "recordings/${it.fileName}").delete() }
            good.delete(); bad.delete()
        }
    }

    @Test fun userStartedMonitoringContinuesAfterActivityGoesToBackground() = runBlocking {
        app.ready.await()
        val prefs = app.settings.flow.first()
        app.settings.voice(false)
        val driver = app.dao.allDrivers().first().id
        val activity = ActivityScenario.launch(MainActivity::class.java)
        try {
            activity.onActivity { ContextCompat.startForegroundService(it, Intent(it, DrivingService::class.java).setAction(DrivingService.START).putExtra("driver", driver).putExtra("demo", true)) }
            withTimeout(8_000) { while (!app.monitor.value.running) delay(100) }
            val tripId = requireNotNull(app.monitor.value.tripId)
            val before = requireNotNull(app.dao.trip(tripId)).lastSeenAt
            activity.onActivity { it.moveTaskToBack(true) }
            withTimeout(8_000) { while ((app.dao.trip(tripId)?.lastSeenAt ?: 0) <= before) delay(200) }
            assertTrue(app.monitor.value.running)
            app.startService(Intent(app, DrivingService::class.java).setAction(DrivingService.STOP))
            withTimeout(8_000) { while (app.dao.trip(tripId)?.endedAt == null) delay(100) }
            assertFalse(app.monitor.value.running)
        } finally {
            if (app.monitor.value.running) app.startService(Intent(app, DrivingService::class.java).setAction(DrivingService.STOP))
            activity.close()
            app.settings.voice(prefs.voice)
        }
    }

    private fun wav(durationMs: Int): ByteArray {
        val length = 16_000 * durationMs / 1000 * 2
        return ByteBuffer.allocate(44 + length).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + length); put("WAVEfmt ".toByteArray())
            putInt(16); putShort(1); putShort(1); putInt(16_000); putInt(32_000); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(length); put(ByteArray(length))
        }.array()
    }
}
