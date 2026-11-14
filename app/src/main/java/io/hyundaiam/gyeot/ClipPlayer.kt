package io.hyundaiam.gyeot

import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import io.hyundaiam.gyeot.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.UUID

enum class PlaybackResult { STARTED, MISSING, FOCUS_DENIED }
enum class VoicePhase { IDLE, PREPARING, PLAYING, COMPLETED, WAITING_FOCUS, MISSING, ERROR, MUTED }
data class VoicePlayback(val scenarioId: String? = null, val phase: VoicePhase = VoicePhase.IDLE, val renderedCount: Int = 0, val lastRenderedScenario: String? = null)

class ClipPlayer(private val app: GyeotApplication) {
    val message = MutableStateFlow("")
    val playing = MutableStateFlow<String?>(null)
    val state = MutableStateFlow(VoicePlayback())
    private var renderWatcher: Job? = null
    private val texts by lazy { JSONObject(app.assets.open("legacy_voice_texts.json").bufferedReader().use { it.readText() }) }
    private val packaged by lazy { JSONObject(app.assets.open("voices/recorded/manifest.json").bufferedReader().use { it.readText() }) }
    fun hasPackaged(persona: String, scenarioId: String): Boolean = packaged.optJSONObject(persona)?.has(scenarioId) == true
    fun packagedCount(): Int = Personas.names.keys.sumOf { packaged.optJSONObject(it)?.length() ?: 0 }
    private val audioManager = app.getSystemService(AudioManager::class.java)
    private var focus: AudioFocusRequest? = null
    private val player by lazy {
        ExoPlayer.Builder(app).build().apply {
            setAudioAttributes(androidx.media3.common.AudioAttributes.Builder().setUsage(androidx.media3.common.C.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE).setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_SPEECH).build(), false)
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) { if (playbackState == Player.STATE_ENDED) { playing.value = null; renderWatcher?.cancel(); state.update { it.copy(phase = VoicePhase.COMPLETED) }; message.value = "음성 안내 완료"; releaseFocus() } }
                override fun onPlayerError(error: PlaybackException) { message.value = "음성을 재생하지 못했어요. 파일 형식을 확인해 주세요."; playing.value = null; renderWatcher?.cancel(); state.update { it.copy(phase = VoicePhase.ERROR) }; releaseFocus() }
            })
        }
    }
    fun legacyText(persona: String, scenario: Scenario): String? = scenario.legacyClip?.let { texts.optJSONObject(persona)?.optString(it)?.takeIf(String::isNotEmpty) }
    fun report(text: String, phase: VoicePhase? = null) { message.value = text; if (phase != null) state.update { it.copy(phase = phase) } }
    suspend fun play(persona: String, scenario: Scenario, allowLegacy: Boolean = true): PlaybackResult {
        require(Personas.valid(persona))
        val custom = withContext(Dispatchers.IO) { app.dao.clip(persona, scenario.id) }
        val bundled = packaged.optJSONObject(persona)?.optString(scenario.id)?.takeIf(String::isNotEmpty)
        val uri = if (custom != null) Uri.fromFile(File(app.filesDir, "recordings/${custom.fileName}"))
            else if (bundled != null) Uri.parse("asset:///voices/recorded/$bundled")
            else scenario.legacyClip?.takeIf { allowLegacy }?.let { Uri.parse("asset:///voices/legacy/$persona-$it.mp3") }
        if (uri == null) { message.value = "${scenario.id} · 녹음 파일이 없어 화면으로 안내해요."; state.update { it.copy(scenarioId = scenario.id, phase = VoicePhase.MISSING) }; return PlaybackResult.MISSING }
        return withContext(Dispatchers.Main) {
            stop()
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE).setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setOnAudioFocusChangeListener { if (it == AudioManager.AUDIOFOCUS_LOSS || it == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT || it == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) { stop(); report("다른 음성 안내로 재생을 중지했어요.", VoicePhase.WAITING_FOCUS) } }.build()
            focus = request
            if (audioManager.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { report("다른 음성 안내가 끝나기를 기다리고 있어요.", VoicePhase.WAITING_FOCUS); releaseFocus(); return@withContext PlaybackResult.FOCUS_DENIED }
            state.update { it.copy(scenarioId = scenario.id, phase = VoicePhase.PREPARING) }
            // Pleos recommends 150–200 ms PCM pre-roll before recorded guidance.
            player.setMediaItems(listOf(MediaItem.fromUri("asset:///voices/preroll.wav"), MediaItem.fromUri(uri))); player.prepare(); player.play()
            playing.value = "${persona}_${scenario.id}"
            message.value = if (custom != null || bundled != null) "내 녹음 재생 중" else "기존 임시 음성 재생 중"
            renderWatcher = app.scope.launch {
                while (isActive) {
                    if (player.currentMediaItemIndex == 1 && player.isPlaying && player.currentPosition >= 100) {
                        state.update { it.copy(phase = VoicePhase.PLAYING, renderedCount = it.renderedCount + 1, lastRenderedScenario = scenario.id) }
                        break
                    }
                    delay(100)
                }
            }
            PlaybackResult.STARTED
        }
    }
    fun stop() { renderWatcher?.cancel(); player.stop(); playing.value = null; state.update { it.copy(phase = VoicePhase.IDLE) }; releaseFocus() }
    private fun releaseFocus() { val request = focus; focus = null; request?.let { audioManager.abandonAudioFocusRequest(it) } }

    suspend fun importClip(uri: Uri, persona: String, scenarioId: String): String = withContext(Dispatchers.IO) {
        require(Personas.valid(persona) && app.catalog.find(scenarioId) != null)
        val directory = File(app.filesDir, "recordings").apply { mkdirs() }
        val target = File(directory, "${UUID.randomUUID()}.audio")
        try {
            app.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "파일을 열 수 없어요." }
                target.outputStream().use { output ->
                    val buffer = ByteArray(8192); var total = 0L
                    while (true) { val size = input.read(buffer); if (size < 0) break; total += size; require(total <= 10 * 1024 * 1024) { "10 MB 이하의 파일을 선택해 주세요." }; output.write(buffer, 0, size) }
                }
            }
            val retriever = MediaMetadataRetriever()
            val duration = try { retriever.setDataSource(target.absolutePath); retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0 } finally { retriever.release() }
            require(duration in 500..30_000) { "0.5초 이상 30초 이하의 음성 파일을 선택해 주세요." }
            app.dao.putClip(ClipEntity(persona, scenarioId, target.name, duration, System.currentTimeMillis()))
            // Previous files are retained so replacing a recording does not destroy it.
            "$scenarioId · 내 목소리를 등록했어요."
        } catch (e: Exception) { target.delete(); throw e }
    }
}
