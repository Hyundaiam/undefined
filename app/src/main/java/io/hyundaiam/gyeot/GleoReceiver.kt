package io.hyundaiam.gyeot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

object GleoProtocol {
    val actions = setOf("GET_DRIVING_CONTEXT", "GET_DRIVING_PROGRESS", "GET_RECORDING_STATUS")
    const val RESPONSE = "ai.pleos.playground.intent.action.APP_ACTION_RESPONSE"
    fun parameters(raw: String?): JSONObject {
        if (raw.isNullOrBlank()) return JSONObject()
        require(raw.length <= 8192) { "Action parameters are too large." }
        val value = JSONTokener(raw).nextValue()
        return when (value) { is JSONObject -> value; is String -> JSONObject(value); else -> throw IllegalArgumentException("Expected an object.") }
    }
    fun callback(action: String, status: String, message: String, data: JSONArray? = null): JSONObject {
        require(action in actions && status in setOf("success", "asking", "fail"))
        return JSONObject().put("functionName", action).put("status", status).put("message", message).put("mode", "voice").apply { if (data != null) put("data", data.toString()) }
    }
    fun validPackage(value: String) = value.length in 3..200 && Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+").matches(value) && value != "io.hyundaiam.gyeot"
}

/** Documented service-component broadcasts. Responses go only to the configured Gleo package. */
class GleoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action?.takeIf { it in GleoProtocol.actions } ?: return
        val result = goAsync()
        val app = context.applicationContext as GyeotApplication
        app.scope.launch(Dispatchers.IO) {
            try {
                withTimeout(6_000) {
                    app.ready.await()
                    val prefs = app.settings.flow.first()
                    // Never broadcast a driver's data to arbitrary apps or an unconfigured target.
                    if (!prefs.gleoEnabled || !GleoProtocol.validPackage(prefs.gleoPackage)) return@withTimeout
                    val response = runCatching {
                        GleoProtocol.parameters(intent.getStringExtra("ACTION_PARAM"))
                        val source = prefs.source.name
                        val summary = app.dao.habitSummary(prefs.driverId, source)
                        val payload = JSONObject().put("source", source).put("simulated", prefs.source != io.hyundaiam.gyeot.core.SignalSource.PLEOS)
                        when (action) {
                            "GET_DRIVING_CONTEXT" -> {
                                val state = app.monitor.value
                                val frame = state.frame?.takeIf { state.running && state.driverId == prefs.driverId && it.source.name == source }
                                payload.put("monitoring", frame != null).put("speedKph", frame?.speedKph ?: JSONObject.NULL)
                                    .put("roadSpeedLimitKph", frame?.speedLimitKph ?: JSONObject.NULL)
                                    .put("observedAt", frame?.timeMs ?: JSONObject.NULL)
                                    .put("candidateEngine", "LOCAL_RULES_NOT_GLEO")
                                    .put("recentCandidates", JSONArray().apply {
                                        app.dao.recentEvents(prefs.driverId, source).take(5).forEach { e -> put(JSONObject().put("scenarioId", e.scenarioId).put("tone", e.tone).put("evidence", e.reason).put("confidence", e.confidence)) }
                                    })
                            }
                            "GET_DRIVING_PROGRESS" -> {
                                payload.put("goodEvents", summary.sumOf { it.good }).put("cautionEvents", summary.sumOf { it.caution })
                                    .put("habits", JSONArray().apply { summary.forEach { h -> put(JSONObject().put("category", h.category).put("good", h.good).put("caution", h.caution)) } })
                            }
                            "GET_RECORDING_STATUS" -> {
                                val clips = app.dao.clips().first()
                                payload.put("requiredDrivingSlotsPerPersona", 30).put("commonSlotsPerPersona", 8)
                                    .put("personalRecordings", clips.size).put("bundledHumanRecordings", app.voicePlayer.packagedCount()).put("temporaryVoicesAvailable", true)
                            }
                        }
                        GleoProtocol.callback(action, "success", "The requested locally stored driving information is available.", JSONArray().put(payload))
                    }.getOrElse { GleoProtocol.callback(action, "fail", "The driving request could not be processed.") }
                    context.sendBroadcast(Intent(GleoProtocol.RESPONSE).setPackage(prefs.gleoPackage).putExtra("packageName", context.packageName).putExtra("response", response.toString()))
                }
            } finally { result.finish() }
        }
    }
}
