package io.hyundaiam.gyeot

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.hyundaiam.gyeot.core.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Parked-only control panel. Buttons inject sensor sequences, never a feedback verdict. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScenarioReplayScreen(app: GyeotApplication, driver: DriverEntity?, state: MonitorUi, toggle: () -> Unit, replay: (Scenario) -> Unit) {
    val scope = rememberCoroutineScope()
    var caution by rememberSaveable { mutableStateOf(true) }
    var selectedId by rememberSaveable { mutableStateOf("B02") }
    val selected = app.catalog.find(selectedId) ?: return
    val persona = driver?.persona ?: "friend"
    val playback by app.voicePlayer.state.collectAsStateWithLifecycle()
    val voiceMessage by app.voicePlayer.message.collectAsStateWithLifecycle()
    val ink = Color(0xFF252525)
    val subtle = Color(0xFF747471)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("50가지 상황, 직접 확인해요.", fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                Text("시험 센서 입력 → 조건 판정 → 상황에 맞는 음성. 실제 차량 기록과 별도로 확인하는 50상황 시뮬레이터예요.", fontSize = 13.sp, color = subtle, modifier = Modifier.padding(top = 8.dp))
            }
            OutlinedButton(onClick = toggle, modifier = Modifier.height(54.dp)) { Text(if(state.running) "시뮬레이터 종료" else "시뮬레이터 시작") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Personas.names.forEach { (id, name) -> FilterChip(selected = persona == id, onClick = { driver?.let { scope.launch { app.dao.setPersona(it.id, id) } } }, label = { Text(name) }) }
            Spacer(Modifier.weight(1f))
            FilterChip(selected = !caution, onClick = { caution = false; selectedId = "G01" }, label = { Text("좋은 상황 25") })
            FilterChip(selected = caution, onClick = { caution = true; selectedId = "B01" }, label = { Text("주의 상황 25") })
        }
        Surface(color = Color.White, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Color(0xFFE0E0DB))) {
            Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("재현할 상황  ${selected.id}", color = subtle, fontSize = 12.sp)
                    Text(selected.title, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
                    Text(selected.trigger, fontSize = 15.sp)
                    Text(if(selected.simulationSupport == "SDK_AND_FIXTURE") "차량 신호로도 감지 · 기준 등록/입력 조건 필요" else "추가 인지 정보는 이 시뮬레이터의 시험 입력으로 재현", color = subtle, fontSize = 12.sp)
                    Text("“${app.voicePlayer.feedbackText(persona, selected)}”", fontSize = 18.sp, modifier = Modifier.padding(vertical = 6.dp))
                    Button(onClick = { scope.launch { app.settings.voice(true); app.settings.praise(true); replay(selected) } }, enabled = state.running, modifier = Modifier.height(58.dp)) { Text("${selected.id} 센서 상황 재현", fontSize = 16.sp) }
                }
                Surface(Modifier.weight(1f), color = ink, shape = RoundedCornerShape(10.dp)) {
                    Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("곁의 감지 결과", fontSize = 13.sp, color = Color.LightGray)
                        Text(state.lastScenarioId?.let { "$it · ${app.catalog.find(it)?.title}" } ?: "아직 감지된 상황이 없어요", fontSize = 22.sp, color = Color.White)
                        Text(state.replayStage, color = Color.LightGray, fontSize = 14.sp)
                        if (state.replayId != null) Text(inputSummary(state.frame), fontSize = 13.sp, color = Color.LightGray)
                        Text(state.feedback, fontSize = 18.sp, color = Color.White)
                        Text(when(playback.phase) { VoicePhase.PLAYING -> "● ${playback.scenarioId} 음성 안내 중"; VoicePhase.COMPLETED -> "✓ ${playback.scenarioId} 음성 안내 완료"; VoicePhase.PREPARING -> "${playback.scenarioId} 음성 준비 중"; else -> voiceMessage.ifEmpty { "음성 대기" } }, fontSize = 14.sp, color = Color.White)
                    }
                }
            }
        }
        Text("${if(caution) "주의" else "좋은"} 상황을 고른 다음 ‘센서 상황 재현’을 누르세요. 최대 31초 분량의 센서 변화를 약 5초 안에 재현해요.", color = subtle, fontSize = 13.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            app.catalog.scenarios.filter { it.tone == if(caution) Tone.CAUTION else Tone.GOOD }.forEach { scenario ->
                FilterChip(selected = selectedId == scenario.id, onClick = { selectedId = scenario.id }, label = { Text("${scenario.id}  ${scenario.title}") })
            }
        }
        Text("이 화면의 도로·실내·ADAS 입력은 합성 시험 데이터예요. 실제 차량 기록과 분리해 저장하며 차량을 제어하지 않아요.", fontSize = 12.sp, color = subtle)
    }
}

private fun inputSummary(f: SignalFrame?): String {
    if (f == null) return "센서 입력 대기"
    return listOfNotNull(
        f.speedKph?.let { "속도 ${it.roundToInt()} km/h" }, f.speedLimitKph?.let { "제한 ${it.roundToInt()}" }, f.distanceM?.let { "앞차 ${it.roundToInt()} m" },
        f.handsOnWheel?.let { "핸들 ${it}손" }, f.bothFeetOnPedals?.let { if(it) "양발 조작" else "한 발 조작" },
        f.tunnel?.takeIf { it }?.let { "터널" }, f.windowOpen?.let { if(it) "창문 열림" else "창문 닫힘" },
        f.beltBySeat.takeIf { it.isNotEmpty() }?.let { "벨트 ${it.values.count { v -> v }}/${it.size}" },
        f.laneChange?.let { if(it) "차로 변경" else "차로 변경 대기" }, f.indicatorOn?.let { if(it) "방향지시등 켜짐" else "방향지시등 꺼짐" },
        f.wetRoad?.takeIf { it }?.let { "젖은 노면" }, f.schoolZone?.takeIf { it }?.let { "어린이보호구역" }, f.redLight?.takeIf { it }?.let { "적색 신호" },
        f.stopLineDistanceM?.let { "정지선 ${it} m" }, f.night?.takeIf { it }?.let { "야간" },
        f.blindSpotOccupied?.takeIf { it }?.let { "사각지대 차량" }, f.drowsy?.takeIf { it }?.let { "졸음 감지" },
        f.emergencyVehicle?.takeIf { it }?.let { "긴급차량 접근" }, f.pedestrianAhead?.takeIf { it }?.let { "보행자 접근" },
        f.doorsOpen?.let { if(it) "도어 열림" else "모든 도어 닫힘" }, f.tirePressureLow?.let { if(it) "공기압 낮음" else "공기압 정상" },
        f.remainingRangeKm?.let { "잔여 주행 ${it.roundToInt()} km" }, f.routeRemainingKm?.let { "경로 ${it.roundToInt()} km" },
        f.seatDeviationFraction?.let { "좌석 기준 편차 ${(it*100).roundToInt()}%" }, f.ignitionOn?.let { if(it) "시동 켜짐" else "시동 꺼짐" }, f.mergeZone?.takeIf { it }?.let { "합류 구간" }
    ).joinToString(" · ")
}
