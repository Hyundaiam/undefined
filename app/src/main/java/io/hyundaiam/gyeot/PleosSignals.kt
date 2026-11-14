package io.hyundaiam.gyeot

import android.content.Context
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.capability.AreaBounds
import ai.pleos.playground.vehicle.api.model.area.*
import ai.pleos.playground.vehicle.listener.*
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.hyundaiam.gyeot.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs

/** Read-only integration with published Pleos artifacts. Unsupported signals remain null. */
class PleosSignals(private val app: GyeotApplication, private val scope: CoroutineScope, driver: DriverEntity, private val locationAllowed: Boolean) {
    val frames = MutableStateFlow(SignalFrame(System.currentTimeMillis(), SignalSource.PLEOS))
    private val connected = ConcurrentHashMap.newKeySet<String>()
    private val cleanups = CopyOnWriteArrayList<() -> Unit>()
    private var vehicle: Vehicle? = null
    private var navi: NaviHelper? = null
    private var fused: FusedLocation? = null
    private var car: Any? = null
    private var properties: Any? = null
    private var limitTime = 0L
    private val gson = Gson()
    private val mapType = object : TypeToken<Map<String, Int>>() {}.type
    private val seatBaseline: Map<String, Int> = gson.fromJson(driver.seatBaseline, mapType)
    private val closedWindows: Map<String, Int> = gson.fromJson(driver.closedWindows, mapType)
    private val seatRanges = ConcurrentHashMap<String, AreaBounds<Int>>()
    private fun error(key: String, e: Throwable) { app.capability(key, "미연결 · ${e.javaClass.simpleName}") }
    private fun update(block: (SignalFrame) -> SignalFrame) { frames.update { block(it).copy(timeMs = System.currentTimeMillis()) } }
    private fun readable(type: AccessType) = type == AccessType.READ || type == AccessType.READ_WRITE
    private fun seatKey(a: SeatArea) = a.values.joinToString("+") { it.name }
    private fun windowKey(a: WindowArea) = a.values.joinToString("+") { it.name }
    private fun deviation(values: Map<String, Int>): Double? {
        if (seatBaseline.isEmpty() || !seatBaseline.keys.all { it in values && seatRanges.containsKey(it) }) return null
        return seatBaseline.mapNotNull { (key, baseline) ->
            val bounds = seatRanges.getValue(key)
            if (bounds.maxValue > bounds.minValue) abs(values.getValue(key) - baseline).toDouble() / (bounds.maxValue - bounds.minValue) else null
        }.maxOrNull()
    }

    fun start() {
        app.capability("실내 손·발 위치", "미제공 · 추가 실내 센서 필요")
        app.capability("앞차 거리·주변 차종", "미제공 · ADAS 입력 필요")
        app.capability("터널·보행자 인지", "미제공 · 검증된 도로/외부 인지 입력 필요")
        runCatching { Vehicle(app).also { vehicle = it; it.initialize() } }.onFailure { error("Vehicle SDK", it) }
        val listener = object : NaviHelperEventListener {
            override fun onCurrentRoadSpeedLimit(speedLimit: Int) {
                limitTime = System.currentTimeMillis()
                update { it.copy(speedLimitKph = speedLimit.takeIf { n -> n in 1..200 }?.toDouble(), speedLimitTimeMs = limitTime, speedLimitOrigin = "NAVI") }
                app.capability("도로 제한 속도", if (speedLimit > 0) "NaviHelper 연결됨" else "현재 도로 정보 없음")
            }
            override fun onRouteStarted(routeStartInfo: ai.pleos.playground.navi.data.RouteStartInfo) { app.monitor.update { it.copy(navigationActive = true) } }
            override fun onRouteEnded() { app.monitor.update { it.copy(navigationActive = false) }; update { it.copy(speedLimitKph = null) } }
            override fun onRouteCancelled() { onRouteEnded() }
        }
        runCatching {
            NaviHelper(app).also { sdk ->
                navi = sdk; sdk.addListener(listener)
                sdk.initialize(onConnected = { app.capability("NaviHelper SDK", "연결됨") }, onFailed = { error("NaviHelper SDK", it) })
                cleanups += { sdk.removeListener(listener) }
            }
        }.onFailure { error("NaviHelper SDK", it) }
        runCatching {
            val sdk = FusedLocation(app); fused = sdk
            if (sdk.isSupported() && locationAllowed) {
                sdk.initialize()
                val callback = object : FusedLocationListener {
                    override fun onChanged(locationData: LocationData?) { app.capability("FusedLocation SDK", if (locationData != null) "위치 수신 중 · 좌표 저장 안 함" else "위치 데이터 없음") }
                    override fun onError(exception: Exception) { error("FusedLocation SDK", exception) }
                }
                sdk.registerFusedLocationCallback(callback)
                cleanups += { sdk.unregisterFusedLocationCallback(callback) }
            } else app.capability("FusedLocation SDK", if (locationAllowed) "환경 미지원" else "위치 수집 꺼짐")
        }.onFailure { error("FusedLocation SDK", it) }
        scope.launch {
            while (isActive) { probe(); delay(10_000) }
        }
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                val speed = readSpeed()
                val gear = readGear()
                update { it.copy(speedKph = speed, gear = gear, speedTimeMs = if (speed != null) System.currentTimeMillis() else null, speedLimitKph = it.speedLimitKph.takeIf { System.currentTimeMillis() - limitTime < 30_000 }) }
                delay(1_000)
            }
        }
    }

    private fun probe() {
        val sdk = vehicle ?: return
        runCatching {
            val seat = sdk.getSeat()
            seat.checkSeatOccupancyCapabilities(onComplete = { config, areas ->
                if (readable(config.accessType)) areas.forEach { area ->
                    val key = seatKey(area)
                    if (connected.add("occupancy:$key")) {
                        val callback = object : SeatOccupancyListener {
                            override fun onSeatOccupancyUpdated(value: Boolean?) { update { it.copy(occupiedSeats = if (value == true) it.occupiedSeats + key else it.occupiedSeats - key) } }
                            override fun onFailed(exception: Exception) { update { it.copy(occupiedSeats = it.occupiedSeats - key) }; error("착석 $key", exception) }
                        }
                        seat.registerSeatOccupancy(area, callback); seat.getCurrentSeatOccupancy(area, callback::onSeatOccupancyUpdated) { callback.onFailed(it) }
                        cleanups += { seat.unregisterSeatOccupancy(area, callback) }
                    }
                }
                app.capability("좌석 착석", if (areas.isEmpty()) "지원 좌석 없음" else "${areas.size}개 좌석 지원")
            }, onFailed = { error("좌석 착석", it) })
            seat.checkSeatBeltBuckledCapabilities(onComplete = { config, areas ->
                if (readable(config.accessType)) areas.forEach { area ->
                    val key = seatKey(area)
                    if (connected.add("belt:$key")) {
                        val callback = object : SeatBeltBuckledStateListener {
                            override fun onSeatBeltBuckledStateUpdated(value: Boolean?) { update { it.copy(beltBySeat = if (value == null) it.beltBySeat - key else it.beltBySeat + (key to value)) } }
                            override fun onFailed(exception: Exception) { update { it.copy(beltBySeat = it.beltBySeat - key) }; error("안전벨트 $key", exception) }
                        }
                        seat.registerSeatBeltBuckledState(area, callback); seat.getCurrentSeatBeltBuckledState(area, callback::onSeatBeltBuckledStateUpdated) { callback.onFailed(it) }
                        cleanups += { seat.unregisterSeatBeltBuckledState(area, callback) }
                    }
                }
                app.capability("안전벨트", if (areas.isEmpty()) "지원 좌석 없음" else "${areas.size}개 좌석 지원")
            }, onFailed = { error("안전벨트", it) })
            seat.checkSeatBackrestAnglePositionCapabilities(onComplete = { config, areas ->
                if (readable(config.accessType)) areas.forEach { (area, range) ->
                    val key = seatKey(area); seatRanges[key] = range
                    if (connected.add("seat:$key")) {
                        val callback = object : SeatBackrestAnglePositionListener {
                            override fun onSeatBackrestAnglePositionUpdated(value: Int?) {
                                update { f ->
                                    val values = if (value == null) f.backrestPositions - key else f.backrestPositions + (key to value)
                                    f.copy(backrestPositions = values, seatDeviationFraction = deviation(values))
                                }
                            }
                            override fun onFailed(exception: Exception) { update { it.copy(backrestPositions = it.backrestPositions - key, seatDeviationFraction = null) }; error("등받이 $key", exception) }
                        }
                        seat.registerSeatBackrestAnglePosition(area, callback); seat.getCurrentSeatBackrestAnglePosition(area, callback::onSeatBackrestAnglePositionUpdated) { callback.onFailed(it) }
                        cleanups += { seat.unregisterSeatBackrestAnglePosition(area, callback) }
                    }
                }
                app.capability("좌석 등받이", if (areas.isEmpty()) "지원 좌석 없음" else "${areas.size}개 좌석 · 기준 자세 등록 후 비교")
            }, onFailed = { error("좌석 등받이", it) })
            val window = sdk.getWindow()
            window.checkWindowPositionCapabilities(onComplete = { config, areas ->
                if (readable(config.accessType)) areas.keys.forEach { area ->
                    val key = windowKey(area)
                    if (connected.add("window:$key")) {
                        val callback = object : WindowPositionListener {
                            override fun onWindowPositionUpdated(value: Int?) {
                                update { f ->
                                    val values = if (value == null) f.windowPositions - key else f.windowPositions + (key to value)
                                    val calibrated = closedWindows.isNotEmpty() && closedWindows.keys.all { it in values }
                                    f.copy(windowPositions = values, windowOpen = if (calibrated) closedWindows.any { (k, v) -> values[k] != v } else null)
                                }
                            }
                            override fun onFailed(exception: Exception) { update { it.copy(windowPositions = it.windowPositions - key, windowOpen = null) }; error("창문 $key", exception) }
                        }
                        window.registerWindowPosition(area, callback); window.getCurrentWindowPosition(area, callback::onWindowPositionUpdated) { callback.onFailed(it) }
                        cleanups += { window.unregisterWindowPosition(area, callback) }
                    }
                }
                app.capability("창문 위치", if (areas.isEmpty()) "지원 창문 없음" else "${areas.size}개 지원 · 닫힘 기준 등록 필요")
            }, onFailed = { error("창문 위치", it) })
            val steering = sdk.getSteeringWheel()
            steering.checkSteeringWheelAngleCapabilities(onComplete = { config, _ ->
                if (readable(config.accessType) && connected.add("steering")) {
                    val callback = object : SteeringWheelAngleListener {
                        override fun onSteeringWheelAngleUpdated(value: Float?) { update { it.copy(steeringDegrees = value?.takeIf(Float::isFinite)) } }
                        override fun onFailed(exception: Exception) { update { it.copy(steeringDegrees = null) }; error("조향각", exception) }
                    }
                    steering.registerSteeringWheelAngle(callback); steering.getCurrentSteeringWheelAngle(callback::onSteeringWheelAngleUpdated) { callback.onFailed(it) }
                    cleanups += { steering.unregisterSteeringWheelAngle(callback) }
                    app.capability("조향각", "지원 · 손 위치 인지는 별도 센서 필요")
                }
            }, onFailed = { error("조향각", it) })
            val brake = sdk.getBrake()
            brake.checkBrakePedalStatusCapabilities(onComplete = { config ->
                if (readable(config.accessType) && connected.add("brake")) {
                    val callback = object : BrakePedalStatusListener {
                        override fun onBrakePedalStatusUpdated(value: Boolean?) { update { it.copy(brakePressed = value) } }
                        override fun onFailed(exception: Exception) { update { it.copy(brakePressed = null) }; error("브레이크", exception) }
                    }
                    brake.registerBrakePedalStatus(callback); brake.getCurrentBrakePedalStatus(callback::onBrakePedalStatusUpdated) { callback.onFailed(it) }
                    cleanups += { brake.unregisterBrakePedalStatus(callback) }
                    app.capability("브레이크", "지원 · 발 구분 정보 없음")
                }
            }, onFailed = { error("브레이크", it) })
            val signal = sdk.getTurnSignal()
            signal.checkTurnSignalStateCapabilities(onComplete = { config ->
                if (readable(config.accessType) && connected.add("indicator")) {
                    val callback = object : TurnSignalStateListener {
                        override fun onTurnSignalStateUpdated(value: ai.pleos.playground.vehicle.constant.area.TurnSignalValue?) { update { it.copy(indicatorOn = value?.let { v -> v != ai.pleos.playground.vehicle.constant.area.TurnSignalValue.OFF }) } }
                        override fun onFailed(exception: Exception) { update { it.copy(indicatorOn = null) }; error("방향지시등", exception) }
                    }
                    signal.registerTurnSignalState(callback); signal.getCurrentTurnSignalState(callback::onTurnSignalStateUpdated) { callback.onFailed(it) }
                    cleanups += { signal.unregisterTurnSignalState(callback) }
                    app.capability("방향지시등", "지원 · 차로 변경 인지는 별도 입력 필요")
                }
            }, onFailed = { error("방향지시등", it) })
        }.onFailure { error("Vehicle SDK", it) }
    }

    /** Optional, public Android Automotive Car API; no hidden or vendor property IDs. */
    private fun readSpeed(): Double? = runCatching {
        val manager = propertyManager()
        val id = propertyId("PERF_VEHICLE_SPEED")
        check(manager.javaClass.getMethod("getCarPropertyConfig", Int::class.javaPrimitiveType).invoke(manager, id) != null)
        val value = (manager.javaClass.getMethod("getFloatProperty", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType).invoke(manager, id, 0) as Float)
        check(value.isFinite())
        app.capability("차량 속도 (AAOS)", "차량 속도 수신 중")
        abs(value.toDouble()) * 3.6
    }.onFailure { error("차량 속도 (AAOS)", it) }.getOrNull()

    private fun readGear(): String? = runCatching {
        val manager = propertyManager()
        val id = propertyId("GEAR_SELECTION")
        check(manager.javaClass.getMethod("getCarPropertyConfig", Int::class.javaPrimitiveType).invoke(manager, id) != null)
        val value = manager.javaClass.getMethod("getIntProperty", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType).invoke(manager, id, 0) as Int
        val type = Class.forName("android.car.VehicleGear")
        val name = type.fields.firstOrNull { it.name.startsWith("GEAR_") && it.type == Int::class.javaPrimitiveType && it.getInt(null) == value }?.name
        app.capability("기어 (AAOS)", if (name == null) "현재 값 없음" else "수신 중 · $name")
        name?.takeUnless { it == "GEAR_UNKNOWN" }
    }.onFailure { error("기어 (AAOS)", it) }.getOrNull()

    private fun propertyId(name: String) = Class.forName("android.car.VehiclePropertyIds").getField(name).getInt(null)
    private fun propertyManager(): Any {
        if (properties == null) {
            val type = Class.forName("android.car.Car")
            car = type.getMethod("createCar", Context::class.java).invoke(null, app)
            properties = type.getMethod("getCarManager", String::class.java).invoke(car, type.getField("PROPERTY_SERVICE").get(null))
        }
        return requireNotNull(properties)
    }

    fun close() {
        cleanups.reversed().forEach { runCatching { it() } }; cleanups.clear()
        runCatching { navi?.release() }; runCatching { fused?.release() }; runCatching { vehicle?.release() }
        runCatching { car?.javaClass?.getMethod("disconnect")?.invoke(car) }
    }
}
