package com.sreecharan.brainrot_buddies_steal_battle.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
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
@Composable
fun StealJuiceOverlay(
    request: StealJuiceRequest,
    modifier: Modifier = Modifier,
) {
    val anim = request.anim
    val textMeasurer = rememberTextMeasurer()
    val buddyImage = rememberBuddyImage(anim.buddyImageRes)
    var poofFired by remember(anim) { mutableStateOf(false) }
    val lastFrame = remember(anim) { mutableLongStateOf(0L) }
    val totalMs = if (anim.success) JuiceTimings.STEAL_SUCCESS_MS else JuiceTimings.STEAL_FAIL_MS

    LaunchedEffect(anim) {
        delay(totalMs)
        request.onDone()
    }

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val t = request.frameTime.value - anim.startUptimeMs
            val center = Offset(size.width / 2f, size.height * STEAL_CENTER_Y_FRAC)
            val buddySize = STEAL_BUDDY_DP.toPx()
            val phase = stealPhase(anim.success, t)
            if (phase == StealPhase.STRETCH && !poofFired) {
                poofFired = true
                request.juice.stealPoof(center.x, center.y)
            }
            val frame =
                StealFrame(
                    anim = anim,
                    t = t,
                    center = center,
                    buddySize = buddySize,
                    buddyImage = buddyImage,
                    textMeasurer = textMeasurer,
                    reduceMotion = request.reduceMotion,
                )
            drawStealPhase(frame)
            request.juice.particles.update(frameDtSeconds(lastFrame, request.frameTime))
            request.juice.particles.draw(this)
        }
    }
}

private enum class StealPhase { HIT_STOP, STRETCH, ARC, FAIL }

private fun stealPhase(
    success: Boolean,
    t: Long,
): StealPhase =
    when {
        !success -> StealPhase.FAIL
        t < JuiceTimings.STEAL_HITSTOP_MS -> StealPhase.HIT_STOP
        t < STRETCH_END_MS -> StealPhase.STRETCH
        else -> StealPhase.ARC
    }

/** One drawn frame of the steal animation. */
private data class StealFrame(
    val anim: StealAnimState,
    val t: Long,
    val center: Offset,
    val buddySize: Float,
    val buddyImage: ImageBitmap?,
    val textMeasurer: TextMeasurer,
    val reduceMotion: Boolean,
)

private fun DrawScope.drawStealPhase(frame: StealFrame) {
    when (stealPhase(frame.anim.success, frame.t)) {
        StealPhase.HIT_STOP -> drawHitStop(frame)
        StealPhase.STRETCH -> drawStretch(frame)
        StealPhase.ARC -> drawSackArc(frame)
        StealPhase.FAIL -> drawFailPhase(frame)
    }
}

/** Hit-stop: the frame freezes, zoom punches 1.08x. */
private fun DrawScope.drawHitStop(frame: StealFrame) {
    drawBuddySprite(
        BuddySpriteSpec(
            image = frame.buddyImage,
            center = frame.center,
            sizePx = frame.buddySize * ZOOM_PUNCH,
            seed = frame.anim.seed,
            rarity = Rarity.COMMON,
        ),
    )
}

/** Stretch 1.2y + "Yoink!" label. The warm poof fires once, in the caller. */
private fun DrawScope.drawStretch(frame: StealFrame) {
    val p = ((frame.t - JuiceTimings.STEAL_HITSTOP_MS) / STRETCH_MS).coerceIn(0f, 1f)
    drawBuddySprite(
        BuddySpriteSpec(
            image = frame.buddyImage,
            center = frame.center,
            sizePx = frame.buddySize,
            seed = frame.anim.seed,
            rarity = Rarity.COMMON,
            scaleX = 1f - STRETCH_SQUASH_X * p,
            scaleY = 1f + STRETCH_SQUASH_Y * p,
        ),
    )
    val label =
        frame.textMeasurer.measure(
            AnnotatedString("Yoink!"),
            style =
                TextStyle(
                    fontSize = (YOINK_BASE_SP + YOINK_GROW_SP * p).sp,
                    fontWeight = FontWeight.Black,
                    color = GOLD,
                ),
        )
    drawText(
        label,
        topLeft =
            frame.center - Offset(label.size.width / 2f, label.size.height / 2f) +
                Offset(0f, -frame.buddySize * YOINK_LABEL_DY_FRAC),
    )
}

/** Arc into the sack (bottom-right) with a trail. */
private fun DrawScope.drawSackArc(frame: StealFrame) {
    val p =
        ((frame.t - STRETCH_END_MS) / (JuiceTimings.STEAL_SUCCESS_MS - STRETCH_END_MS).toFloat())
            .coerceIn(0f, 1f)
    val sack = Offset(size.width * SACK_X_FRAC, size.height * SACK_Y_FRAC)
    val pos =
        Offset(
            frame.center.x + (sack.x - frame.center.x) * p,
            frame.center.y + (sack.y - frame.center.y) * p - sin(p * PI.toFloat()) * ARC_LIFT_DP.toPx(),
        )
    val shrink = 1f - SACK_SHRINK * p
    drawCircle(
        color = SACK_BROWN.copy(alpha = SACK_ALPHA),
        radius = SACK_RADIUS_DP.toPx(),
        center = sack,
    )
    drawBuddySprite(
        BuddySpriteSpec(
            image = frame.buddyImage,
            center = pos,
            sizePx = frame.buddySize * shrink,
            seed = frame.anim.seed,
            rarity = Rarity.COMMON,
            rotationDeg = if (frame.reduceMotion) 0f else p * ARC_SPIN_DEG,
            alpha = 1f - p * ARC_FADE,
        ),
    )
}

/** Fail: dome spring-wobble ripple + taunt wiggle + "Shielded!". */
private fun DrawScope.drawFailPhase(frame: StealFrame) {
    val p = (frame.t / JuiceTimings.STEAL_FAIL_MS.toFloat()).coerceIn(0f, 1f)
    val wobble =
        if (frame.reduceMotion) {
            0f
        } else {
            sin(p * PI.toFloat() * FAIL_OSCILLATIONS) * (1f - p)
        }
    drawArc(
        color = SHIELD_BLUE.copy(alpha = SHIELD_DOME_ALPHA),
        startAngle = DOME_START_DEG,
        sweepAngle = DOME_SWEEP_DEG,
        useCenter = false,
        topLeft =
            Offset(
                frame.center.x - frame.buddySize * DOME_X_FRAC,
                frame.center.y - frame.buddySize * DOME_Y_FRAC,
            ),
        size = Size(frame.buddySize * DOME_SIZE_FRAC, frame.buddySize * DOME_SIZE_FRAC),
        style = Stroke(width = (DOME_STROKE_BASE + DOME_STROKE_WOBBLE * wobble).dp.toPx()),
    )
    drawBuddySprite(
        BuddySpriteSpec(
            image = frame.buddyImage,
            center = frame.center + Offset(wobble * TAUNT_DX_DP.toPx(), 0f),
            sizePx = frame.buddySize,
            seed = frame.anim.seed,
            rarity = Rarity.COMMON,
            rotationDeg = wobble * TAUNT_ROT_DEG,
        ),
    )
    val pop = easeOutBackTiny(p)
    val label =
        frame.textMeasurer.measure(
            AnnotatedString("Shielded!"),
            style =
                TextStyle(
                    fontSize = (SHIELDED_BASE_SP * pop).sp,
                    fontWeight = FontWeight.Black,
                    color = SHIELD_BLUE,
                ),
        )
    drawText(
        label,
        topLeft =
            frame.center - Offset(label.size.width / 2f, label.size.height / 2f) +
                Offset(0f, -frame.buddySize * SHIELDED_LABEL_DY_FRAC),
    )
}

/** Per-frame delta in seconds for particle updates; 1/60 s on the first frame. */
private fun frameDtSeconds(
    lastFrame: MutableLongState,
    frameTime: State<Long>,
): Float {
    val last = lastFrame.longValue
    lastFrame.longValue = frameTime.value
    return if (last == 0L) {
        FRAME_DT_FALLBACK
    } else {
        ((frameTime.value - last) / MILLIS_TO_SECONDS_F).coerceIn(0f, DT_MAX_SECONDS)
    }
}

private const val STEAL_CENTER_Y_FRAC = 0.42f
private const val STRETCH_END_MS = 300L
private const val STRETCH_MS = 220f
private const val SACK_X_FRAC = 0.82f
private const val SACK_Y_FRAC = 0.85f
private const val SACK_SHRINK = 0.6f
private const val SACK_ALPHA = 0.9f
private const val FAIL_OSCILLATIONS = 3f
private const val FRAME_DT_FALLBACK = 0.016f
private const val MILLIS_TO_SECONDS_F = 1000f
private const val DT_MAX_SECONDS = 0.1f
private const val EASE_C1_TINY = 1.30158f
private const val ZOOM_PUNCH = 1.08f
private const val STRETCH_SQUASH_X = 0.1f
private const val STRETCH_SQUASH_Y = 0.2f
private const val YOINK_BASE_SP = 30f
private const val YOINK_GROW_SP = 14f
private const val YOINK_LABEL_DY_FRAC = 0.8f
private const val ARC_SPIN_DEG = 120f
private const val ARC_FADE = 0.3f
private const val DOME_START_DEG = 180f
private const val DOME_SWEEP_DEG = 180f
private const val DOME_X_FRAC = 0.9f
private const val DOME_Y_FRAC = 0.4f
private const val DOME_SIZE_FRAC = 1.8f
private const val DOME_STROKE_BASE = 3f
private const val DOME_STROKE_WOBBLE = 4f
private const val SHIELD_DOME_ALPHA = 0.5f
private const val TAUNT_ROT_DEG = 24f
private const val SHIELDED_BASE_SP = 26f
private const val SHIELDED_LABEL_DY_FRAC = 0.85f

private val GOLD = Color(0xFFFFD54F)
private val SACK_BROWN = Color(0xFF8D6E63)
private val SHIELD_BLUE = Color(0xFF4FC3F7)
private val STEAL_BUDDY_DP = 140.dp
private val ARC_LIFT_DP = 90.dp
private val SACK_RADIUS_DP = 34.dp
private val TAUNT_DX_DP = 30.dp

private fun easeOutBackTiny(t: Float): Float {
    val c1 = EASE_C1_TINY
    val u = t - 1f
    return 1f + (c1 + 1f) * u * u * u + c1 * u * u
}
