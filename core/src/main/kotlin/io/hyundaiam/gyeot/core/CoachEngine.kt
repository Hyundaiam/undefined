package io.hyundaiam.gyeot.core

/** Candidate detection, not an AI or a legal compliance verdict. Unknown data stays unknown. */
class CoachEngine {
    private val since = mutableMapOf<String, Long>()
    private val emitted = mutableMapOf<String, Long>()
    private var previous: SignalFrame? = null

    fun reset() { since.clear(); emitted.clear(); previous = null }

    fun evaluate(frame: SignalFrame, nowMs: Long = frame.timeMs): List<Candidate> {
        if (nowMs - frame.timeMs !in 0..5_000) return emptyList()
        if (previous?.source != frame.source) reset()
        val candidates = mutableListOf<Candidate>()
        fun condition(id: String, active: Boolean?, holdMs: Long, reason: String) {
            val key = "${frame.source}:$id"
            if (active != true) { since.remove(key); return }
            val started = since.getOrPut(key) { frame.timeMs }
            val last = emitted[key]
            val cooldown = if (id.startsWith("G")) 300_000L else 120_000L
            if (frame.timeMs - started >= holdMs && (last == null || frame.timeMs - last >= cooldown)) {
                emitted[key] = frame.timeMs
                candidates += Candidate(id, reason, .9, frame.source)
            }
        }
        val speed = frame.speedKph?.takeIf { it.isFinite() && it in 0.0..300.0 && (frame.source == SignalSource.DEMO || frame.speedTimeMs?.let { t -> nowMs - t in 0..3_000 } == true) }
        val limit = frame.speedLimitKph?.takeIf { it.isFinite() && it in 1.0..200.0 && (frame.source == SignalSource.DEMO || frame.speedLimitTimeMs?.let { t -> nowMs - t in 0..30_000 } == true) }
        val moving = speed?.let { it > 5 } ?: (frame.gear?.let { it == "GEAR_DRIVE" })
        // Noise tolerance is an engineering filter, not a legal allowance.
        condition("B02", if (speed != null && limit != null) speed > limit + 3 else null, 3_000, "내비게이션 제한 속도보다 높은 속도가 지속됨")
        condition("G02", if (speed != null && limit != null) speed in 15.0..limit else null, 30_000, "확인된 제한 속도 이하로 일정 시간 주행함")
        val gap = if (speed != null && speed > 15 && frame.distanceM?.isFinite() == true && frame.distanceM >= 0) frame.distanceM / (speed / 3.6) else null
        condition("B01", gap?.let { it < 1.2 }, 3_000, "외부 인지 입력의 시간 차간거리가 짧게 유지됨")
        condition("G01", gap?.let { it >= 2.5 }, 20_000, "외부 인지 입력에서 차간거리 여유가 유지됨")
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
        condition("B05", if (speed != null && frame.recommendedCurveSpeed != null) speed > frame.recommendedCurveSpeed else null, 2_000, "곡선 구간의 확인된 권장 속도보다 빠름")
        condition("B06", if (frame.laneChange == true) frame.indicatorOn?.not() else null, 0, "별도 차로 변경 입력에서 방향지시등 사용이 확인되지 않음")
        condition("B07", if (moving == true) frame.laneDeviation else null, 3_000, "별도 차선 인지 입력에서 의도하지 않은 차선 이탈이 지속됨")
        condition("B13", if (moving == true) frame.seatDeviationFraction?.let { it > .25 } else null, 10_000, "정차 중 등록한 등받이 기준에서 크게 벗어남·정차 후 확인 필요")
        condition("G13", if (moving == true) frame.seatDeviationFraction?.let { it <= .1 } else null, 30_000, "등록한 기준 좌석 자세를 유지함")
        val prev = previous
        val dt = if (prev != null) (frame.timeMs - prev.timeMs) / 1000.0 else 0.0
        val acceleration = if (speed != null && prev?.speedKph != null && dt in .2..5.0) (speed - prev.speedKph) / 3.6 / dt else null
        condition("B03", acceleration?.let { it > 3 }, 1_000, "속도 변화에서 급격한 가속이 관측됨")
        // Never punish emergency braking when the surrounding situation is unknown.
        condition("B04", if (frame.emergencyBraking == false) acceleration?.let { it < -3 } else null, 0, "불가피한 회피 상황이 아닌 급제동이 확인됨")
        previous = frame
        return candidates.sortedBy { if (it.scenarioId.startsWith("B")) 0 else 1 }
    }
}

class SpeechGate {
    private var lastSpeech = Long.MIN_VALUE
    fun allow(tone: Tone, nowMs: Long, navigationSpeaking: Boolean): Boolean {
        if (navigationSpeaking || (lastSpeech != Long.MIN_VALUE && nowMs - lastSpeech < if (tone == Tone.CAUTION) 15_000 else 60_000)) return false
        lastSpeech = nowMs
        return true
    }
}
