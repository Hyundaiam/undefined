package io.hyundaiam.gyeot

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.gson.Gson
import io.hyundaiam.gyeot.core.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private val Ink = Color(0xFF252525)
private val Paper = Color(0xFFF6F6F3)
private val Subtle = Color(0xFF858580)
private val Line = Color(0xFFE0E0DB)
private val Shape = RoundedCornerShape(16.dp)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Ink, onPrimary = Color.White, background = Paper, surface = Color.White, onSurface = Ink, secondaryContainer = Color(0xFFECECE7))) {
                App(this, application as GyeotApplication)
            }
        }
    }
}

@Composable
private fun App(activity: MainActivity, app: GyeotApplication) {
    val scope = rememberCoroutineScope()
    val prefs by app.settings.flow.collectAsStateWithLifecycle(Preferences())
    val drivers by remember { app.dao.drivers() }.collectAsStateWithLifecycle(emptyList())
    val monitor by app.monitor.collectAsStateWithLifecycle()
    val driver = drivers.firstOrNull { it.id == prefs.driverId } ?: drivers.firstOrNull()
    val source = if (prefs.demo) "DEMO" else "PLEOS"
    val events by remember(driver?.id, source) { app.dao.events(driver?.id ?: 1, source) }.collectAsStateWithLifecycle(emptyList())
    val habits by remember(driver?.id, source) { app.dao.habits(driver?.id ?: 1, source) }.collectAsStateWithLifecycle(emptyList())
    val trips by remember(driver?.id, source) { app.dao.tripStats(driver?.id ?: 1, source) }.collectAsStateWithLifecycle(emptyList())
    val clips by remember { app.dao.clips() }.collectAsStateWithLifecycle(emptyList())
    val audioMessage by app.voicePlayer.message.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var driverMenu by remember { mutableStateOf(false) }
    var addDriver by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var importTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    val message: (String) -> Unit = { Toast.makeText(activity, it, Toast.LENGTH_SHORT).show() }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = importTarget
        if (uri != null && target != null) scope.launch { runCatching { app.voicePlayer.importClip(uri, target.first, target.second) }.onSuccess(message).onFailure { message(it.message ?: "파일을 등록하지 못했어요.") } }
    }
    fun start() {
        val id = driver?.id ?: return
        ContextCompat.startForegroundService(activity, Intent(activity, DrivingService::class.java).setAction(DrivingService.START).putExtra("driver", id).putExtra("demo", prefs.demo))
    }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { start() }
    fun toggle() {
        if (monitor.running) activity.startService(Intent(activity, DrivingService::class.java).setAction(DrivingService.STOP))
        else {
            val requested = buildList {
                if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                if (!prefs.demo) add("android.car.permission.CAR_SPEED")
            }.filter { permission ->
                ContextCompat.checkSelfPermission(activity, permission) != android.content.pm.PackageManager.PERMISSION_GRANTED &&
                    runCatching { activity.packageManager.getPermissionInfo(permission, 0) }.isSuccess
            }
            if (requested.isEmpty()) start() else permissions.launch(requested.toTypedArray())
        }
    }
    Surface(color = Paper, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 28.dp)) {
            Row(Modifier.fillMaxWidth().height(76.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("곁:", fontWeight = FontWeight.Bold, fontSize = 36.sp, letterSpacing = (-3).sp)
                Spacer(Modifier.width(12.dp)); Text("DRIVE\nCOMPANION", color = Subtle, fontSize = 8.sp, lineHeight = 11.sp)
                Spacer(Modifier.weight(1f))
                listOf("함께 달리기", "나의 운전", "목소리 · 녹음", "연결 · 설정").forEachIndexed { index, label ->
                    TextButton(onClick = { tab = index }, modifier = Modifier.padding(horizontal = 4.dp)) { Text(label, color = if (tab == index) Ink else Subtle, fontWeight = if (tab == index) FontWeight.SemiBold else FontWeight.Normal, fontSize = 15.sp) }
                }
                Spacer(Modifier.weight(1f))
                Box {
                    TextButton(onClick = { driverMenu = true }, enabled = !monitor.running) { Icon(Icons.Outlined.PersonOutline, null, modifier = Modifier.size(19.dp)); Spacer(Modifier.width(6.dp)); Text(driver?.name ?: "준비 중") }
                    DropdownMenu(expanded = driverMenu, onDismissRequest = { driverMenu = false }) {
                        drivers.forEach { d -> DropdownMenuItem(text = { Text(d.name) }, onClick = { scope.launch { app.settings.driver(d.id) }; driverMenu = false }) }
                        HorizontalDivider(); DropdownMenuItem(text = { Text("운전자 추가") }, onClick = { addDriver = true; driverMenu = false })
                    }
                }
            }
            HorizontalDivider(color = Line)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    0 -> DriveScreen(app, driver, prefs, monitor, clips, ::toggle) { scenario -> activity.startService(Intent(activity, DrivingService::class.java).setAction(DrivingService.SCENARIO).putExtra("scenario", scenario.id)) }
                    1 -> ProgressScreen(habits, trips, events, app, prefs.demo)
                    2 -> VoiceScreen(app, driver, clips, audioMessage, !monitor.running || monitor.demo,
                        onPersona = { id -> driver?.let { scope.launch { app.dao.setPersona(it.id, id) } } },
                        onPlay = { scenario -> scope.launch { app.voicePlayer.play(driver?.persona ?: "friend", scenario) } },
                        onImport = { scenario -> importTarget = (driver?.persona ?: "friend") to scenario.id; filePicker.launch(arrayOf("audio/*")) })
                    3 -> SettingsScreen(app, prefs, monitor, driver, message)
                }
            }
            HorizontalDivider(color = Line)
            Row(Modifier.fillMaxWidth().height(46.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (monitor.running) "● 함께 달리는 중" else "○ 주행 대기", fontSize = 11.sp, color = Subtle)
                Spacer(Modifier.width(14.dp)); Text(if (prefs.demo) "시연 데이터 · 실제 학습과 구분" else "차량 신호 · 운전자별 로컬 저장", fontSize = 11.sp, color = Subtle)
                Spacer(Modifier.weight(1f))
                if (monitor.running) TextButton(onClick = { activity.moveTaskToBack(true) }) { Text("내비게이션으로 돌아가기", fontSize = 12.sp); Icon(Icons.Outlined.ArrowForward, null, Modifier.size(16.dp)) }
                else Text("PLEOS CONNECT / ANDROID APP", fontSize = 9.sp, letterSpacing = 1.sp, color = Subtle)
            }
        }
    }
    if (addDriver) AlertDialog(onDismissRequest = { addDriver = false }, title = { Text("새 운전자를 기억할게요") }, text = { OutlinedTextField(name, { name = it.take(24) }, label = { Text("운전자 이름") }, singleLine = true) }, confirmButton = { TextButton(onClick = { scope.launch { val id = app.dao.addDriver(DriverEntity(name = name.trim())); app.settings.driver(id) }; name = ""; addDriver = false }, enabled = name.isNotBlank()) { Text("추가") } }, dismissButton = { TextButton(onClick = { addDriver = false }) { Text("취소") } })
}

@Composable
private fun Heading(eyebrow: String, title: String, subtitle: String, action: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(vertical = 23.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(eyebrow, fontSize = 9.sp, color = Subtle, letterSpacing = 2.sp)
            Text(title, fontSize = 27.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 8.dp))
            Text(subtitle, fontSize = 12.sp, color = Subtle, modifier = Modifier.padding(top = 6.dp))
        }
        action()
    }
}
@Composable
private fun DriveScreen(app: GyeotApplication, driver: DriverEntity?, prefs: Preferences, state: MonitorUi, clips: List<ClipEntity>, toggle: () -> Unit, simulate: (Scenario) -> Unit) {
    val scope = rememberCoroutineScope()
    var cautions by rememberSaveable { mutableStateOf(false) }
    val last = app.catalog.find(state.lastScenarioId ?: "C01")
    val persona = driver?.persona ?: "friend"
    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Heading("ALWAYS BY YOUR SIDE", "오늘도, 함께 달려요.", "${driver?.name ?: "운전자"}의 작은 변화도 곁이 기억할게요.") {
            AssistChip(onClick = {}, label = { Text(if (prefs.demo) "시연 모드" else "Pleos 차량 신호") })
            Spacer(Modifier.width(14.dp))
            Button(onClick = toggle, shape = RoundedCornerShape(9.dp), enabled = driver != null) { Icon(if (state.running) Icons.Outlined.Stop else Icons.Outlined.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text(if (state.running) "주행 종료" else "함께 출발") }
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Surface(Modifier.weight(1.35f).height(300.dp), color = Ink, shape = Shape) {
                Box(Modifier.fillMaxSize().padding(25.dp)) {
                    Column {
                        Row(verticalAlignment = Alignment.Bottom) { Text(state.frame?.speedKph?.let { "%.0f".format(it) } ?: "—", fontSize = 83.sp, fontWeight = FontWeight.Light, color = Color.White); Text(" km/h", color = Color(0xFF9B9B97), fontSize = 15.sp, modifier = Modifier.padding(bottom = 17.dp)); Spacer(Modifier.weight(1f)); Text(state.frame?.speedLimitKph?.let { "제한 ${it.toInt()}" } ?: "제한 속도 미수신", color = Color(0xFFBBBBB5), fontSize = 13.sp) }
                        Text(if (state.running) "${if (prefs.demo) "시연" else "주행"} · ${state.frame?.gear?.removePrefix("GEAR_") ?: "기어 미수신"}" else "주행 시작 후 차량 정보를 표시해요", fontSize = 12.sp, color = Color(0xFFB6B6AF))
                    }
                    RoadIllustration(Modifier.fillMaxWidth().height(140.dp).align(Alignment.BottomCenter))
                    Row(Modifier.fillMaxWidth().align(Alignment.BottomCenter), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(state.frame?.distanceM?.let { "앞차 ${it.toInt()} m${if (prefs.demo) " · 시연" else ""}" } ?: "앞차 거리 미제공", color = Color(0xFFBFBFB9), fontSize = 10.sp)
                        Text("관측 가능한 정보만 기록", color = Color(0xFF9A9A93), fontSize = 10.sp)
                    }
                }
            }
            Surface(Modifier.weight(1f).height(300.dp), color = Color(0xFFEDEDE7), shape = Shape) {
                Column(Modifier.padding(25.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Avatar(persona); Spacer(Modifier.width(15.dp)); Column { Text("오늘의 드라이빙 메이트", color = Subtle, fontSize = 11.sp); Text(Personas.names[persona] ?: "친구", fontSize = 23.sp, modifier = Modifier.padding(top = 5.dp)) } }
                    val ownVoice = last?.let { s -> clips.any { it.persona == persona && it.scenarioId == s.id } || app.voicePlayer.hasPackaged(persona, s.id) } == true
                    Text(if (ownVoice) "내 목소리로 전하는 ${last?.title}" else last?.let { app.voicePlayer.legacyText(persona, it) ?: it.recordingText } ?: "오늘도 같이 가자.", fontSize = 20.sp, lineHeight = 32.sp, fontWeight = FontWeight.Medium)
                    Row(verticalAlignment = Alignment.CenterVertically) { Text(last?.id ?: "C01", fontSize = 10.sp, color = Subtle); Spacer(Modifier.weight(1f)); TextButton(onClick = { last?.let { scope.launch { app.voicePlayer.play(persona, it) } } }) { Icon(Icons.Outlined.VolumeUp, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("다시 듣기") } }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(if (prefs.demo) "이런 순간, 곁은 어떻게 말할까요?" else "현재 연결 상태", fontSize = 17.sp, fontWeight = FontWeight.Medium); Text(state.status, color = Subtle, fontSize = 11.sp, modifier = Modifier.padding(top = 5.dp)) }
            if (prefs.demo) { FilterChip(selected = !cautions, onClick = { cautions = false }, label = { Text("좋은 순간 15") }); Spacer(Modifier.width(8.dp)); FilterChip(selected = cautions, onClick = { cautions = true }, label = { Text("주의 순간 15") }) }
        }
        if (prefs.demo) LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            items(app.catalog.scenarios.filter { it.tone == if (cautions) Tone.CAUTION else Tone.GOOD }) { s ->
                OutlinedButton(onClick = { simulate(s) }, enabled = state.running, shape = RoundedCornerShape(8.dp)) { Text("${s.id}  ${s.title}", fontSize = 12.sp) }
            }
        } else CapabilityList(state.capabilities)
    }
}

@Composable
private fun RoadIllustration(modifier: Modifier) {
    Canvas(modifier) {
        val mid = size.width * .5f
        for (x in listOf(-.65f, -.22f, .22f, .65f)) drawLine(Color(0xFF5C5C58), Offset(mid + x * size.width * .12f, 0f), Offset(mid + x * size.width * .6f, size.height), 1.5f)
        for (i in 1..4) { val y = size.height * i / 5; drawLine(Color(0xFF353532), Offset(mid - y, y), Offset(mid + y, y), 1f) }
        drawRoundRect(Color(0xFFCFCFCA), Offset(mid - 27.dp.toPx(), size.height * .35f), Size(54.dp.toPx(), 82.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(10.dp.toPx()))
        drawRoundRect(Color(0xFF555550), Offset(mid - 20.dp.toPx(), size.height * .35f + 13.dp.toPx()), Size(40.dp.toPx(), 20.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
        drawLine(Color.White, Offset(mid - 22.dp.toPx(), size.height * .35f + 70.dp.toPx()), Offset(mid + 22.dp.toPx(), size.height * .35f + 70.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
    }
}

@Composable
private fun ProgressScreen(habits: List<HabitEntity>, trips: List<TripStats>, events: List<EventEntity>, app: GyeotApplication, demo: Boolean) {
    val good = habits.sumOf { it.good }; val caution = habits.sumOf { it.caution }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 25.dp)) {
        Heading("BETTER WITH EVERY DRIVE", "운전도, 함께 자라는 거니까.", "${if (demo) "시연" else "차량"} 기록 · 운전자별로 저장해 다음 주행과 비교해요.")
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Surface(Modifier.weight(.9f).height(230.dp), color = Ink, shape = Shape) { Column(Modifier.padding(25.dp)) { Text("관측된 이벤트 중 좋은 습관", color = Color.LightGray, fontSize = 12.sp); Text(Counts(good, caution).goodRate?.let { "$it%" } ?: "—", color = Color.White, fontSize = 73.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(top = 18.dp)); Text("좋은 순간 $good · 주의 순간 $caution", color = Color.LightGray, fontSize = 14.sp); Text(if (good + caution == 0) "첫 주행의 기록을 기다리고 있어요." else "법규 준수나 안전을 보증하는 점수가 아니에요.", color = Color.Gray, fontSize = 10.sp, modifier = Modifier.padding(top = 14.dp)) } }
            Surface(Modifier.weight(1.4f).height(230.dp), color = Color.White, shape = Shape) { Column(Modifier.padding(24.dp)) {
                Text("지난 주행과 비교", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                if (trips.isEmpty()) Text("주행을 마치면 비교할 기록이 쌓여요.", color = Subtle, modifier = Modifier.padding(top = 35.dp))
                trips.take(2).forEachIndexed { i, t -> Row(Modifier.fillMaxWidth().padding(top = 23.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(if (i == 0) "가장 최근 주행" else "그 이전 주행", fontSize = 13.sp); Text(formatDate(t.startedAt) + if (t.interrupted) " · 중단된 주행 복구" else if (t.endedAt == null) " · 진행 중" else "", fontSize = 10.sp, color = Subtle, modifier = Modifier.padding(top = 4.dp)) }; Text(Counts(t.good, t.caution).goodRate?.let { "$it%" } ?: "관측 대기", fontSize = 24.sp) } }
            } }
        }
        Text("곁이 기억한 순간", fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 27.dp, bottom = 10.dp))
        if (events.isEmpty()) Text("아직 기록이 없어요. 주행을 시작하거나 시연 상황을 골라보세요.", color = Subtle, fontSize = 13.sp)
        events.take(30).forEach { e -> Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) { Text(if (e.tone == "GOOD") "+" else "!", modifier = Modifier.width(32.dp), fontSize = 20.sp); Column(Modifier.weight(1f)) { Text(app.catalog.find(e.scenarioId)?.title ?: e.scenarioId, fontSize = 15.sp); Text(e.reason, fontSize = 11.sp, color = Subtle, modifier = Modifier.padding(top = 5.dp)) }; Text(formatDate(e.observedAt), fontSize = 10.sp, color = Subtle) }; HorizontalDivider(color = Line) }
    }
}

@Composable
private fun VoiceScreen(app: GyeotApplication, driver: DriverEntity?, clips: List<ClipEntity>, message: String, editable: Boolean, onPersona: (String) -> Unit, onPlay: (Scenario) -> Unit, onImport: (Scenario) -> Unit) {
    var group by rememberSaveable { mutableIntStateOf(0) }
    val persona = driver?.persona ?: "friend"
    Column {
        Heading("A VOICE THAT FEELS LIKE HOME", "우리 목소리로, 더 가까이.", "기존 음성으로 먼저 체험하고, 녹음한 파일을 상황별로 교체해요.")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Personas.names.forEach { (id, name) ->
                OutlinedCard(onClick = { onPersona(id) }, enabled = editable, modifier = Modifier.weight(1f), colors = CardDefaults.outlinedCardColors(containerColor = if (id == persona) Ink else Color.White), border = BorderStroke(1.dp, if (id == persona) Ink else Line)) {
                    Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) { Avatar(id); Spacer(Modifier.width(13.dp)); Column { Text(name, fontSize = 16.sp, color = if (id == persona) Color.White else Ink); Text(if (id == persona) "함께하는 중" else "목소리 선택", fontSize = 10.sp, color = if (id == persona) Color.LightGray else Subtle, modifier = Modifier.padding(top = 5.dp)) } }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf("칭찬 15", "주의 · 잔소리 15", "공통 멘트 8").forEachIndexed { i, label -> FilterChip(group == i, { group = i }, { Text(label) }); Spacer(Modifier.width(8.dp)) }
            Spacer(Modifier.weight(1f)); Text(message, fontSize = 11.sp, color = Subtle)
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 25.dp)) {
            items(app.catalog.scenarios.filter { it.tone == listOf(Tone.GOOD, Tone.CAUTION, Tone.COMMON)[group] }, key = { it.id }) { s ->
                val custom = clips.any { it.persona == persona && it.scenarioId == s.id } || app.voicePlayer.hasPackaged(persona, s.id)
                Surface(color = Color.White, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Line)) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) { Text(s.id, fontSize = 11.sp, color = Subtle, modifier = Modifier.width(42.dp)); Text(s.title, fontSize = 17.sp, fontWeight = FontWeight.Medium); Spacer(Modifier.width(12.dp)); Text(if (custom) "내 녹음 등록됨" else if (s.legacyClip != null) "기존 임시 음성" else "녹음 대기", fontSize = 10.sp, color = Subtle) }
                            Text(s.recordingText, fontSize = 13.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 8.dp, end = 18.dp))
                            if (!custom && s.legacyClip != null) Text("임시 음성 대사: ${app.voicePlayer.legacyText(persona, s)}", fontSize = 10.sp, lineHeight = 17.sp, color = Subtle, modifier = Modifier.padding(top = 7.dp))
                        }
                        IconButton(onClick = { onPlay(s) }, enabled = custom || s.legacyClip != null) { Icon(Icons.Outlined.PlayCircleOutline, "${s.id} 미리 듣기") }
                        OutlinedButton(onClick = { onImport(s) }, enabled = editable, shape = RoundedCornerShape(7.dp)) { Icon(Icons.Outlined.UploadFile, null, Modifier.size(17.dp)); Spacer(Modifier.width(7.dp)); Text("녹음 파일", fontSize = 12.sp) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(app: GyeotApplication, prefs: Preferences, state: MonitorUi, driver: DriverEntity?, message: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var gleoPackage by remember(prefs.gleoPackage) { mutableStateOf(prefs.gleoPackage) }
    val editable = !state.running
    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 25.dp)) {
        Heading("READY FOR PLEOS CONNECT", "연결하고, 나를 기억하기.", "주행 설정은 출발 전에 바꿔주세요. 데이터와 녹음은 이 기기에 저장됩니다.")
        SettingRow("시연 모드", "켜면 30가지 예시 상황을 사용해요. 시연 학습은 실제 기록에 합쳐지지 않아요.", prefs.demo, editable) { scope.launch { app.settings.demo(it) } }
        SettingRow("상황별 음성 안내", "등록된 녹음과 기존 임시 음성만 재생해요.", prefs.voice, true) { scope.launch { app.settings.voice(it) }; if (!it) app.voicePlayer.stop() }
        SettingRow("좋은 습관도 말해주기", "칭찬을 줄이고 싶을 때 끌 수 있어요.", prefs.praise, true) { scope.launch { app.settings.praise(it) } }
        Row(Modifier.fillMaxWidth().padding(vertical = 19.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("정차 중 내 좌석 · 창문 기준 등록", fontSize = 16.sp); Text("좌석을 편안한 자세로 맞추고 모든 창문을 닫은 뒤 등록해 주세요. 차량을 제어하지 않아요.", fontSize = 11.sp, color = Subtle, modifier = Modifier.padding(top = 6.dp)) }
            OutlinedButton(onClick = {
                val frame = state.frame
                if (frame == null || frame.source != SignalSource.PLEOS || (frame.backrestPositions.isEmpty() && frame.windowPositions.isEmpty())) message("먼저 차량 신호를 수신한 뒤 주행을 종료해 주세요.")
                else if (System.currentTimeMillis() - frame.timeMs !in 0..15_000 || frame.speedKph?.let { it <= .5 } != true || frame.gear != "GEAR_PARK") message("차량이 P단으로 정차한 최신 신호가 필요해요. 정차 상태에서 주행을 종료한 직후 등록해 주세요.")
                else if (driver != null) scope.launch { app.dao.calibrate(driver.id, Gson().toJson(frame.backrestPositions), Gson().toJson(frame.windowPositions)); message("기준을 저장했어요. 다음 주행부터 비교합니다.") }
            }, enabled = editable, shape = RoundedCornerShape(8.dp)) { Text("기준 등록") }
        }
        HorizontalDivider(color = Line)
        Text("Gleo 음성 명령 연결", fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 25.dp))
        Text("공식 Action · Callback 방식으로 운전 상황, 개선 이력, 녹음 상태를 전달합니다. Gleo 통합 승인과 응답 대상 앱 설정이 필요해요.", fontSize = 12.sp, lineHeight = 21.sp, color = Subtle, modifier = Modifier.padding(top = 10.dp))
        Row(Modifier.fillMaxWidth().padding(vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = gleoPackage, onValueChange = { gleoPackage = it }, label = { Text("차량에 설치된 Gleo 앱 패키지명") }, modifier = Modifier.weight(1f), singleLine = true, enabled = editable)
            Spacer(Modifier.width(15.dp)); Button(onClick = { scope.launch { app.settings.gleo(GleoProtocol.validPackage(gleoPackage), gleoPackage); message(if (GleoProtocol.validPackage(gleoPackage)) "Gleo 응답 대상을 저장했어요." else "올바른 패키지명을 입력해 주세요.") } }, enabled = editable) { Text("연결 설정 저장") }
        }
        SettingRow("Gleo에 운전 요약 제공", "설정한 앱에만 요약을 보내요. 이름·좌표·녹음 파일은 전달하지 않아요.", prefs.gleoEnabled, editable && GleoProtocol.validPackage(prefs.gleoPackage)) { scope.launch { app.settings.gleo(it, prefs.gleoPackage) } }
        Text("Gleo의 자동 센서 판단 호출 API는 공개 문서에서 확인되지 않았습니다. 현재 자동 코칭은 로컬 규칙 후보이며, Gleo 최종 답변은 사용자가 음성으로 요청할 때 제공됩니다.", color = Subtle, fontSize = 11.sp, lineHeight = 19.sp, modifier = Modifier.padding(vertical = 15.dp))
        Text("실시간 연결 상태", fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(vertical = 15.dp))
        CapabilityList(state.capabilities)
    }
}
@Composable
private fun SettingRow(title: String, description: String, checked: Boolean, enabled: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, fontSize = 16.sp); Text(description, color = Subtle, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp)) }; Switch(checked, change, enabled = enabled) }
    HorizontalDivider(color = Line)
}
@Composable
private fun CapabilityList(capabilities: Map<String, String>) {
    if (capabilities.isEmpty()) Text("차량 신호 모드로 주행을 시작하면 지원 항목과 연결 상태를 확인할 수 있어요.", fontSize = 12.sp, color = Subtle)
    capabilities.forEach { (key, value) -> Row(Modifier.fillMaxWidth().padding(vertical = 9.dp)) { Text(key, modifier = Modifier.weight(1f), fontSize = 12.sp); Text(value, fontSize = 11.sp, color = Subtle) }; HorizontalDivider(color = Line) }
}
@Composable
private fun Avatar(persona: String) {
    Surface(color = Color(0xFFDDDDD6), shape = RoundedCornerShape(50), modifier = Modifier.size(43.dp)) { Box(contentAlignment = Alignment.Center) { Text(mapOf("friend" to "친", "grandma" to "할", "mom" to "엄", "sibling" to "동")[persona] ?: "곁", fontSize = 19.sp, color = Ink, fontWeight = FontWeight.Medium) } }
}
private fun formatDate(time: Long) = SimpleDateFormat("MM.dd HH:mm", Locale.KOREA).apply { timeZone = TimeZone.getTimeZone("Asia/Seoul") }.format(Date(time))
