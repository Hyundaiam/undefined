package io.hyundaiam.gyeot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.hyundaiam.gyeot.core.Personas
import kotlinx.coroutines.launch

/** Only voice preferences are exposed from the companion's single settings button. */
class VoiceSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK
        setContent { VoiceSettings(this, application as GyeotApplication) }
    }
}

@Composable
private fun VoiceSettings(activity: VoiceSettingsActivity, app: GyeotApplication) {
    val prefs by app.settings.flow.collectAsStateWithLifecycle(Preferences())
    val drivers by remember { app.dao.drivers() }.collectAsStateWithLifecycle(emptyList())
    val voice by app.voicePlayer.state.collectAsStateWithLifecycle()
    val ux by app.ux.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val driver = drivers.firstOrNull { it.id == prefs.driverId } ?: drivers.firstOrNull()
    val editable = ux.known && !ux.restricted
    val descriptions = mapOf("friend" to "10년지기처럼 편한 경상도 친구", "grandma" to "투박한 말투 속 다정한 잔소리", "mom" to "언제나 내 걱정부터 하는 엄마", "sibling" to "옆자리에 앉은 다섯 살 동생")
    CompanionTheme {
        Surface(Modifier.fillMaxSize(), color = CompanionBlack) {
            Column(Modifier.fillMaxSize().padding(horizontal = 40.dp).verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
                Row(Modifier.fillMaxWidth().height(100.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = activity::finish, modifier = Modifier.size(64.dp)) { Icon(Icons.Outlined.ArrowBack, "돌아가기", Modifier.size(26.dp)) }
                    Spacer(Modifier.width(16.dp)); Text("음성 설정", fontSize = 29.sp, fontWeight = FontWeight.Medium)
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("음성 안내", fontSize = 21.sp)
                        Text(if (prefs.voice) "필요한 순간에 목소리로 알려줘요" else "목소리를 잠시 쉬게 했어요", color = Color(0xFF999999), fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                    Switch(checked = prefs.voice, enabled = editable, onCheckedChange = { enabled -> if (!enabled) app.voicePlayer.stop(); scope.launch { app.settings.voice(enabled) } },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = Color(0xFFE5E5E5), checkedBorderColor = Color(0xFFE5E5E5),
                            uncheckedThumbColor = Color(0xFF999999), uncheckedTrackColor = Color(0xFF262626), uncheckedBorderColor = Color(0xFF555555),
                            disabledCheckedThumbColor = Color(0xFF333333), disabledCheckedTrackColor = Color(0xFF666666), disabledCheckedBorderColor = Color(0xFF666666),
                            disabledUncheckedThumbColor = Color(0xFF555555), disabledUncheckedTrackColor = Color(0xFF202020), disabledUncheckedBorderColor = Color(0xFF333333)),
                        modifier = Modifier.semantics { contentDescription = "음성 안내" })
                }
                HorizontalDivider(Modifier.padding(top = 20.dp, bottom = 28.dp), color = Color(0xFF292929))
                Text("함께할 목소리", fontSize = 17.sp, color = Color(0xFFB8B8B8), modifier = Modifier.padding(start = 16.dp, bottom = 18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Personas.names.forEach { (id, name) ->
                        val selected = driver?.persona == id
                        Surface(
                            modifier = Modifier.weight(1f).selectable(selected, enabled = editable && driver != null, role = Role.RadioButton,
                                onClick = { driver?.let { d -> app.voicePlayer.stop(); scope.launch { app.dao.setPersona(d.id, id) } } }),
                            shape = RoundedCornerShape(20.dp), color = if (selected) Color(0xFFE5E5E5) else Color(0xFF151515),
                            border = BorderStroke(1.dp, if (selected) Color.White else Color(0xFF333333))
                        ) {
                            Column(Modifier.height(174.dp).padding(24.dp), verticalArrangement = Arrangement.SpaceBetween) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("0${Personas.names.keys.indexOf(id) + 1}", fontSize = 14.sp, color = if (selected) Color(0xFF606060) else Color(0xFF777777))
                                    if (selected) Icon(Icons.Outlined.Check, "선택됨", Modifier.size(20.dp), tint = Color.Black)
                                }
                                Column {
                                    Text(name, fontSize = 23.sp, fontWeight = FontWeight.Medium, color = if (selected) Color.Black else Color(0xFFEEEEEE))
                                    Text(descriptions[id] ?: "", fontSize = 13.sp, lineHeight = 20.sp, color = if (selected) Color(0xFF555555) else Color(0xFF999999), modifier = Modifier.padding(top = 10.dp))
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 28.dp, start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("선택한 목소리는 바로 저장돼요.", color = Color(0xFF888888), fontSize = 14.sp)
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(onClick = { scope.launch { app.catalog.find("C01")?.let { app.voicePlayer.play(driver?.persona ?: "friend", it) } } },
                        enabled = editable && prefs.voice && driver != null && voice.phase !in setOf(VoicePhase.PREPARING, VoicePhase.PLAYING),
                        modifier = Modifier.height(64.dp), shape = RoundedCornerShape(32.dp), contentPadding = PaddingValues(horizontal = 26.dp)) {
                        Icon(Icons.Outlined.VolumeUp, null, Modifier.size(21.dp)); Spacer(Modifier.width(10.dp)); Text("미리 듣기", fontSize = 17.sp)
                    }
                }
            }
        }
    }
}
