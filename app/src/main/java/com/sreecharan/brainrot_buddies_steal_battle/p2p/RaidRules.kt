package com.sreecharan.brainrot_buddies_steal_battle.p2p

import java.time.LocalDate
import java.time.ZoneId

/**
 * Pure raid-rule logic, per docs/contract.md §1 and §8.
 *
 * Window identity (the 6-hour cycle at 8 AM / 2 PM / 8 PM / 2 AM local)
 * comes from Builder A's [com.sreecharan.brainrot_buddies_steal_battle.game.engine.GameEngine];
 * this object owns the P2P steal validations and the anti-grief rules:
 * - During a window: up to 3 raids, 1 per friend.
 * - One successful shadow heist per snapshot.
 * - 30-minute steal cooldown per buddy.
 * - Rookie shield: a base is steal-immune while its owner's onboarding day < 7
 *   (except practice visits, which never steal). Enforced on the victim's
 *   device in LiveRaidResponder; the §8 snapshot carries no onboarding day,
 *   so the attacker cannot know it.
 * - Revenge raids: 24h window, free, do not count against the 3.
 *
 * No Android dependencies: fully unit-testable.
 */
object RaidRules {

    const val MAX_RAIDS_PER_WINDOW = 3
    const val STEAL_COOLDOWN_MS = 30L * 60L * 1000L
    const val REVENGE_WINDOW_MS = 24L * 60L * 60L * 1000L

    /** A base is rookie-shielded below this onboarding day (victim-side). */
    const val RAID_UNLOCK_DAY = 7

    /** Start of a raid window as epoch millis, for record filtering. */
    fun windowStartMs(
        date: LocalDate,
        startHour: Int,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long = date.atTime(startHour, 0).atZone(zone).toInstant().toEpochMilli()

    /** True while the attacker still has raid slots left in this window. */
    fun canStartRaid(raidsUsed: Int): Boolean = raidsUsed < MAX_RAIDS_PER_WINDOW

    /** Rookie shield: steal-immune until onboarding day 7. */
    fun isRookieShielded(onboardingDay: Int): Boolean = onboardingDay < RAID_UNLOCK_DAY

    /** Revenge is available for 24h after the theft and only once. */
    fun canRevenge(raidCreatedAtMs: Long, revengeUsed: Boolean, nowMs: Long): Boolean =
        !revengeUsed && nowMs - raidCreatedAtMs < REVENGE_WINDOW_MS

    /**
     * Attacker-side pre-check before sending a RaidRequest.
     * The victim's device re-validates everything and has the final say.
     *
     * LongParameterList is suppressed by design: one parameter per §1
     * anti-grief rule, each named; grouping them would hide the rules.
     */
    @Suppress("LongParameterList")
    fun canAttemptSteal(
        raidsUsed: Int,
        alreadyRaidedFriend: Boolean,
        snapshotRaided: Boolean,
        isShadow: Boolean,
        slotOccupied: Boolean,
        baseShielded: Boolean,
        buddyOnCooldown: Boolean,
    ): StealVerdict = when {
        !canStartRaid(raidsUsed) -> StealVerdict.Rejected(StealRejectReason.WINDOW_EXHAUSTED)
        alreadyRaidedFriend -> StealVerdict.Rejected(StealRejectReason.ALREADY_RAIDED)
        isShadow && snapshotRaided -> StealVerdict.Rejected(StealRejectReason.SNAPSHOT_SPENT)
        !slotOccupied -> StealVerdict.Rejected(StealRejectReason.EMPTY)
        baseShielded -> StealVerdict.Rejected(StealRejectReason.SHIELDED)
        buddyOnCooldown -> StealVerdict.Rejected(StealRejectReason.COOLDOWN)
        else -> StealVerdict.Accepted
    }

    /**
     * Victim-side validation of a live RaidRequest, per §8:
     * shield? cooldown? already raided this window? rookie shield?
     * Rookie shield reports as "shielded" on the wire (frozen reason set).
     *
     * LongParameterList is suppressed by design: one parameter per §8
     * validation input, each named; grouping them would hide the checks.
     */
    @Suppress("LongParameterList")
    fun validateLiveSteal(
        slotOccupied: Boolean,
        alreadyRaidedThisWindow: Boolean,
        baseShieldUntilMs: Long,
        slotShieldUntilMs: Long,
        buddyCooldownUntilMs: Long,
        victimRookieShielded: Boolean,
        nowMs: Long,
    ): StealVerdict = when {
        !slotOccupied -> StealVerdict.Rejected(StealRejectReason.EMPTY)
        alreadyRaidedThisWindow -> StealVerdict.Rejected(StealRejectReason.ALREADY_RAIDED)
        victimRookieShielded || nowMs < baseShieldUntilMs || nowMs < slotShieldUntilMs ->
            StealVerdict.Rejected(StealRejectReason.SHIELDED)
        nowMs < buddyCooldownUntilMs -> StealVerdict.Rejected(StealRejectReason.COOLDOWN)
        else -> StealVerdict.Accepted
    }

    /**
     * Attacker-side validation of a shadow heist against a saved snapshot.
     * One successful shadow heist per snapshot; snapshots refresh only on a
     * new P2P exchange.
     */
    fun validateShadowSteal(
        snapshot: BaseSnapshot,
        slot: Int,
        snapshotAlreadyRaided: Boolean,
        nowMs: Long,
    ): StealVerdict {
        val target = snapshot.slots.firstOrNull { it.slot == slot }
        return when {
            snapshotAlreadyRaided -> StealVerdict.Rejected(StealRejectReason.SNAPSHOT_SPENT)
            target == null || target.buddyId.isBlank() -> StealVerdict.Rejected(StealRejectReason.EMPTY)
            nowMs < snapshot.shieldUntil -> StealVerdict.Rejected(StealRejectReason.SHIELDED)
            else -> StealVerdict.Accepted
        }
    }
}

/** Outcome of a steal validation. */
sealed interface StealVerdict {
    data object Accepted : StealVerdict
    data class Rejected(val reason: StealRejectReason) : StealVerdict
}

/**
 * Reject reasons. [wire] holds the frozen §8 reason strings sent in
 * RaidResult; the extra reasons are local-only and never go on the wire.
 */
enum class StealRejectReason(val wire: String?) {
    SHIELDED("shielded"),
    COOLDOWN("cooldown"),
    EMPTY("empty"),
    ALREADY_RAIDED("already_raided"),

    /** Local only: the 3-raids window budget is spent. */
    WINDOW_EXHAUSTED(null),

    /** Local only: this snapshot already gave up one shadow heist. */
    SNAPSHOT_SPENT(null),

    /** Local only: the attacker's base has no free unlocked slot. */
    BASE_FULL(null),
}
