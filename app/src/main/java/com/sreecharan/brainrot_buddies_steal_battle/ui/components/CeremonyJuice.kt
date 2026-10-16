package com.sreecharan.brainrot_buddies_steal_battle.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
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

            when (ceremony.phase) {
                CeremonyPhase.DRUMROLL -> {
                    // Darken ramps in; the ticket shakes with anticipation.
                    val darken = (t / JuiceTimings.CEREMONY_DRUMROLL_MS.toFloat()).coerceIn(0f, 1f)
                    drawRect(Color.Black.copy(alpha = 0.72f * darken))
                    val ticketSize = TICKET_DP.toPx()
                    val shakeRot = if (reduceMotion) {
                        0f
                    } else {
                        sin(2f * PI.toFloat() * t / TICKET_SHAKE_PERIOD_MS) * TICKET_SHAKE_DEG * darken
                    }
                    drawContext.canvas.save()
                    drawContext.canvas.translate(center.x, center.y)
                    drawContext.canvas.rotate(shakeRot)
                    drawRoundRect(
                        color = if (ceremony.ticket == TicketType.LUCKY) {
                            Color(0xFF4FC3F7)
                        } else {
                            Color(0xFFBA68C8)
                        },
                        topLeft = Offset(-ticketSize / 2f, -ticketSize * 0.7f),
                        size = Size(ticketSize, ticketSize * 1.4f),
                        cornerRadius = CornerRadius(18.dp.toPx()),
                    )
                    drawContext.canvas.restore()
                    val q = textMeasurer.measure(
                        AnnotatedString("?"),
                        style = TextStyle(
                            fontSize = 64.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White.copy(alpha = darken),
                        ),
                    )
                    drawText(
                        textMeasurer,
                        q,
                        topLeft = center - Offset(q.size.width / 2f, q.size.height / 2f),
                    )
                }
                CeremonyPhase.REVEAL -> {
                    drawRect(Color.Black.copy(alpha = 0.72f))
                    if (buddy != null) {
                        if (!revealFxFired) {
                            revealFxFired = true
                            juice.confetti(center.x, center.y)
                            if (buddy.rarity == Rarity.EPIC) {
                                juice.goldRays(center.x, center.y, size.width * size.height)
                            }
                        }
                        // Slow reveal: scale up with an overshoot, never a flash.
                        val p = (t / REVEAL_SCALE_MS.toFloat()).coerceIn(0f, 1f)
                        val scale = easeOutBackSmall(p)
                        val buddySize = REVEAL_BUDDY_DP.toPx() * scale
                        if (buddy.rarity == Rarity.EPIC && !reduceMotion) {
                            drawCircle(
                                color = Color(0xFFFFD54F).copy(alpha = 0.35f),
                                radius = buddySize,
                                center = center,
                                style = Stroke(width = 6.dp.toPx()),
                            )
                        }
                        drawBuddySprite(
                            image = buddyImage,
                            center = center,
                            sizePx = buddySize,
                            seed = buddy.seed,
                            rarity = buddy.rarity,
                        )
                        val rarityText = if (buddy.rarity == Rarity.EPIC) "EPIC!" else "RARE!"
                        val rarityColor = if (buddy.rarity == Rarity.EPIC) {
                            Color(0xFFFFD54F)
                        } else {
                            Color(0xFF4FC3F7)
                        }
                        val label = textMeasurer.measure(
                            AnnotatedString(rarityText),
                            style = TextStyle(
                                fontSize = 44.sp,
                                fontWeight = FontWeight.Black,
                                color = rarityColor,
                            ),
                        )
                        drawText(
                            textMeasurer,
                            label,
                            topLeft = Offset(
                                center.x - label.size.width / 2f,
                                center.y + buddySize * 0.62f,
                            ),
                        )
                        val name = textMeasurer.measure(
                            AnnotatedString(buddy.name),
                            style = TextStyle(
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            ),
                        )
                        drawText(
                            textMeasurer,
                            name,
                            topLeft = Offset(
                                center.x - name.size.width / 2f,
                                center.y + buddySize * 0.62f + label.size.height + 4.dp.toPx(),
                            ),
                        )
                    }
                }
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

        if (skippable) {
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
private const val FRAME_DT_FALLBACK = 0.016f
private const val MILLIS_TO_SECONDS_F = 1000f
private const val DT_MAX_SECONDS = 0.1f
private const val EASE_C1_SMALL = 1.2f

private val TICKET_DP = 120.dp
private val REVEAL_BUDDY_DP = 170.dp
