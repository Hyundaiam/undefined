package io.hyundaiam.gyeot.core

import com.google.gson.Gson
import com.google.gson.JsonParser

/** Only sensor values cross the web bridge. Clocks, source and verdicts belong to the app. */
object WebSensorProtocol {
    private val numbers = setOf("speedKph", "speedLimitKph", "distanceM", "steeringDegrees", "handsOnWheel", "recommendedCurveSpeed", "seatDeviationFraction", "weatherRecommendedSpeed", "stopLineDistanceM", "remainingRangeKm", "routeRemainingKm")
    private val booleans = setOf("brakePressed", "emergencyBraking", "tunnel", "windowOpen", "bothFeetOnPedals", "automaticTransmission", "laneChange", "indicatorOn", "laneDeviation", "wetRoad", "pedestrianAhead", "crosswalkAhead", "pedestrianYielded", "ignitionOn", "parkingBrakeOn", "driverLeaving", "schoolZone", "redLight", "night", "emergencyVehicle", "emergencyYielded", "blindSpotOccupied", "drowsy", "restStop", "doorsOpen", "tirePressureLow", "mergeZone")
    private val maps = setOf("beltBySeat", "windowPositions", "backrestPositions")
    fun parse(json: String, now: Long): SignalFrame {
        require(json.toByteArray().size <= 8192) { "Sensor frame too large" }
        val root = JsonParser.parseString(json)
        require(root.isJsonObject) { "Expected sensor object" }
        val obj = root.asJsonObject
        require(obj.keySet().all { it in numbers || it in booleans || it in maps || it in setOf("occupiedSeats","gear") }) { "Only raw sensor fields are accepted" }
        for ((key, value) in obj.entrySet()) {
            if (value.isJsonNull) continue
            when(key) {
                in numbers -> require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber && value.asDouble.isFinite()) { "Invalid number: $key" }
                in booleans -> require(value.isJsonPrimitive && value.asJsonPrimitive.isBoolean) { "Invalid boolean: $key" }
                in maps -> {
                    require(value.isJsonObject && value.asJsonObject.size() <= 8)
                    value.asJsonObject.entrySet().forEach { (seat, v) ->
                        require(seat.length <= 32)
                        require(v.isJsonPrimitive && if(key == "beltBySeat") v.asJsonPrimitive.isBoolean else v.asJsonPrimitive.isNumber && v.asDouble.isFinite() && v.asDouble % 1 == 0.0)
                    }
                }
                "occupiedSeats" -> require(value.isJsonArray && value.asJsonArray.size() <= 8 && value.asJsonArray.all { it.isJsonPrimitive && it.asJsonPrimitive.isString && it.asString.length <= 32 })
                "gear" -> require(value.isJsonPrimitive && value.asJsonPrimitive.isString && value.asString in setOf("GEAR_PARK","GEAR_DRIVE","GEAR_NEUTRAL","GEAR_REVERSE"))
            }
        }
        fun bounded(key: String, min: Double, max: Double) { obj[key]?.takeUnless { it.isJsonNull }?.let { require(it.asDouble in min..max) { "Out of range: $key" } } }
        bounded("speedKph", 0.0, 300.0); bounded("speedLimitKph", 1.0, 200.0); bounded("distanceM", 0.0, 5000.0)
        bounded("handsOnWheel", 0.0, 2.0); bounded("seatDeviationFraction", 0.0, 1.0)
        bounded("recommendedCurveSpeed", 1.0, 200.0); bounded("weatherRecommendedSpeed", 1.0, 200.0)
        bounded("remainingRangeKm", 0.0, 1500.0); bounded("routeRemainingKm", .01, 1500.0)
        maps.forEach { if(!obj.has(it) || obj[it].isJsonNull) obj.add(it, com.google.gson.JsonObject()) }
        if(!obj.has("occupiedSeats") || obj["occupiedSeats"].isJsonNull) obj.add("occupiedSeats", com.google.gson.JsonArray())
        obj.addProperty("timeMs", now); obj.addProperty("source", "WEB_SIM")
        if(obj["speedKph"]?.isJsonNull == false) obj.addProperty("speedTimeMs", now)
        if(obj["speedLimitKph"]?.isJsonNull == false) obj.addProperty("speedLimitTimeMs", now)
        obj.addProperty("speedLimitOrigin", "WEB_SIMULATOR")
        return Gson().fromJson(obj, SignalFrame::class.java)
    }
}
