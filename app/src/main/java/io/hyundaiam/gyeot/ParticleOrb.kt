package io.hyundaiam.gyeot

import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import kotlin.math.*

private data class Dust(val x: Float, val y: Float, val z: Float, val seed: Float, val radius: Float)
private data class ProjectedDust(val center: Offset, val depth: Float, val radius: Float)

private fun sphereDust(): List<Dust> = List(680) { i ->
    val theta = i * 2.3999632f
    val count = if (i < 480) 480 else 200
    val index = if (i < 480) i else i - 480
    val y = 1f - 2f * (index + .5f) / count
    val ring = sqrt(1f - y * y)
    val radius = if (i < 480) 1f else .25f + .65f * ((i * 37 % 199) / 199f).pow(.4f)
    Dust(cos(theta) * ring * radius, y * radius, sin(theta) * ring * radius, i * .753f, 1.0f + (i * 17 % 19) / 9f)
}

@Composable
internal fun ParticleOrb(amplitude: Float, speaking: Boolean, modifier: Modifier = Modifier) {
    val particles = remember { sphereDust() }
    val motion = rememberInfiniteTransition(label = "orb drift")
    val time = motion.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(57_120, easing = LinearEasing), RepeatMode.Restart), label = "orb rotation")
    val energy = animateFloatAsState(amplitude.coerceIn(0f, 1f), spring(dampingRatio = 1f, stiffness = 500f), label = "speech energy")
    Canvas(modifier.semantics {
        contentDescription = "곁 음성 구체"
        stateDescription = if (speaking) "음성 안내 중" else "곁 준비됨"
    }) {
        val t = time.value
        val e = energy.value
        val center = Offset(size.width * .5f, size.height * .51f)
        val base = min(size.width * .27f, size.height * .36f)
        val radius = base * (1f + sin(t * 8f) * .012f + e * .16f)
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = .035f + e * .025f), Color.White.copy(alpha = .012f), Color.Transparent), center, radius * 1.42f), radius * 1.42f, center)
        drawCircle(Color.White.copy(alpha = .035f + e * .035f), radius, center, style = Stroke(.7f))
        val yaw = t
        val tilt = .27f + sin(t) * .12f
        val cy = cos(yaw); val sy = sin(yaw); val ct = cos(tilt); val st = sin(tilt)
        val projected = particles.map { p ->
            val x = p.x * cy + p.z * sy
            val z = -p.x * sy + p.z * cy
            val y = p.y * ct - z * st
            val depth = p.y * st + z * ct
            val ripple = 1f + e * .095f * sin(p.seed + t * 34f) + .008f * sin(t * 7f + p.seed)
            val perspective = 1f + depth * .12f
            ProjectedDust(center + Offset(x, y) * radius * ripple * perspective, depth,
                p.radius * density * (.56f + (depth + 1f) * .20f) * (1f + e * .28f))
        }.sortedBy { it.depth }
        projected.forEachIndexed { i, p ->
            val brightness = (.24f + (p.depth + 1f) * .29f + e * .14f).coerceIn(0f, 1f)
            if (i % 3 == 0) drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = brightness * .19f), Color.Transparent), p.center, p.radius * 3.8f), p.radius * 3.8f, p.center)
            drawCircle(Color.White.copy(alpha = brightness), p.radius, p.center)
        }
        val core = base * (.045f + e * .023f)
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = .19f + e * .2f), Color.White.copy(alpha = .045f), Color.Transparent), center, core * 7f), core * 7f, center)
        drawCircle(Color.White.copy(alpha = .045f + e * .045f), core * 2.5f, center)
        drawCircle(Color.White.copy(alpha = .13f + e * .15f), core * 1.65f, center)
        drawCircle(Color.White.copy(alpha = .85f), core * .62f, center)
        drawCircle(Color.White, core * .23f, center)
    }
}
