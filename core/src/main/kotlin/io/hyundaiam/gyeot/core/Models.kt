package io.hyundaiam.gyeot.core

import com.google.gson.Gson

enum class SignalSource { PLEOS, DEMO, EMULATOR, WEB_SIM }
enum class Tone { GOOD, CAUTION, COMMON }
data class Scenario(
    val id: String, val title: String, val category: String, val tone: Tone,
    val trigger: String, val recordingText: String, val requiredSignals: List<String>,
    val legacyClip: String? = null,
    val simulationSupport: String? = null, val simulationSteps: String? = null,
)
data class ScenarioCatalog(val scenarios: List<Scenario>) {
    fun find(id: String) = scenarios.firstOrNull { it.id == id }
    companion object {
        fun parse(json: String): ScenarioCatalog = Gson().fromJson(json, ScenarioCatalog::class.java).also {
            require(it.scenarios.map(Scenario::id).distinct().size == it.scenarios.size)
            require(it.scenarios.count { s -> s.tone == Tone.GOOD } == 25)
            require(it.scenarios.count { s -> s.tone == Tone.CAUTION } == 25)
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
    val speedLimitOrigin: String? = null,
    val wetRoad: Boolean? = null, val weatherRecommendedSpeed: Double? = null,
    val pedestrianAhead: Boolean? = null, val crosswalkAhead: Boolean? = null, val pedestrianYielded: Boolean? = null,
    val ignitionOn: Boolean? = null, val parkingBrakeOn: Boolean? = null, val driverLeaving: Boolean? = null,
    val schoolZone: Boolean? = null, val redLight: Boolean? = null, val stopLineDistanceM: Double? = null,
    val night: Boolean? = null, val emergencyVehicle: Boolean? = null, val emergencyYielded: Boolean? = null,
    val blindSpotOccupied: Boolean? = null, val drowsy: Boolean? = null, val restStop: Boolean? = null,
    val doorsOpen: Boolean? = null, val tirePressureLow: Boolean? = null,
    val remainingRangeKm: Double? = null, val routeRemainingKm: Double? = null, val mergeZone: Boolean? = null,
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
