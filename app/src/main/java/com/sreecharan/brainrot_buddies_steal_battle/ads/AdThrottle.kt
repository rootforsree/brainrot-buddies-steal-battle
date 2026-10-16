package com.sreecharan.brainrot_buddies_steal_battle.ads

/**
 * Pure throttle for the single rewarded-ad placement.
 * Contract: max 1 show per 30 minutes.
 */
class AdThrottle(
    val minIntervalMs: Long = DEFAULT_MIN_INTERVAL_MS,
) {

    fun canShow(nowMs: Long, lastShownMs: Long): Boolean {
        if (lastShownMs <= 0L) return true
        return nowMs - lastShownMs >= minIntervalMs
    }

    fun millisUntilNextShow(nowMs: Long, lastShownMs: Long): Long {
        if (lastShownMs <= 0L) return 0L
        return (minIntervalMs - (nowMs - lastShownMs)).coerceAtLeast(0L)
    }

    companion object {
        const val DEFAULT_MIN_INTERVAL_MS = 30L * 60L * 1000L
    }
}
