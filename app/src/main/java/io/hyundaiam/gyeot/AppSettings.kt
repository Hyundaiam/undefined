package io.hyundaiam.gyeot

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore("gyeot_settings")
data class Preferences(val driverId: Long = 1, val demo: Boolean = true, val voice: Boolean = true, val praise: Boolean = true, val gleoEnabled: Boolean = false, val gleoPackage: String = "")
class AppSettings(private val context: Context) {
    private val driver = longPreferencesKey("driver")
    private val demo = booleanPreferencesKey("demo")
    private val voice = booleanPreferencesKey("voice")
    private val praise = booleanPreferencesKey("praise")
    private val gleo = booleanPreferencesKey("gleo")
    private val gleoPackage = stringPreferencesKey("gleo_package")
    val flow = context.store.data.map { p -> Preferences(p[driver] ?: 1, p[demo] ?: true, p[voice] ?: true, p[praise] ?: true, p[gleo] ?: false, p[gleoPackage] ?: "") }
    suspend fun driver(id: Long) { context.store.edit { it[driver] = id } }
    suspend fun demo(enabled: Boolean) { context.store.edit { it[demo] = enabled } }
    suspend fun voice(enabled: Boolean) { context.store.edit { it[voice] = enabled } }
    suspend fun praise(enabled: Boolean) { context.store.edit { it[praise] = enabled } }
    suspend fun gleo(enabled: Boolean, target: String) { context.store.edit { it[gleo] = enabled; it[gleoPackage] = target.trim() } }
}
