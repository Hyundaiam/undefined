package io.hyundaiam.gyeot.core

/** Candidate detection, not an AI or a legal compliance verdict. Unknown data stays unknown. */
class CoachEngine {
    private val since = mutableMapOf<String, Long>()
    private val emitted = mutableMapOf<String, Long>()
    private var previous: SignalFrame? = null
    private var speedEpisode = false
    private var episodeLimit: Double? = null
    private var indicatorSince: Long? = null
    private var gentleBrakingSince: Long? = null
    private val active = mutableSetOf<String>()
    fun isActive(id: String) = id in active

    fun reset() { since.clear(); emitted.clear(); active.clear(); previous = null; speedEpisode = false; episodeLimit = null; indicatorSince = null; gentleBrakingSince = null }

    fun evaluate(frame: SignalFrame, nowMs: Long = frame.timeMs): List<Candidate> {
        if (nowMs - frame.timeMs !in 0..5_000) return emptyList()
        if (previous?.source != frame.source) reset()
        active.clear()
        val candidates = mutableListOf<Candidate>()
        fun condition(id: String, active: Boolean?, holdMs: Long, reason: String) {
            val key = "${frame.source}:$id"
            if (active != true) { since.remove(key); return }
            this@CoachEngine.active.add(id)
            val started = since.getOrPut(key) { frame.timeMs }
            val last = emitted[key]
            val cooldown = when { id == "B02" -> 30_000L; id == "C06" -> 0L; id.startsWith("G") -> 300_000L; else -> 120_000L }
            if (frame.timeMs - started >= holdMs && (last == null || frame.timeMs - last >= cooldown)) {
                emitted[key] = frame.timeMs
                candidates += Candidate(id, reason, .9, frame.source)
            }
        }
        val speed = frame.speedKph?.takeIf { it.isFinite() && it in 0.0..300.0 && (frame.source == SignalSource.DEMO || frame.speedTimeMs?.let { t -> nowMs - t in 0..3_000 } == true) }
        val limit = frame.speedLimitKph?.takeIf { it.isFinite() && it in 1.0..200.0 && (frame.source == SignalSource.DEMO || frame.speedLimitTimeMs?.let { t -> nowMs - t in 0..30_000 } == true) }
        val moving = speed?.let { it > 5 } ?: (frame.gear?.let { it == "GEAR_DRIVE" })
        if (speedEpisode && limit != episodeLimit) { speedEpisode = false; emitted.remove("${frame.source}:B02") }
        condition("B02", if (speed != null && limit != null && frame.schoolZone != true) speed > limit else null, 2_000, "확인된 제한 속도보다 높은 속도가 2초 이상 지속됨")
        if (candidates.any { it.scenarioId == "B02" }) { speedEpisode = true; episodeLimit = limit }
        condition("C06", speedEpisode && speed != null && limit != null && speed <= limit, 3_000, "과속 안내 후 제한 속도 이하로 감속한 상태를 유지함")
        if (candidates.any { it.scenarioId == "C06" }) { speedEpisode = false; emitted.remove("${frame.source}:B02") }
        condition("G02", if (speed != null && limit != null && frame.schoolZone != true) speed in 15.0..limit else null, 30_000, "확인된 제한 속도 이하로 일정 시간 주행함")
        val gap = if (speed != null && speed > 15 && frame.distanceM?.isFinite() == true && frame.distanceM >= 0) frame.distanceM / (speed / 3.6) else null
        val ordinaryGap = gap.takeIf { frame.night != true && frame.wetRoad != true && frame.mergeZone != true }
        condition("B01", ordinaryGap?.let { it < 1.2 }, 3_000, "외부 인지 입력의 시간 차간거리가 짧게 유지됨")
        condition("G01", ordinaryGap?.let { it >= 2.5 }, 20_000, "외부 인지 입력에서 차간거리 여유가 유지됨")
        condition("B10", frame.tunnel?.let { t -> frame.windowOpen?.let { t && it } }, 2_000, "터널 구간에서 열린 창문이 확인됨")
        condition("G10", frame.tunnel?.let { t -> frame.windowOpen?.let { t && !it } }, 10_000, "터널 구간에서 창문을 닫은 상태로 유지함")
        val occupied = frame.occupiedSeats
        val allBeltsKnown = occupied.isNotEmpty() && occupied.all { it in frame.beltBySeat }
        val unbuckled = if (allBeltsKnown) occupied.any { frame.beltBySeat[it] == false } else null
        condition("B14", if (moving == true) unbuckled else null, 2_000, "탑승이 확인된 좌석의 안전벨트가 미착용 상태임")
        condition("G14", if (moving == true) unbuckled?.not() else null, 30_000, "확인된 탑승 좌석 모두 안전벨트를 착용함")
        condition("B11", if (speed != null && speed > 20) frame.handsOnWheel?.let { it < 2 } else null, 8_000, "별도 실내 인지 입력에서 한 손 운전이 지속됨")
        condition("G11", if (speed != null && speed > 20) frame.handsOnWheel?.let { it >= 2 } else null, 30_000, "별도 실내 인지 입력에서 양손 운전이 유지됨")
        condition("B12", if (moving == true && frame.automaticTransmission == true) frame.bothFeetOnPedals else null, 3_000, "자동변속 차량의 별도 발 위치 입력에서 양발 조작이 확인됨")
        condition("G12", moving == true && frame.automaticTransmission == true && frame.bothFeetOnPedals == false, 10_000, "별도 발 위치 입력에서 자동변속 차량의 한 발 페달 조작이 유지됨")
        condition("B05", if (speed != null && frame.recommendedCurveSpeed != null) speed > frame.recommendedCurveSpeed else null, 2_000, "곡선 구간의 확인된 권장 속도보다 빠름")
        condition("G05", moving == true && speed != null && frame.recommendedCurveSpeed?.let { it.isFinite() && it > 0 && speed <= it } == true, 5_000, "곡선 권장 속도 이하로 감속해 주행함")
        condition("B06", if (frame.laneChange == true) frame.indicatorOn?.not() else null, 0, "별도 차로 변경 입력에서 방향지시등 사용이 확인되지 않음")
        if (frame.indicatorOn == true) { if (indicatorSince == null) indicatorSince = frame.timeMs } else indicatorSince = null
        condition("G06", frame.mergeZone != true && frame.laneChange == true && frame.indicatorOn == true && indicatorSince?.let { frame.timeMs - it >= 1_500 } == true, 0, "방향지시등을 먼저 켠 뒤 차로 변경이 확인됨")
        condition("B07", if (moving == true) frame.laneDeviation else null, 3_000, "별도 차선 인지 입력에서 의도하지 않은 차선 이탈이 지속됨")
        condition("G07", moving == true && frame.laneDeviation == false && frame.laneChange != true, 10_000, "별도 차선 인지 입력에서 차선 안 주행이 유지됨")
        condition("G08", frame.pedestrianAhead == true && frame.crosswalkAhead == true && frame.pedestrianYielded == true && speed != null && speed <= .5, 1_000, "횡단보도 보행자에게 정지해 양보함")
        condition("B08", frame.pedestrianAhead == true && frame.crosswalkAhead == true && frame.pedestrianYielded == false && moving == true, 1_000, "보행자 양보가 확인되지 않은 채 횡단보도에 접근함")
        val weatherSpeed = frame.weatherRecommendedSpeed?.takeIf { it.isFinite() && it > 0 }
        condition("G09", frame.wetRoad == true && speed != null && weatherSpeed != null && speed <= weatherSpeed && gap != null && gap >= 3, 5_000, "젖은 도로 권장 속도와 차간거리 여유를 유지함")
        condition("B09", frame.wetRoad == true && ((speed != null && weatherSpeed != null && speed > weatherSpeed) || (gap != null && gap < 2)), 2_000, "젖은 도로의 확인된 권장 속도 또는 거리 기준을 벗어남")
        condition("B13", if (moving == true) frame.seatDeviationFraction?.let { it > .25 } else null, 10_000, "정차 중 등록한 등받이 기준에서 크게 벗어남·정차 후 확인 필요")
        condition("G13", if (moving == true) frame.seatDeviationFraction?.let { it <= .1 } else null, 30_000, "등록한 기준 좌석 자세를 유지함")
        val prev = previous
        val dt = if (prev != null) (frame.timeMs - prev.timeMs) / 1000.0 else 0.0
        val acceleration = if (speed != null && prev?.speedKph != null && dt in .2..5.0) (speed - prev.speedKph) / 3.6 / dt else null
        condition("G03", speed != null && speed in 1.0..30.0 && acceleration != null && acceleration in .2..1.5, 3_000, "낮은 속도에서 부드러운 가속으로 출발함")
        condition("B03", acceleration?.let { it > 3 }, 1_000, "속도 변화에서 급격한 가속이 관측됨")
        // Never punish emergency braking when the surrounding situation is unknown.
        condition("B04", if (frame.emergencyBraking == false) acceleration?.let { it < -3 } else null, 0, "불가피한 회피 상황이 아닌 급제동이 확인됨")
        if (acceleration != null && acceleration in -2.0..-.2) {
            if (gentleBrakingSince == null) gentleBrakingSince = prev?.timeMs
        } else if (speed == null || speed > .5) gentleBrakingSince = null
        condition("G04", speed != null && speed <= .5 && acceleration != null && acceleration in -2.0..-.2 && gentleBrakingSince?.let { frame.timeMs - it >= 2_000 } == true, 0, "지속적으로 부드럽게 감속해 완전히 정지함")
        if (speed != null && speed <= .5) gentleBrakingSince = null
        val stopped = speed != null && speed <= .5
        condition("G15", stopped && frame.gear == "GEAR_PARK" && frame.ignitionOn == false && frame.parkingBrakeOn == true, 1_000, "완전 정차·P단·주차브레이크·시동 종료가 확인됨")
        condition("B15", stopped && frame.driverLeaving == true && frame.gear != null && frame.ignitionOn != null && frame.parkingBrakeOn != null && (frame.gear != "GEAR_PARK" || frame.ignitionOn || !frame.parkingBrakeOn), 1_000, "하차 의사와 함께 주차·시동 종료 절차 누락이 확인됨")
        condition("G16", frame.schoolZone == true && moving == true && speed != null && limit != null && speed <= limit, 5_000, "어린이보호구역의 확인된 제한 속도 이하로 주행함")
        condition("B16", frame.schoolZone == true && speed != null && limit != null && speed > limit, 2_000, "어린이보호구역의 확인된 제한 속도를 초과함")
        val stopLine = frame.stopLineDistanceM?.takeIf(Double::isFinite)
        condition("G17", frame.redLight == true && stopped && stopLine != null && stopLine >= 0, 1_000, "적색 신호에서 정지선 앞 완전 정지가 확인됨")
        condition("B17", frame.redLight == true && speed != null && speed > .5 && stopLine != null && stopLine < 0, 0, "적색 신호에서 정지선을 넘어 진행함")
        condition("G18", frame.night == true && gap != null && gap >= 3.5, 8_000, "야간에 넉넉한 시간 차간거리를 유지함")
        condition("B18", frame.night == true && gap != null && gap < 2, 2_000, "야간에 짧은 시간 차간거리가 지속됨")
        condition("G19", frame.emergencyVehicle == true && frame.emergencyYielded == true, 1_000, "접근 긴급차량에 양보 완료가 확인됨")
        condition("B19", frame.emergencyVehicle == true && frame.emergencyYielded == false && moving == true, 3_000, "접근 긴급차량에 양보가 확인되지 않고 주행함")
        condition("G20", moving == true && frame.blindSpotOccupied == true && frame.indicatorOn == true && frame.laneChange == false, 2_000, "사각지대 차량 때문에 차로 변경을 기다림")
        condition("B20", frame.blindSpotOccupied == true && frame.laneChange == true, 0, "사각지대 차량이 있는데 차로 변경함")
        condition("G21", frame.drowsy == true && frame.restStop == true && stopped && frame.gear == "GEAR_PARK", 1_000, "졸음 확인 후 휴게 장소에 정차함")
        condition("B21", frame.drowsy == true && moving == true, 2_000, "졸음이 확인된 상태에서 계속 주행함")
        condition("G22", moving == true && frame.doorsOpen == false, 5_000, "모든 문이 닫힌 상태로 출발함")
        condition("B22", moving == true && frame.doorsOpen == true, 0, "문이 열린 상태의 주행이 확인됨")
        condition("G23", stopped && frame.gear == "GEAR_PARK" && frame.tirePressureLow == false, 1_000, "정차 중 타이어 공기압 정상 상태가 확인됨")
        condition("B23", moving == true && frame.tirePressureLow == true, 2_000, "낮은 타이어 공기압 상태로 주행함")
        val range = frame.remainingRangeKm?.takeIf { it.isFinite() && it in 0.0..1500.0 }
        val route = frame.routeRemainingKm?.takeIf { it.isFinite() && it > 0 && it <= 1500 }
        condition("G24", moving == true && range != null && route != null && range - route >= 20, 5_000, "예상 잔여 주행거리가 남은 경로보다 20km 이상 여유 있음")
        condition("B24", moving == true && range != null && route != null && range < route, 2_000, "예상 잔여 주행거리가 남은 경로보다 부족함")
        condition("G25", moving == true && frame.mergeZone == true && frame.laneChange == true && frame.indicatorOn == true && gap != null && gap >= 2.5, 1_000, "합류 구간에서 방향지시등과 넉넉한 차간거리를 유지함")
        condition("B25", moving == true && frame.mergeZone == true && frame.laneChange == true && gap != null && gap < 1.5, 1_000, "합류 중 짧은 시간 차간거리가 확인됨")
        previous = frame
        return candidates.sortedBy { when { it.scenarioId == "B02" -> 0; it.scenarioId.startsWith("B") -> 1; else -> 2 } }
    }
}

class SpeechGate {
    private var lastCaution = Long.MIN_VALUE
    private var lastOther = Long.MIN_VALUE
    private var lastUrgent = Long.MIN_VALUE
    fun reset() { lastCaution = Long.MIN_VALUE; lastOther = Long.MIN_VALUE; lastUrgent = Long.MIN_VALUE }
    fun canSpeak(tone: Tone, nowMs: Long, navigationSpeaking: Boolean = false, urgent: Boolean = false): Boolean {
        if (navigationSpeaking) return false
        return if (urgent) lastUrgent == Long.MIN_VALUE || nowMs - lastUrgent >= 15_000
        else if (tone == Tone.CAUTION) lastCaution == Long.MIN_VALUE || nowMs - lastCaution >= 15_000
        else (lastOther == Long.MIN_VALUE || nowMs - lastOther >= if (tone == Tone.COMMON) 3_000 else 60_000)
    }
    fun markPlayed(tone: Tone, nowMs: Long, urgent: Boolean = false) { if (tone == Tone.CAUTION) lastCaution = nowMs else lastOther = nowMs; if (urgent) lastUrgent = nowMs }
    fun allow(tone: Tone, nowMs: Long, navigationSpeaking: Boolean): Boolean {
        if (!canSpeak(tone, nowMs, navigationSpeaking)) return false
        markPlayed(tone, nowMs)
        return true
    }
}
