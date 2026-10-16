package com.sreecharan.brainrot_buddies_steal_battle.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sreecharan.brainrot_buddies_steal_battle.data.model.Rarity
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

/**
 * Steal juice for Builder D's raid visit screen, contract §10 to the ms.
 * D owns the screen and the sounds; this file owns the motion.
 *
 * - Success (~700 ms): 80 ms hit-stop, buddy stretches 1.2y, zoom punch
 *   1.08x, "Yoink!" + warm-tinted poof, arc into the sack with a trail.
 * - Fail (~500 ms): shield dome spring-wobble ripple, buddy taunt wiggle,
 *   "Shielded!" easeOutBack pop. Comedy, never punishment (§11).
 */
@Immutable
data class StealAnimState(
    val buddyImageRes: Int,
    val buddyName: String,
    val seed: Long,
    val success: Boolean,
    val startUptimeMs: Long,
)

@Composable
fun StealJuiceOverlay(
    anim: StealAnimState,
    frameTime: State<Long>,
    juice: JuiceController,
    reduceMotion: Boolean,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val buddyImage = rememberBuddyImage(anim.buddyImageRes)
    var poofFired by remember(anim) { mutableStateOf(false) }
    val lastFrame = remember(anim) { mutableLongStateOf(0L) }
    val totalMs = if (anim.success) JuiceTimings.STEAL_SUCCESS_MS else JuiceTimings.STEAL_FAIL_MS

    LaunchedEffect(anim) {
        delay(totalMs)
        onDone()
    }

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val t = frameTime.value - anim.startUptimeMs
            val center = Offset(size.width / 2f, size.height * STEAL_CENTER_Y_FRAC)
            val buddySize = STEAL_BUDDY_DP.toPx()

            if (anim.success) {
                when {
                    t < JuiceTimings.STEAL_HITSTOP_MS -> {
                        // Hit-stop: the frame freezes, zoom punches 1.08x.
                        drawBuddySprite(
                            image = buddyImage,
                            center = center,
                            sizePx = buddySize * 1.08f,
                            seed = anim.seed,
                            rarity = Rarity.COMMON,
                        )
                    }
                    t < STRETCH_END_MS -> {
                        // Stretch 1.2y + "Yoink!" + warm poof.
                        val p = ((t - JuiceTimings.STEAL_HITSTOP_MS) / STRETCH_MS).coerceIn(0f, 1f)
                        if (!poofFired) {
                            poofFired = true
                            juice.stealPoof(center.x, center.y)
                        }
                        drawBuddySprite(
                            image = buddyImage,
                            center = center,
                            sizePx = buddySize,
                            seed = anim.seed,
                            rarity = Rarity.COMMON,
                            scaleX = 1f - 0.1f * p,
                            scaleY = 1f + 0.2f * p,
                        )
                        val label = textMeasurer.measure(
                            AnnotatedString("Yoink!"),
                            style = TextStyle(
                                fontSize = (30 + 14 * p).sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFFFFD54F),
                            ),
                        )
                        drawText(
                            textMeasurer,
                            label,
                            topLeft = center - Offset(label.size.width / 2f, label.size.height / 2f) +
                                Offset(0f, -buddySize * 0.8f),
                        )
                    }
                    else -> {
                        // Arc into the sack (bottom-right) with a trail.
                        val p = ((t - STRETCH_END_MS) / (JuiceTimings.STEAL_SUCCESS_MS - STRETCH_END_MS).toFloat())
                            .coerceIn(0f, 1f)
                        val sack = Offset(size.width * SACK_X_FRAC, size.height * SACK_Y_FRAC)
                        val pos = Offset(
                            center.x + (sack.x - center.x) * p,
                            center.y + (sack.y - center.y) * p - sin(p * PI.toFloat()) * 90.dp.toPx(),
                        )
                        val shrink = 1f - SACK_SHRINK * p
                        drawCircle(
                            color = Color(0xFF8D6E63).copy(alpha = 0.9f),
                            radius = 34.dp.toPx(),
                            center = sack,
                        )
                        drawBuddySprite(
                            image = buddyImage,
                            center = pos,
                            sizePx = buddySize * shrink,
                            seed = anim.seed,
                            rarity = Rarity.COMMON,
                            rotationDeg = if (reduceMotion) 0f else p * 120f,
                            alpha = 1f - p * 0.3f,
                        )
                    }
                }
            } else {
                // Fail: dome spring-wobble ripple + taunt wiggle + "Shielded!".
                val p = (t / JuiceTimings.STEAL_FAIL_MS.toFloat()).coerceIn(0f, 1f)
                val wobble = if (reduceMotion) {
                    0f
                } else {
                    sin(p * PI.toFloat() * FAIL_OSCILLATIONS) * (1f - p)
                }
                drawArc(
                    color = Color(0xFF4FC3F7).copy(alpha = 0.5f),
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(center.x - buddySize * 0.9f, center.y - buddySize * 0.4f),
                    size = androidx.compose.ui.geometry.Size(buddySize * 1.8f, buddySize * 1.8f),
                    style = Stroke(width = (3 + 4 * wobble).dp.toPx()),
                )
                drawBuddySprite(
                    image = buddyImage,
                    center = center + Offset(wobble * 30.dp.toPx(), 0f),
                    sizePx = buddySize,
                    seed = anim.seed,
                    rarity = Rarity.COMMON,
                    rotationDeg = wobble * 24f,
                )
                val pop = easeOutBackTiny(p)
                val label = textMeasurer.measure(
                    AnnotatedString("Shielded!"),
                    style = TextStyle(
                        fontSize = (26 * pop).sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF4FC3F7),
                    ),
                )
                drawText(
                    textMeasurer,
                    label,
                    topLeft = center - Offset(label.size.width / 2f, label.size.height / 2f) +
                        Offset(0f, -buddySize * 0.85f),
                )
            }

            juice.particles.update(
                run {
                    val last = lastFrame.longValue
                    lastFrame.longValue = frameTime.value
                    if (last == 0L) FRAME_DT_FALLBACK else ((frameTime.value - last) / MILLIS_TO_SECONDS_F).coerceIn(0f, DT_MAX_SECONDS)
                },
            )
            juice.particles.draw(this)
        }
    }
}

private const val STEAL_CENTER_Y_FRAC = 0.42f
private const val STRETCH_END_MS = 300L
private const val STRETCH_MS = 220f
private const val SACK_X_FRAC = 0.82f
private const val SACK_Y_FRAC = 0.85f
private const val SACK_SHRINK = 0.6f
private const val FAIL_OSCILLATIONS = 3f
private const val FRAME_DT_FALLBACK = 0.016f
private const val MILLIS_TO_SECONDS_F = 1000f
private const val DT_MAX_SECONDS = 0.1f
private const val EASE_C1_TINY = 1.30158f

private val STEAL_BUDDY_DP = 140.dp

private fun easeOutBackTiny(t: Float): Float {
    val c1 = EASE_C1_TINY
    val u = t - 1f
    return 1f + (c1 + 1f) * u * u * u + c1 * u * u
}
