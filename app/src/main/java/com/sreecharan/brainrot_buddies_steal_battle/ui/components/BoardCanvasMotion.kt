package com.sreecharan.brainrot_buddies_steal_battle.ui.components

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Idle life (contract §10, 3 layers) — pure math from (seed, time).
 * Zero allocation; called per buddy per frame.
 */

/**
 * Breath (2.5-4 s sine), blink (2-7 s, 70 ms close / 100 ms open, 18%
 * double), gaze saccades (0.25-0.9 s), follow-through sway and a silly
 * personality act every 30-60 s.
 */
internal fun idlePose(request: IdlePoseRequest): IdlePose {
    val aSeed = abs(request.seed)
    val tMs = request.tMs
    // Breath: 2.5-4 s sine, scale 1.0 <-> 1.03.
    val breathPeriod = JuiceTimings.BREATH_MIN_MS + aSeed % (JuiceTimings.BREATH_MAX_MS - JuiceTimings.BREATH_MIN_MS)
    val breathPhase = (aSeed % BREATH_PHASE_MOD) / BREATH_PHASE_DIV
    val breath = sin(2.0 * PI * tMs / breathPeriod + breathPhase).toFloat()
    val scaleX = 1f + JuiceTimings.BREATH_AMPLITUDE * breath
    val scaleY = 1f - JuiceTimings.BREATH_AMPLITUDE * BREATH_Y_RATIO * breath
    val blink = idleBlink(aSeed, tMs)
    // Gaze saccades.
    val gazePeriod = JuiceTimings.GAZE_MIN_MS +
        (aSeed * GAZE_SEED_MUL) % (JuiceTimings.GAZE_MAX_MS - JuiceTimings.GAZE_MIN_MS)
    val gazeIdx = tMs / gazePeriod + aSeed
    val gazeAngle = hash01(aSeed, gazeIdx) * 2f * PI.toFloat()
    val gaze = Offset(cos(gazeAngle), sin(gazeAngle)) * GAZE_RADIUS
    // Silly personality act every 30-60 s (or forced, e.g. tour spotlight).
    var rotation = 0f
    var offsetY = 0f
    if (!request.calm) {
        val act = idleSillyAct(aSeed, tMs, request.sizePx, request.forceAct)
        rotation += act.rotation
        offsetY += act.offsetY
        if (request.wobbleWalk) {
            rotation += sin(tMs / WADDLE_PERIOD_MS_F).toFloat() * WADDLE_DEGREES
            offsetY += -abs(sin(tMs / WADDLE_HOP_PERIOD_MS_F)).toFloat() * request.sizePx * WADDLE_HOP_FRAC
        }
        // Follow-through sway ~400 ms after movement: tiny lagging tilt.
        rotation += sin((tMs - JuiceTimings.SWAY_MS) / SWAY_PERIOD_MS_F).toFloat() * SWAY_DEGREES
    }
    return IdlePose(scaleX, scaleY, rotation, offsetY, gaze, blink)
}

/** Everything idlePose needs, in one value. */
internal data class IdlePoseRequest(
    val seed: Long,
    val tMs: Long,
    val sizePx: Float,
    val wobbleWalk: Boolean,
    val calm: Boolean,
    val forceAct: Boolean = false,
)

internal data class IdlePose(
    val scaleX: Float,
    val scaleY: Float,
    val rotationDeg: Float,
    val offsetY: Float,
    val gaze: Offset,
    val blink: Float,
)

/** Blink layer: 2-7 s period, 70 ms close / 100 ms open, 18% double-blink. */
private fun idleBlink(aSeed: Long, tMs: Long): Float {
    val blinkPeriod = JuiceTimings.BLINK_MIN_MS +
        (aSeed * BLINK_SEED_MUL) % (JuiceTimings.BLINK_MAX_MS - JuiceTimings.BLINK_MIN_MS)
    val cycleT = (tMs + aSeed % blinkPeriod) % blinkPeriod
    val doubleBlink = (aSeed / DOUBLE_BLINK_DIV) % PERCENT_L <
        (JuiceTimings.DOUBLE_BLINK_CHANCE * DOUBLE_BLINK_PCT).toLong()
    return when {
        cycleT < JuiceTimings.BLINK_CLOSE_MS -> cycleT / JuiceTimings.BLINK_CLOSE_MS.toFloat()
        cycleT < JuiceTimings.BLINK_CLOSE_MS + JuiceTimings.BLINK_OPEN_MS ->
            1f - (cycleT - JuiceTimings.BLINK_CLOSE_MS) / JuiceTimings.BLINK_OPEN_MS.toFloat()
        doubleBlink && cycleT < DOUBLE_BLINK_MID_MS -> (cycleT - DOUBLE_BLINK_GAP_MS) / DOUBLE_BLINK_CLOSE_MS_F
        doubleBlink && cycleT < DOUBLE_BLINK_END_MS -> 1f - (cycleT - DOUBLE_BLINK_MID_MS) / DOUBLE_BLINK_OPEN_MS_F
        else -> 0f
    }
}

private data class SillyAct(val rotation: Float, val offsetY: Float)

/** One silly personality act: spin, hop, or wiggle. Inactive most of the time. */
private fun idleSillyAct(aSeed: Long, tMs: Long, sizePx: Float, forceAct: Boolean): SillyAct {
    val actPeriod = JuiceTimings.SILLY_ACT_MIN_MS +
        (aSeed * ACT_SEED_MUL) % (JuiceTimings.SILLY_ACT_MAX_MS - JuiceTimings.SILLY_ACT_MIN_MS)
    val actT = (tMs + aSeed % actPeriod) % actPeriod
    val actKind = ((aSeed / ACT_KIND_DIV) % ACT_KIND_MOD).toInt()
    val active = actT < SILLY_ACT_MS || forceAct
    val p = if (forceAct) {
        (tMs % SILLY_ACT_MS) / SILLY_ACT_MS.toFloat()
    } else {
        actT / SILLY_ACT_MS.toFloat()
    }
    return when {
        !active -> SillyAct(0f, 0f)
        actKind == 0 -> SillyAct(SPIN_DEGREES * p, 0f)
        actKind == 1 -> SillyAct(0f, -sin(p * PI.toFloat()) * sizePx * HOP_ACT_FRAC)
        else -> SillyAct(sin(p * 2f * PI.toFloat()) * WIGGLE_ACT_DEGREES, 0f)
    }
}

/** Deterministic pseudo-random in [0, 1] from (seed, index). */
private fun hash01(seed: Long, idx: Long): Float {
    var h = seed * HASH_A + idx * HASH_B
    h = (h xor (h ushr HASH_SHIFT_A)) * HASH_B
    h = (h xor (h ushr HASH_SHIFT_B)) * HASH_C
    h = h xor (h ushr HASH_SHIFT_C)
    return (h ushr HASH_SHIFT_FINAL).toFloat() / HASH_DIVISOR.toFloat()
}

internal fun easeOutBack(t: Float): Float {
    val c1 = EASE_C1
    val c3 = c1 + 1f
    val u = t - 1f
    return 1f + c3 * u * u * u + c1 * u * u
}

internal fun easeInOut(t: Float): Float =
    if (t < EASE_MIDPOINT) {
        EASE_DOUBLE * t * t
    } else {
        1f - ((EASE_DOUBLE * t - EASE_DOUBLE) * (EASE_DOUBLE * t - EASE_DOUBLE)) / EASE_DOUBLE
    }

private const val SILLY_ACT_MS = 600f

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
private const val EASE_DOUBLE = 2f
private const val HASH_A = -0x61C8864680B583EBL
private const val HASH_B = -0x40A7B8925D427DE7L
private const val HASH_C = -0x6B2FB644D1B20CBL
private const val HASH_DIVISOR = 1L shl 53
private const val HASH_SHIFT_A = 30
private const val HASH_SHIFT_B = 27
private const val HASH_SHIFT_C = 31
private const val HASH_SHIFT_FINAL = 11
