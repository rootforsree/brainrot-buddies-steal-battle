package com.sreecharan.brainrot_buddies_steal_battle.ui.components

import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Contract §10 juice rules as specs: trauma shake decays and never sustains,
 * the photosensitivity guard enforces the hard limits.
 */
class JuiceTimingsTest {

    @Test
    fun `trauma shake is quadratic and decays about 1 per second`() {
        val shake = TraumaShake()
        shake.add(0.5f)

        val offset = shake.offset(1_000L, 100f)
        // offset magnitude = trauma^2 * maxPx * |noise| <= 0.25 * 100
        val magnitude = kotlin.math.sqrt(
            (offset.x * offset.x + offset.y * offset.y).toDouble(),
        ).toFloat()
        assertTrue(magnitude <= 25.001f, "offset must equal trauma^2 * noise * maxPx")

        shake.update(0.25f)
        assertEquals(0.25f, shake.trauma, 0.001f)

        shake.update(1f)
        assertEquals(0f, shake.trauma, 0.001f)
        assertEquals(0f, shake.offset(2_000L, 100f).x, 0.001f)
        assertEquals(0f, shake.offset(2_000L, 100f).y, 0.001f)
    }

    @Test
    fun `trauma clamps at 1 and never sustains without new events`() {
        val shake = TraumaShake()
        shake.add(0.8f)
        shake.add(0.8f)
        assertEquals(1f, shake.trauma, 0.001f)

        // Twenty seconds with no events: fully decayed, never sustained.
        repeat(20) { shake.update(1f) }
        assertEquals(0f, shake.trauma, 0.001f)
    }

    @Test
    fun `pseudo noise stays in range and varies with time`() {
        val samples = (0L until 100L).map { TraumaShake.pseudoNoise(it * 37L, 12.9898) }
        assertTrue(samples.all { it in -1f..1f })
        assertTrue(samples.toSet().size > 90, "noise must not repeat trivially")
    }

    @Test
    fun `flash guard allows at most 3 flashes per second`() {
        var now = 0L
        val guard = FlashGuard(clock = { now })

        assertTrue(guard.allowFlash(0.1f, isRed = false))
        assertTrue(guard.allowFlash(0.1f, isRed = false))
        assertTrue(guard.allowFlash(0.1f, isRed = false))
        assertFalse(guard.allowFlash(0.1f, isRed = false), "4th flash in one second is denied")

        now += 1_001L
        assertTrue(guard.allowFlash(0.1f, isRed = false), "window slides after a second")
    }

    @Test
    fun `flash guard never allows red flashes or large areas`() {
        var now = 0L
        val guard = FlashGuard(clock = { now })

        assertFalse(guard.allowFlash(0.05f, isRed = true), "no red flashes, ever")
        assertFalse(
            guard.allowFlash(JuiceTimings.FLASH_MAX_AREA_FRACTION, isRed = false),
            "flashing area must stay under 25%",
        )
        assertFalse(guard.allowFlash(0.9f, isRed = false))
    }

    @Test
    fun `particle system caps at 1000 and simulates fast`() {
        val system = ParticleSystem()
        val spec = Bursts.CONFETTI
        repeat(200) { system.burst(100f, 100f, spec) }
        assertTrue(system.aliveCount <= JuiceTimings.PARTICLE_MAX)

        val start = System.nanoTime()
        repeat(60) { system.update(0.016f) }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000.0
        assertTrue(elapsedMs < 120.0, "60 frames of full-pool sim took ${elapsedMs}ms (budget 120ms)")
        assertTrue(system.aliveCount < 1000, "particles die on schedule")
    }

    @Test
    fun `burst counts stay inside the 5 to 20 band`() {
        listOf(
            Bursts.CONFETTI, Bursts.DUST_POOF, Bursts.COINS,
            Bursts.STARS, Bursts.BLUE_RING, Bursts.GOLD_RAYS, Bursts.RED_POOF,
        ).forEach { spec ->
            assertTrue(
                spec.count in JuiceTimings.PARTICLE_BURST_MIN..JuiceTimings.PARTICLE_BURST_MAX,
                "${spec.count} outside the 5-20 band",
            )
        }
    }

    @Test
    fun `claim timing constants match contract section 10`() {
        assertEquals(60L, JuiceTimings.CLAIM_SQUASH_MS)
        assertEquals(190L, JuiceTimings.CLAIM_ARC_MS)
        assertEquals(350L, JuiceTimings.CLAIM_LAND_MS)
        assertEquals(600L, JuiceTimings.CLAIM_TOTAL_MS)
        assertEquals(100L, JuiceTimings.EPIC_SLOWMO_MS)
        assertEquals(80L, JuiceTimings.STEAL_HITSTOP_MS)
        assertEquals(700L, JuiceTimings.STEAL_SUCCESS_MS)
        assertEquals(500L, JuiceTimings.STEAL_FAIL_MS)
        assertEquals(3, JuiceTimings.FLASH_MAX_PER_SEC)
        assertTrue(abs(JuiceTimings.FLASH_MAX_AREA_FRACTION - 0.25f) < 0.001f)
    }
}
