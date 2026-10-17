package com.sreecharan.brainrot_buddies_steal_battle.ads

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdThrottleTest {
    private val throttle = AdThrottle()
    private val halfHour = 30L * 60L * 1000L

    @Test
    fun `first show is always allowed`() {
        assertTrue(throttle.canShow(nowMs = 1_000L, lastShownMs = 0L))
    }

    @Test
    fun `second show within 30 minutes is blocked`() {
        val now = 10_000_000L
        assertFalse(throttle.canShow(now, lastShownMs = now - halfHour + 60_000L))
    }

    @Test
    fun `show at exactly 30 minutes is allowed`() {
        val now = 10_000_000L
        assertTrue(throttle.canShow(now, lastShownMs = now - halfHour))
    }

    @Test
    fun `show after 30 minutes is allowed`() {
        val now = 10_000_000L
        assertTrue(throttle.canShow(now, lastShownMs = now - halfHour - 1L))
    }

    @Test
    fun `remaining time counts down to the next slot`() {
        val now = 10_000_000L
        assertEquals(0L, throttle.millisUntilNextShow(now, lastShownMs = 0L))
        assertEquals(60_000L, throttle.millisUntilNextShow(now, lastShownMs = now - halfHour + 60_000L))
        assertEquals(0L, throttle.millisUntilNextShow(now, lastShownMs = now - halfHour))
    }

    @Test
    fun `custom interval is honored`() {
        val strict = AdThrottle(minIntervalMs = 60_000L)
        val now = 10_000_000L
        assertFalse(strict.canShow(now, lastShownMs = now - 59_000L))
        assertTrue(strict.canShow(now, lastShownMs = now - 60_000L))
    }
}
