package com.sreecharan.brainrot_buddies_steal_battle.p2p

import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RaidRulesTest {

    private val zone = ZoneId.of("America/Chicago")

    // ---- window start math (window identity comes from GameEngine) ----

    @Test
    fun `window start converts the engine window to epoch millis`() {
        val start = RaidRules.windowStartMs(LocalDate.of(2026, 10, 16), 14, zone)
        val expected = LocalDate.of(2026, 10, 16).atTime(14, 0).atZone(zone)
            .toInstant().toEpochMilli()
        assertEquals(expected, start)
    }

    @Test
    fun `three raids per window then exhausted`() {
        assertTrue(RaidRules.canStartRaid(0))
        assertTrue(RaidRules.canStartRaid(2))
        assertFalse(RaidRules.canStartRaid(3))
        assertFalse(RaidRules.canStartRaid(4))
    }

    // ---- rookie shield: steal-immune until onboarding day 7 ----

    @Test
    fun `rookie shield holds below day 7 and lifts on day 7`() {
        assertTrue(RaidRules.isRookieShielded(1))
        assertTrue(RaidRules.isRookieShielded(6))
        assertFalse(RaidRules.isRookieShielded(7))
        assertFalse(RaidRules.isRookieShielded(30))
    }

    // ---- revenge ----

    @Test
    fun `revenge is available for 24h and only once`() {
        val now = 1_700_000_000_000L
        val hour = 60L * 60L * 1000L
        assertTrue(RaidRules.canRevenge(now - 23 * hour, revengeUsed = false, now))
        assertFalse(RaidRules.canRevenge(now - 25 * hour, revengeUsed = false, now))
        assertFalse(RaidRules.canRevenge(now - 1 * hour, revengeUsed = true, now))
    }

    // ---- live steal validation ----

    private data class LiveStealFixture(
        val slotOccupied: Boolean = true,
        val alreadyRaided: Boolean = false,
        val baseShieldUntil: Long = 0L,
        val slotShieldUntil: Long = 0L,
        val cooldownUntil: Long = 0L,
        val rookieShielded: Boolean = false,
    )

    private fun liveVerdict(fixture: LiveStealFixture = LiveStealFixture()): StealVerdict {
        val now = 1_700_000_000_000L
        return RaidRules.validateLiveSteal(
            slotOccupied = fixture.slotOccupied,
            alreadyRaidedThisWindow = fixture.alreadyRaided,
            baseShieldUntilMs = fixture.baseShieldUntil,
            slotShieldUntilMs = fixture.slotShieldUntil,
            buddyCooldownUntilMs = fixture.cooldownUntil,
            victimRookieShielded = fixture.rookieShielded,
            nowMs = now,
        )
    }

    @Test
    fun `live steal accepted when everything is clear`() {
        assertTrue(liveVerdict() is StealVerdict.Accepted)
    }

    @Test
    fun `live steal rejects empty slot`() {
        val verdict = liveVerdict(LiveStealFixture(slotOccupied = false))
        assertEquals(StealRejectReason.EMPTY, (verdict as StealVerdict.Rejected).reason)
    }

    @Test
    fun `live steal rejects second raid on same friend in window`() {
        val verdict = liveVerdict(LiveStealFixture(alreadyRaided = true))
        assertEquals(StealRejectReason.ALREADY_RAIDED, (verdict as StealVerdict.Rejected).reason)
    }

    @Test
    fun `live steal rejects shielded base and shielded slot`() {
        val now = 1_700_000_000_000L
        assertEquals(
            StealRejectReason.SHIELDED,
            (liveVerdict(LiveStealFixture(baseShieldUntil = now + 1000)) as StealVerdict.Rejected).reason,
        )
        assertEquals(
            StealRejectReason.SHIELDED,
            (liveVerdict(LiveStealFixture(slotShieldUntil = now + 1000)) as StealVerdict.Rejected).reason,
        )
    }

    @Test
    fun `live steal rejects buddy on 30-minute cooldown`() {
        val now = 1_700_000_000_000L
        assertEquals(
            StealRejectReason.COOLDOWN,
            (liveVerdict(LiveStealFixture(cooldownUntil = now + 1000)) as StealVerdict.Rejected).reason,
        )
        assertTrue(liveVerdict(LiveStealFixture(cooldownUntil = now - 1000)) is StealVerdict.Accepted)
    }

    @Test
    fun `live steal rejects rookie-shielded victim as shielded`() {
        val verdict = liveVerdict(LiveStealFixture(rookieShielded = true)) as StealVerdict.Rejected
        assertEquals(StealRejectReason.SHIELDED, verdict.reason)
        // The frozen wire reason for rookie shield is "shielded".
        assertEquals("shielded", verdict.reason.wire)
    }

    @Test
    fun `wire reasons match the frozen contract set`() {
        val wires = StealRejectReason.entries.mapNotNull { it.wire }
        assertEquals(setOf("shielded", "cooldown", "empty", "already_raided"), wires.toSet())
    }

    // ---- attacker pre-checks ----

    @Test
    fun `attacker pre-check enforces window budget first`() {
        val verdict = RaidRules.canAttemptSteal(
            raidsUsed = 3,
            alreadyRaidedFriend = false,
            snapshotRaided = false,
            isShadow = false,
            slotOccupied = true,
            baseShielded = false,
            buddyOnCooldown = false,
        )
        assertEquals(StealRejectReason.WINDOW_EXHAUSTED, (verdict as StealVerdict.Rejected).reason)
    }

    @Test
    fun `attacker pre-check blocks an already-spent snapshot`() {
        val verdict = RaidRules.canAttemptSteal(
            raidsUsed = 0,
            alreadyRaidedFriend = false,
            snapshotRaided = true,
            isShadow = true,
            slotOccupied = true,
            baseShielded = false,
            buddyOnCooldown = false,
        )
        assertEquals(StealRejectReason.SNAPSHOT_SPENT, (verdict as StealVerdict.Rejected).reason)
    }

    // ---- shadow heist ----

    private fun snapshot(shieldUntil: Long = 0L) = BaseSnapshot(
        guestId = "guest-2",
        baseName = "Noodle Nest",
        exportedAt = 1_700_000_000_000L,
        shieldUntil = shieldUntil,
        coinsPerSec = 49,
        slots = listOf(SnapshotSlot(slot = 2, buddyId = "gulp", level = 1)),
    )

    @Test
    fun `shadow heist accepted against a fresh snapshot`() {
        val verdict = RaidRules.validateShadowSteal(
            snapshot(), 2, snapshotAlreadyRaided = false, 1_700_000_000_000L,
        )
        assertTrue(verdict is StealVerdict.Accepted)
    }

    @Test
    fun `only one shadow heist per snapshot`() {
        val verdict = RaidRules.validateShadowSteal(
            snapshot(), 2, snapshotAlreadyRaided = true, 1_700_000_000_000L,
        )
        assertEquals(StealRejectReason.SNAPSHOT_SPENT, (verdict as StealVerdict.Rejected).reason)
    }

    @Test
    fun `shadow heist respects snapshot shield`() {
        val verdict = RaidRules.validateShadowSteal(
            snapshot(shieldUntil = 1_700_000_000_000L + 3_600_000L),
            2,
            snapshotAlreadyRaided = false,
            1_700_000_000_000L,
        )
        assertEquals(StealRejectReason.SHIELDED, (verdict as StealVerdict.Rejected).reason)
    }

    @Test
    fun `shadow heist rejects empty slot`() {
        val verdict = RaidRules.validateShadowSteal(
            snapshot(), 7, snapshotAlreadyRaided = false, 1_700_000_000_000L,
        )
        assertEquals(StealRejectReason.EMPTY, (verdict as StealVerdict.Rejected).reason)
    }
}
