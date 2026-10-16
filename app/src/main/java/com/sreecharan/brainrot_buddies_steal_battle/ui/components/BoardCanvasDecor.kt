package com.sreecharan.brainrot_buddies_steal_battle.ui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sreecharan.brainrot_buddies_steal_battle.ui.home.BoardCanvasState
import com.sreecharan.brainrot_buddies_steal_battle.ui.home.CoachMarkUi
import com.sreecharan.brainrot_buddies_steal_battle.ui.home.CoachTarget
import com.sreecharan.brainrot_buddies_steal_battle.ui.home.FloatUpAnchor
import com.sreecharan.brainrot_buddies_steal_battle.ui.home.FloatUpUi
import com.sreecharan.brainrot_buddies_steal_battle.ui.home.TourModeUi
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.AppColors
import kotlin.math.PI
import kotlin.math.sin

/** Overlays and camera work for the board canvas: dome, spotlight, float-ups, coach mark. */

/** Tour camera: horizontal pan plus a gentle zoom around the grid center. */
internal fun DrawScope.withCamera(
    layout: BoardLayout,
    camera: CameraState,
    draw: DrawScope.() -> Unit,
) {
    val pivot = layout.gridCenter()
    translate(-camera.panXFraction * size.width, 0f) {
        scale(camera.zoom, camera.zoom, pivot = pivot) {
            draw()
        }
    }
}

/** Maps a board point through the same camera transform (for overlays). */
internal fun cameraPoint(layout: BoardLayout, camera: CameraState, width: Float, point: Offset): Offset {
    val pivot = layout.gridCenter()
    val panned = point + Offset(-camera.panXFraction * width, 0f)
    return pivot + (panned - pivot) * camera.zoom
}

internal fun DrawScope.drawShieldDome(
    layout: BoardLayout,
    board: BoardCanvasState,
    tUptime: Long,
    colors: AppColors,
    textMeasurer: TextMeasurer,
) {
    val gridBottom = layout.gridTop + GRID_ROWS * (layout.cell + layout.spacing)
    val domeTopLeft = Offset(
        layout.gridLeft - layout.cell * DOME_PAD_FRAC,
        layout.gridTop - layout.cell * DOME_TOP_PAD_FRAC,
    )
    val domeSize = Size(
        GRID_COLS * (layout.cell + layout.spacing) + layout.cell * DOME_W_PAD_FRAC,
        (gridBottom - layout.gridTop) + layout.cell * DOME_H_PAD_FRAC,
    )
    // Dome: translucent arc over the whole grid.
    drawArc(
        color = SHIELD_DOME_BLUE.copy(alpha = DOME_FILL_ALPHA),
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = true,
        topLeft = domeTopLeft,
        size = domeSize,
    )
    drawArc(
        color = SHIELD_DOME_BLUE.copy(alpha = DOME_OUTLINE_ALPHA),
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = domeTopLeft,
        size = domeSize,
        style = Stroke(width = 3.dp.toPx()),
    )
    // Shimmer sweep, slow and calm.
    val sweep = (tUptime % SHIMMER_PERIOD_MS) / SHIMMER_PERIOD_MS.toFloat()
    drawArc(
        color = Color.White.copy(alpha = 0.35f * (1f - sweep)),
        startAngle = 180f + sweep * 160f,
        sweepAngle = 20f,
        useCenter = false,
        topLeft = domeTopLeft,
        size = domeSize,
        style = Stroke(width = 6.dp.toPx()),
    )
    // Countdown under the dome (§1: visible dome + countdown).
    val remainingMs = (board.shieldEndsAtMs - board.nowMs).coerceAtLeast(0L)
    val hours = remainingMs / MILLIS_PER_HOUR
    val minutes = (remainingMs % MILLIS_PER_HOUR) / MILLIS_PER_MINUTE
    val text = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    val label = textMeasurer.measure(
        AnnotatedString(text),
        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.secondaryLabel),
    )
    drawText(
        textMeasurer,
        label,
        topLeft = Offset(
            layout.gridLeft + GRID_COLS * (layout.cell + layout.spacing) / 2f - label.size.width / 2f,
            gridBottom + 4.dp.toPx(),
        ),
    )
}

internal fun DrawScope.drawTourSpotlight(
    layout: BoardLayout,
    tour: TourModeUi,
    camera: CameraState,
    tUptime: Long,
    colors: AppColors,
) {
    val center = cameraPoint(layout, camera, size.width, layout.slotCenter(tour.spotlightSlot))
    val pulse = 1f + SPOTLIGHT_PULSE * sin(2f * PI.toFloat() * tUptime / SPOTLIGHT_PERIOD_MS)
    drawCircle(
        color = colors.accent.copy(alpha = 0.7f),
        radius = layout.cell * SPOTLIGHT_RING_FRAC * pulse * camera.zoom,
        center = center,
        style = Stroke(width = 4.dp.toPx()),
    )
}

internal fun DrawScope.drawFloatUps(
    layout: BoardLayout,
    board: BoardCanvasState,
    tUptime: Long,
    textMeasurer: TextMeasurer,
    colors: AppColors,
) {
    board.floatUps.forEach { item ->
        val t = (tUptime - item.startUptimeMs).coerceAtLeast(0L)
        val p = (t / item.durationMs.toFloat()).coerceIn(0f, 1f)
        if (p >= 1f) return@forEach
        val anchor = when (val a = item.anchor) {
            is FloatUpAnchor.Slot -> layout.slotCenter(a.index)
            is FloatUpAnchor.Conveyor -> layout.laneCenter(1, LANE_MID_PROGRESS)
            FloatUpAnchor.Vault -> Offset(size.width / 2f, 0f)
            FloatUpAnchor.Center -> Offset(size.width / 2f, size.height / 2f)
        }
        val rise = FLOAT_RISE_DP.toPx() * p
        val alpha = if (p > FLOAT_FADE_START) 1f - (p - FLOAT_FADE_START) / FLOAT_FADE_RANGE else 1f
        val label = textMeasurer.measure(
            AnnotatedString(item.text),
            style = TextStyle(
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = colors.label.copy(alpha = alpha),
            ),
        )
        drawText(
            textMeasurer,
            label,
            topLeft = anchor + Offset(-label.size.width / 2f, -label.size.height / 2f - rise),
        )
    }
}

internal fun DrawScope.drawCoachMark(
    layout: BoardLayout,
    mark: CoachMarkUi,
    tUptime: Long,
    colors: AppColors,
    textMeasurer: TextMeasurer,
) {
    val target = when (mark.target) {
        CoachTarget.CONVEYOR -> Offset(size.width / 2f, layout.beltTop + layout.beltHeight / 2f)
        CoachTarget.GRID -> Offset(size.width / 2f, layout.gridTop + layout.cell)
        CoachTarget.VAULT -> Offset(size.width / 2f, 0f)
    }
    // Pulsing arrow: "tap me now" without a single word of tutorial (§11).
    val bob = sin(2f * PI.toFloat() * tUptime / COACH_BOB_PERIOD_MS) * COACH_BOB_DP.toPx()
    val arrowC = target + Offset(0f, -layout.buddySize - COACH_ARROW_OFFSET_DP.toPx() + bob)
    val s = COACH_ARROW_SIZE_DP.toPx()
    drawPath(
        path = Path().apply {
            moveTo(arrowC.x - s, arrowC.y - s)
            lineTo(arrowC.x + s, arrowC.y - s)
            lineTo(arrowC.x, arrowC.y + s)
            close()
        },
        color = colors.accent,
    )
    val label = textMeasurer.measure(
        AnnotatedString(mark.text),
        style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White),
    )
    val pad = COACH_PAD_DP.toPx()
    val bgTopLeft = Offset(
        arrowC.x - label.size.width / 2f - pad,
        arrowC.y - s - label.size.height - pad * 2f,
    )
    drawRoundRect(
        color = colors.accent,
        topLeft = bgTopLeft,
        size = Size(label.size.width + pad * 2f, label.size.height + pad * 2f),
        cornerRadius = CornerRadius(12.dp.toPx()),
    )
    drawText(textMeasurer, label, topLeft = bgTopLeft + Offset(pad, pad))
}

internal fun DrawScope.drawPadlock(center: Offset, size: Float, color: Color) {
    val bodyW = size
    val bodyH = size * PADLOCK_BODY_H_FRAC
    val bodyTop = center.y - bodyH / 2f + size * PADLOCK_SHACKLE_FRAC
    drawRoundRect(
        color = color,
        topLeft = Offset(center.x - bodyW / 2f, bodyTop),
        size = Size(bodyW, bodyH),
        cornerRadius = CornerRadius(size * PADLOCK_CORNER_FRAC),
    )
    drawArc(
        color = color,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(center.x - bodyW * PADLOCK_SHACKLE_W_FRAC, bodyTop - bodyW * PADLOCK_SHACKLE_W_FRAC),
        size = Size(bodyW * PADLOCK_SHACKLE_SIZE_FRAC, bodyW * PADLOCK_SHACKLE_SIZE_FRAC),
        style = Stroke(width = size * PADLOCK_STROKE_FRAC),
    )
}

private const val PADLOCK_BODY_H_FRAC = 0.8f
private const val PADLOCK_SHACKLE_FRAC = 0.25f
private const val PADLOCK_CORNER_FRAC = 0.15f
private const val PADLOCK_SHACKLE_W_FRAC = 0.32f
private const val PADLOCK_SHACKLE_SIZE_FRAC = 0.64f
private const val PADLOCK_STROKE_FRAC = 0.16f
private const val SHIMMER_PERIOD_MS = 4000L
private const val MILLIS_PER_HOUR = 3_600_000L
private const val MILLIS_PER_MINUTE = 60_000L
private const val DOME_PAD_FRAC = 0.3f
private const val DOME_TOP_PAD_FRAC = 0.9f
private const val DOME_W_PAD_FRAC = 0.6f
private const val DOME_H_PAD_FRAC = 1.2f
private const val DOME_FILL_ALPHA = 0.16f
private const val DOME_OUTLINE_ALPHA = 0.45f
private const val SPOTLIGHT_PULSE = 0.06f
private const val SPOTLIGHT_PERIOD_MS = 900f
private const val SPOTLIGHT_RING_FRAC = 0.68f
private const val FLOAT_FADE_START = 0.7f
private const val FLOAT_FADE_RANGE = 0.3f
private const val LANE_MID_PROGRESS = 0.5f
private const val COACH_BOB_PERIOD_MS = 700f

private val SHIELD_DOME_BLUE = Color(0xFF4FC3F7)
private val FLOAT_RISE_DP = 24.dp
private val COACH_BOB_DP = 8.dp
private val COACH_ARROW_OFFSET_DP = 28.dp
private val COACH_ARROW_SIZE_DP = 14.dp
private val COACH_PAD_DP = 10.dp
