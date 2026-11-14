package io.hyundaiam.gyeot.core

enum class SpeedState { WAITING_SPEED, WAITING_LIMIT, WITHIN_LIMIT, OVER_LIMIT }
data class SpeedAssessment(val state: SpeedState, val speed: Double? = null, val limit: Double? = null) {
    val excess get() = if (speed != null && limit != null) (speed - limit).coerceAtLeast(0.0) else null
    companion object {
        fun from(frame: SignalFrame?, now: Long): SpeedAssessment {
            val speed = frame?.speedKph?.takeIf { it.isFinite() && it in 0.0..300.0 &&
                (frame.source == SignalSource.DEMO || frame.speedTimeMs?.let { t -> now - t in 0..3_000 } == true) }
            val limit = frame?.speedLimitKph?.takeIf { it.isFinite() && it in 1.0..200.0 &&
                (frame.source == SignalSource.DEMO || frame.speedLimitTimeMs?.let { t -> now - t in 0..30_000 } == true) }
            return SpeedAssessment(when { speed == null -> SpeedState.WAITING_SPEED; limit == null -> SpeedState.WAITING_LIMIT; speed > limit -> SpeedState.OVER_LIMIT; else -> SpeedState.WITHIN_LIMIT }, speed, limit)
        }
    }
}
