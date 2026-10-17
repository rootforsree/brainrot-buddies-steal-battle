package com.sreecharan.brainrot_buddies_steal_battle.ui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sreecharan.brainrot_buddies_steal_battle.data.model.Rarity
import com.sreecharan.brainrot_buddies_steal_battle.ui.home.ClaimAnimUi
import com.sreecharan.brainrot_buddies_steal_battle.ui.home.ConveyorBuddyUi
import com.sreecharan.brainrot_buddies_steal_battle.ui.home.SlotUi
import com.sreecharan.brainrot_buddies_steal_battle.ui.home.SlottedBuddyUi
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.AppColors
import kotlin.math.PI
import kotlin.math.sin

/** Board drawing: conveyor, grid, claim animations. Frame orchestration lives in BaseBoardCanvas. */

/** Everything drawConveyor needs, in one value. */
internal data class ConveyorDrawParams(
    val layout: BoardLayout,
    val board: BoardCanvasState,
    val tUptime: Long,
    val clockOffset: Long,
    val colors: AppColors,
    val images: Map<Int, ImageBitmap?>,
    val reduceMotion: Boolean,
)

/** Everything drawGrid needs, in one value. */
internal data class GridDrawParams(
    val layout: BoardLayout,
    val board: BoardCanvasState,
    val tUptime: Long,
    val colors: AppColors,
    val images: Map<Int, ImageBitmap?>,
    val textMeasurer: TextMeasurer,
    val reduceMotion: Boolean,
)

/** Everything drawClaimAnims needs, in one value. */
internal data class ClaimDrawParams(
    val layout: BoardLayout,
    val board: BoardCanvasState,
    val tUptime: Long,
    val images: Map<Int, ImageBitmap?>,
    val juice: JuiceController,
    val phaseTracker: MutableMap<String, Int>,
    val reduceMotion: Boolean,
)

internal fun DrawScope.drawConveyor(p: ConveyorDrawParams) {
    val beltWidth = size.width - BELT_MARGIN_DP.toPx()
    val beltLeft = PAD_DP.toPx()
    drawRoundRect(
        color = p.colors.cardSurface,
        topLeft = Offset(beltLeft, p.layout.beltTop),
        size = Size(beltWidth, p.layout.beltHeight),
        cornerRadius = CornerRadius(20.dp.toPx()),
    )
    drawRoundRect(
        color = p.colors.secondaryLabel.copy(alpha = BELT_EDGE_ALPHA),
        topLeft = Offset(beltLeft, p.layout.beltTop),
        size = Size(beltWidth, p.layout.beltHeight),
        cornerRadius = CornerRadius(20.dp.toPx()),
        style = Stroke(width = 2.dp.toPx()),
    )
    // Animated chevrons flowing down the belt.
    if (!p.reduceMotion) {
        val chevronT = (p.tUptime % CHEVRON_PERIOD_MS) / CHEVRON_PERIOD_MS.toFloat()
        for (i in 0 until CHEVRON_COUNT) {
            val cp = (i / CHEVRON_COUNT.toFloat() + chevronT) % 1f
            val y = p.layout.beltTop + cp * p.layout.beltHeight
            val alpha = (1f - cp) * CHEVRON_ALPHA
            val cx = beltLeft + beltWidth / 2f
            val w = CHEVRON_W_DP.toPx()
            val stroke = CHEVRON_STROKE_DP.toPx()
            drawLine(
                p.colors.secondaryLabel.copy(alpha = alpha),
                Offset(cx - w / 2f, y - 8.dp.toPx()),
                Offset(cx, y),
                strokeWidth = stroke,
            )
            drawLine(
                p.colors.secondaryLabel.copy(alpha = alpha),
                Offset(cx, y),
                Offset(cx + w / 2f, y - 8.dp.toPx()),
                strokeWidth = stroke,
            )
        }
    }
    // Next-spawn timer: "show the next fun thing, always" (§11).
    val spawnIn = (p.board.nextSpawnMs - p.board.nowMs).coerceIn(0L, JuiceTimings.CONVEYOR_CADENCE_MS)
    val spawnP = 1f - spawnIn / JuiceTimings.CONVEYOR_CADENCE_MS.toFloat()
    drawArc(
        color = p.colors.accent,
        startAngle = -90f,
        sweepAngle = 360f * spawnP,
        useCenter = false,
        topLeft = Offset(beltLeft + beltWidth - 44.dp.toPx(), p.layout.beltTop + 8.dp.toPx()),
        size = Size(28.dp.toPx(), 28.dp.toPx()),
        style = Stroke(width = 4.dp.toPx()),
    )

    // Riders.
    p.board.conveyor.forEach { item ->
        drawConveyorRider(p, item)
    }
}

private fun DrawScope.drawConveyorRider(p: ConveyorDrawParams, item: ConveyorBuddyUi) {
    val progress = rideProgress(item, p.tUptime, p.clockOffset)
    if (progress !in 0f..1f) return
    val center = p.layout.laneCenter(item.lane, progress)
    val pose = idlePose(
        IdlePoseRequest(item.seed, p.tUptime, p.layout.buddySize, !p.reduceMotion, p.reduceMotion),
    )
    var scaleX = pose.scaleX
    var scaleY = pose.scaleY
    // Last 3 s: anticipation crouch (§10).
    val remainingMs = JuiceTimings.CONVEYOR_RIDE_MS - progress * JuiceTimings.CONVEYOR_RIDE_MS
    if (remainingMs < JuiceTimings.ANTICIPATION_MS && !p.reduceMotion) {
        val crouch = CROUCH_HALF + CROUCH_HALF * sin(2f * PI.toFloat() * p.tUptime / CROUCH_PERIOD_MS)
        scaleY *= 1f - CROUCH_SQUASH_Y * crouch
        scaleX *= 1f + CROUCH_STRETCH_X * crouch
    }
    // Ticket pulls glow gold on the belt.
    if (item.ticketPull) {
        drawCircle(
            Brush.radialGradient(
                colors = listOf(GOLD.copy(alpha = TICKET_GLOW_ALPHA), Color.Transparent),
                center = center,
                radius = p.layout.buddySize,
            ),
            radius = p.layout.buddySize,
            center = center,
        )
    }
    drawBuddyWithPose(
        p.images,
        item.imageRes,
        BuddySpriteSpec(
            image = null,
            center = center + Offset(0f, pose.offsetY),
            sizePx = p.layout.buddySize,
            seed = item.seed,
            rarity = item.rarity,
            scaleX = scaleX,
            scaleY = scaleY,
            rotationDeg = pose.rotationDeg,
            gaze = pose.gaze,
            blink = pose.blink,
        ),
    )
    // Expiry ring in the last 3 s.
    if (remainingMs < JuiceTimings.ANTICIPATION_MS) {
        val ringP = 1f - remainingMs / JuiceTimings.ANTICIPATION_MS.toFloat()
        val ringR = p.layout.buddySize * EXPIRY_RING_FRAC
        drawArc(
            color = p.colors.warning.copy(alpha = EXPIRY_RING_ALPHA),
            startAngle = -90f,
            sweepAngle = 360f * ringP,
            useCenter = false,
            topLeft = center - Offset(ringR, ringR),
            size = Size(ringR * 2f, ringR * 2f),
            style = Stroke(width = 3.dp.toPx()),
        )
    }
}

internal fun rideProgress(item: ConveyorBuddyUi, tUptime: Long, clockOffset: Long): Float {
    val spawnedUptime = item.spawnedAtMs + clockOffset
    return ((tUptime - spawnedUptime).toFloat() / JuiceTimings.CONVEYOR_RIDE_MS).coerceIn(0f, 1f)
}

internal fun DrawScope.drawGrid(p: GridDrawParams) {
    p.board.slots.forEach { slot ->
        drawSlot(p, slot)
    }
}

private fun DrawScope.drawSlot(p: GridDrawParams, slot: SlotUi) {
    val layout = p.layout
    val center = layout.slotCenter(slot.index)
    val cellTopLeft = Offset(center.x - layout.cell / 2f, center.y - layout.cell / 2f)
    val corner = CornerRadius(SLOT_CORNER_DP.toPx())
    if (!slot.unlocked) {
        drawRoundRect(
            color = p.colors.secondaryLabel.copy(alpha = LOCKED_BG_ALPHA),
            topLeft = cellTopLeft,
            size = Size(layout.cell, layout.cell),
            cornerRadius = corner,
        )
        drawPadlock(
            center,
            layout.cell * PADLOCK_FRAC,
            p.colors.secondaryLabel.copy(alpha = PADLOCK_ALPHA),
        )
        return
    }
    drawRoundRect(
        color = p.colors.cardSurface,
        topLeft = cellTopLeft,
        size = Size(layout.cell, layout.cell),
        cornerRadius = corner,
    )
    val buddy = slot.buddy ?: return
    // Rarity flex, readable from 3 feet (§2 V1.4): Epic = oversized.
    val flexSize = if (buddy.rarity == Rarity.EPIC) layout.buddySize * EPIC_FLEX_SCALE else layout.buddySize
    drawSlottedBuddy(p, slot, buddy, center, flexSize)
}

private fun DrawScope.drawSlottedBuddy(
    p: GridDrawParams,
    slot: SlotUi,
    buddy: SlottedBuddyUi,
    center: Offset,
    flexSize: Float,
) {
    val layout = p.layout
    // Wiggle for locked slots that got tapped (§11: never silence).
    var rotation = 0f
    val wiggle = p.board.wiggleSlot
    if (wiggle != null && wiggle.slotIndex == slot.index && !p.reduceMotion) {
        val t = (p.tUptime - wiggle.startUptimeMs) / WIGGLE_PERIOD_F
        if (t in 0f..1f) {
            rotation = sin(t * PI.toFloat() * WIGGLE_OSCILLATIONS) * WIGGLE_MAX_DEG * (1f - t)
        }
    }
    val isTourSpot = p.board.tour?.spotlightSlot == slot.index
    // Waddle-in after a claim lands (§1).
    var scale = 1f
    var hopY = 0f
    p.board.waddleAnims[buddy.uid]?.let { waddleStart ->
        if (!p.reduceMotion) {
            val t = ((p.tUptime - waddleStart) / JuiceTimings.WADDLE_IN_MS.toFloat()).coerceIn(0f, 1f)
            scale = easeOutBack(t)
            hopY = -sin(t * PI.toFloat()) * layout.cell * HOP_FRAC
        }
    }
    // Boing when tapped.
    p.board.boingAnims[buddy.uid]?.let { start ->
        if (!p.reduceMotion) {
            val t = ((p.tUptime - start) / BOING_PERIOD_F).coerceIn(0f, 1f)
            if (t < 1f) {
                scale *= 1f + BOING_AMPLITUDE * sin(t * PI.toFloat())
            }
        }
    }
    val pose = idlePose(
        IdlePoseRequest(
            seed = buddy.seed,
            tMs = p.tUptime,
            sizePx = flexSize,
            wobbleWalk = false,
            calm = p.reduceMotion,
            forceAct = isTourSpot && !p.reduceMotion,
        ),
    )
    val dimmed = buddy.stealCooldownEndsAtMs > p.board.nowMs
    drawBuddyWithPose(
        p.images,
        buddy.imageRes,
        BuddySpriteSpec(
            image = null,
            center = center + Offset(0f, pose.offsetY + hopY),
            sizePx = flexSize * scale,
            seed = buddy.seed,
            rarity = buddy.rarity,
            scaleX = pose.scaleX * scale,
            scaleY = pose.scaleY * scale,
            rotationDeg = pose.rotationDeg + rotation,
            gaze = pose.gaze,
            blink = pose.blink,
            alpha = if (dimmed) DIMMED_ALPHA else 1f,
        ),
    )
    drawRarityFlex(p, buddy, center, flexSize)
    drawSlotBadges(p, slot, buddy, center, dimmed)
}

/** Rarity flex, readable from 3 feet (§2 V1.4): blue ring, golden glow, rainbow ring. */
private fun DrawScope.drawRarityFlex(
    p: GridDrawParams,
    buddy: SlottedBuddyUi,
    center: Offset,
    flexSize: Float,
) {
    when {
        buddy.rarity == Rarity.EPIC -> {
            drawCircle(
                Brush.radialGradient(
                    colors = listOf(GOLD.copy(alpha = EPIC_GLOW_ALPHA), Color.Transparent),
                    center = center,
                    radius = flexSize,
                ),
                radius = flexSize,
                center = center,
            )
        }
        buddy.level >= LEVEL_RAINBOW_MIN && !p.reduceMotion -> {
            val hue = (p.tUptime / RAINBOW_STEP_MS % HUE_DEGREES).toFloat()
            drawCircle(
                color = Color.hsv(hue, RAINBOW_SAT, 1f, RAINBOW_ALPHA),
                radius = flexSize * RAINBOW_RING_FRAC,
                center = center,
                style = Stroke(width = 4.dp.toPx()),
            )
        }
        buddy.rarity == Rarity.RARE -> {
            drawCircle(
                color = RARE_BLUE.copy(alpha = RARE_RING_ALPHA),
                radius = flexSize * RARE_RING_FRAC,
                center = center,
                style = Stroke(width = 3.dp.toPx()),
            )
        }
    }
}

private fun DrawScope.drawSlotBadges(
    p: GridDrawParams,
    slot: SlotUi,
    buddy: SlottedBuddyUi,
    center: Offset,
    dimmed: Boolean,
) {
    val layout = p.layout
    // Steal cooldown: small clock badge.
    if (dimmed) {
        val badgeC = center + Offset(layout.cell * BADGE_OFFSET_FRAC, -layout.cell * BADGE_OFFSET_FRAC)
        drawCircle(color = p.colors.cardSurface, radius = 11.dp.toPx(), center = badgeC)
        val handR = BADGE_HAND_DP.toPx()
        drawArc(
            color = p.colors.secondaryLabel,
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
        val badgeC = center + Offset(-layout.cell * BADGE_OFFSET_FRAC, layout.cell * BADGE_OFFSET_FRAC)
        drawCircle(LEVEL_BADGE_GOLD, badgeR, badgeC)
        val label = p.textMeasurer.measure(
            AnnotatedString("L${buddy.level}"),
            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White),
        )
        drawText(
            label,
            topLeft = badgeC - Offset(label.size.width / 2f, label.size.height / 2f),
        )
    }
    // Per-slot shield: little dome.
    if (slot.shielded) {
        drawArc(
            color = SLOT_SHIELD_BLUE.copy(alpha = SLOT_SHIELD_ALPHA),
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(
                center.x - layout.cell * SLOT_SHIELD_X_FRAC,
                center.y - layout.cell * SLOT_SHIELD_Y_FRAC,
            ),
            size = Size(layout.cell * SLOT_SHIELD_SIZE_FRAC, layout.cell * SLOT_SHIELD_SIZE_FRAC),
            style = Stroke(width = 3.dp.toPx()),
        )
    }
}

internal fun DrawScope.drawClaimAnims(p: ClaimDrawParams) {
    p.board.claimAnims.values.forEach { anim ->
        val t = p.tUptime - anim.startUptimeMs
        if (t < 0) return@forEach
        // Epic: 100 ms of 0.3x slow-mo on grab, then catch up seamlessly.
        val te = when {
            p.reduceMotion || anim.rarity != Rarity.EPIC -> t
            t < JuiceTimings.EPIC_SLOWMO_MS -> (t * JuiceTimings.EPIC_SLOWMO_SCALE).toLong()
            else -> t - SLOWMO_CATCHUP_MS
        }
        val from = p.layout.laneCenter(anim.fromLane, anim.fromProgress)
        val to = anim.targetSlotIndex?.let { p.layout.slotCenter(it) }
            ?: Offset(size.width / 2f, p.layout.gridTop)
        drawClaimPhase(p, anim, te, from, to)
    }
}

/** One claim animation frame: 0-60 squash, 60-250 arc, 250-600 landing (§10). */
private fun DrawScope.drawClaimPhase(
    p: ClaimDrawParams,
    anim: ClaimAnimUi,
    te: Long,
    from: Offset,
    to: Offset,
) {
    fun drawClaimBuddy(center: Offset, size: Float, sx: Float = 1f, sy: Float = 1f, rot: Float = 0f) {
        drawBuddyWithPose(
            p.images,
            anim.imageRes,
            BuddySpriteSpec(
                image = null,
                center = center,
                sizePx = size,
                seed = anim.seed,
                rarity = anim.rarity,
                scaleX = sx,
                scaleY = sy,
                rotationDeg = rot,
            ),
        )
    }
    fun phaseEntry(phase: Int, fire: () -> Unit) {
        if ((p.phaseTracker[anim.instanceId] ?: -1) < phase) {
            p.phaseTracker[anim.instanceId] = phase
            fire()
        }
    }
    when {
        te < JuiceTimings.CLAIM_SQUASH_MS -> {
            val q = te / JuiceTimings.CLAIM_SQUASH_MS.toFloat()
            drawClaimBuddy(from, p.layout.buddySize, sx = 1f + CLAIM_SQUASH * q, sy = 1f - CLAIM_SQUASH * q)
            phaseEntry(0) { p.juice.stars(from.x, from.y) }
        }
        te < JuiceTimings.CLAIM_SQUASH_MS + JuiceTimings.CLAIM_ARC_MS -> {
            val q = (te - JuiceTimings.CLAIM_SQUASH_MS) / JuiceTimings.CLAIM_ARC_MS.toFloat()
            val eased = easeInOut(q)
            drawClaimBuddy(
                Offset(
                    from.x + (to.x - from.x) * eased,
                    from.y + (to.y - from.y) * eased - sin(q * PI.toFloat()) * ARC_HEIGHT_PX,
                ),
                p.layout.buddySize,
                rot = q * ARC_SPIN_DEG,
            )
            phaseEntry(1) { p.juice.stars(from.x, from.y) }
        }
        te < JuiceTimings.CLAIM_TOTAL_MS -> {
            val q = ((te - JuiceTimings.CLAIM_SQUASH_MS - JuiceTimings.CLAIM_ARC_MS) /
                JuiceTimings.CLAIM_LAND_MS.toFloat()).coerceIn(0f, 1f)
            // Overshoot 1.15 -> 1.0 (§10).
            val scale = 1f + LAND_SQUASH * (1f - easeOutBack(q))
            drawClaimBuddy(to, p.layout.buddySize * scale, sx = scale, sy = 2f - scale)
            phaseEntry(2) {
                p.juice.dust(to.x, to.y)
                if (anim.rarity == Rarity.RARE) p.juice.blueRing(to.x, to.y)
                if (anim.rarity == Rarity.EPIC) {
                    p.juice.goldRays(to.x, to.y, size.width * size.height)
                    if (!p.reduceMotion) p.juice.shake.add(EPIC_LAND_TRAUMA)
                }
            }
        }
    }
}

private val BELT_MARGIN_DP = 32.dp
private const val CHEVRON_COUNT = 5
private const val CHEVRON_PERIOD_MS = 2400L
private const val CHEVRON_ALPHA = 0.35f
private const val CROUCH_HALF = 0.5f
private const val CROUCH_PERIOD_MS = 200f
private const val CROUCH_SQUASH_Y = 0.12f
private const val CROUCH_STRETCH_X = 0.08f
private const val EXPIRY_RING_FRAC = 0.65f
private const val EXPIRY_RING_ALPHA = 0.8f
private val SLOT_CORNER_DP = 14.dp
private const val PADLOCK_FRAC = 0.28f
private const val PADLOCK_ALPHA = 0.6f
private const val LOCKED_BG_ALPHA = 0.12f
private const val WIGGLE_PERIOD_F = 450f
private const val WIGGLE_OSCILLATIONS = 4f
private const val WIGGLE_MAX_DEG = 8f
private const val HOP_FRAC = 0.25f
private const val BOING_PERIOD_F = 450f
private const val BOING_AMPLITUDE = 0.2f
private const val EPIC_FLEX_SCALE = 1.18f
private const val RAINBOW_STEP_MS = 10L
private const val HUE_DEGREES = 360L
private const val RAINBOW_RING_FRAC = 0.62f
private const val RAINBOW_SAT = 0.7f
private const val RAINBOW_ALPHA = 0.85f
private const val RARE_RING_FRAC = 0.58f
private const val RARE_RING_ALPHA = 0.8f
private const val LEVEL_RAINBOW_MIN = 3
private const val LAND_SQUASH = 0.15f
private const val CLAIM_SQUASH = 0.15f
private const val ARC_SPIN_DEG = 18f
private const val ARC_HEIGHT_PX = 120f
private const val SLOWMO_CATCHUP_MS = 70L
private const val EPIC_LAND_TRAUMA = 0.35f
private const val DIMMED_ALPHA = 0.55f
private const val BADGE_OFFSET_FRAC = 0.32f
private const val TICKET_GLOW_ALPHA = 0.55f
private const val EPIC_GLOW_ALPHA = 0.55f
private const val BELT_EDGE_ALPHA = 0.25f
private const val SLOT_SHIELD_ALPHA = 0.5f
private const val SLOT_SHIELD_X_FRAC = 0.45f
private const val SLOT_SHIELD_Y_FRAC = 0.1f
private const val SLOT_SHIELD_SIZE_FRAC = 0.9f

private val PAD_DP = 16.dp
private val CHEVRON_W_DP = 28.dp
private val CHEVRON_STROKE_DP = 4.dp
private val BADGE_HAND_DP = 7.dp
private val LEVEL_BADGE_DP = 12.dp
private val GOLD = Color(0xFFFFD54F)
private val RARE_BLUE = Color(0xFF448AFF)
private val LEVEL_BADGE_GOLD = Color(0xFFFFB300)
private val SLOT_SHIELD_BLUE = Color(0xFF4FC3F7)
