package com.sreecharan.brainrot_buddies_steal_battle.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
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
import com.sreecharan.brainrot_buddies_steal_battle.data.model.TicketType
import com.sreecharan.brainrot_buddies_steal_battle.ui.home.CeremonyPhase
import com.sreecharan.brainrot_buddies_steal_battle.ui.home.MysteryCeremonyUi
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

/**
 * Mystery-pull ceremony for Lucky/Mega tickets, contract §2 V1.6:
 * drumroll, screen darken, slow reveal, escalating music, confetti physics.
 * Rare+ is skippable after 1 s (contract §10); the reveal auto-completes.
 */
// Public API: HomeScreen calls with all 7 arguments, so the parameter list
// cannot be regrouped without breaking that call site.
@Suppress("LongParameterList")
@Composable
fun MysteryCeremonyOverlay(
    ceremony: MysteryCeremonyUi,
    frameTime: State<Long>,
    juice: JuiceController,
    reduceMotion: Boolean,
    onSkip: () -> Unit,
    onRevealDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val buddy = ceremony.buddy
    val buddyImage = rememberBuddyImage(buddy?.imageRes ?: 0)
    var revealFxFired by remember(ceremony.buddy?.id) { mutableStateOf(false) }
    val lastFrame = remember { mutableLongStateOf(0L) }

    // The reveal plays out, then hands back to the board.
    LaunchedEffect(ceremony.phase, ceremony.buddy?.id) {
        if (ceremony.phase == CeremonyPhase.REVEAL && ceremony.buddy != null) {
            delay(REVEAL_HOLD_MS)
            onRevealDone()
        }
    }

    // Skip unlocks 1 s into the reveal (§10).
    val skippable = ceremony.phase == CeremonyPhase.REVEAL &&
        frameTime.value - ceremony.phaseStartUptimeMs > JuiceTimings.SKIPPABLE_AFTER_MS

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { if (skippable) onSkip() },
                ),
        ) {
            val t = frameTime.value - ceremony.phaseStartUptimeMs
            val center = Offset(size.width / 2f, size.height * REVEAL_CENTER_Y_FRAC)
            val frame = CeremonyFrame(
                ceremony = ceremony,
                t = t,
                center = center,
                buddyImage = buddyImage,
                textMeasurer = textMeasurer,
                reduceMotion = reduceMotion,
            )
            when (ceremony.phase) {
                CeremonyPhase.DRUMROLL -> drawDrumroll(frame)
                CeremonyPhase.REVEAL -> {
                    if (buddy != null && !revealFxFired) {
                        revealFxFired = true
                        juice.confetti(center.x, center.y)
                        if (buddy.rarity == Rarity.EPIC) {
                            juice.goldRays(center.x, center.y, size.width * size.height)
                        }
                    }
                    drawReveal(frame)
                }
            }

            juice.particles.update(frameDtSeconds(lastFrame, frameTime))
            juice.particles.draw(this)
        }

        if (skippable) {
            CeremonySkipButton(onSkip)
        }
    }
}

@Composable
private fun BoxScope.CeremonySkipButton(onSkip: () -> Unit) {
    IconButton(
        onClick = onSkip,
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(16.dp)
            .size(48.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = "Skip ceremony",
            tint = Color.White,
        )
    }
}

/** One drawn frame of the mystery ceremony. */
private data class CeremonyFrame(
    val ceremony: MysteryCeremonyUi,
    val t: Long,
    val center: Offset,
    val buddyImage: ImageBitmap?,
    val textMeasurer: TextMeasurer,
    val reduceMotion: Boolean,
)

/** Drumroll: darken ramps in and the ticket shakes with anticipation. */
private fun DrawScope.drawDrumroll(frame: CeremonyFrame) {
    val darken = (frame.t / JuiceTimings.CEREMONY_DRUMROLL_MS.toFloat()).coerceIn(0f, 1f)
    drawRect(Color.Black.copy(alpha = DARKEN_MAX_ALPHA * darken))
    val ticketSize = TICKET_DP.toPx()
    val shakeRot = if (frame.reduceMotion) {
        0f
    } else {
        sin(2f * PI.toFloat() * frame.t / TICKET_SHAKE_PERIOD_MS) * TICKET_SHAKE_DEG * darken
    }
    drawContext.canvas.save()
    drawContext.canvas.translate(frame.center.x, frame.center.y)
    drawContext.canvas.rotate(shakeRot)
    drawRoundRect(
        color = if (frame.ceremony.ticket == TicketType.LUCKY) TICKET_LUCKY else TICKET_MEGA,
        topLeft = Offset(-ticketSize / 2f, -ticketSize * TICKET_H_FRAC),
        size = Size(ticketSize, ticketSize * TICKET_ASPECT),
        cornerRadius = CornerRadius(18.dp.toPx()),
    )
    drawContext.canvas.restore()
    val q = frame.textMeasurer.measure(
        AnnotatedString("?"),
        style = TextStyle(
            fontSize = 64.sp,
            fontWeight = FontWeight.Black,
            color = Color.White.copy(alpha = darken),
        ),
    )
    drawText(
        frame.textMeasurer,
        q,
        topLeft = frame.center - Offset(q.size.width / 2f, q.size.height / 2f),
    )
}

/** Reveal: slow scale-up with an overshoot, never a flash. */
private fun DrawScope.drawReveal(frame: CeremonyFrame) {
    val buddy = frame.ceremony.buddy ?: return
    drawRect(Color.Black.copy(alpha = DARKEN_MAX_ALPHA))
    val p = (frame.t / REVEAL_SCALE_MS.toFloat()).coerceIn(0f, 1f)
    val scale = easeOutBackSmall(p)
    val buddySize = REVEAL_BUDDY_DP.toPx() * scale
    if (buddy.rarity == Rarity.EPIC && !frame.reduceMotion) {
        drawCircle(
            color = EPIC_GOLD.copy(alpha = EPIC_RING_ALPHA),
            radius = buddySize,
            center = frame.center,
            style = Stroke(width = 6.dp.toPx()),
        )
    }
    drawBuddySprite(
        BuddySpriteSpec(
            image = frame.buddyImage,
            center = frame.center,
            sizePx = buddySize,
            seed = buddy.seed,
            rarity = buddy.rarity,
        ),
    )
    val rarityText = if (buddy.rarity == Rarity.EPIC) "EPIC!" else "RARE!"
    val rarityColor = if (buddy.rarity == Rarity.EPIC) EPIC_GOLD else RARE_SKY
    val label = frame.textMeasurer.measure(
        AnnotatedString(rarityText),
        style = TextStyle(
            fontSize = 44.sp,
            fontWeight = FontWeight.Black,
            color = rarityColor,
        ),
    )
    drawText(
        frame.textMeasurer,
        label,
        topLeft = Offset(
            frame.center.x - label.size.width / 2f,
            frame.center.y + buddySize * LABEL_DY_FRAC,
        ),
    )
    val name = frame.textMeasurer.measure(
        AnnotatedString(buddy.name),
        style = TextStyle(
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        ),
    )
    drawText(
        frame.textMeasurer,
        name,
        topLeft = Offset(
            frame.center.x - name.size.width / 2f,
            frame.center.y + buddySize * LABEL_DY_FRAC + label.size.height + 4.dp.toPx(),
        ),
    )
}

/** Per-frame delta in seconds for particle updates; 1/60 s on the first frame. */
private fun frameDtSeconds(lastFrame: MutableLongState, frameTime: State<Long>): Float {
    val last = lastFrame.longValue
    lastFrame.longValue = frameTime.value
    return if (last == 0L) {
        FRAME_DT_FALLBACK
    } else {
        ((frameTime.value - last) / MILLIS_TO_SECONDS_F).coerceIn(0f, DT_MAX_SECONDS)
    }
}

/** Gentler overshoot for the reveal: delight without the full easeOutBack snap. */
private fun DrawScope.easeOutBackSmall(t: Float): Float {
    val c1 = EASE_C1_SMALL
    val u = t - 1f
    return 1f + (c1 + 1f) * u * u * u + c1 * u * u
}

private const val REVEAL_SCALE_MS = 900L
private const val REVEAL_HOLD_MS = 3200L
private const val REVEAL_CENTER_Y_FRAC = 0.42f
private const val TICKET_SHAKE_PERIOD_MS = 120f
private const val TICKET_SHAKE_DEG = 6f
private const val TICKET_H_FRAC = 0.7f
private const val TICKET_ASPECT = 1.4f
private const val DARKEN_MAX_ALPHA = 0.72f
private const val EPIC_RING_ALPHA = 0.35f
private const val LABEL_DY_FRAC = 0.62f
private const val FRAME_DT_FALLBACK = 0.016f
private const val MILLIS_TO_SECONDS_F = 1000f
private const val DT_MAX_SECONDS = 0.1f
private const val EASE_C1_SMALL = 1.2f

private val TICKET_LUCKY = Color(0xFF4FC3F7)
private val TICKET_MEGA = Color(0xFFBA68C8)
private val EPIC_GOLD = Color(0xFFFFD54F)
private val RARE_SKY = Color(0xFF4FC3F7)
private val TICKET_DP = 120.dp
private val REVEAL_BUDDY_DP = 170.dp
