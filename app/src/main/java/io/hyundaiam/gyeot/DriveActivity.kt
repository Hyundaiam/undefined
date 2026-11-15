package io.hyundaiam.gyeot

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import io.hyundaiam.gyeot.core.SignalSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** The companion stays on screen while all driving interaction happens in the simulator. */
class DriveActivity : ComponentActivity() {
    private val app get() = application as GyeotApplication
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        lifecycleScope.launch {
            val prefs = app.settings.flow.first()
            if (prefs.source in setOf(SignalSource.PLEOS, SignalSource.EMULATOR) && !speedPermission()) {
                app.monitor.value = MonitorUi(status = "속도 권한을 허용하면 차량을 살펴볼 수 있어요")
            } else startCurrentTrip(requestPermissions = false)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK
        // Close only the screen. Pleos may keep a moved-back split-screen task visible.
        // Sensor collection and speech belong to DrivingService, not this Activity.
        onBackPressedDispatcher.addCallback(this) { finish() }
        prepareCompanion(intent)
        setContent { CompanionView(this, app) }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); prepareCompanion(intent) }

    private fun prepareCompanion(intent: Intent) {
        lifecycleScope.launch {
            app.ready.await()
            if (app.monitor.value.running) return@launch
            if (TestEnvironment.available) {
                when {
                    intent.getBooleanExtra("webSimulator", false) -> { app.settings.web(true); app.settings.praise(true); app.settings.voice(true) }
                    intent.getBooleanExtra("speedTest", false) -> { app.settings.testRoad(60); app.settings.voice(true) }
                }
            }
            startCurrentTrip()
        }
    }

    private fun speedPermission() = ContextCompat.checkSelfPermission(this, "android.car.permission.CAR_SPEED") == PackageManager.PERMISSION_GRANTED

    fun startCurrentTrip(requestPermissions: Boolean = true) {
        lifecycleScope.launch {
            app.ready.await()
            if (app.monitor.value.running) return@launch
            val prefs = app.settings.flow.first()
            val required = buildList {
                if (prefs.source in setOf(SignalSource.PLEOS, SignalSource.EMULATOR) && !speedPermission()) add("android.car.permission.CAR_SPEED")
                if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this@DriveActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (requestPermissions && required.isNotEmpty()) { permissions.launch(required.toTypedArray()); return@launch }
            ContextCompat.startForegroundService(this@DriveActivity, Intent(this@DriveActivity, DrivingService::class.java).setAction(DrivingService.START)
                .putExtra("driver", prefs.driverId).putExtra("demo", prefs.demo).putExtra("testLimit", prefs.testRoadLimit).putExtra("webSimulator", prefs.web))
        }
    }
}

@Composable
private fun CompanionView(activity: DriveActivity, app: GyeotApplication) {
    val voice by app.voicePlayer.state.collectAsStateWithLifecycle()
    val amplitude by app.voicePlayer.amplitude.collectAsStateWithLifecycle()
    val prefs by app.settings.flow.collectAsStateWithLifecycle(Preferences())
    val ux by app.ux.state.collectAsStateWithLifecycle()
    val speaking = prefs.voice && voice.phase == VoicePhase.PLAYING
    CompanionTheme {
        Surface(Modifier.fillMaxSize(), color = CompanionBlack) {
            Box(Modifier.fillMaxSize()) {
                ParticleOrb(if (speaking) amplitude else 0f, speaking, Modifier.fillMaxSize())
                OutlinedButton(
                    onClick = { activity.startActivity(Intent(activity, VoiceSettingsActivity::class.java)) },
                    enabled = ux.known && !ux.restricted,
                    shape = RoundedCornerShape(32.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = .17f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD4D4D4), disabledContentColor = Color(0xFF686868)),
                    modifier = Modifier.align(Alignment.TopEnd).padding(28.dp).height(64.dp).semantics {
                        stateDescription = if (ux.known && !ux.restricted) "음성 설정 열기" else "정차 중 설정 가능"
                    },
                    contentPadding = PaddingValues(horizontal = 24.dp)
                ) {
                    Icon(Icons.Outlined.Settings, contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp)); Text("설정", fontSize = 18.sp)
                }
            }
        }
    }
}

internal val CompanionBlack = Color(0xFF090909)

@Composable
internal fun CompanionTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(primary = Color.White, onPrimary = Color.Black, background = CompanionBlack,
        surface = Color(0xFF121212), onSurface = Color(0xFFF1F1F1), onSurfaceVariant = Color(0xFF999999),
        outline = Color(0xFF3C3C3C), secondaryContainer = Color(0xFF292929), onSecondaryContainer = Color.White), content = content)
}
