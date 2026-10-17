package com.sreecharan.brainrot_buddies_steal_battle.game.engine

import com.sreecharan.brainrot_buddies_steal_battle.data.model.Rarity
import org.junit.jupiter.api.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Suppress("MagicNumber")
class GameEngineTest {
    private val engine = GameEngine()
    private val chicago = ZoneId.of("America/Chicago")

    // ---------- Conveyor pulls ----------

    @Test
    fun `commons dominate pulls at roughly seventy percent`() {
        val random = Random(7)
        val rarities = List(10_000) { engine.resolvePull(PullInput(), random).rarity }

        val commonShare = rarities.count { it == Rarity.COMMON } / 10_000.0

        assertTrue(commonShare in 0.65..0.75, "common share was $commonShare")
    }

    @Test
    fun `epics land at roughly eight percent`() {
        val random = Random(7)
        val rarities = List(10_000) { engine.resolvePull(PullInput(), random).rarity }

        val epicShare = rarities.count { it == Rarity.EPIC } / 10_000.0

        assertTrue(epicShare in 0.06..0.10, "epic share was $epicShare")
    }

    @Test
    fun `mega ticket guarantees an epic and is consumed`() {
        val out = engine.resolvePull(PullInput(hasMegaTicket = true), Random(1))

        assertEquals(Rarity.EPIC, out.rarity)
        assertTrue(out.consumedMegaTicket)
        assertFalse(out.consumedLuckyTicket)
        assertFalse(out.pityTriggered)
    }

    @Test
    fun `lucky ticket guarantees at least rare and is consumed`() {
        val out = engine.resolvePull(PullInput(hasLuckyTicket = true), Random(3))

        assertTrue(out.rarity == Rarity.RARE || out.rarity == Rarity.EPIC)
        assertTrue(out.consumedLuckyTicket)
        assertFalse(out.consumedMegaTicket)
    }

    @Test
    fun `lucky ticket can still roll an epic`() {
        val seed =
            generateSequence(0L) { it + 1 }
                .first { engine.resolvePull(PullInput(hasLuckyTicket = true), Random(it)).rarity == Rarity.EPIC }

        val out = engine.resolvePull(PullInput(hasLuckyTicket = true), Random(seed))

        assertEquals(Rarity.EPIC, out.rarity)
    }

    @Test
    fun `twenty five commons without rare triggers the rare pity`() {
        val out = engine.resolvePull(PullInput(commonsSinceRare = 25, pullsSinceEpic = 10), Random(1))

        assertEquals(Rarity.RARE, out.rarity)
        assertTrue(out.pityTriggered)
        assertEquals(0, out.commonsSinceRare)
        assertEquals(11, out.pullsSinceEpic)
    }

    @Test
    fun `sixty pulls without epic triggers the epic pity`() {
        val out = engine.resolvePull(PullInput(commonsSinceRare = 5, pullsSinceEpic = 60), Random(1))

        assertEquals(Rarity.EPIC, out.rarity)
        assertTrue(out.pityTriggered)
        assertEquals(0, out.pullsSinceEpic)
        assertEquals(0, out.commonsSinceRare)
    }

    @Test
    fun `epic pity wins when both pity thresholds are hit`() {
        val out = engine.resolvePull(PullInput(commonsSinceRare = 40, pullsSinceEpic = 60), Random(1))

        assertEquals(Rarity.EPIC, out.rarity)
        assertTrue(out.pityTriggered)
    }

    @Test
    fun `bite reroll lifts the rolled rarity exactly one tier`() {
        val seed = 42L
        val plain = engine.resolvePull(PullInput(), Random(seed))
        val rerolled = engine.resolvePull(PullInput(hasBiteReroll = true), Random(seed))

        val expected =
            when (plain.rarity) {
                Rarity.COMMON -> Rarity.RARE
                Rarity.RARE -> Rarity.EPIC
                Rarity.EPIC -> Rarity.EPIC
            }
        assertEquals(expected, rerolled.rarity)
        assertTrue(rerolled.consumedBiteReroll)
    }

    @Test
    fun `bite reroll on an epic stays epic`() {
        val seed =
            generateSequence(0L) { it + 1 }
                .first { engine.resolvePull(PullInput(), Random(it)).rarity == Rarity.EPIC }

        val rerolled = engine.resolvePull(PullInput(hasBiteReroll = true), Random(seed))

        assertEquals(Rarity.EPIC, rerolled.rarity)
        assertTrue(rerolled.consumedBiteReroll)
    }

    @Test
    fun `pity counters reset after a rare or better pull`() {
        val out =
            engine.resolvePull(
                PullInput(hasMegaTicket = true, commonsSinceRare = 10, pullsSinceEpic = 59),
                Random(1),
            )

        assertEquals(0, out.commonsSinceRare)
        assertEquals(0, out.pullsSinceEpic)
    }

    @Test
    fun `saturday event doubles epic chance`() {
        val random = Random(7)
        val rarities =
            List(10_000) {
                engine.resolvePull(PullInput(saturdayEvent = true), random).rarity
            }

        val epicShare = rarities.count { it == Rarity.EPIC } / 10_000.0

        assertTrue(epicShare in 0.13..0.19, "event epic share was $epicShare")
    }

    // ---------- Economy ----------

    @Test
    fun `upgrade costs match the contract examples`() {
        assertEquals(100L, engine.upgradeCostCoins(baseCoinsPerSec = 1, targetLevel = 2))
        assertEquals(1600L, engine.upgradeCostCoins(baseCoinsPerSec = 8, targetLevel = 3))
        assertEquals(8000L, engine.upgradeCostCoins(baseCoinsPerSec = 40, targetLevel = 3))

        assertEquals(5, engine.upgradeCostBites(targetLevel = 2))
        assertEquals(10, engine.upgradeCostBites(targetLevel = 3))
    }

    @Test
    fun `upgrade multipliers are one one-point-five two-point-two`() {
        assertEquals(1.0, engine.upgradeMultiplier(1))
        assertEquals(1.5, engine.upgradeMultiplier(2))
        assertEquals(2.2, engine.upgradeMultiplier(3))
    }

    @Test
    fun `slot unlock costs follow the twenty step ladder`() {
        assertEquals(50L, engine.slotUnlockCost(1))
        assertEquals(100L, engine.slotUnlockCost(2))
        assertEquals(100_000L, engine.slotUnlockCost(11))
        assertEquals(150_000L, engine.slotUnlockCost(12))
        assertEquals(150_000L, engine.slotUnlockCost(20))
    }

    @Test
    fun `buddy income scales with the level multiplier`() {
        assertEquals(8.0, engine.buddyCoinsPerSec(baseCoinsPerSec = 8, level = 1))
        assertEquals(12.0, engine.buddyCoinsPerSec(baseCoinsPerSec = 8, level = 2))
        assertEquals(63_360.0, engine.buddyCoinsPerHour(baseCoinsPerSec = 8, level = 3))
    }

    @Test
    fun `album tier rewards match the contract`() {
        assertEquals(500L, engine.albumTierReward(Rarity.COMMON))
        assertEquals(2_500L, engine.albumTierReward(Rarity.RARE))
        assertEquals(10_000L, engine.albumTierReward(Rarity.EPIC))
    }

    @Test
    fun `consolation is half the stolen buddy hourly earnings`() {
        assertEquals(14_400L, engine.consolationCoins(stolenBuddyCoinsPerSec = 8.0))
    }

    // ---------- Vault and offline earnings ----------

    @Test
    fun `vault accrues income between ticks`() {
        val coins =
            engine.accrueVaultCoins(
                currentCoins = 0L,
                totalCoinsPerSec = 10.0,
                lastTickMs = 0L,
                nowMs = 3_600_000L,
            )

        assertEquals(36_000L, coins)
    }

    @Test
    fun `vault never exceeds four hours of income`() {
        val coins =
            engine.accrueVaultCoins(
                currentCoins = 140_000L,
                totalCoinsPerSec = 10.0,
                lastTickMs = 0L,
                nowMs = 3_600_000L,
            )

        assertEquals(144_000L, coins)
    }

    @Test
    fun `vault accrual ignores time before the last tick`() {
        val coins =
            engine.accrueVaultCoins(
                currentCoins = 1_000L,
                totalCoinsPerSec = 10.0,
                lastTickMs = 5_000L,
                nowMs = 1_000L,
            )

        assertEquals(1_000L, coins)
    }

    @Test
    fun `offline earnings cap at four hours`() {
        val coins = engine.offlineEarningsCoins(10.0, awayMs = 28_800_000L)

        assertEquals(144_000L, coins)
    }

    @Test
    fun `offline earnings scale with coins per second`() {
        val coins = engine.offlineEarningsCoins(40.0, awayMs = 3_600_000L)

        assertEquals(144_000L, coins)
    }

    // ---------- Raid windows ----------

    @Test
    fun `nine thirty am falls in the eight am window`() {
        val now = ZonedDateTime.of(2026, 10, 16, 9, 30, 0, 0, chicago)

        assertEquals("2026-10-16-08", engine.raidWindowBucket(now))
    }

    @Test
    fun `one thirty am falls in the previous day eight pm window`() {
        val now = ZonedDateTime.of(2026, 10, 16, 1, 30, 0, 0, chicago)

        assertEquals("2026-10-15-20", engine.raidWindowBucket(now))
    }

    @Test
    fun `two am starts a new window bucket`() {
        val now = ZonedDateTime.of(2026, 10, 16, 2, 0, 0, 0, chicago)

        assertEquals("2026-10-16-02", engine.raidWindowBucket(now))
    }

    @Test
    fun `millis until next window at nine thirty is four and a half hours`() {
        val now = ZonedDateTime.of(2026, 10, 16, 9, 30, 0, 0, chicago)

        assertEquals(16_200_000L, engine.millisUntilNextRaidWindow(now))
    }

    @Test
    fun `millis until next window at one thirty am is thirty minutes`() {
        val now = ZonedDateTime.of(2026, 10, 16, 1, 30, 0, 0, chicago)

        assertEquals(1_800_000L, engine.millisUntilNextRaidWindow(now))
    }

    // ---------- Login calendar ----------

    @Test
    fun `first claim starts day one with a one day streak`() {
        val advance =
            engine.advanceCalendarDay(
                currentDay = 1,
                lastClaimDate = "",
                streakDays = 0,
                todayKey = "2026-10-16",
                yesterdayKey = "2026-10-15",
            )

        assertEquals(CalendarAdvance(1, "2026-10-16", 1, claimed = true), advance)
    }

    @Test
    fun `consecutive claim advances the day and streak`() {
        val advance =
            engine.advanceCalendarDay(
                currentDay = 3,
                lastClaimDate = "2026-10-15",
                streakDays = 3,
                todayKey = "2026-10-16",
                yesterdayKey = "2026-10-15",
            )

        assertEquals(CalendarAdvance(4, "2026-10-16", 4, claimed = true), advance)
    }

    @Test
    fun `claiming twice in one day is a no-op`() {
        val advance =
            engine.advanceCalendarDay(
                currentDay = 3,
                lastClaimDate = "2026-10-16",
                streakDays = 3,
                todayKey = "2026-10-16",
                yesterdayKey = "2026-10-15",
            )

        assertEquals(CalendarAdvance(3, "2026-10-16", 3, claimed = false), advance)
    }

    @Test
    fun `a missed day pauses without resetting`() {
        val advance =
            engine.advanceCalendarDay(
                currentDay = 5,
                lastClaimDate = "2026-10-13",
                streakDays = 5,
                todayKey = "2026-10-16",
                yesterdayKey = "2026-10-15",
            )

        assertEquals(CalendarAdvance(5, "2026-10-16", 5, claimed = true), advance)
    }

    @Test
    fun `calendar caps at day thirty`() {
        val advance =
            engine.advanceCalendarDay(
                currentDay = 30,
                lastClaimDate = "2026-10-15",
                streakDays = 29,
                todayKey = "2026-10-16",
                yesterdayKey = "2026-10-15",
            )

        assertEquals(CalendarAdvance(30, "2026-10-16", 30, claimed = true), advance)
    }

    @Test
    fun `day rewards match the frozen calendar`() {
        assertEquals(DayReward(coins = 200L), engine.dayReward(1))
        assertEquals(DayReward(megaTickets = 1), engine.dayReward(7))
        assertEquals(DayReward(guaranteedRarity = Rarity.RARE), engine.dayReward(14))
        assertEquals(DayReward(coins = 5_000L), engine.dayReward(21))
        assertEquals(DayReward(guaranteedRarity = Rarity.EPIC), engine.dayReward(30))
        assertEquals(DayReward(), engine.dayReward(5))
    }

    // ---------- Redeem codes ----------

    @Test
    fun `stealfun26 grants five hundred coins and the party emote`() {
        val result = engine.validateRedeemCode("STEALFUN26", emptySet())

        assertEquals(
            RedeemResult.Granted(coins = 500L, luckyTickets = 0, megaTickets = 0, emoteId = "party"),
            result,
        )
    }

    @Test
    fun `buddyup26 grants one lucky ticket`() {
        val result = engine.validateRedeemCode("BUDDYUP26", emptySet())

        assertEquals(
            RedeemResult.Granted(coins = 0L, luckyTickets = 1, megaTickets = 0, emoteId = null),
            result,
        )
    }

    @Test
    fun `a used code is rejected as already used`() {
        val result = engine.validateRedeemCode("STEALFUN26", setOf("STEALFUN26"))

        assertEquals(RedeemResult.AlreadyUsed, result)
    }

    @Test
    fun `an unknown code is rejected as invalid`() {
        val result = engine.validateRedeemCode("NOTACODE", emptySet())

        assertEquals(RedeemResult.Invalid, result)
    }

    @Test
    fun `code matching ignores case and whitespace`() {
        val result = engine.validateRedeemCode("  stealfun26 ", emptySet())

        assertTrue(result is RedeemResult.Granted)
    }

    // ---------- Saturday event ----------

    @Test
    fun `zoom race is active saturday at eleven oh five`() {
        val saturday = ZonedDateTime.of(2026, 10, 17, 11, 5, 0, 0, chicago)

        assertTrue(engine.isZoomRaceActive(saturday))
    }

    @Test
    fun `zoom race is not active saturday at noon`() {
        val saturdayNoon = ZonedDateTime.of(2026, 10, 17, 12, 0, 0, 0, chicago)
        val friday = ZonedDateTime.of(2026, 10, 16, 11, 5, 0, 0, chicago)

        assertFalse(engine.isZoomRaceActive(saturdayNoon))
        assertFalse(engine.isZoomRaceActive(friday))
    }

    @Test
    fun `millis until zoom race from friday morning is twenty five hours`() {
        val fridayMorning = ZonedDateTime.of(2026, 10, 16, 10, 0, 0, 0, chicago)

        assertEquals(90_000_000L, engine.millisUntilZoomRace(fridayMorning))
    }
}
