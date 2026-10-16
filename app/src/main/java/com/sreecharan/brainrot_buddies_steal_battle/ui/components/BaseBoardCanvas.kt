package com.sreecharan.brainrot_buddies_steal_battle.ui.components

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sreecharan.brainrot_buddies_steal_battle.ui.home.BoardCanvasState
import com.sreecharan.brainrot_buddies_steal_battle.data.model.Rarity
import com.sreecharan.brainrot_buddies_steal_battle.ui.home.ClaimAnimUi
import com.sreecharan.brainrot_buddies_steal_battle.ui.home.CoachMarkUi
import com.sreecharan.brainrot_buddies_steal_battle.ui.home.CoachTarget
import com.sreecharan.brainrot_buddies_steal_battle.ui.home.ConveyorBuddyUi
import com.sreecharan.brainrot_buddies_steal_battle.ui.home.FloatUpAnchor
import com.sreecharan.brainrot_buddies_steal_battle.ui.home.FloatUpUi
import com.sreecharan.brainrot_buddies_steal_battle.ui.home.SlotUi
import com.sreecharan.brainrot_buddies_steal_battle.ui.home.TourModeUi
import com.sreecharan.brainrot_buddies_steal_battle.ui.home.WiggleUi
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.AppColors
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.LocalAppColors
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * The whole game board in one Canvas: conveyor, 24-slot grid, shield dome,
 * claim arcs, particles, trauma shake and idle life. 60 fps by construction:
 * the only per-frame state read is [frameTime], observed inside the draw
 * scope (redraw, not recomposition), and every per-buddy idle value is pure
 * math from (seed, time) with zero allocation.
 *
 * Tap targets: creatures get an invisible 1.5x forgiving hit zone (§11);
 * slots are >= 56 dp.
 */
@Composable
fun BaseBoardCanvas(
    board: BoardCanvasState,
    frameTime: State<Long>,
    camera: CameraState,
    juice: JuiceController,
    reduceMotion: Boolean,
    onConveyorTap: (String) -> Unit,
    onSlotTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val minTargetPx = remember(density) { with(density) { MIN_TARGET_DP.toPx() } }

    // Wall clock (data layer) -> uptime (animation clock) offset.
    var clockOffset by remember { mutableLongStateOf(SystemClock.uptimeMillis() - System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(CLOCK_SYNC_MS)
            clockOffset = SystemClock.uptimeMillis() - System.currentTimeMillis()
        }
    }

    // Buddy art, keyed stably so sprites survive list churn.
    val imageResIds = remember(board.conveyor, board.slots, board.claimAnims) {
        buildSet {
            board.conveyor.forEach { add(it.imageRes) }
            board.slots.forEach { slot -> slot.buddy?.let { add(it.imageRes) } }
            board.claimAnims.values.forEach { add(it.imageRes) }
        }
    }
    val images = remember { mutableStateMapOf<Int, ImageBitmap?>() }
    imageResIds.forEach { imageRes ->
        key(imageRes) {
            val image = rememberBuddyImage(imageRes)
            SideEffect { images[imageRes] = image }
        }
    }
    LaunchedEffect(imageResIds) {
        images.keys.retainAll(imageResIds)
    }

    val layoutHolder = remember { LayoutHolder() }
    val phaseTracker = remember { mutableMapOf<String, Int>() }
    val lastFrame = remember { mutableLongStateOf(0L) }
    val latestTaps by rememberUpdatedState(onConveyorTap to onSlotTap)
    val latestOffset by rememberUpdatedState(clockOffset)
    val latestMinTarget by rememberUpdatedState(minTargetPx)

    Canvas(
        modifier = modifier.pointerInput(board.conveyor, board.slots) {
            detectTapGestures { tap ->
                val layout = layoutHolder.layout ?: return@detectTapGestures
                val (onConveyor, onSlot) = latestTaps
                val uptimeNow = SystemClock.uptimeMillis()
                // Conveyor first: creatures get the 1.5x forgiving hit zone.
                val hit = board.conveyor.asReversed().firstOrNull { item ->
                    val progress = rideProgress(item, uptimeNow, latestOffset)
                    if (progress !in 0f..1f) {
                        return@firstOrNull false
                    }
                    val center = layout.laneCenter(item.lane, progress)
                    val radius = layout.buddySize * HIT_ZONE_SCALE
                    (tap - center).getDistance() <= radius
                }
                if (hit != null) {
                    onConveyor(hit.id)
                    return@detectTapGestures
                }
                val col = ((tap.x - layout.gridLeft) / (layout.cell + layout.spacing)).toInt()
                val row = ((tap.y - layout.gridTop) / (layout.cell + layout.spacing)).toInt()
                if (col in 0 until GRID_COLS && row in 0 until GRID_ROWS) {
                    val index = row * GRID_COLS + col
                    val slot = board.slots.getOrNull(index) ?: return@detectTapGestures
                    // 56 dp minimum tap target: expand the hit rect when the
                    // visual cell is smaller.
                    val center = layout.slotCenter(index)
                    val half = (latestMinTarget / 2f).coerceAtLeast(layout.cell / 2f)
                    if (abs(tap.x - center.x) <= half && abs(tap.y - center.y) <= half) {
                        onSlot(slot.index)
                    }
                }
            }
        },
    ) {
        val tUptime = frameTime.value
        val dtSeconds = run {
            val last = lastFrame.longValue
            lastFrame.longValue = tUptime
            if (last == 0L) FRAME_DT_FALLBACK else ((tUptime - last) / MILLIS_TO_SECONDS_F).coerceIn(0f, DT_MAX_SECONDS)
        }

        val layout = computeLayout()
        layoutHolder.layout = layout
        // Drop phase entries for finished claim anims.
        phaseTracker.keys.retainAll(board.claimAnims.keys)

        if (!reduceMotion) {
            juice.shake.update(dtSeconds)
        }
        val shakeOffset = if (reduceMotion) {
            Offset.Zero
        } else {
            juice.shake.offset(tUptime, SHAKE_MAX_PX)
        }

        drawBoardBackground(colors)

        translate(shakeOffset.x, shakeOffset.y) {
            // Tour camera: pan + gentle zoom over the board.
            withCamera(layout, camera) {
                drawConveyor(layout, board, tUptime, latestOffset, colors, images, reduceMotion)
                drawGrid(layout, board, tUptime, colors, images, textMeasurer, reduceMotion)
                if (board.shieldDome) {
                    drawShieldDome(layout, board, tUptime, colors, textMeasurer)
                }
                drawClaimAnims(layout, board, tUptime, images, juice, phaseTracker, reduceMotion)
            }
            // Particles shake with the board that spawned them.
            juice.particles.update(dtSeconds)
            juice.particles.draw(this)
            if (board.tour != null) {
                drawTourSpotlight(layout, board.tour, camera, tUptime, colors)
            }
        }

        drawFloatUps(layout, board, tUptime, textMeasurer, colors)
        if (board.coachMark != null) {
            drawCoachMark(layout, board.coachMark, tUptime, colors, textMeasurer)
        }
    }
}

/** Everything the canvas draws, as one immutable snapshot. */
@Immutable
data class BoardCanvasState(
    val conveyor: ImmutableList<ConveyorBuddyUi>,
    val slots: ImmutableList<SlotUi>,
    val claimAnims: ImmutableMap<String, ClaimAnimUi>,
    val waddleAnims: ImmutableMap<String, Long>,
    val boingAnims: ImmutableMap<String, Long>,
    val floatUps: ImmutableList<FloatUpUi>,
    val wiggleSlot: WiggleUi?,
    val shieldDome: Boolean,
    val shieldEndsAtMs: Long,
    val tour: TourModeUi?,
    val coachMark: CoachMarkUi?,
    val nowMs: Long,
    val nextSpawnMs: Long,
)

@Immutable
data class CameraState(val panXFraction: Float = 0f, val zoom: Float = 1f)

// ------------------------------------------------------------------
// Layout
// ------------------------------------------------------------------

private class LayoutHolder {
    var layout: BoardLayout? = null
}

private data class BoardLayout(
    val beltTop: Float,
    val beltHeight: Float,
    val laneX: FloatArray,
    val gridTop: Float,
    val gridLeft: Float,
    val cell: Float,
    val spacing: Float,
    val buddySize: Float,
) {
    fun slotCenter(index: Int): Offset {
        val col = index % GRID_COLS
        val row = index / GRID_COLS
        return Offset(
            gridLeft + col * (cell + spacing) + cell / 2f,
            gridTop + row * (cell + spacing) + cell / 2f,
        )
    }

    fun laneCenter(lane: Int, progress: Float): Offset = Offset(
        laneX[lane.coerceIn(0, CONVEYOR_LANES - 1)],
        beltTop + progress.coerceIn(0f, 1f) * beltHeight,
    )

    fun gridCenter(): Offset = Offset(
        gridLeft + (GRID_COLS * (cell + spacing) - spacing) / 2f,
        gridTop + (GRID_ROWS * (cell + spacing) - spacing) / 2f,
    )
}

private fun DrawScope.computeLayout(): BoardLayout {
    val padding = PAD_DP.toPx()
    val beltHeight = BELT_HEIGHT_DP.toPx()
    val beltTop = padding
    val laneX = FloatArray(CONVEYOR_LANES) { i ->
        padding + (size.width - padding * 2f) * (i + 1f) / (CONVEYOR_LANES + 1f)
    }
    val gridTop = beltTop + beltHeight + padding
    val spacing = GRID_SPACING_DP.toPx()
    val gridWidth = size.width - padding * 2f
    val gridHeight = size.height - gridTop - padding
    val cell = minOf(
        (gridWidth - spacing * (GRID_COLS - 1)) / GRID_COLS,
        (gridHeight - spacing * (GRID_ROWS - 1)) / GRID_ROWS,
    ).coerceAtLeast(MIN_CELL_DP.toPx())
    val gridLeft = padding + (gridWidth - (GRID_COLS * cell + (GRID_COLS - 1) * spacing)) / 2f
    return BoardLayout(
        beltTop = beltTop,
        beltHeight = beltHeight,
        laneX = laneX,
        gridTop = gridTop,
        gridLeft = gridLeft,
        cell = cell,
        spacing = spacing,
        buddySize = cell * BUDDY_CELL_FRAC,
    )
}

/** Tour camera: horizontal pan plus a gentle zoom around the grid center. */
private fun DrawScope.withCamera(
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
private fun cameraPoint(layout: BoardLayout, camera: CameraState, width: Float, point: Offset): Offset {
    val pivot = layout.gridCenter()
    val panned = point + Offset(-camera.panXFraction * width, 0f)
    return pivot + (panned - pivot) * camera.zoom
}

// ------------------------------------------------------------------
// Background + conveyor
// ------------------------------------------------------------------

private fun DrawScope.drawBoardBackground(colors: AppColors) {
    drawRect(colors.groupedBackground)
}

private fun DrawScope.drawConveyor(
    layout: BoardLayout,
    board: BoardCanvasState,
    tUptime: Long,
    clockOffset: Long,
    colors: AppColors,
    images: Map<Int, ImageBitmap?>,
    reduceMotion: Boolean,
) {
    val beltWidth = size.width - BELT_MARGIN_DP.toPx()
    val beltLeft = PAD_DP.toPx()
    drawRoundRect(
        color = colors.cardSurface,
        topLeft = Offset(beltLeft, layout.beltTop),
        size = Size(beltWidth, layout.beltHeight),
        cornerRadius = CornerRadius(20.dp.toPx()),
    )
    drawRoundRect(
        color = colors.secondaryLabel.copy(alpha = 0.25f),
        topLeft = Offset(beltLeft, layout.beltTop),
        size = Size(beltWidth, layout.beltHeight),
        cornerRadius = CornerRadius(20.dp.toPx()),
        style = Stroke(width = 2.dp.toPx()),
    )
    // Animated chevrons flowing down the belt.
    if (!reduceMotion) {
        val chevronT = (tUptime % CHEVRON_PERIOD_MS) / CHEVRON_PERIOD_MS.toFloat()
        for (i in 0 until CHEVRON_COUNT) {
            val p = (i / CHEVRON_COUNT.toFloat() + chevronT) % 1f
            val y = layout.beltTop + p * layout.beltHeight
            val alpha = (1f - p) * CHEVRON_ALPHA
            val cx = beltLeft + beltWidth / 2f
            val w = CHEVRON_W_DP.toPx()
            val stroke = CHEVRON_STROKE_DP.toPx()
            drawLine(
                colors.secondaryLabel.copy(alpha = alpha),
                Offset(cx - w / 2f, y - 8.dp.toPx()),
                Offset(cx, y),
                strokeWidth = stroke,
            )
            drawLine(
                colors.secondaryLabel.copy(alpha = alpha),
                Offset(cx, y),
                Offset(cx + w / 2f, y - 8.dp.toPx()),
                strokeWidth = stroke,
            )
        }
    }
    // Next-spawn timer: "show the next fun thing, always" (§11).
    val spawnIn = (board.nextSpawnMs - board.nowMs).coerceIn(0L, JuiceTimings.CONVEYOR_CADENCE_MS)
    val spawnP = 1f - spawnIn / JuiceTimings.CONVEYOR_CADENCE_MS.toFloat()
    drawArc(
        color = colors.accent,
        startAngle = -90f,
        sweepAngle = 360f * spawnP,
        useCenter = false,
        topLeft = Offset(beltLeft + beltWidth - 44.dp.toPx(), layout.beltTop + 8.dp.toPx()),
        size = Size(28.dp.toPx(), 28.dp.toPx()),
        style = Stroke(width = 4.dp.toPx()),
    )

    // Riders.
    board.conveyor.forEach { item ->
        val progress = rideProgress(item, tUptime, clockOffset)
        if (progress !in 0f..1f) return@forEach
        val center = layout.laneCenter(item.lane, progress)
        val pose = idlePose(item.seed, tUptime, layout.buddySize, wobbleWalk = !reduceMotion, calm = reduceMotion)
        var scaleX = pose.scaleX
        var scaleY = pose.scaleY
        // Last 3 s: anticipation crouch (§10).
        val remainingMs = JuiceTimings.CONVEYOR_RIDE_MS - progress * JuiceTimings.CONVEYOR_RIDE_MS
        if (remainingMs < JuiceTimings.ANTICIPATION_MS && !reduceMotion) {
            val crouch = CROUCH_HALF + CROUCH_HALF * sin(2f * PI.toFloat() * tUptime / CROUCH_PERIOD_MS)
            scaleY *= 1f - CROUCH_SQUASH_Y * crouch
            scaleX *= 1f + CROUCH_STRETCH_X * crouch
        }
        // Ticket pulls glow gold on the belt.
        if (item.ticketPull) {
            drawCircle(
                Brush.radialGradient(
                    colors = listOf(Color(0xFFFFD54F).copy(alpha = 0.55f), Color.Transparent),
                    center = center,
                    radius = layout.buddySize,
                ),
                radius = layout.buddySize,
                center = center,
            )
        }
        drawBuddyWithPose(
            images = images,
            imageRes = item.imageRes,
            center = center + Offset(0f, pose.offsetY),
            sizePx = layout.buddySize,
            seed = item.seed,
            rarity = item.rarity,
            scaleX = scaleX,
            scaleY = scaleY,
            rotationDeg = pose.rotationDeg,
            gaze = pose.gaze,
            blink = pose.blink,
        )
        // Expiry ring in the last 3 s.
        if (remainingMs < JuiceTimings.ANTICIPATION_MS) {
            val p = 1f - remainingMs / JuiceTimings.ANTICIPATION_MS.toFloat()
            val ringR = layout.buddySize * EXPIRY_RING_FRAC
            drawArc(
                color = colors.warning.copy(alpha = 0.8f),
                startAngle = -90f,
                sweepAngle = 360f * p,
                useCenter = false,
                topLeft = center - Offset(ringR, ringR),
                size = Size(ringR * 2f, ringR * 2f),
                style = Stroke(width = 3.dp.toPx()),
            )
        }
    }
}

private fun rideProgress(item: ConveyorBuddyUi, tUptime: Long, clockOffset: Long): Float {
    val spawnedUptime = item.spawnedAtMs + clockOffset
    return ((tUptime - spawnedUptime).toFloat() / JuiceTimings.CONVEYOR_RIDE_MS).coerceIn(0f, 1f)
}

// ------------------------------------------------------------------
// Grid
// ------------------------------------------------------------------

private fun DrawScope.drawGrid(
    layout: BoardLayout,
    board: BoardCanvasState,
    tUptime: Long,
    colors: AppColors,
    images: Map<String, ImageBitmap?>,
    textMeasurer: TextMeasurer,
    reduceMotion: Boolean,
) {
    board.slots.forEach { slot ->
        val center = layout.slotCenter(slot.index)
        val topLeft = Offset(center.x - layout.cell / 2f, center.y - layout.cell / 2f)
        val corner = CornerRadius(SLOT_CORNER_DP.toPx())

        // Wiggle for locked slots that got tapped (§11: never silence).
        var rotation = 0f
        board.wiggleSlot?.let { wiggle ->
            if (wiggle.slotIndex == slot.index && !reduceMotion) {
                val t = (tUptime - wiggle.startUptimeMs) / WIGGLE_PERIOD_F
                if (t in 0f..1f) {
                    rotation = sin(t * PI.toFloat() * WIGGLE_OSCILLATIONS) * WIGGLE_MAX_DEG * (1f - t)
                }
            }
        }

        if (!slot.unlocked) {
            drawRoundRect(
                color = colors.secondaryLabel.copy(alpha = 0.12f),
                topLeft = topLeft,
                size = Size(layout.cell, layout.cell),
                cornerRadius = corner,
            )
            drawPadlock(center, layout.cell * PADLOCK_FRAC, colors.secondaryLabel.copy(alpha = 0.6f))
            return@forEach
        }

        drawRoundRect(
            color = colors.cardSurface,
            topLeft = topLeft,
            size = Size(layout.cell, layout.cell),
            cornerRadius = corner,
        )

        val buddy = slot.buddy ?: return@forEach
        val isTourSpot = board.tour?.spotlightSlot == slot.index

        // Waddle-in after a claim lands (§1).
        val waddleStart = board.waddleAnims[buddy.uid]
        var scale = 1f
        var hopY = 0f
        if (waddleStart != null && !reduceMotion) {
            val t = ((tUptime - waddleStart) / JuiceTimings.WADDLE_IN_MS.toFloat()).coerceIn(0f, 1f)
            scale = easeOutBack(t)
            hopY = -sin(t * PI.toFloat()) * layout.cell * HOP_FRAC
        }
        // Boing when tapped.
        board.boingAnims[buddy.uid]?.let { start ->
            if (!reduceMotion) {
                val t = ((tUptime - start) / BOING_PERIOD_F).coerceIn(0f, 1f)
                if (t < 1f) {
                    scale *= 1f + BOING_AMPLITUDE * sin(t * PI.toFloat())
                }
            }
        }

        // Rarity flex, readable from 3 feet (§2 V1.4): Rare = blue ring,
        // Epic = golden glow + oversized, L3 = rainbow ring.
        val flexSize = if (buddy.rarity == Rarity.EPIC) layout.buddySize * EPIC_FLEX_SCALE else layout.buddySize
        when {
            buddy.rarity == Rarity.EPIC -> {
                drawCircle(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFFFFD54F).copy(alpha = 0.5f), Color.Transparent),
                        center = center,
                        radius = flexSize,
                    ),
                    radius = flexSize,
                    center = center,
                )
            }
            buddy.level >= 3 && !reduceMotion -> {
                val hue = (tUptime / RAINBOW_STEP_MS % HUE_DEGREES).toFloat()
                drawCircle(
                    color = Color.hsv(hue, 0.7f, 1f, 0.85f),
                    radius = flexSize * RAINBOW_RING_FRAC,
                    center = center,
                    style = Stroke(width = 4.dp.toPx()),
                )
            }
            buddy.rarity == Rarity.RARE -> {
                drawCircle(
                    color = Color(0xFF448AFF).copy(alpha = 0.8f),
                    radius = flexSize * RARE_RING_FRAC,
                    center = center,
                    style = Stroke(width = 3.dp.toPx()),
                )
            }
        }

        val pose = idlePose(
            seed = buddy.seed,
            tMs = tUptime,
            sizePx = flexSize,
            wobbleWalk = false,
            calm = reduceMotion,
            forceAct = isTourSpot && !reduceMotion,
        )
        val dimmed = buddy.stealCooldownEndsAtMs > board.nowMs
        drawBuddyWithPose(
            images = images,
            imageRes = buddy.imageRes,
            center = center + Offset(0f, pose.offsetY + hopY),
            sizePx = flexSize * scale,
            seed = buddy.seed,
            rarity = buddy.rarity,
            scaleX = pose.scaleX * scale,
            scaleY = pose.scaleY * scale,
            rotationDeg = pose.rotationDeg + rotation,
            gaze = pose.gaze,
            blink = pose.blink,
            alpha = if (dimmed) 0.55f else 1f,
        )

        // Steal cooldown: small clock badge.
        if (dimmed) {
            val badgeC = center + Offset(layout.cell * 0.32f, -layout.cell * 0.32f)
            drawCircle(color = colors.cardSurface, radius = 11.dp.toPx(), center = badgeC)
            val handR = BADGE_HAND_DP.toPx()
            drawArc(
                color = colors.secondaryLabel,
                startAngle = -90f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = badgeC - Offset(handR, handR),
                size = Size(handR * 2f, handR * 2f),
                style = Stroke(width = 2.5.dp.toPx()),
            )
        }

        // Level badge.
        if (buddy.level > 1) {
            val badgeR = LEVEL_BADGE_DP.toPx()
            val badgeC = center + Offset(-layout.cell * 0.32f, layout.cell * 0.32f)
            drawCircle(Color(0xFFFFB300), badgeR, badgeC)
            val label = textMeasurer.measure(
                AnnotatedString("L${buddy.level}"),
                style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White),
            )
            drawText(
                textMeasurer,
                label,
                topLeft = badgeC - Offset(label.size.width / 2f, label.size.height / 2f),
            )
        }

        // Per-slot shield: little dome.
        if (slot.shielded) {
            drawArc(
                color = Color(0xFF4FC3F7).copy(alpha = 0.5f),
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(center.x - layout.cell * 0.45f, center.y - layout.cell * 0.1f),
                size = Size(layout.cell * 0.9f, layout.cell * 0.9f),
                style = Stroke(width = 3.dp.toPx()),
            )
        }
    }
}

private fun DrawScope.drawPadlock(center: Offset, size: Float, color: Color) {
    val bodyW = size
    val bodyH = size * PADLOCK_BODY_H_FRAC
    val bodyTop = center.y - bodyH / 2f + size * PADLOCK_SHACKLE_FRAC
    drawRoundRect(
        color = color,
        topLeft = Offset(center.x - bodyW / 2f, bodyTop),
        size = Size(bodyW, bodyH),
        cornerRadius = CornerRadius(size * 0.15f),
    )
    drawArc(
        color = color,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(center.x - bodyW * 0.32f, bodyTop - bodyW * 0.32f),
        size = Size(bodyW * 0.64f, bodyW * 0.64f),
        style = Stroke(width = size * 0.16f),
    )
}

// ------------------------------------------------------------------
// Shield dome
// ------------------------------------------------------------------

private fun DrawScope.drawShieldDome(
    layout: BoardLayout,
    board: BoardCanvasState,
    tUptime: Long,
    colors: AppColors,
    textMeasurer: TextMeasurer,
) {
    val gridBottom = layout.gridTop + GRID_ROWS * (layout.cell + layout.spacing)
    val domeTopLeft = Offset(
        layout.gridLeft - layout.cell * 0.3f,
        layout.gridTop - layout.cell * 0.9f,
    )
    val domeSize = Size(
        GRID_COLS * (layout.cell + layout.spacing) + layout.cell * 0.6f,
        (gridBottom - layout.gridTop) + layout.cell * 1.2f,
    )
    // Dome: translucent arc over the whole grid.
    drawArc(
        color = Color(0xFF4FC3F7).copy(alpha = 0.16f),
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = true,
        topLeft = domeTopLeft,
        size = domeSize,
    )
    drawArc(
        color = Color(0xFF4FC3F7).copy(alpha = 0.45f),
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

// ------------------------------------------------------------------
// Claim animations (§10: 0-60 squash, 60-250 arc, 250-600 landing)
// ------------------------------------------------------------------

private fun DrawScope.drawClaimAnims(
    layout: BoardLayout,
    board: BoardCanvasState,
    tUptime: Long,
    images: Map<Int, ImageBitmap?>,
    juice: JuiceController,
    phaseTracker: MutableMap<String, Int>,
    reduceMotion: Boolean,
) {
    board.claimAnims.values.forEach { anim ->
        val t = tUptime - anim.startUptimeMs
        if (t < 0) return@forEach
        // Epic: 100 ms of 0.3x slow-mo on grab, then catch up seamlessly.
        val te = when {
            reduceMotion || anim.rarity != Rarity.EPIC -> t
            t < JuiceTimings.EPIC_SLOWMO_MS -> (t * JuiceTimings.EPIC_SLOWMO_SCALE).toLong()
            else -> t - SLOWMO_CATCHUP_MS
        }
        val from = layout.laneCenter(anim.fromLane, anim.fromProgress)
        val to = anim.targetSlotIndex?.let { layout.slotCenter(it) }
            ?: Offset(size.width / 2f, layout.gridTop)

        fun phaseEntry(phase: Int, fire: () -> Unit) {
            if ((phaseTracker[anim.instanceId] ?: -1) < phase) {
                phaseTracker[anim.instanceId] = phase
                fire()
            }
        }

        when {
            te < JuiceTimings.CLAIM_SQUASH_MS -> {
                val p = te / JuiceTimings.CLAIM_SQUASH_MS.toFloat()
                drawBuddyWithPose(
                    images = images,
                    imageRes = anim.imageRes,
                    center = from,
                    sizePx = layout.buddySize,
                    seed = anim.seed,
                    rarity = anim.rarity,
                    scaleX = 1f + 0.15f * p,
                    scaleY = 1f - 0.15f * p,
                )
                phaseEntry(0) { juice.stars(from.x, from.y) }
            }
            te < JuiceTimings.CLAIM_SQUASH_MS + JuiceTimings.CLAIM_ARC_MS -> {
                val p = (te - JuiceTimings.CLAIM_SQUASH_MS) / JuiceTimings.CLAIM_ARC_MS.toFloat()
                val eased = easeInOut(p)
                val pos = Offset(
                    from.x + (to.x - from.x) * eased,
                    from.y + (to.y - from.y) * eased - sin(p * PI.toFloat()) * ARC_HEIGHT_PX,
                )
                drawBuddyWithPose(
                    images = images,
                    imageRes = anim.imageRes,
                    center = pos,
                    sizePx = layout.buddySize,
                    seed = anim.seed,
                    rarity = anim.rarity,
                    rotationDeg = p * 18f,
                )
                phaseEntry(1) { juice.stars(from.x, from.y) }
            }
            te < JuiceTimings.CLAIM_TOTAL_MS -> {
                val p = ((te - JuiceTimings.CLAIM_SQUASH_MS - JuiceTimings.CLAIM_ARC_MS) /
                    JuiceTimings.CLAIM_LAND_MS.toFloat()).coerceIn(0f, 1f)
                // Overshoot 1.15 -> 1.0 (§10).
                val overshoot = easeOutBack(p)
                val scale = 1f + LAND_SQUASH * (1f - overshoot)
                drawBuddyWithPose(
                    images = images,
                    imageRes = anim.imageRes,
                    center = to,
                    sizePx = layout.buddySize * scale,
                    seed = anim.seed,
                    rarity = anim.rarity,
                    scaleX = scale,
                    scaleY = 2f - scale,
                )
                phaseEntry(2) {
                    juice.dust(to.x, to.y)
                    if (anim.rarity == Rarity.RARE) juice.blueRing(to.x, to.y)
                    if (anim.rarity == Rarity.EPIC) {
                        juice.goldRays(to.x, to.y, size.width * size.height)
                        if (!reduceMotion) juice.shake.add(EPIC_LAND_TRAUMA)
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------
// Tour spotlight, float-ups, coach mark
// ------------------------------------------------------------------

private fun DrawScope.drawTourSpotlight(
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

private fun DrawScope.drawFloatUps(
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
            is FloatUpAnchor.Conveyor -> layout.laneCenter(1, 0.5f)
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

private fun DrawScope.drawCoachMark(
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

// ------------------------------------------------------------------
// Idle life (contract §10, 3 layers) — pure math from (seed, time)
// ------------------------------------------------------------------

private data class IdlePose(
    val scaleX: Float,
    val scaleY: Float,
    val rotationDeg: Float,
    val offsetY: Float,
    val gaze: Offset,
    val blink: Float,
)

/**
 * Breath (2.5-4 s sine), blink (2-7 s, 70 ms close / 100 ms open, 18%
 * double), gaze saccades (0.25-0.9 s), follow-through sway and a silly
 * personality act every 30-60 s. Zero allocation; called per buddy per frame.
 */
private fun idlePose(
    seed: Long,
    tMs: Long,
    sizePx: Float,
    wobbleWalk: Boolean,
    calm: Boolean,
    forceAct: Boolean = false,
): IdlePose {
    val aSeed = abs(seed)
    // Breath: 2.5-4 s sine, scale 1.0 <-> 1.03.
    val breathPeriod = JuiceTimings.BREATH_MIN_MS + aSeed % (JuiceTimings.BREATH_MAX_MS - JuiceTimings.BREATH_MIN_MS)
    val breathPhase = (aSeed % BREATH_PHASE_MOD) / BREATH_PHASE_DIV
    val breath = sin(2.0 * PI * tMs / breathPeriod + breathPhase).toFloat()
    val scaleX = 1f + JuiceTimings.BREATH_AMPLITUDE * breath
    val scaleY = 1f - JuiceTimings.BREATH_AMPLITUDE * BREATH_Y_RATIO * breath

    // Blink.
    val blinkPeriod = JuiceTimings.BLINK_MIN_MS + (aSeed * BLINK_SEED_MUL) % (JuiceTimings.BLINK_MAX_MS - JuiceTimings.BLINK_MIN_MS)
    val cycleT = (tMs + aSeed % blinkPeriod) % blinkPeriod
    val doubleBlink = (aSeed / DOUBLE_BLINK_DIV) % PERCENT_L < (JuiceTimings.DOUBLE_BLINK_CHANCE * DOUBLE_BLINK_PCT).toLong()
    val blink = when {
        cycleT < JuiceTimings.BLINK_CLOSE_MS -> cycleT / JuiceTimings.BLINK_CLOSE_MS.toFloat()
        cycleT < JuiceTimings.BLINK_CLOSE_MS + JuiceTimings.BLINK_OPEN_MS ->
            1f - (cycleT - JuiceTimings.BLINK_CLOSE_MS) / JuiceTimings.BLINK_OPEN_MS.toFloat()
        doubleBlink && cycleT < DOUBLE_BLINK_MID_MS -> (cycleT - DOUBLE_BLINK_GAP_MS) / DOUBLE_BLINK_CLOSE_MS_F
        doubleBlink && cycleT < DOUBLE_BLINK_END_MS -> 1f - (cycleT - DOUBLE_BLINK_MID_MS) / DOUBLE_BLINK_OPEN_MS_F
        else -> 0f
    }

    // Gaze saccades.
    val gazePeriod = JuiceTimings.GAZE_MIN_MS + (aSeed * GAZE_SEED_MUL) % (JuiceTimings.GAZE_MAX_MS - JuiceTimings.GAZE_MIN_MS)
    val gazeIdx = tMs / gazePeriod + aSeed
    val gazeAngle = hash01(aSeed, gazeIdx) * 2f * PI.toFloat()
    val gaze = Offset(cos(gazeAngle), sin(gazeAngle)) * GAZE_RADIUS

    // Silly personality act every 30-60 s (or forced, e.g. tour spotlight).
    var rotation = 0f
    var offsetY = 0f
    if (!calm) {
        val actPeriod = JuiceTimings.SILLY_ACT_MIN_MS + (aSeed * ACT_SEED_MUL) % (JuiceTimings.SILLY_ACT_MAX_MS - JuiceTimings.SILLY_ACT_MIN_MS)
        val actT = (tMs + aSeed % actPeriod) % actPeriod
        val actKind = ((aSeed / ACT_KIND_DIV) % ACT_KIND_MOD).toInt()
        if (actT < SILLY_ACT_MS || forceAct) {
            val p = if (forceAct) {
                (tMs % SILLY_ACT_MS) / SILLY_ACT_MS.toFloat()
            } else {
                actT / SILLY_ACT_MS.toFloat()
            }
            when (actKind) {
                0 -> rotation = SPIN_DEGREES * p
                1 -> offsetY = -sin(p * PI.toFloat()) * sizePx * HOP_ACT_FRAC
                else -> rotation = sin(p * 2f * PI.toFloat()) * WIGGLE_ACT_DEGREES
            }
        }
        if (wobbleWalk) {
            rotation += sin(tMs / WADDLE_PERIOD_MS_F).toFloat() * WADDLE_DEGREES
            offsetY += -abs(sin(tMs / WADDLE_HOP_PERIOD_MS_F)).toFloat() * sizePx * WADDLE_HOP_FRAC
        }
        // Follow-through sway ~400 ms after movement: tiny lagging tilt.
        rotation += sin((tMs - JuiceTimings.SWAY_MS) / SWAY_PERIOD_MS_F).toFloat() * SWAY_DEGREES
    }
    return IdlePose(scaleX, scaleY, rotation, offsetY, gaze, blink)
}

/** Deterministic pseudo-random in [0, 1] from (seed, index). */
private fun hash01(seed: Long, idx: Long): Float {
    var h = seed * HASH_A + idx * HASH_B
    h = (h xor (h ushr 30)) * HASH_B
    h = (h xor (h ushr 27)) * HASH_C
    h = h xor (h ushr 31)
    return (h ushr 11).toFloat() / HASH_DIVISOR.toFloat()
}

private fun DrawScope.drawBuddyWithPose(
    images: Map<Int, ImageBitmap?>,
    imageRes: Int,
    center: Offset,
    sizePx: Float,
    seed: Long,
    rarity: Rarity,
    scaleX: Float = 1f,
    scaleY: Float = 1f,
    rotationDeg: Float = 0f,
    gaze: Offset = Offset.Zero,
    blink: Float = 0f,
    alpha: Float = 1f,
) {
    if (rotationDeg == 0f && scaleX == 1f && scaleY == 1f) {
        drawBuddySprite(images[imageRes], center, sizePx, seed, rarity, gaze, blink, alpha)
        return
    }
    drawContext.canvas.save()
    drawContext.canvas.translate(center.x, center.y)
    drawContext.canvas.rotate(rotationDeg)
    drawContext.canvas.scale(scaleX, scaleY)
    drawContext.canvas.translate(-center.x, -center.y)
    drawBuddySprite(images[imageRes], center, sizePx, seed, rarity, gaze, blink, alpha)
    drawContext.canvas.restore()
}

// ------------------------------------------------------------------
// Easings + constants
// ------------------------------------------------------------------

private fun easeOutBack(t: Float): Float {
    val c1 = EASE_C1
    val c3 = c1 + 1f
    val u = t - 1f
    return 1f + c3 * u * u * u + c1 * u * u
}

private fun easeInOut(t: Float): Float =
    if (t < EASE_MIDPOINT) {
        2f * t * t
    } else {
        1f - ((-2f * t + 2f) * (-2f * t + 2f)) / 2f
    }

private const val GRID_COLS = 6
private const val GRID_ROWS = 4
private const val CONVEYOR_LANES = 3
/** 1.5x forgiving hit zone: radius = buddySize * 0.75 (§11). */
private const val HIT_ZONE_SCALE = 0.75f
private const val ARC_HEIGHT_PX = 120f
private const val CHEVRON_COUNT = 5
private const val CHEVRON_PERIOD_MS = 2400L
private const val CLOCK_SYNC_MS = 30_000L
private const val FRAME_DT_FALLBACK = 0.016f
private const val SHAKE_MAX_PX = 14f
private const val SILLY_ACT_MS = 600f
private const val EPIC_LAND_TRAUMA = 0.35f
/** 100 ms * (1 - 0.3): the catch-up after the epic slow-mo grab. */
private const val SLOWMO_CATCHUP_MS = 70L
private const val HASH_A = -0x61C8864680B583EBL
private const val HASH_B = -0x40A7B8925D427DE7L
private const val HASH_C = -0x6B2FB644D1B20CBL
private const val HASH_DIVISOR = 1L shl 53

// Board dimensions (dp values hoisted for detekt's MagicNumber).
private val MIN_TARGET_DP = 56.dp
private val PAD_DP = 16.dp
private val BELT_HEIGHT_DP = 190.dp
private val BELT_MARGIN_DP = 32.dp
private val GRID_SPACING_DP = 8.dp
private val MIN_CELL_DP = 24.dp
private val SLOT_CORNER_DP = 14.dp
private val CHEVRON_W_DP = 28.dp
private val CHEVRON_STROKE_DP = 4.dp
private val BADGE_HAND_DP = 7.dp
private val LEVEL_BADGE_DP = 12.dp
private val FLOAT_RISE_DP = 24.dp
private val COACH_BOB_DP = 8.dp
private val COACH_ARROW_OFFSET_DP = 28.dp
private val COACH_ARROW_SIZE_DP = 14.dp
private val COACH_PAD_DP = 10.dp

// Draw proportions.
private const val BUDDY_CELL_FRAC = 0.78f
private const val CHEVRON_ALPHA = 0.35f
private const val CROUCH_HALF = 0.5f
private const val CROUCH_PERIOD_MS = 200f
private const val CROUCH_SQUASH_Y = 0.12f
private const val CROUCH_STRETCH_X = 0.08f
private const val EXPIRY_RING_FRAC = 0.65f
private const val WIGGLE_PERIOD_F = 450f
private const val WIGGLE_OSCILLATIONS = 4f
private const val WIGGLE_MAX_DEG = 8f
private const val PADLOCK_FRAC = 0.28f
private const val PADLOCK_BODY_H_FRAC = 0.8f
private const val PADLOCK_SHACKLE_FRAC = 0.25f
private const val HOP_FRAC = 0.25f
private const val BOING_PERIOD_F = 450f
private const val BOING_AMPLITUDE = 0.2f
private const val EPIC_FLEX_SCALE = 1.18f
private const val RAINBOW_STEP_MS = 10L
private const val HUE_DEGREES = 360L
private const val RAINBOW_RING_FRAC = 0.62f
private const val RARE_RING_FRAC = 0.58f
private const val LAND_SQUASH = 0.15f
private const val SPOTLIGHT_PULSE = 0.06f
private const val SPOTLIGHT_PERIOD_MS = 900f
private const val SPOTLIGHT_RING_FRAC = 0.68f
private const val FLOAT_FADE_START = 0.7f
private const val FLOAT_FADE_RANGE = 0.3f
private const val COACH_BOB_PERIOD_MS = 700f

// Time math.
private const val MILLIS_TO_SECONDS_F = 1000f
private const val DT_MAX_SECONDS = 0.1f
private const val MILLIS_PER_HOUR = 3_600_000L
private const val MILLIS_PER_MINUTE = 60_000L
private const val SHIMMER_PERIOD_MS = 4000L

// Idle-life hash constants.
private const val BREATH_PHASE_MOD = 628L
private const val BREATH_PHASE_DIV = 100.0
private const val BREATH_Y_RATIO = 0.6f
private const val BLINK_SEED_MUL = 31L
private const val DOUBLE_BLINK_DIV = 7L
private const val PERCENT_L = 100L
private const val DOUBLE_BLINK_PCT = 100
private const val DOUBLE_BLINK_MID_MS = 240L
private const val DOUBLE_BLINK_GAP_MS = 170L
private const val DOUBLE_BLINK_CLOSE_MS_F = 70f
private const val DOUBLE_BLINK_END_MS = 340L
private const val DOUBLE_BLINK_OPEN_MS_F = 100f
private const val GAZE_SEED_MUL = 13L
private const val GAZE_RADIUS = 0.6f
private const val ACT_SEED_MUL = 17L
private const val ACT_KIND_DIV = 101L
private const val ACT_KIND_MOD = 3L
private const val SPIN_DEGREES = 360f
private const val HOP_ACT_FRAC = 0.3f
private const val WIGGLE_ACT_DEGREES = 15f
private const val WADDLE_PERIOD_MS_F = 180.0
private const val WADDLE_DEGREES = 8f
private const val WADDLE_HOP_PERIOD_MS_F = 360.0
private const val WADDLE_HOP_FRAC = 0.08f
private const val SWAY_PERIOD_MS_F = 900.0
private const val SWAY_DEGREES = 1.5f
private const val EASE_C1 = 1.70158f
private const val EASE_MIDPOINT = 0.5f
