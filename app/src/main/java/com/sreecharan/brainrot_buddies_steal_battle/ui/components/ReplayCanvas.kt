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
import com.sreecharan.brainrot_buddies_steal_battle.ui.home.ReplayKind
import com.sreecharan.brainrot_buddies_steal_battle.ui.home.StagedReplayUi
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.AppColors
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.LocalAppColors
import kotlin.math.PI
import kotlin.math.sin

/**
 * Staged 5 s steal/revenge auto-replay, contract §2 V1.1. Staged for a
 * parent to film: slow-mo grab, comedy caption word-by-word, victim
 * reaction, catchphrase sting. No in-app recording, no upload, no share.
 *
 * Builder D triggers these via raid records ([BaseGameData]); this file
 * only renders. Skippable freely: tap anywhere or the X button.
 */
// Public API: HomeScreen calls with all 6 arguments, so the parameter list
// cannot be regrouped without breaking that call site.
@Suppress("LongParameterList")
@Composable
fun ReplayCanvas(
    replay: StagedReplayUi,
    frameTime: State<Long>,
    juice: JuiceController,
    reduceMotion: Boolean,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val textMeasurer = rememberTextMeasurer()
    val buddyImage = rememberBuddyImage(replay.buddyImageRes)
    var confettiFired by remember(replay.id) { mutableStateOf(false) }
    val lastFrame = remember(replay.id) { mutableLongStateOf(0L) }

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onSkip,
                ),
        ) {
            val t = frameTime.value - replay.startUptimeMs
            // Scrim fades in over the first 400 ms.
            val scrimAlpha = (t / SCRIM_FADE_MS).coerceIn(0f, 1f) * SCRIM_MAX_ALPHA
            drawRect(Color.Black.copy(alpha = scrimAlpha))

            val center = Offset(size.width / 2f, size.height * REPLAY_BUDDY_Y_FRAC)
            val frame = ReplayFrame(
                replay = replay,
                t = t,
                center = center,
                buddySize = REPLAY_BUDDY_DP.toPx(),
                buddyImage = buddyImage,
                textMeasurer = textMeasurer,
                colors = colors,
                reduceMotion = reduceMotion,
            )
            if (replayPhase(t) == ReplayPhase.STING && !confettiFired) {
                confettiFired = true
                juice.confetti(center.x, center.y)
            }
            drawReplayPhase(frame)
            juice.particles.update(frameDtSeconds(lastFrame, frameTime))
            juice.particles.draw(this)
        }

        IconButton(
            onClick = onSkip,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .size(48.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Skip replay",
                tint = Color.White,
            )
        }
    }
}

private enum class ReplayPhase { GRAB, CAPTION, REACTION, STING }

private fun replayPhase(t: Long): ReplayPhase = when {
    t < REPLAY_GRAB_MS -> ReplayPhase.GRAB
    t < REPLAY_CAPTION_MS -> ReplayPhase.CAPTION
    t < REPLAY_REACTION_MS -> ReplayPhase.REACTION
    else -> ReplayPhase.STING
}

/** One drawn frame of the staged replay. */
private data class ReplayFrame(
    val replay: StagedReplayUi,
    val t: Long,
    val center: Offset,
    val buddySize: Float,
    val buddyImage: ImageBitmap?,
    val textMeasurer: TextMeasurer,
    val colors: AppColors,
    val reduceMotion: Boolean,
)

private fun DrawScope.drawReplayPhase(frame: ReplayFrame) {
    when (replayPhase(frame.t)) {
        ReplayPhase.GRAB -> drawGrabPhase(frame)
        ReplayPhase.CAPTION -> drawCaptionPhase(frame)
        ReplayPhase.REACTION -> drawReactionPhase(frame)
        ReplayPhase.STING -> drawStingPhase(frame)
    }
}

/** 0-800 ms: slow-mo grab. Buddy stretches, camera punches in. */
private fun DrawScope.drawGrabPhase(frame: ReplayFrame) {
    val replay = frame.replay
    val p = (frame.t / REPLAY_GRAB_MS.toFloat()).coerceIn(0f, 1f)
    val stretch = if (frame.reduceMotion) 1f else 1f + GRAB_STRETCH * sin(p * PI.toFloat())
    val zoom = 1f + GRAB_ZOOM * p
    drawBuddySprite(
        BuddySpriteSpec(
            image = frame.buddyImage,
            center = frame.center,
            sizePx = frame.buddySize * zoom,
            seed = replay.buddySeed,
            rarity = Rarity.COMMON,
        ),
    )
    // Stretch ring to sell the grab.
    if (!frame.reduceMotion) {
        drawCircle(
            color = Color.White.copy(alpha = RING_MAX_ALPHA * (1f - p)),
            radius = frame.buddySize * RING_RADIUS_FRAC * stretch,
            center = frame.center,
            style = Stroke(width = 5.dp.toPx()),
        )
    }
    drawReplayCaption(
        text = if (replay.kind == ReplayKind.STEAL) "Yoink!" else "Payback!",
        center = frame.center + Offset(0f, -frame.buddySize * CAPTION_DY_FRAC),
        scale = CAPTION_SCALE_BASE + CAPTION_SCALE_GROW * p,
        color = GOLD,
        textMeasurer = frame.textMeasurer,
    )
}

/** 800-2500 ms: comedy caption, word by word. */
private fun DrawScope.drawCaptionPhase(frame: ReplayFrame) {
    val replay = frame.replay
    drawBuddySprite(
        BuddySpriteSpec(
            image = frame.buddyImage,
            center = frame.center,
            sizePx = frame.buddySize,
            seed = replay.buddySeed,
            rarity = Rarity.COMMON,
        ),
    )
    val words = replay.caption.split(" ")
    val wordT = (frame.t - REPLAY_GRAB_MS) / CAPTION_WORD_MS
    val visible = words.take((wordT + 1).toInt().coerceIn(1, words.size))
    drawCaptionCard(
        text = visible.joinToString(" "),
        fullText = replay.caption,
        center = Offset(size.width / 2f, size.height * CAPTION_CARD_Y_FRAC),
        colors = frame.colors,
        textMeasurer = frame.textMeasurer,
    )
}

/** 2500-3500 ms: victim reaction card wobble. */
private fun DrawScope.drawReactionPhase(frame: ReplayFrame) {
    val replay = frame.replay
    val p = (frame.t - REPLAY_CAPTION_MS) / (REPLAY_REACTION_MS - REPLAY_CAPTION_MS).toFloat()
    val wobble = if (frame.reduceMotion) {
        0f
    } else {
        sin(p * PI.toFloat() * REACTION_OSCILLATIONS) * REACTION_WOBBLE_PX * (1f - p)
    }
    drawBuddySprite(
        BuddySpriteSpec(
            image = frame.buddyImage,
            center = frame.center + Offset(wobble * WOBBLE_DX_MUL, 0f),
            sizePx = frame.buddySize,
            seed = replay.buddySeed,
            rarity = Rarity.COMMON,
            rotationDeg = wobble,
        ),
    )
    drawCaptionCard(
        text = if (replay.kind == ReplayKind.STEAL) {
            "${replay.otherBaseName} had NO idea!"
        } else {
            "${replay.otherBaseName} never saw it coming!"
        },
        fullText = replay.caption,
        center = Offset(size.width / 2f, size.height * CAPTION_CARD_Y_FRAC),
        colors = frame.colors,
        textMeasurer = frame.textMeasurer,
    )
}

/** 3500-5000 ms: catchphrase sting + confetti. */
private fun DrawScope.drawStingPhase(frame: ReplayFrame) {
    val replay = frame.replay
    val p = ((frame.t - REPLAY_REACTION_MS) / (JuiceTimings.REPLAY_MS - REPLAY_REACTION_MS).toFloat())
        .coerceIn(0f, 1f)
    val spin = if (frame.reduceMotion) 0f else SPIN_DEGREES * p
    drawBuddySprite(
        BuddySpriteSpec(
            image = frame.buddyImage,
            center = frame.center,
            sizePx = frame.buddySize,
            seed = replay.buddySeed,
            rarity = Rarity.COMMON,
            rotationDeg = spin,
        ),
    )
    drawReplayCaption(
        text = replay.buddyName,
        center = frame.center + Offset(0f, frame.buddySize * STING_CAPTION_DY_FRAC),
        scale = 1f,
        color = Color.White,
        textMeasurer = frame.textMeasurer,
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

private fun DrawScope.drawReplayCaption(
    text: String,
    center: Offset,
    scale: Float,
    color: Color,
    textMeasurer: TextMeasurer,
) {
    val label = textMeasurer.measure(
        AnnotatedString(text),
        style = TextStyle(fontSize = (CAPTION_FONT_SP * scale).sp, fontWeight = FontWeight.Black, color = color),
    )
    // Soft backing so it reads over any base.
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.45f),
        topLeft = center - Offset(label.size.width / 2f + 16.dp.toPx(), label.size.height / 2f + 8.dp.toPx()),
        size = Size(label.size.width + 32.dp.toPx(), label.size.height + 16.dp.toPx()),
        cornerRadius = CornerRadius(16.dp.toPx()),
    )
    drawText(
        label,
        topLeft = center - Offset(label.size.width / 2f, label.size.height / 2f),
    )
}

private fun DrawScope.drawCaptionCard(
    text: String,
    fullText: String,
    center: Offset,
    colors: AppColors,
    textMeasurer: TextMeasurer,
) {
    // Measure the full caption once so the card does not jump as words appear.
    val full = textMeasurer.measure(
        AnnotatedString(fullText),
        style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.label),
    )
    val pad = CAPTION_PAD_DP.toPx()
    val cardW = (full.size.width + pad * 2f).coerceAtMost(size.width - 64.dp.toPx())
    val cardH = full.size.height + pad * 2f
    drawRoundRect(
        color = colors.cardSurface,
        topLeft = center - Offset(cardW / 2f, cardH / 2f),
        size = Size(cardW, cardH),
        cornerRadius = CornerRadius(18.dp.toPx()),
    )
    val visible = textMeasurer.measure(
        AnnotatedString(text),
        style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.label),
    )
    drawText(
        textMeasurer,
        visible,
        topLeft = center - Offset(visible.size.width / 2f, visible.size.height / 2f),
    )
}

private const val REPLAY_GRAB_MS = 800L
private const val REPLAY_CAPTION_MS = 2500L
private const val REPLAY_REACTION_MS = 3500L
private const val CAPTION_WORD_MS = 220L

private const val SCRIM_FADE_MS = 400f
private const val SCRIM_MAX_ALPHA = 0.72f
private const val REPLAY_BUDDY_Y_FRAC = 0.38f
private const val GRAB_STRETCH = 0.2f
private const val GRAB_ZOOM = 0.08f
private const val RING_MAX_ALPHA = 0.5f
private const val RING_RADIUS_FRAC = 0.7f
private const val CAPTION_DY_FRAC = 0.9f
private const val CAPTION_SCALE_BASE = 0.8f
private const val CAPTION_SCALE_GROW = 0.4f
private const val CAPTION_CARD_Y_FRAC = 0.62f
private const val REACTION_OSCILLATIONS = 3f
private const val REACTION_WOBBLE_PX = 6f
private const val WOBBLE_DX_MUL = 4f
private const val STING_CAPTION_DY_FRAC = 0.85f
private const val SPIN_DEGREES = 360f
private const val FRAME_DT_FALLBACK = 0.016f
private const val MILLIS_TO_SECONDS_F = 1000f
private const val DT_MAX_SECONDS = 0.1f
private const val CAPTION_FONT_SP = 34f

private val GOLD = Color(0xFFFFD54F)
private val REPLAY_BUDDY_DP = 150.dp
private val CAPTION_PAD_DP = 16.dp
