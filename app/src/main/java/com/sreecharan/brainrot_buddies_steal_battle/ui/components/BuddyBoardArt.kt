package com.sreecharan.brainrot_buddies_steal_battle.ui.components

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.sreecharan.brainrot_buddies_steal_battle.data.model.Rarity
import kotlin.math.abs

/**
 * Loads the buddy image from its real `R.drawable` id (contract §6), or
 * null when [imageRes] is 0 (the caller draws [drawProceduralBuddy]
 * instead). Decoding happens once per res id and is remembered across
 * frames. No placeholder drawable files are ever created.
 */
@Composable
fun rememberBuddyImage(imageRes: Int): ImageBitmap? {
    val context = LocalContext.current
    return remember(imageRes) {
        if (imageRes == 0) {
            null
        } else {
            runCatching {
                BitmapFactory.decodeResource(context.resources, imageRes)?.asImageBitmap()
            }.getOrNull()
        }
    }
}

/**
 * Draws the buddy sprite centered at [center] with [sizePx] diameter.
 * Falls back to a procedural buddy (seed-deterministic, rarity-tinted) when
 * [image] is null. [gaze] offsets the pupils for the idle-life saccades;
 * [blink] 0..1 closes the lids (procedural fallback only).
 */
fun DrawScope.drawBuddySprite(
    image: ImageBitmap?,
    center: Offset,
    sizePx: Float,
    seed: Long,
    rarity: Rarity,
    gaze: Offset = Offset.Zero,
    blink: Float = 0f,
    alpha: Float = 1f,
    rotationDeg: Float = 0f,
    scaleX: Float = 1f,
    scaleY: Float = 1f,
) {
    if (rotationDeg == 0f && scaleX == 1f && scaleY == 1f) {
        drawBuddySpriteInner(image, center, sizePx, seed, rarity, gaze, blink, alpha)
        return
    }
    drawContext.canvas.save()
    drawContext.canvas.translate(center.x, center.y)
    drawContext.canvas.rotate(rotationDeg)
    drawContext.canvas.scale(scaleX, scaleY)
    drawContext.canvas.translate(-center.x, -center.y)
    drawBuddySpriteInner(image, center, sizePx, seed, rarity, gaze, blink, alpha)
    drawContext.canvas.restore()
}

private fun DrawScope.drawBuddySpriteInner(
    image: ImageBitmap?,
    center: Offset,
    sizePx: Float,
    seed: Long,
    rarity: Rarity,
    gaze: Offset = Offset.Zero,
    blink: Float = 0f,
    alpha: Float = 1f,
) {
    if (image != null) {
        val dstSize = IntSize(sizePx.toInt(), sizePx.toInt())
        drawImage(
            image = image,
            dstOffset = IntOffset(
                (center.x - sizePx / HALF).toInt(),
                (center.y - sizePx / HALF).toInt(),
            ),
            dstSize = dstSize,
            alpha = alpha,
        )
    } else {
        drawProceduralBuddy(center, sizePx, seed, rarity, gaze, blink, alpha)
    }
}

/**
 * The pre-art-drop buddy: a round body tinted by rarity, two eyes with
 * saccade-driven pupils, a smile. Deterministic from [seed] so screenshots
 * are stable. Cute enough to ship against, honest about being temporary.
 */
fun DrawScope.drawProceduralBuddy(
    center: Offset,
    sizePx: Float,
    seed: Long,
    rarity: Rarity,
    gaze: Offset = Offset.Zero,
    blink: Float = 0f,
    alpha: Float = 1f,
) {
    val radius = sizePx / HALF
    val bodyColor = when (rarity) {
        Rarity.COMMON -> Color(COMMON_BODY)
        Rarity.RARE -> Color(RARE_BODY)
        Rarity.EPIC -> Color(EPIC_BODY)
    }.copy(alpha = alpha)
    // Body hue shifts slightly per buddy so 34 commons do not look identical.
    val hueShift = (abs(seed % HUE_SEED_RANGE) - HUE_SEED_HALF) / HUE_DIVISOR
    val body = Color(
        red = (bodyColor.red + hueShift).coerceIn(0f, 1f),
        green = bodyColor.green,
        blue = (bodyColor.blue - hueShift).coerceIn(0f, 1f),
        alpha = alpha,
    )
    drawCircle(body, radius, center)

    val eyeY = center.y - radius * EYE_Y_FRAC
    val eyeDX = radius * EYE_DX_FRAC
    val eyeR = radius * EYE_R_FRAC
    val lid = blink.coerceIn(0f, 1f)
    for (side in listOf(-1f, 1f)) {
        val eyeCenter = Offset(center.x + eyeDX * side, eyeY)
        if (lid < LID_CLOSED_THRESHOLD) {
            drawCircle(Color.White.copy(alpha = alpha), eyeR, eyeCenter)
            val pupilR = eyeR * PUPIL_FRAC * (1f - lid * LID_SHRINK)
            drawCircle(
                Color(INK).copy(alpha = alpha),
                pupilR,
                eyeCenter + gaze * eyeR * GAZE_FRAC,
            )
        } else {
            drawLine(
                Color(INK).copy(alpha = alpha),
                eyeCenter + Offset(-eyeR, 0f),
                eyeCenter + Offset(eyeR, 0f),
                strokeWidth = radius * LID_STROKE_FRAC,
            )
        }
    }
    // Smile.
    drawArc(
        color = Color(INK).copy(alpha = alpha * SMILE_ALPHA),
        startAngle = SMILE_START_DEG,
        sweepAngle = SMILE_SWEEP_DEG,
        useCenter = false,
        topLeft = Offset(center.x - radius * SMILE_W_FRAC, center.y - radius * SMILE_Y_FRAC),
        size = androidx.compose.ui.geometry.Size(radius * SMILE_W, radius * SMILE_H),
        style = Stroke(width = radius * SMILE_STROKE_FRAC),
    )
}

private const val HALF = 2f
private const val COMMON_BODY = 0xFF9CCC65
private const val RARE_BODY = 0xFF4FC3F7
private const val EPIC_BODY = 0xFFBA68C8
private const val INK = 0xFF1D1D1F
private const val HUE_SEED_RANGE = 40L
private const val HUE_SEED_HALF = 20L
private const val HUE_DIVISOR = 200f
private const val EYE_Y_FRAC = 0.15f
private const val EYE_DX_FRAC = 0.36f
private const val EYE_R_FRAC = 0.20f
private const val LID_CLOSED_THRESHOLD = 0.9f
private const val PUPIL_FRAC = 0.5f
private const val LID_SHRINK = 0.5f
private const val GAZE_FRAC = 0.5f
private const val LID_STROKE_FRAC = 0.08f
private const val SMILE_ALPHA = 0.8f
private const val SMILE_START_DEG = 20f
private const val SMILE_SWEEP_DEG = 140f
private const val SMILE_W_FRAC = 0.45f
private const val SMILE_Y_FRAC = 0.1f
private const val SMILE_W = 0.9f
private const val SMILE_H = 0.7f
private const val SMILE_STROKE_FRAC = 0.07f
