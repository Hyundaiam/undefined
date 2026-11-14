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
    private var autoDemo = false
    private var replayJob: Job? = null
    private var startedAt = 0L
    private var testLimit = 0
    private val pendingSpeech = linkedMapOf<String, Pair<Candidate, Long>>()
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
            RESET_WEB -> if(mode == SignalSource.WEB_SIM && TestEnvironment.available) {
                engine.reset(); speechGate.reset(); pendingSpeech.clear(); app.voicePlayer.resetSession()
                app.monitor.update { it.copy(frame = null, feedback = "", feedbackAt = null, lastScenarioId = null, status = "웹에서 출발해 주세요") }
            }
            SCENARIO -> {
                val id = intent.getStringExtra("scenario") ?: return START_NOT_STICKY
                if (mode == SignalSource.DEMO && tripId != null && id in ScenarioFixtures.ids) {
                    replayJob?.cancel()
                    replayJob = scope.launch { simulate(id) }
                }
            }
            START -> if (!starting && tripId == null) {
                starting = true
                autoDemo = intent.getBooleanExtra("autoDemo", false)
                val prefsDemo = intent.getBooleanExtra("demo", true)
                testLimit = intent.getIntExtra("testLimit", 0).takeIf { TestEnvironment.available && it in 10..130 } ?: 0
                mode = when { intent.getBooleanExtra("webSimulator", false) && TestEnvironment.available -> SignalSource.WEB_SIM; prefsDemo -> SignalSource.DEMO; testLimit > 0 -> SignalSource.EMULATOR; else -> SignalSource.PLEOS }
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
        val open = PendingIntent.getActivity(this, 0, Intent(this, DriveActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
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
        if(mode == SignalSource.WEB_SIM) app.voicePlayer.resetSession()
        val driver = requireNotNull(withContext(Dispatchers.IO) { app.dao.driver(driverId) })
        startedAt = System.currentTimeMillis()
        tripId = withContext(Dispatchers.IO) { app.dao.addTrip(TripEntity(driverId = driverId, source = mode.name, startedAt = startedAt, lastSeenAt = startedAt)) }
        app.monitor.value = MonitorUi(true, driverId, mode == SignalSource.DEMO, tripId, startedAt, status = "차량 신호 연결 중", source = mode)
        if (mode in setOf(SignalSource.PLEOS, SignalSource.EMULATOR)) {
            // FusedLocation's platform permission behavior must be validated in Pleos.
            // The service reads no location in this first release; it probes support only.
            backend = PleosSignals(app, scope, driver, false).also { it.start() }
        }
        scope.launch {
            if (mode in setOf(SignalSource.PLEOS, SignalSource.EMULATOR) && app.settings.flow.first().voice) app.catalog.find("C01")?.let { app.voicePlayer.play(driver.persona, it) }
        }
        scope.launch {
            var seconds = 0
            while (isActive) {
                val now = System.currentTimeMillis()
                val frame = if(mode == SignalSource.WEB_SIM) app.webSimulator.frame.value?.takeIf { now - it.timeMs in 0..1600 }
                else if (mode != SignalSource.DEMO) backend?.frames?.value?.let { raw ->
                    if (mode == SignalSource.EMULATOR) raw.copy(timeMs = now, source = mode, speedLimitKph = testLimit.toDouble(), speedLimitTimeMs = now, speedLimitOrigin = "EMULATOR_TEST")
                    else raw.copy(timeMs = now)
                } else (app.monitor.value.frame ?: SignalFrame(now, SignalSource.DEMO)).copy(timeMs = now)
                if(mode == SignalSource.WEB_SIM && frame == null) {
                    if(app.monitor.value.frame != null) { app.voicePlayer.stop(); pendingSpeech.clear(); engine.reset() }
                    app.monitor.update { it.copy(frame = null, status = "웹 가상주행 연결 대기 · 브라우저에서 출발해 주세요") }
                    tripId?.let { withContext(Dispatchers.IO) { app.dao.heartbeat(it,now) } }
                }
                if (frame != null) {
                    val hasSignal = frame.speedKph != null || frame.speedLimitKph != null || frame.beltBySeat.isNotEmpty() || frame.windowPositions.isNotEmpty() || frame.backrestPositions.isNotEmpty() || frame.gear != null
                    val assessment = SpeedAssessment.from(frame, now)
                    val status = when(assessment.state) {
                        SpeedState.WAITING_SPEED -> "차량 속도 대기 · 연결 또는 속도 권한을 확인해 주세요"
                        SpeedState.WAITING_LIMIT -> "제한 속도 미수신 · 과속 안내를 기다리고 있어요"
                        SpeedState.OVER_LIMIT -> "제한 속도 초과 · 속도를 낮춰주세요"
                        SpeedState.WITHIN_LIMIT -> "제한 속도 이내 · 주행을 확인하고 있어요"
                    }
                    if (mode != SignalSource.DEMO) app.monitor.update { it.copy(frame = frame, status = status) }
                    withContext(Dispatchers.IO) {
                        val id = tripId ?: return@withContext
                        app.dao.heartbeat(id, now)
                        if (seconds % 2 == 0 && hasSignal) app.dao.addObservation(ObservationEntity(driverId = driverId, tripId = id, source = mode.name, observedAt = now, payload = gson.toJson(frame)))
                        if (seconds % 60 == 0) app.dao.trimObservations()
                    }
                    if (mode != SignalSource.DEMO) engine.evaluate(frame, now).forEach { record(it) }
                    if (mode != SignalSource.DEMO) flushSpeech(frame, now)
                }
                if (mode == SignalSource.DEMO && autoDemo && seconds > 0 && seconds % 15 == 0) {
                    val list = app.catalog.scenarios.filter { it.tone != Tone.COMMON }
                    if (replayJob?.isActive != true) replayJob = scope.launch { simulate(list[(seconds / 15 - 1) % list.size].id) }
                }
                seconds++; delay(if(mode == SignalSource.WEB_SIM) 250 else 1_000)
            }
        }
    }
    private suspend fun simulate(id: String) {
        val scenario = app.catalog.find(id) ?: return
        if (scenario.tone == Tone.COMMON) return
        engine.reset(); speechGate.reset(); pendingSpeech.clear(); app.voicePlayer.stop()
        app.monitor.update { it.copy(replayId = id, replayStage = "센서 입력 재현 중", replayProgress = 0f, lastScenarioId = null, feedback = "", status = "시험 입력 · ${scenario.title}") }
        val samples = ScenarioFixtures.frames(id)
        var detected = false
        for ((index, sample) in samples.withIndex()) {
            currentCoroutineContext().ensureActive()
            val now = System.currentTimeMillis()
            val display = sample.copy(timeMs = now, speedTimeMs = now.takeIf { sample.speedKph != null }, speedLimitTimeMs = now.takeIf { sample.speedLimitKph != null }, speedLimitOrigin = "SCENARIO_FIXTURE")
            app.monitor.update { it.copy(frame = display, replayProgress = (index + 1f) / samples.size, replayStage = "센서 재현 · 가상 ${sample.timeMs / 1_000}초") }
            withContext(Dispatchers.IO) {
                tripId?.let { app.dao.addObservation(ObservationEntity(driverId = driverId, tripId = it, source = "DEMO", observedAt = now, payload = gson.toJson(display))) }
            }
            // The selected ID never becomes a verdict. The same rules as live driving
            // evaluate the synthetic sensor sequence on an explicitly accelerated clock.
            val results = engine.evaluate(sample, sample.timeMs)
            for (result in results) {
                record(result.copy(reason = "시험 센서 · 가상 ${sample.timeMs / 1_000}초 · ${result.reason}"))
                if (result.scenarioId == id) detected = true
            }
            flushSpeech(display, now)
            if (detected) break
            delay(150)
        }
        app.monitor.update { it.copy(replayProgress = 1f, replayStage = if (detected) "조건 판정 완료 · ${it.lastScenarioId}" else "조건 미충족 · 입력을 확인해 주세요") }
        while (pendingSpeech.isNotEmpty()) {
            delay(1_000)
            app.monitor.value.frame?.let { flushSpeech(it, System.currentTimeMillis()) }
        }
    }
    private suspend fun record(candidate: Candidate) {
        val id = tripId ?: return
        val scenario = app.catalog.find(candidate.scenarioId) ?: return
        val now = System.currentTimeMillis()
        withContext(Dispatchers.IO) {
            app.dao.record(EventEntity(driverId = driverId, tripId = id, source = candidate.source.name, scenarioId = scenario.id, category = scenario.category, tone = scenario.tone.name, reason = candidate.reason, confidence = candidate.confidence, observedAt = now))
        }
        val persona = withContext(Dispatchers.IO) { app.dao.driver(driverId)?.persona } ?: "friend"
        app.monitor.update { it.copy(lastScenarioId = scenario.id, feedback = app.voicePlayer.feedbackText(persona, scenario), feedbackAt = now) }
        pendingSpeech[scenario.id] = candidate to now
    }
    private suspend fun flushSpeech(frame: SignalFrame, now: Long) {
        val prefs = app.settings.flow.first()
        val assessment = SpeedAssessment.from(frame, now)
        pendingSpeech.entries.removeAll { (id, value) ->
            now - value.second > 20_000 || (id == "B02" && assessment.state != SpeedState.OVER_LIMIT) ||
                (id == "C06" && assessment.state != SpeedState.WITHIN_LIMIT) ||
                (mode != SignalSource.DEMO && id != "C06" && !engine.isActive(id)) ||
                (!prefs.praise && app.catalog.find(id)?.tone == Tone.GOOD)
        }
        if (!prefs.voice) { app.voicePlayer.report("음성 안내가 꺼져 있어요.", VoicePhase.MUTED); return }
        val driver = withContext(Dispatchers.IO) { app.dao.driver(driverId) } ?: return
        val ordered = pendingSpeech.keys.sortedBy { when { it == "B02" -> 0; it.startsWith("B") -> 1; it == "C06" -> 2; else -> 3 } }
        for (id in ordered) {
            val scenario = app.catalog.find(id) ?: continue
            val urgent = id == "B02" || id == "B16"
            if (app.voicePlayer.playing.value != null && !urgent) continue
            if (!speechGate.canSpeak(scenario.tone, now, urgent = urgent)) continue
            // Some old clips mention a fixed 60 km/h limit or an unverified improvement.
            val legacyAllowed = mode == SignalSource.DEMO ||
                (id == "B02" && frame.speedLimitKph == 60.0) || id in setOf("B01", "B05", "B10", "B11", "C06")
            when (app.voicePlayer.play(driver.persona, scenario, allowLegacy = legacyAllowed)) {
                PlaybackResult.STARTED -> { speechGate.markPlayed(scenario.tone, now, urgent = urgent); pendingSpeech.remove(id); return }
                PlaybackResult.MISSING -> pendingSpeech.remove(id)
                PlaybackResult.FOCUS_DENIED -> return // Retry while the observation is still relevant.
            }
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
        const val RESET_WEB = "io.hyundaiam.gyeot.RESET_WEB"
    }
}
