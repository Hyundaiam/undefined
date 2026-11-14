package io.hyundaiam.gyeot

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.addCallback
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import io.hyundaiam.gyeot.core.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Glanceable Pleos driving view. Profile, files and configuration stay in MainActivity. */
class DriveActivity : ComponentActivity() {
    private val app get() = application as GyeotApplication
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this) {
            if (!app.ux.state.value.known || app.ux.state.value.restricted) moveTaskToBack(true) else finish()
        }
        handleTestIntent(intent)
        setContent { DrivingView(this, app) }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handleTestIntent(intent) }
    private fun handleTestIntent(intent: Intent) {
        if (!intent.getBooleanExtra("speedTest", false) || !TestEnvironment.available) return
        lifecycleScope.launch {
            app.ready.await()
            if (app.monitor.value.running) return@launch
            app.settings.testRoad(60); app.settings.voice(true)
            startCurrentTrip()
        }
    }
    fun startCurrentTrip() {
        lifecycleScope.launch {
            app.ready.await()
            val prefs = app.settings.flow.first()
            if (prefs.source != SignalSource.DEMO && ContextCompat.checkSelfPermission(this@DriveActivity, "android.car.permission.CAR_SPEED") != PackageManager.PERMISSION_GRANTED) {
                app.monitor.value = MonitorUi(status = "정차 후 설정에서 속도 권한을 허용해 주세요")
                return@launch
            }
            ContextCompat.startForegroundService(this@DriveActivity, Intent(this@DriveActivity, DrivingService::class.java).setAction(DrivingService.START).putExtra("driver", prefs.driverId).putExtra("demo", prefs.demo).putExtra("testLimit", prefs.testRoadLimit))
        }
    }
}

@Composable
private fun DrivingView(activity: DriveActivity, app: GyeotApplication) {
    val monitor by app.monitor.collectAsStateWithLifecycle()
    val prefs by app.settings.flow.collectAsStateWithLifecycle(Preferences())
    val ux by app.ux.state.collectAsStateWithLifecycle()
    val voice by app.voicePlayer.state.collectAsStateWithLifecycle()
    val voiceMessage by app.voicePlayer.message.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val speed = SpeedAssessment.from(monitor.frame.takeIf { monitor.running }, System.currentTimeMillis())
    val over = speed.state == SpeedState.OVER_LIMIT
    fun concise(text: String) = text.take(if (ux.restricted) ux.maxText.coerceAtMost(60) else 100)
    val paper = Color(0xFFF6F6F3)
    val ink = Color(0xFF202020)
    MaterialTheme(colorScheme = lightColorScheme(primary = ink, onPrimary = Color.White, background = paper)) {
        Surface(Modifier.fillMaxSize(), color = paper) {
            Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("곁:", fontSize = 40.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(24.dp))
                    Text(concise(if (monitor.source == SignalSource.EMULATOR || prefs.source == SignalSource.EMULATOR) "에뮬레이터 시험도로 · 제한 ${prefs.testRoadLimit}" else if (prefs.demo) "상황 시연" else "내비게이션 도로 정보"), fontSize = 20.sp)
                    Spacer(Modifier.weight(1f))
                    Text(if (monitor.running) "● 코칭 중" else "○ 주행 대기", fontSize = 20.sp)
                }
                Spacer(Modifier.weight(.25f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("이 도로 제한", fontSize = 22.sp)
                        Surface(Modifier.padding(top = 16.dp).size(150.dp), shape = RoundedCornerShape(75.dp), border = BorderStroke(5.dp, ink), color = Color.White) {
                            Box(contentAlignment = Alignment.Center) { Text(speed.limit?.roundToInt()?.toString() ?: "—", fontSize = 68.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("현재 속도", fontSize = 22.sp)
                        Text(speed.speed?.roundToInt()?.toString() ?: "—", fontSize = 130.sp, fontWeight = FontWeight.Light)
                        Text("km/h", fontSize = 22.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(when(speed.state) { SpeedState.OVER_LIMIT -> "속도를 낮춰주세요"; SpeedState.WITHIN_LIMIT -> "제한 속도 이내"; SpeedState.WAITING_LIMIT -> "도로 정보 대기"; SpeedState.WAITING_SPEED -> "차량 속도 대기" }, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                        if (over) Text("+${speed.excess?.roundToInt()} km/h", fontSize = 50.sp, modifier = Modifier.padding(top = 18.dp))
                        else Text(concise(if (speed.state == SpeedState.WAITING_LIMIT) "제한 속도를 받아야 과속 안내가 시작돼요" else monitor.frame?.gear?.removePrefix("GEAR_") ?: monitor.status), fontSize = 20.sp, modifier = Modifier.padding(top = 18.dp))
                    }
                }
                Spacer(Modifier.weight(.25f))
                Surface(color = if (over) ink else Color.White, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(24.dp)) {
                        Text(concise(if (over) "제한 ${speed.limit?.roundToInt()} · 현재 ${speed.speed?.roundToInt()}. 속도를 낮춰주세요." else monitor.feedback.ifEmpty { monitor.status }), fontSize = 27.sp, color = if (over) Color.White else ink, maxLines = 2)
                        Text(concise(when(voice.phase) { VoicePhase.PLAYING -> "● 음성 안내 중"; VoicePhase.PREPARING -> "음성 준비 중"; VoicePhase.COMPLETED -> "음성 안내 완료"; VoicePhase.MUTED -> "음성 꺼짐"; else -> voiceMessage.ifEmpty { if (prefs.voice) "음성 켜짐 · 상황을 확인하고 있어요" else "음성 꺼짐" } }), fontSize = 19.sp, color = if (over) Color.LightGray else Color.DarkGray, modifier = Modifier.padding(top = 10.dp), maxLines = 2)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { scope.launch { app.settings.voice(!prefs.voice); if (prefs.voice) app.voicePlayer.stop() } }, modifier = Modifier.height(64.dp)) { Text(if (prefs.voice) "음성 끄기" else "음성 켜기", fontSize = 20.sp) }
                    if (monitor.running) OutlinedButton(onClick = { activity.startService(Intent(activity, DrivingService::class.java).setAction(DrivingService.STOP)) }, modifier = Modifier.height(64.dp)) { Text("주행 종료", fontSize = 20.sp) }
                    else if (prefs.source != SignalSource.DEMO) Button(onClick = activity::startCurrentTrip, modifier = Modifier.height(64.dp)) { Text("함께 출발", fontSize = 20.sp) }
                    Spacer(Modifier.weight(1f))
                    if (ux.known && !ux.restricted) OutlinedButton(onClick = { activity.startActivity(Intent(activity, MainActivity::class.java)) }, modifier = Modifier.height(64.dp)) { Text("정차 중 설정", fontSize = 20.sp) }
                    else Text("설정은 정차 후 사용", fontSize = 18.sp)
                }
            }
        }
    }
}
