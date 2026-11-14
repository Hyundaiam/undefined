package io.hyundaiam.gyeot

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map
import io.hyundaiam.gyeot.core.SignalSource

private val Context.store by preferencesDataStore("gyeot_settings")
data class Preferences(val driverId: Long = 1, val demo: Boolean = false, val voice: Boolean = true, val praise: Boolean = true, val gleoEnabled: Boolean = false, val gleoPackage: String = "", val testRoadLimit: Int = 0) {
    val source get() = when { demo -> SignalSource.DEMO; testRoadLimit > 0 && TestEnvironment.available -> SignalSource.EMULATOR; else -> SignalSource.PLEOS }
    val modeLabel get() = when(source) { SignalSource.DEMO -> "상황 시연"; SignalSource.EMULATOR -> "시험도로 ${testRoadLimit} · 에뮬레이터"; SignalSource.PLEOS -> "차량 · 내비게이션" }
}
class AppSettings(private val context: Context) {
    private val driver = longPreferencesKey("driver")
    private val demo = booleanPreferencesKey("demo")
    private val voice = booleanPreferencesKey("voice")
    private val praise = booleanPreferencesKey("praise")
    private val gleo = booleanPreferencesKey("gleo")
    private val gleoPackage = stringPreferencesKey("gleo_package")
    private val testLimit = intPreferencesKey("test_road_limit")
    val flow = context.store.data.map { p -> Preferences(p[driver] ?: 1, p[demo] ?: false, p[voice] ?: true, p[praise] ?: true, p[gleo] ?: false, p[gleoPackage] ?: "", p[testLimit] ?: 0) }
    suspend fun driver(id: Long) { context.store.edit { it[driver] = id } }
    suspend fun demo(enabled: Boolean) { context.store.edit { it[demo] = enabled; if (enabled) it[testLimit] = 0 } }
    suspend fun testRoad(limit: Int) { require(limit == 0 || (TestEnvironment.available && limit in 10..130)); context.store.edit { it[testLimit] = limit; it[demo] = false } }
    suspend fun voice(enabled: Boolean) { context.store.edit { it[voice] = enabled } }
    suspend fun praise(enabled: Boolean) { context.store.edit { it[praise] = enabled } }
    suspend fun gleo(enabled: Boolean, target: String) { context.store.edit { it[gleo] = enabled; it[gleoPackage] = target.trim() } }
}
