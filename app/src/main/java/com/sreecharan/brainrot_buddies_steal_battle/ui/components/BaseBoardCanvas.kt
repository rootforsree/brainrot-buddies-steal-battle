package com.sreecharan.brainrot_buddies_steal_battle.ui.components

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.sreecharan.brainrot_buddies_steal_battle.ui.home.ClaimAnimUi
import com.sreecharan.brainrot_buddies_steal_battle.ui.home.CoachMarkUi
import com.sreecharan.brainrot_buddies_steal_battle.ui.home.ConveyorBuddyUi
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
import kotlin.math.abs

// Public API: HomeScreen calls with all 8 arguments, so the parameter list
// cannot be regrouped without breaking that call site.

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
@Suppress("LongParameterList")
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
    val clockOffset by rememberClockOffset()
    val images = rememberBoardImages(board)
    val layoutHolder = remember { LayoutHolder() }
    val phaseTracker = remember { mutableMapOf<String, Int>() }
    val lastFrame = remember { mutableLongStateOf(0L) }

    Canvas(
        modifier =
            modifier.then(
                boardTapModifier(
                    BoardTapParams(board, layoutHolder, onConveyorTap, onSlotTap, clockOffset, minTargetPx),
                ),
            ),
    ) {
        drawBoardFrame(
            BoardFrameParams(
                board = board,
                frameTime = frameTime,
                camera = camera,
                juice = juice,
                reduceMotion = reduceMotion,
                colors = colors,
                textMeasurer = textMeasurer,
                layoutHolder = layoutHolder,
                phaseTracker = phaseTracker,
                lastFrame = lastFrame,
                clockOffset = clockOffset,
                images = images,
            ),
        )
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
data class CameraState(
    val panXFraction: Float = 0f,
    val zoom: Float = 1f,
)

/** Everything one board frame draw needs, in one value. */
private data class BoardFrameParams(
    val board: BoardCanvasState,
    val frameTime: State<Long>,
    val camera: CameraState,
    val juice: JuiceController,
    val reduceMotion: Boolean,
    val colors: AppColors,
    val textMeasurer: TextMeasurer,
    val layoutHolder: LayoutHolder,
    val phaseTracker: MutableMap<String, Int>,
    val lastFrame: MutableLongState,
    val clockOffset: Long,
    val images: Map<Int, ImageBitmap?>,
)

/** Everything the tap detector needs, in one value. */
private data class BoardTapParams(
    val board: BoardCanvasState,
    val layoutHolder: LayoutHolder,
    val onConveyorTap: (String) -> Unit,
    val onSlotTap: (Int) -> Unit,
    val clockOffset: Long,
    val minTargetPx: Float,
)

/** Wall clock (data layer) -> uptime (animation clock) offset, re-synced. */
@Composable
private fun rememberClockOffset(): State<Long> {
    val clockOffset = remember { mutableLongStateOf(SystemClock.uptimeMillis() - System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(CLOCK_SYNC_MS)
            clockOffset.longValue = SystemClock.uptimeMillis() - System.currentTimeMillis()
        }
    }
    return clockOffset
}

/** Buddy art, keyed stably so sprites survive list churn. */
@Composable
private fun rememberBoardImages(board: BoardCanvasState): MutableMap<Int, ImageBitmap?> {
    val imageResIds =
        remember(board.conveyor, board.slots, board.claimAnims) {
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
    return images
}

/** Tap detection: conveyor creatures first (1.5x forgiving hit zone), then slots. */
@Composable
private fun boardTapModifier(p: BoardTapParams): Modifier {
    val latestTaps by rememberUpdatedState(p.onConveyorTap to p.onSlotTap)
    val latestOffset by rememberUpdatedState(p.clockOffset)
    val latestMinTarget by rememberUpdatedState(p.minTargetPx)
    return Modifier.pointerInput(p.board.conveyor, p.board.slots) {
        detectTapGestures { tap ->
            val layout = p.layoutHolder.layout ?: return@detectTapGestures
            val (onConveyor, onSlot) = latestTaps
            val uptimeNow = SystemClock.uptimeMillis()
            // Conveyor first: creatures get the 1.5x forgiving hit zone.
            val hit =
                p.board.conveyor.asReversed().firstOrNull { item ->
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
                val slot = p.board.slots.getOrNull(index) ?: return@detectTapGestures
                // 56 dp minimum tap target: expand the hit rect when the
                // visual cell is smaller.
                val center = layout.slotCenter(index)
                val half = (latestMinTarget / 2f).coerceAtLeast(layout.cell / 2f)
                if (abs(tap.x - center.x) <= half && abs(tap.y - center.y) <= half) {
                    onSlot(slot.index)
                }
            }
        }
    }
}

private fun DrawScope.drawBoardFrame(p: BoardFrameParams) {
    val tUptime = p.frameTime.value
    val dtSeconds =
        run {
            val last = p.lastFrame.longValue
            p.lastFrame.longValue = tUptime
            if (last == 0L) FRAME_DT_FALLBACK else ((tUptime - last) / MILLIS_TO_SECONDS_F).coerceIn(0f, DT_MAX_SECONDS)
        }

    val layout = computeLayout()
    p.layoutHolder.layout = layout
    // Drop phase entries for finished claim anims.
    p.phaseTracker.keys.retainAll(p.board.claimAnims.keys)

    if (!p.reduceMotion) {
        p.juice.shake.update(dtSeconds)
    }
    val shakeOffset =
        if (p.reduceMotion) {
            Offset.Zero
        } else {
            p.juice.shake.offset(tUptime, SHAKE_MAX_PX)
        }

    drawBoardBackground(p.colors)
    translate(shakeOffset.x, shakeOffset.y) {
        drawShakenBoard(p, layout, tUptime, dtSeconds)
    }

    drawFloatUps(layout, p.board, tUptime, p.textMeasurer, p.colors)
    if (p.board.coachMark != null) {
        drawCoachMark(layout, p.board.coachMark, tUptime, p.colors, p.textMeasurer)
    }
}

private fun DrawScope.drawShakenBoard(
    p: BoardFrameParams,
    layout: BoardLayout,
    tUptime: Long,
    dtSeconds: Float,
) {
    // Tour camera: pan + gentle zoom over the board.
    withCamera(layout, p.camera) {
        drawConveyor(
            ConveyorDrawParams(
                layout = layout,
                board = p.board,
                tUptime = tUptime,
                clockOffset = p.clockOffset,
                colors = p.colors,
                images = p.images,
                reduceMotion = p.reduceMotion,
            ),
        )
        drawGrid(
            GridDrawParams(
                layout = layout,
                board = p.board,
                tUptime = tUptime,
                colors = p.colors,
                images = p.images,
                textMeasurer = p.textMeasurer,
                reduceMotion = p.reduceMotion,
            ),
        )
        if (p.board.shieldDome) {
            drawShieldDome(layout, p.board, tUptime, p.colors, p.textMeasurer)
        }
        drawClaimAnims(
            ClaimDrawParams(
                layout = layout,
                board = p.board,
                tUptime = tUptime,
                images = p.images,
                juice = p.juice,
                phaseTracker = p.phaseTracker,
                reduceMotion = p.reduceMotion,
            ),
        )
    }
    // Particles shake with the board that spawned them.
    p.juice.particles.update(dtSeconds)
    p.juice.particles.draw(this)
    if (p.board.tour != null) {
        drawTourSpotlight(layout, p.board.tour, p.camera, tUptime, p.colors)
    }
}

private fun DrawScope.drawBoardBackground(colors: AppColors) {
    drawRect(colors.groupedBackground)
}

private class LayoutHolder {
    var layout: BoardLayout? = null
}

internal data class BoardLayout(
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

    fun laneCenter(
        lane: Int,
        progress: Float,
    ): Offset =
        Offset(
            laneX[lane.coerceIn(0, CONVEYOR_LANES - 1)],
            beltTop + progress.coerceIn(0f, 1f) * beltHeight,
        )

    fun gridCenter(): Offset =
        Offset(
            gridLeft + (GRID_COLS * (cell + spacing) - spacing) / 2f,
            gridTop + (GRID_ROWS * (cell + spacing) - spacing) / 2f,
        )
}

private fun DrawScope.computeLayout(): BoardLayout {
    val padding = PAD_DP.toPx()
    val beltHeight = BELT_HEIGHT_DP.toPx()
    val beltTop = padding
    val laneX =
        FloatArray(CONVEYOR_LANES) { i ->
            padding + (size.width - padding * 2f) * (i + 1f) / (CONVEYOR_LANES + 1f)
        }
    val gridTop = beltTop + beltHeight + padding
    val spacing = GRID_SPACING_DP.toPx()
    val gridWidth = size.width - padding * 2f
    val gridHeight = size.height - gridTop - padding
    val cell =
        minOf(
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

internal const val GRID_COLS = 6
internal const val GRID_ROWS = 4
private const val CONVEYOR_LANES = 3

/** 1.5x forgiving hit zone: radius = buddySize * 0.75 (§11). */
private const val HIT_ZONE_SCALE = 0.75f
private const val CLOCK_SYNC_MS = 30_000L
private const val FRAME_DT_FALLBACK = 0.016f
private const val MILLIS_TO_SECONDS_F = 1000f
private const val DT_MAX_SECONDS = 0.1f
private const val SHAKE_MAX_PX = 14f
private const val BUDDY_CELL_FRAC = 0.78f

// Board dimensions (dp values hoisted for detekt's MagicNumber).
private val MIN_TARGET_DP = 56.dp
private val PAD_DP = 16.dp
private val BELT_HEIGHT_DP = 190.dp
private val GRID_SPACING_DP = 8.dp
private val MIN_CELL_DP = 24.dp
