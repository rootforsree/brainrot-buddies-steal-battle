package com.sreecharan.brainrot_buddies_steal_battle.ui.components

import android.os.SystemClock
import androidx.compose.ui.geometry.Offset
import kotlin.math.sin

/**
 * Contract §10, to the millisecond. Every animation in the game board reads
 * its timing from here; nothing hardcodes a duration at the call site.
 */
object JuiceTimings {
    /** Every tap answers in <100 ms: sound + visual in the same frame batch. */
    const val TAP_ACK_MAX_MS = 100

    // Claim sequence: 0-60 squash ack, 60-250 spring arc, 250-600 landing.
    const val CLAIM_SQUASH_MS = 60L
    const val CLAIM_ARC_MS = 190L
    const val CLAIM_LAND_MS = 350L
    const val CLAIM_TOTAL_MS = 600L

    /** Epic grab: 100 ms of 0.3x slow-mo. */
    const val EPIC_SLOWMO_MS = 100L
    const val EPIC_SLOWMO_SCALE = 0.3f

    /** Steal success: 80 ms hit-stop, ~700 ms total. Steal fail: ~500 ms. */
    const val STEAL_HITSTOP_MS = 80L
    const val STEAL_SUCCESS_MS = 700L
    const val STEAL_FAIL_MS = 500L

    /** Upgrade: ~600 ms, 12 star sprites spiraling up. */
    const val UPGRADE_MS = 600L
    const val UPGRADE_STARS = 12

    /** Raid bell: ~1.5 s, ONE gentle screen-edge pulse, never sustained. */
    const val RAID_BELL_MS = 1500L

    /** Daily gift: ~1.5 s anticipation shake, ~2 s to reveal. */
    const val GIFT_SHAKE_MS = 1500L
    const val GIFT_REVEAL_MS = 2000L

    /** Staged steal/revenge auto-replay: 5 s cinematic. */
    const val REPLAY_MS = 5000L

    /** Mystery-pull ceremony drumroll before the slow reveal. */
    const val CEREMONY_DRUMROLL_MS = 1200L

    /** New buddy waddle-in after a claim lands. */
    const val WADDLE_IN_MS = 600L

    // Idle life, 3 layers (contract §10).
    const val BREATH_MIN_MS = 2500L
    const val BREATH_MAX_MS = 4000L
    const val BREATH_AMPLITUDE = 0.03f
    const val BLINK_MIN_MS = 2000L
    const val BLINK_MAX_MS = 7000L
    const val BLINK_CLOSE_MS = 70L
    const val BLINK_OPEN_MS = 100L
    const val DOUBLE_BLINK_CHANCE = 0.18f
    const val GAZE_MIN_MS = 250L
    const val GAZE_MAX_MS = 900L
    const val SWAY_MS = 400L
    const val SILLY_ACT_MIN_MS = 30_000L
    const val SILLY_ACT_MAX_MS = 60_000L

    // Conveyor: 8 s cadence, ~15 s ride; last 3 s are anticipation crouch.
    const val CONVEYOR_CADENCE_MS = 8_000L
    const val CONVEYOR_RIDE_MS = 15_000L
    const val ANTICIPATION_MS = 3_000L

    /** Floating "+N" rises ~24 px over 0.6-1.2 s. */
    const val FLOAT_UP_MIN_MS = 600L
    const val FLOAT_UP_MAX_MS = 1200L
    const val FLOAT_UP_PX = 24f

    // Juice rules.
    const val SQUASH_MIN = 0.10f
    const val SQUASH_MAX = 0.20f
    const val PARTICLE_BURST_MIN = 5
    const val PARTICLE_BURST_MAX = 20
    const val PARTICLE_MAX = 1000

    // Photosensitivity HARD LIMITS (WCAG 2.3.1), contract §10.
    const val FLASH_MAX_PER_SEC = 3
    const val FLASH_MAX_AREA_FRACTION = 0.25f

    /** Celebrations: rare+ skippable after 1 s. */
    const val SKIPPABLE_AFTER_MS = 1000L
}

/**
 * Trauma-based screen shake (Eiserloh): offset = trauma^2 x noise, decaying
 * ~1/s. Trauma is only ever ADDED by discrete events (claim land, steal
 * hit, raid bell pulse) and always decays — never sustained, per §10.
 */
class TraumaShake {
    var trauma: Float = 0f
        private set

    fun add(amount: Float) {
        trauma = (trauma + amount).coerceIn(0f, 1f)
    }

    fun update(dtSeconds: Float) {
        trauma = (trauma - dtSeconds * DECAY_PER_SECOND).coerceAtLeast(0f)
    }

    /** Shake offset in px, scaled by [maxPx]. Zero when trauma is gone. */
    fun offset(timeMs: Long, maxPx: Float): Offset {
        val shake = trauma * trauma
        if (shake <= 0f || maxPx <= 0f) return Offset.Zero
        return Offset(
            x = maxPx * shake * pseudoNoise(timeMs, NOISE_SEED_X),
            y = maxPx * shake * pseudoNoise(timeMs, NOISE_SEED_Y),
        )
    }

    companion object {
        private const val DECAY_PER_SECOND = 1f
        private const val NOISE_SEED_X = 12.9898
        private const val NOISE_SEED_Y = 78.2330
        private const val NOISE_TIME_SCALE = 0.013
        private const val NOISE_HASH_SCALE = 43758.5453

        /** Deterministic smooth-ish noise in [-1, 1] from a time value. */
        fun pseudoNoise(t: Long, seed: Double): Float {
            val x = sin(t * NOISE_TIME_SCALE + seed * NOISE_HASH_SCALE) * seed
            return (x - kotlin.math.floor(x)).toFloat() * 2f - 1f
        }
    }
}

/**
 * Enforces the §10 photosensitivity hard limits at the single choke point
 * every flash effect passes through: <= 3 flashes/second, NO red flashes,
 * flashing area < 25% of the screen. Anything that fails is simply not
 * drawn (the game stays fun; the flash was garnish).
 */
class FlashGuard(
    private val clock: () -> Long = SystemClock::uptimeMillis,
) {
    private val recentFlashes = ArrayDeque<Long>()

    fun allowFlash(areaFraction: Float, isRed: Boolean): Boolean {
        if (isRed || areaFraction >= JuiceTimings.FLASH_MAX_AREA_FRACTION) return false
        val now = clock()
        while (recentFlashes.isNotEmpty() && now - recentFlashes.first() > FLASH_WINDOW_MS) {
            recentFlashes.removeFirst()
        }
        val allowed = recentFlashes.size < JuiceTimings.FLASH_MAX_PER_SEC
        if (allowed) {
            recentFlashes.addLast(now)
        }
        return allowed
    }

    companion object {
        private const val FLASH_WINDOW_MS = 1000L
    }
}
