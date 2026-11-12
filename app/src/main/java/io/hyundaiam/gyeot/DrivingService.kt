package io.hyundaiam.gyeot

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import io.hyundaiam.gyeot.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update

class DrivingService : Service() {
    private val app get() = application as GyeotApplication
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var backend: PleosSignals? = null
    private var tripId: Long? = null
    private var driverId = 0L
    private var mode = SignalSource.DEMO
    private var starting = false
    private var autoDemo = true
    private var startedAt = 0L
    private val engine = CoachEngine()
    private val speechGate = SpeechGate()
    private val gson = Gson()

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL, "곁 주행 학습", NotificationManager.IMPORTANCE_LOW))
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            STOP -> stopMonitoring()
            SCENARIO -> {
                val id = intent.getStringExtra("scenario") ?: return START_NOT_STICKY
                if (mode == SignalSource.DEMO && tripId != null) scope.launch { simulate(id) }
            }
            START -> if (!starting && tripId == null) {
                starting = true
                val prefsDemo = intent.getBooleanExtra("demo", true)
                mode = if (prefsDemo) SignalSource.DEMO else SignalSource.PLEOS
                driverId = intent.getLongExtra("driver", 1)
                promote()
                scope.launch {
                    runCatching { startMonitoring() }.onFailure {
                        app.monitor.update { s -> s.copy(status = "주행 시작 실패 · ${it.message}", running = false) }
                        stopSelf()
                    }
                    starting = false
                }
            }
        }
        return START_NOT_STICKY
    }
    private fun promote() {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, DrivingService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_gyeot)
            .setContentTitle(if (mode == SignalSource.DEMO) "곁 · 시연 기록 중" else "곁 · 주행 학습 중")
            .setContentText("내비게이션을 사용하면서도 주행을 기억해요.").setContentIntent(open).setOngoing(true)
            .addAction(0, "주행 종료", stop).build()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, 41, notification, type)
    }
    private suspend fun startMonitoring() {
        app.ready.await()
        val driver = requireNotNull(withContext(Dispatchers.IO) { app.dao.driver(driverId) })
        startedAt = System.currentTimeMillis()
        tripId = withContext(Dispatchers.IO) { app.dao.addTrip(TripEntity(driverId = driverId, source = mode.name, startedAt = startedAt, lastSeenAt = startedAt)) }
        app.monitor.value = MonitorUi(true, driverId, mode == SignalSource.DEMO, tripId, startedAt, status = if (mode == SignalSource.DEMO) "시연 입력으로 학습 중 · 실제 기록과 분리" else "차량 신호 연결 중")
        if (mode == SignalSource.PLEOS) {
            // FusedLocation's platform permission behavior must be validated in Pleos.
            // The service reads no location in this first release; it probes support only.
            backend = PleosSignals(app, scope, driver, false).also { it.start() }
        }
        scope.launch {
            if (app.settings.flow.first().voice) app.catalog.find("C01")?.let { app.voicePlayer.play(driver.persona, it) }
        }
        scope.launch {
            var seconds = 0
            while (isActive) {
                val now = System.currentTimeMillis()
                val frame = if (mode == SignalSource.PLEOS) backend?.frames?.value?.copy(timeMs = now) else app.monitor.value.frame ?: SignalFrame(now, SignalSource.DEMO, speedKph = 58.0, speedLimitKph = 60.0, distanceM = 42.0, gear = "GEAR_DRIVE")
                if (frame != null) {
                    val hasSignal = frame.speedKph != null || frame.speedLimitKph != null || frame.beltBySeat.isNotEmpty() || frame.windowPositions.isNotEmpty() || frame.backrestPositions.isNotEmpty() || frame.gear != null
                    app.monitor.update { it.copy(frame = frame, status = if (mode == SignalSource.PLEOS) { if (hasSignal) "차량의 제공된 신호로 학습 중" else "차량 신호 대기 · 연결 상태를 확인해 주세요" } else it.status) }
                    withContext(Dispatchers.IO) {
                        val id = tripId ?: return@withContext
                        app.dao.heartbeat(id, now)
                        if (seconds % 2 == 0 && hasSignal) app.dao.addObservation(ObservationEntity(driverId = driverId, tripId = id, source = mode.name, observedAt = now, payload = gson.toJson(frame)))
                        if (seconds % 60 == 0) app.dao.trimObservations()
                    }
                    if (mode == SignalSource.PLEOS) engine.evaluate(frame, now).forEach { record(it) }
                }
                if (mode == SignalSource.DEMO && autoDemo && seconds > 0 && seconds % 15 == 0) {
                    val list = app.catalog.scenarios.filter { it.tone != Tone.COMMON }
                    simulate(list[(seconds / 15 - 1) % list.size].id)
                }
                seconds++; delay(1_000)
            }
        }
    }
    private suspend fun simulate(id: String) {
        val scenario = app.catalog.find(id) ?: return
        if (scenario.tone == Tone.COMMON) return
        val frame = SignalFrame(System.currentTimeMillis(), SignalSource.DEMO,
            speedKph = if (id == "B02") 74.0 else if (id.endsWith("15")) 0.0 else 55.0,
            speedLimitKph = 60.0, distanceM = if (id == "B01") 12.0 else 42.0,
            gear = if (id.endsWith("15")) "GEAR_PARK" else "GEAR_DRIVE", tunnel = id.endsWith("10"), windowOpen = id == "B10",
            handsOnWheel = if (id == "B11") 1 else 2,
            occupiedSeats = setOf("ROW_1_LEFT"), beltBySeat = mapOf("ROW_1_LEFT" to (id != "B14")))
        app.monitor.update { it.copy(frame = frame) }
        record(Candidate(id, "시연 입력 · ${scenario.trigger}", 1.0, SignalSource.DEMO))
    }
    private suspend fun record(candidate: Candidate) {
        val id = tripId ?: return
        val scenario = app.catalog.find(candidate.scenarioId) ?: return
        val now = System.currentTimeMillis()
        withContext(Dispatchers.IO) {
            app.dao.record(EventEntity(driverId = driverId, tripId = id, source = candidate.source.name, scenarioId = scenario.id, category = scenario.category, tone = scenario.tone.name, reason = candidate.reason, confidence = candidate.confidence, observedAt = now))
        }
        app.monitor.update { it.copy(lastScenarioId = scenario.id) }
        val prefs = app.settings.flow.first()
        if (prefs.voice && (prefs.praise || scenario.tone != Tone.GOOD) && speechGate.allow(scenario.tone, now, false)) {
            val driver = withContext(Dispatchers.IO) { app.dao.driver(driverId) } ?: return
            // Some old clips mention a fixed 60 km/h limit or an unverified improvement.
            val legacyAllowed = mode == SignalSource.DEMO ||
                (scenario.id == "B02" && app.monitor.value.frame?.speedLimitKph == 60.0) ||
                scenario.id in setOf("B01", "B05", "B10", "B11")
            app.voicePlayer.play(driver.persona, scenario, allowLegacy = legacyAllowed)
        }
    }
    private fun stopMonitoring() {
        val id = tripId; tripId = null
        scope.cancel(); backend?.close(); backend = null; app.voicePlayer.stop()
        if (id != null) app.scope.launch(Dispatchers.IO) { app.dao.finishTrip(id, System.currentTimeMillis()) }
        app.monitor.update { it.copy(running = false, tripId = null, status = "이번 주행을 저장했어요") }
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
    }
    override fun onDestroy() {
        val id = tripId; tripId = null
        scope.cancel(); backend?.close(); app.voicePlayer.stop()
        if (id != null) app.scope.launch(Dispatchers.IO) { app.dao.finishTrip(id, System.currentTimeMillis()) }
        app.monitor.update { it.copy(running = false, tripId = null) }
        super.onDestroy()
    }
    companion object {
        const val CHANNEL = "gyeot_monitor"
        const val START = "io.hyundaiam.gyeot.START"
        const val STOP = "io.hyundaiam.gyeot.STOP"
        const val SCENARIO = "io.hyundaiam.gyeot.SCENARIO"
    }
}
