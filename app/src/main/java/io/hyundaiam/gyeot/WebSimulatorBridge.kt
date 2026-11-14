package io.hyundaiam.gyeot

import android.content.Intent
import com.google.gson.Gson
import com.google.gson.JsonParser
import io.hyundaiam.gyeot.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

/** Debug emulator only. ADB forwards localhost:8090 to the emulator's loopback port. */
class WebSimulatorBridge(private val app: GyeotApplication) {
    val frame = MutableStateFlow<SignalFrame?>(null)
    private val gson = Gson()
    private var received = 0L
    private var server: ServerSocket? = null
    fun start() {
        if(!TestEnvironment.available) return
        app.scope.launch(Dispatchers.IO) {
            runCatching {
                server = ServerSocket(8765, 16, InetAddress.getByName("127.0.0.1"))
                while(isActive) {
                    val socket = requireNotNull(server).accept()
                    launch { socket.use { runCatching { handle(it) } } }
                }
            }.onFailure { app.capability("웹 시뮬레이터", "연결 대기 · ${it.javaClass.simpleName}") }
        }
    }
    private fun line(input: InputStream): String {
        val bytes = ByteArrayOutputStream()
        while(true) {
            val ch = input.read()
            if(ch == -1 || ch == 10) break
            if(ch != 13) bytes.write(ch)
            require(bytes.size() < 4096)
        }
        return bytes.toString("US-ASCII")
    }
    private suspend fun handle(socket: Socket) {
        socket.soTimeout = 3000
        val input = socket.getInputStream().buffered()
        val first = line(input).split(' ')
        if(first.size != 3) return
        val method = first[0]; val path = first[1].substringBefore('?')
        val headers = mutableMapOf<String,String>()
        var count = 0
        while(true) {
            val h = line(input)
            if(h.isEmpty()) break
            require(++count <= 32)
            val key = h.substringBefore(':').lowercase()
            headers[key] = h.substringAfter(':').trim()
        }
        val host = headers["host"]
        if(host !in setOf("127.0.0.1:8090", "localhost:8090", "127.0.0.1:8765") || headers["origin"]?.let { it != "http://$host" } == true || headers["sec-fetch-site"] == "cross-site") {
            respond(socket,403,"application/json", "{\"error\":\"Local simulator only\"}".toByteArray()); return
        }
        val static = mapOf("/" to ("index.html" to "text/html; charset=utf-8"), "/simulator.js" to ("simulator.js" to "text/javascript; charset=utf-8"), "/styles.css" to ("styles.css" to "text/css; charset=utf-8"))
        if(method == "GET" && path in static) {
            val (file,type) = static.getValue(path)
            respond(socket,200,type,app.assets.open("web-simulator/$file").use { it.readBytes() }); return
        }
        if(method == "GET" && path == "/api/status") {
            app.ready.await()
            val prefs = app.settings.flow.first()
            val m = app.monitor.value; val voice = app.voicePlayer.state.value
            val driver = app.dao.driver(m.driverId ?: prefs.driverId)
            val last = app.catalog.find(m.lastScenarioId ?: "")
            val data = mapOf(
                "version" to BuildConfig.VERSION_NAME,
                "clock" to System.currentTimeMillis(),
                "connected" to (m.running && m.source == SignalSource.WEB_SIM),
                "receiving" to (frame.value?.timeMs?.let { System.currentTimeMillis()-it in 0..1600 } == true),
                "acceptedFrames" to received, "source" to m.source.name, "frame" to m.frame,
                "persona" to (driver?.persona ?: "friend"), "voiceEnabled" to prefs.voice,
                "feedback" to mapOf("title" to last?.title,"tone" to last?.tone?.name,"text" to m.feedback,"at" to m.feedbackAt),
                "audio" to mapOf("phase" to voice.phase.name,"scenarioId" to voice.scenarioId,"title" to app.catalog.find(voice.scenarioId ?: "")?.title,"text" to app.catalog.find(voice.scenarioId ?: "")?.let { app.voicePlayer.feedbackText(voice.persona ?: "friend",it) },"renderedCount" to voice.renderedCount,"lastRenderedScenario" to voice.lastRenderedScenario,"message" to app.voicePlayer.message.value),
                "history" to app.dao.recentEvents(driver?.id ?: prefs.driverId,"WEB_SIM").filter { it.tripId == m.tripId }.take(6).map { e -> mapOf("title" to app.catalog.find(e.scenarioId)?.title,"tone" to e.tone,"at" to e.observedAt) }
            )
            respond(socket,200,"application/json; charset=utf-8",gson.toJson(data).toByteArray()); return
        }
        if(method != "POST" || !path.startsWith("/api/")) { respond(socket,404,"application/json","{}".toByteArray()); return }
        if(headers["content-type"]?.substringBefore(';') != "application/json") { respond(socket,403,"application/json","{}".toByteArray()); return }
        val length = headers["content-length"]?.toIntOrNull()
        if(length == null || length !in 2..8192 || headers.containsKey("transfer-encoding")) { respond(socket,400,"application/json","{}".toByteArray()); return }
        val bytes = ByteArray(length); var offset = 0
        while(offset < length) { val n = input.read(bytes,offset,length-offset); require(n > 0); offset += n }
        val error = runCatching {
            when(path) {
                "/api/frame" -> {
                    require(app.monitor.value.running && app.monitor.value.source == SignalSource.WEB_SIM) { "웹 가상주행 모드가 필요해요" }
                    frame.value = WebSensorProtocol.parse(bytes.toString(Charsets.UTF_8),System.currentTimeMillis()); received++
                }
                "/api/settings" -> {
                    val obj = JsonParser.parseString(bytes.toString(Charsets.UTF_8)).asJsonObject
                    require(obj.keySet().all { it in setOf("persona","voice") })
                    app.ready.await()
                    obj["persona"]?.let { require(it.isJsonPrimitive && it.asJsonPrimitive.isString && Personas.valid(it.asString)); app.dao.setPersona(app.settings.flow.first().driverId,it.asString) }
                    obj["voice"]?.let { require(it.isJsonPrimitive && it.asJsonPrimitive.isBoolean); app.settings.voice(it.asBoolean); if(!it.asBoolean) withContext(Dispatchers.Main) { app.voicePlayer.stop() } }
                }
                "/api/reset" -> {
                    require(app.monitor.value.running && app.monitor.value.source == SignalSource.WEB_SIM)
                    frame.value = null
                    withContext(Dispatchers.Main) { app.startService(Intent(app,DrivingService::class.java).setAction(DrivingService.RESET_WEB)) }
                }
                else -> throw IllegalArgumentException("Unknown operation")
            }
        }.exceptionOrNull()
        val body = if(error == null) mapOf("ok" to true,"clock" to System.currentTimeMillis()) else mapOf("error" to (error.message ?: "Invalid input"))
        respond(socket,if(error == null) 200 else 400,"application/json; charset=utf-8",gson.toJson(body).toByteArray())
    }
    private fun respond(socket: Socket, status: Int, type: String, body: ByteArray) {
        val reason = when(status) { 200 -> "OK"; 400 -> "Bad Request"; 403 -> "Forbidden"; else -> "Not Found" }
        val header = "HTTP/1.1 $status $reason\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nContent-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'\r\nConnection: close\r\n\r\n"
        socket.getOutputStream().apply { write(header.toByteArray(Charsets.US_ASCII)); write(body); flush() }
    }
}
