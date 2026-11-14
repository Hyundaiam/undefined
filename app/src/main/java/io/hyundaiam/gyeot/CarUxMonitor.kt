package io.hyundaiam.gyeot

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.lang.reflect.Proxy

data class UxState(val known: Boolean = false, val restricted: Boolean = true, val activeFlags: Int = 0, val maxText: Int = 80)

/** Read public AAOS UX restrictions; never infer these from gear or speed. */
class CarUxMonitor(private val context: Context) {
    val state = MutableStateFlow(UxState())
    private var car: Any? = null
    private var manager: Any? = null
    private var listener: Any? = null
    fun start(scope: CoroutineScope) {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            state.value = UxState(known = true, restricted = false); return
        }
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                runCatching {
                    if (manager == null) connect()
                    manager?.let { publish(it.javaClass.getMethod("getCurrentCarUxRestrictions").invoke(it)) }
                }.onFailure { state.value = UxState() }
                delay(1_000)
            }
        }
    }
    private fun connect() {
        val type = Class.forName("android.car.Car")
        car = type.getMethod("createCar", Context::class.java).invoke(null, context)
        val service = type.getField("CAR_UX_RESTRICTION_SERVICE").get(null) as String
        manager = type.getMethod("getCarManager", String::class.java).invoke(car, service)
        val listenerType = Class.forName("android.car.drivingstate.CarUxRestrictionsManager\$OnUxRestrictionsChangedListener")
        listener = Proxy.newProxyInstance(listenerType.classLoader, arrayOf(listenerType)) { proxy, method, args ->
            when(method.name) {
                "onUxRestrictionsChanged" -> { args?.firstOrNull()?.let(::publish); null }
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                "toString" -> "GyeotUxListener"
                else -> null
            }
        }
        manager!!.javaClass.getMethod("registerListener", listenerType).invoke(manager, listener)
    }
    private fun publish(value: Any?) {
        if (value == null) return
        val type = value.javaClass
        val restricted = type.getMethod("isRequiresDistractionOptimization").invoke(value) as Boolean
        val flags = type.getMethod("getActiveRestrictions").invoke(value) as Int
        val max = (type.getMethod("getMaxRestrictedStringLength").invoke(value) as Int).takeIf { it > 0 } ?: 80
        state.value = UxState(true, restricted, flags, max)
    }
}
