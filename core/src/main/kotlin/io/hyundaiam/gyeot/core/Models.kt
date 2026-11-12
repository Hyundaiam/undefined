package io.hyundaiam.gyeot.core

import com.google.gson.Gson

enum class SignalSource { PLEOS, DEMO }
enum class Tone { GOOD, CAUTION, COMMON }
data class Scenario(
    val id: String, val title: String, val category: String, val tone: Tone,
    val trigger: String, val recordingText: String, val requiredSignals: List<String>,
    val legacyClip: String? = null,
)
data class ScenarioCatalog(val scenarios: List<Scenario>) {
    fun find(id: String) = scenarios.firstOrNull { it.id == id }
    companion object {
        fun parse(json: String): ScenarioCatalog = Gson().fromJson(json, ScenarioCatalog::class.java).also {
            require(it.scenarios.map(Scenario::id).distinct().size == it.scenarios.size)
            require(it.scenarios.count { s -> s.tone == Tone.GOOD } == 15)
            require(it.scenarios.count { s -> s.tone == Tone.CAUTION } == 15)
        }
    }
}
data class SignalFrame(
    val timeMs: Long, val source: SignalSource,
    val speedKph: Double? = null, val speedLimitKph: Double? = null,
    val distanceM: Double? = null, val steeringDegrees: Float? = null,
    val brakePressed: Boolean? = null, val emergencyBraking: Boolean? = null,
    val gear: String? = null, val tunnel: Boolean? = null, val windowOpen: Boolean? = null,
    val beltBySeat: Map<String, Boolean> = emptyMap(),
    val occupiedSeats: Set<String> = emptySet(),
    val windowPositions: Map<String, Int> = emptyMap(),
    val backrestPositions: Map<String, Int> = emptyMap(),
    val handsOnWheel: Int? = null, val bothFeetOnPedals: Boolean? = null,
    val automaticTransmission: Boolean? = null,
    val recommendedCurveSpeed: Double? = null,
    val laneChange: Boolean? = null, val indicatorOn: Boolean? = null,
    val laneDeviation: Boolean? = null,
    val seatDeviationFraction: Double? = null,
    val speedTimeMs: Long? = null, val speedLimitTimeMs: Long? = null,
)
data class Candidate(val scenarioId: String, val reason: String, val confidence: Double, val source: SignalSource)
data class Counts(val good: Int, val caution: Int) {
    val total get() = good + caution
    val goodRate: Int? get() = if (total == 0) null else (100.0 * good / total).toInt()
}
object Personas {
    val names = linkedMapOf("friend" to "경상도 친구", "grandma" to "욕쟁이 할머니", "mom" to "걱정 많은 엄마", "sibling" to "5살 동생")
    fun valid(id: String) = id in names
}
