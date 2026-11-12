package io.hyundaiam.gyeot

import android.app.Application
import androidx.room.Room
import io.hyundaiam.gyeot.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

data class MonitorUi(
    val running: Boolean = false, val driverId: Long? = null, val demo: Boolean = true,
    val tripId: Long? = null, val startedAt: Long? = null, val frame: SignalFrame? = null,
    val lastScenarioId: String? = null, val status: String = "다음 주행을 기다리고 있어요",
    val capabilities: Map<String, String> = emptyMap(),
    val navigationActive: Boolean = false,
)
class GyeotApplication : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val database by lazy { Room.databaseBuilder(this, GyeotDatabase::class.java, "gyeot.db").build() }
    val dao get() = database.dao()
    val settings by lazy { AppSettings(this) }
    val catalog by lazy { ScenarioCatalog.parse(assets.open("scenarios.json").bufferedReader().use { it.readText() }) }
    val monitor = MutableStateFlow(MonitorUi())
    val voicePlayer by lazy { ClipPlayer(this) }
    lateinit var ready: Deferred<Unit>
    override fun onCreate() {
        super.onCreate()
        ready = scope.async(Dispatchers.IO) {
            if (dao.allDrivers().isEmpty()) dao.addDriver(DriverEntity(name = "운전자 1"))
            dao.recoverTrips()
        }
    }
    fun capability(key: String, status: String) { monitor.update { it.copy(capabilities = it.capabilities + (key to status)) } }
}
