package com.sreecharan.brainrot_buddies_steal_battle.ui.components

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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

/** Everything needed to draw one buddy sprite, in one value. */
@Immutable
data class BuddySpriteSpec(
    val image: ImageBitmap?,
    val center: Offset,
    val sizePx: Float,
    val seed: Long,
    val rarity: Rarity,
    val gaze: Offset = Offset.Zero,
    val blink: Float = 0f,
    val alpha: Float = 1f,
    val rotationDeg: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
)

/**
 * Draws the buddy sprite centered at [BuddySpriteSpec.center] with
 * [BuddySpriteSpec.sizePx] diameter. Falls back to a procedural buddy
 * (seed-deterministic, rarity-tinted) when [BuddySpriteSpec.image] is null.
 * [BuddySpriteSpec.gaze] offsets the pupils for the idle-life saccades;
 * [BuddySpriteSpec.blink] 0..1 closes the lids (procedural fallback only).
 */
fun DrawScope.drawBuddySprite(spec: BuddySpriteSpec) {
    if (spec.rotationDeg == 0f && spec.scaleX == 1f && spec.scaleY == 1f) {
        drawBuddySpriteInner(spec)
        return
    }
    drawContext.canvas.save()
    drawContext.canvas.translate(spec.center.x, spec.center.y)
    drawContext.canvas.rotate(spec.rotationDeg)
    drawContext.canvas.scale(spec.scaleX, spec.scaleY)
    drawContext.canvas.translate(-spec.center.x, -spec.center.y)
    drawBuddySpriteInner(spec)
    drawContext.canvas.restore()
}

/**
 * Draws [spec], resolving the bitmap from [images] by [imageRes] first.
 * Applies the canvas transform only when the pose actually transforms.
 */
internal fun DrawScope.drawBuddyWithPose(
    images: Map<Int, ImageBitmap?>,
    imageRes: Int,
    spec: BuddySpriteSpec,
) {
    val resolved = spec.copy(image = images[imageRes])
    if (resolved.rotationDeg == 0f && resolved.scaleX == 1f && resolved.scaleY == 1f) {
        drawBuddySprite(resolved)
        return
    }
    drawContext.canvas.save()
    drawContext.canvas.translate(resolved.center.x, resolved.center.y)
    drawContext.canvas.rotate(resolved.rotationDeg)
    drawContext.canvas.scale(resolved.scaleX, resolved.scaleY)
    drawContext.canvas.translate(-resolved.center.x, -resolved.center.y)
    drawBuddySprite(resolved)
    drawContext.canvas.restore()
}

private fun DrawScope.drawBuddySpriteInner(spec: BuddySpriteSpec) {    val image = spec.image
    if (image != null) {
        val dstSize = IntSize(spec.sizePx.toInt(), spec.sizePx.toInt())
        drawImage(
            image = image,
            dstOffset = IntOffset(
                (spec.center.x - spec.sizePx / HALF).toInt(),
                (spec.center.y - spec.sizePx / HALF).toInt(),
            ),
            dstSize = dstSize,
            alpha = spec.alpha,
        )
    } else {
        drawProceduralBuddy(spec)
    }
}

/**
 * The pre-art-drop buddy: a round body tinted by rarity, two eyes with
 * saccade-driven pupils, a smile. Deterministic from [BuddySpriteSpec.seed]
 * so screenshots are stable. Cute enough to ship against, honest about
 * being temporary.
 */
fun DrawScope.drawProceduralBuddy(spec: BuddySpriteSpec) {
    val radius = spec.sizePx / HALF
    val bodyColor = when (spec.rarity) {
        Rarity.COMMON -> Color(COMMON_BODY)
        Rarity.RARE -> Color(RARE_BODY)
        Rarity.EPIC -> Color(EPIC_BODY)
    }.copy(alpha = spec.alpha)
    // Body hue shifts slightly per buddy so 34 commons do not look identical.
    val hueShift = (abs(spec.seed % HUE_SEED_RANGE) - HUE_SEED_HALF) / HUE_DIVISOR
    val body = Color(
        red = (bodyColor.red + hueShift).coerceIn(0f, 1f),
        green = bodyColor.green,
        blue = (bodyColor.blue - hueShift).coerceIn(0f, 1f),
        alpha = spec.alpha,
    )
    drawCircle(body, radius, spec.center)

    val eyeY = spec.center.y - radius * EYE_Y_FRAC
    val eyeDX = radius * EYE_DX_FRAC
    val eyeR = radius * EYE_R_FRAC
    val lid = spec.blink.coerceIn(0f, 1f)
    for (side in listOf(-1f, 1f)) {
        val eyeCenter = Offset(spec.center.x + eyeDX * side, eyeY)
        if (lid < LID_CLOSED_THRESHOLD) {
            drawCircle(Color.White.copy(alpha = spec.alpha), eyeR, eyeCenter)
            val pupilR = eyeR * PUPIL_FRAC * (1f - lid * LID_SHRINK)
            drawCircle(
                Color(INK).copy(alpha = spec.alpha),
                pupilR,
                eyeCenter + spec.gaze * eyeR * GAZE_FRAC,
            )
        } else {
            drawLine(
                Color(INK).copy(alpha = spec.alpha),
                eyeCenter + Offset(-eyeR, 0f),
                eyeCenter + Offset(eyeR, 0f),
                strokeWidth = radius * LID_STROKE_FRAC,
            )
        }
    }
    // Smile.
    drawArc(
        color = Color(INK).copy(alpha = spec.alpha * SMILE_ALPHA),
        startAngle = SMILE_START_DEG,
        sweepAngle = SMILE_SWEEP_DEG,
        useCenter = false,
        topLeft = Offset(spec.center.x - radius * SMILE_W_FRAC, spec.center.y - radius * SMILE_Y_FRAC),
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
