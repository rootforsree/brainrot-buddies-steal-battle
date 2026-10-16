package com.sreecharan.brainrot_buddies_steal_battle.game.engine

import com.sreecharan.brainrot_buddies_steal_battle.data.model.Rarity
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.random.Random

/** Input for one conveyor pull resolution. */
data class PullInput(
    val hasLuckyTicket: Boolean = false,
    val hasMegaTicket: Boolean = false,
    val hasBiteReroll: Boolean = false,
    val commonsSinceRare: Int = 0,
    val pullsSinceEpic: Int = 0,
    val saturdayEvent: Boolean = false,
)

/** Outcome of one conveyor pull resolution, including updated pity counters. */
data class PullOutput(
    val rarity: Rarity,
    val consumedLuckyTicket: Boolean,
    val consumedMegaTicket: Boolean,
    val consumedBiteReroll: Boolean,
    val commonsSinceRare: Int,
    val pullsSinceEpic: Int,
    val pityTriggered: Boolean,
)

/** One 6-hour raid window, identified by the local date of its start. */
data class RaidWindow(
    val date: LocalDate,
    val startHour: Int,
)

/** Reward granted by one day of the 30-day login calendar. */
data class DayReward(
    val coins: Long = 0L,
    val luckyTickets: Int = 0,
    val megaTickets: Int = 0,
    val guaranteedRarity: Rarity? = null,
)

/** Result of advancing the login calendar for today. */
data class CalendarAdvance(
    val day: Int,
    val lastClaimDate: String,
    val streakDays: Int,
    val claimed: Boolean,
)

/** Result of validating a redeem code. */
sealed interface RedeemResult {
    data class Granted(
        val coins: Long,
        val luckyTickets: Int,
        val megaTickets: Int,
        val emoteId: String?,
    ) : RedeemResult

    data object AlreadyUsed : RedeemResult
    data object Invalid : RedeemResult
}

/**
 * Pure game logic for Steal Battle. No Android classes, no I/O, no coroutines:
 * every function is thread-agnostic math, so no dispatcher is injected or
 * hardcoded. All frozen numbers come from docs/contract.md §4.
 *
 * The rules are split into focused seams (pulls, economy, raid windows,
 * calendar, redeem, events) so no single type exceeds the complexity
 * budget; [GameEngine] composes them all with delegation and stays the
 * type the UI injects.
 */
@Singleton
class GameEngine @Inject constructor() :
    PullRules by PullEngine,
    EconomyRules by EconomyEngine,
    RaidWindowRules by RaidWindowEngine,
    CalendarRules by CalendarEngine,
    RedeemRules by RedeemEngine,
    EventRules by EventEngine

/** Conveyor pull resolution. */
interface PullRules {

    /**
     * Resolves one conveyor pull. Precedence: Mega Ticket, then Epic pity,
     * then Lucky Ticket, then Common pity, then the weighted roll (with an
     * optional bite re-roll lifting the rolled rarity one tier).
     */
    fun resolvePull(input: PullInput, random: Random = Random.Default): PullOutput
}

/** Upgrade, slot, album, vault and offline-earnings math. */
interface EconomyRules {

    /**
     * Upgrade coin cost. Follows the §4 worked examples exactly:
     * 100 x baseCps x (targetLevel - 1), so Common->L2 = 100, Rare->L3 = 1600,
     * Epic->L3 = 8000. (The §4 formula text says "targetLevel"; the three
     * worked examples all use targetLevel - 1. Coordinator to confirm.)
     */
    fun upgradeCostCoins(baseCoinsPerSec: Int, targetLevel: Int): Long

    /** Upgrade bite cost: 5 x (targetLevel - 1), matching the §4 examples. */
    fun upgradeCostBites(targetLevel: Int): Int

    fun upgradeMultiplier(level: Int): Double

    fun buddyCoinsPerSec(baseCoinsPerSec: Int, level: Int): Double

    fun buddyCoinsPerHour(baseCoinsPerSec: Int, level: Int): Double

    /**
     * Cost to unlock the [unlockNumber]-th extra slot (1..20, covering base
     * slots 4..23). §4 ladder: 50 ... 100000, then 150000 x9.
     */
    fun slotUnlockCost(unlockNumber: Int): Long

    /** Album tier-completion coin rewards: Commons 500 / Rares 2500 / Epics 10000. */
    fun albumTierReward(rarity: Rarity): Long

    /** Victim consolation: 50% of the stolen buddy's hourly earnings. */
    fun consolationCoins(stolenBuddyCoinsPerSec: Double): Long

    /** Vault cap: 4 hours of current income. */
    fun vaultCapCoins(totalCoinsPerSec: Double): Long

    /**
     * Accrues vault coins between [lastTickMs] and [nowMs], capped at 4 hours
     * of current income. Coins never decrease here; collecting is a write.
     */
    fun accrueVaultCoins(
        currentCoins: Long,
        totalCoinsPerSec: Double,
        lastTickMs: Long,
        nowMs: Long,
    ): Long

    /** Offline earnings: income for time away, capped at 4 hours. */
    fun offlineEarningsCoins(totalCoinsPerSec: Double, awayMs: Long): Long
}

/** Raid window math. */
interface RaidWindowRules {

    /**
     * The 6-hour raid window containing [now]. Windows start at 2, 8, 14 and
     * 20 local time; times before 2 AM belong to the previous day's 8 PM
     * window.
     */
    fun currentRaidWindow(now: ZonedDateTime = ZonedDateTime.now()): RaidWindow

    /** Storage bucket for a window: "yyyy-MM-dd-HH" of the window start. */
    fun raidWindowBucket(window: RaidWindow): String

    fun raidWindowBucket(now: ZonedDateTime = ZonedDateTime.now()): String

    fun millisUntilNextRaidWindow(now: ZonedDateTime = ZonedDateTime.now()): Long
}

/** Login calendar math. */
interface CalendarRules {

    fun todayKey(zone: ZoneId = ZoneId.systemDefault(), now: Instant = Instant.now()): String

    fun yesterdayKey(zone: ZoneId = ZoneId.systemDefault(), now: Instant = Instant.now()): String

    /**
     * Advances the calendar for a claim today. A missed day PAUSES: the day
     * and streak stay put, never reset. Claiming twice in one day is a no-op.
     */
    fun advanceCalendarDay(
        currentDay: Int,
        lastClaimDate: String,
        streakDays: Int,
        todayKey: String,
        yesterdayKey: String,
    ): CalendarAdvance

    /** Frozen 30-day calendar rewards (§1): day 1, 7, 14, 21, 30. */
    fun dayReward(day: Int): DayReward
}

/** Redeem code validation. */
interface RedeemRules {

    fun normalizeRedeemCode(raw: String): String

    /**
     * Validates a redeem code against already-used codes. STEALFUN26 grants
     * 500 coins + the party emote; BUDDYUP26 grants 1 Lucky Ticket.
     */
    fun validateRedeemCode(rawCode: String, usedCodes: Set<String>): RedeemResult
}

/** Saturday event math. */
interface EventRules {

    /** Zara's Zoom Race: Saturdays 11:00-11:20 local, 20-minute sprint. */
    fun isZoomRaceActive(now: ZonedDateTime = ZonedDateTime.now()): Boolean

    fun millisUntilZoomRace(now: ZonedDateTime = ZonedDateTime.now()): Long
}

internal object PullEngine : PullRules {

    override fun resolvePull(input: PullInput, random: Random): PullOutput {
        val rolled = rollRarity(input, random)
        val newPullsSinceEpic =
            if (rolled.rarity == Rarity.EPIC) 0 else input.pullsSinceEpic + 1
        val newCommonsSinceRare = when (rolled.rarity) {
            Rarity.COMMON -> input.commonsSinceRare + 1
            Rarity.RARE, Rarity.EPIC -> 0
        }
        return PullOutput(
            rarity = rolled.rarity,
            consumedLuckyTicket = rolled.consumedLucky,
            consumedMegaTicket = rolled.consumedMega,
            consumedBiteReroll = rolled.consumedReroll,
            commonsSinceRare = newCommonsSinceRare,
            pullsSinceEpic = newPullsSinceEpic,
            pityTriggered = rolled.pity,
        )
    }

    private data class RolledPull(
        val rarity: Rarity,
        val consumedLucky: Boolean,
        val consumedMega: Boolean,
        val consumedReroll: Boolean,
        val pity: Boolean,
    )

    private fun rollRarity(input: PullInput, random: Random): RolledPull = when {
        input.hasMegaTicket -> RolledPull(
            rarity = Rarity.EPIC,
            consumedLucky = false,
            consumedMega = true,
            consumedReroll = false,
            pity = false,
        )
        input.pullsSinceEpic >= PULLS_WITHOUT_EPIC_PITY -> RolledPull(
            rarity = Rarity.EPIC,
            consumedLucky = false,
            consumedMega = false,
            consumedReroll = false,
            pity = true,
        )
        input.hasLuckyTicket -> RolledPull(
            rarity = luckyTicketRarity(random),
            consumedLucky = true,
            consumedMega = false,
            consumedReroll = false,
            pity = false,
        )
        input.commonsSinceRare >= COMMONS_WITHOUT_RARE_PITY -> RolledPull(
            rarity = Rarity.RARE,
            consumedLucky = false,
            consumedMega = false,
            consumedReroll = false,
            pity = true,
        )
        input.hasBiteReroll -> {
            val rolled = weightedRarity(random, input.saturdayEvent).promoteOneTier()
            RolledPull(
                rarity = rolled,
                consumedLucky = false,
                consumedMega = false,
                consumedReroll = true,
                pity = false,
            )
        }
        else -> RolledPull(
            rarity = weightedRarity(random, input.saturdayEvent),
            consumedLucky = false,
            consumedMega = false,
            consumedReroll = false,
            pity = false,
        )
    }

    private fun weightedRarity(random: Random, saturdayEvent: Boolean): Rarity {
        val commonWeight = if (saturdayEvent) EVENT_COMMON_WEIGHT else PULL_COMMON_WEIGHT
        val rareWeight = if (saturdayEvent) EVENT_RARE_WEIGHT else PULL_RARE_WEIGHT
        val epicWeight = if (saturdayEvent) EVENT_EPIC_WEIGHT else PULL_EPIC_WEIGHT
        val roll = random.nextInt(commonWeight + rareWeight + epicWeight)
        return when {
            roll < commonWeight -> Rarity.COMMON
            roll < commonWeight + rareWeight -> Rarity.RARE
            else -> Rarity.EPIC
        }
    }

    private fun luckyTicketRarity(random: Random): Rarity {
        val roll = random.nextInt(PULL_RARE_WEIGHT + PULL_EPIC_WEIGHT)
        return if (roll < PULL_RARE_WEIGHT) Rarity.RARE else Rarity.EPIC
    }

    private fun Rarity.promoteOneTier(): Rarity = when (this) {
        Rarity.COMMON -> Rarity.RARE
        Rarity.RARE -> Rarity.EPIC
        Rarity.EPIC -> Rarity.EPIC
    }

    // Pull weights (§4): Common 70 / Rare 22 / Epic 8. Saturday event
    // doubles Epic chance to 16, taken from the Common share.
    private const val PULL_COMMON_WEIGHT = 70
    private const val PULL_RARE_WEIGHT = 22
    private const val PULL_EPIC_WEIGHT = 8
    private const val EVENT_COMMON_WEIGHT = 62
    private const val EVENT_RARE_WEIGHT = 22
    private const val EVENT_EPIC_WEIGHT = 16

    // Pity (§4): 25 Commons without Rare+ -> Rare; 60 pulls without Epic -> Epic.
    private const val COMMONS_WITHOUT_RARE_PITY = 25
    private const val PULLS_WITHOUT_EPIC_PITY = 60
}

internal object EconomyEngine : EconomyRules {

    override fun upgradeCostCoins(baseCoinsPerSec: Int, targetLevel: Int): Long {
        require(targetLevel in LEVEL_TWO..MAX_BUDDY_LEVEL) { "targetLevel must be 2..3" }
        return UPGRADE_COIN_FACTOR * baseCoinsPerSec * (targetLevel - 1)
    }

    override fun upgradeCostBites(targetLevel: Int): Int {
        require(targetLevel in LEVEL_TWO..MAX_BUDDY_LEVEL) { "targetLevel must be 2..3" }
        return UPGRADE_BITE_FACTOR * (targetLevel - 1)
    }

    override fun upgradeMultiplier(level: Int): Double = when (level) {
        1 -> UPGRADE_L1_MULTIPLIER
        LEVEL_TWO -> UPGRADE_L2_MULTIPLIER
        MAX_BUDDY_LEVEL -> UPGRADE_L3_MULTIPLIER
        else -> throw IllegalArgumentException("level must be 1..3")
    }

    override fun buddyCoinsPerSec(baseCoinsPerSec: Int, level: Int): Double =
        baseCoinsPerSec * upgradeMultiplier(level)

    override fun buddyCoinsPerHour(baseCoinsPerSec: Int, level: Int): Double =
        buddyCoinsPerSec(baseCoinsPerSec, level) * SECONDS_PER_HOUR

    override fun slotUnlockCost(unlockNumber: Int): Long {
        require(unlockNumber in 1..MAX_SLOT_UNLOCKS) { "unlockNumber must be 1..20" }
        return SLOT_UNLOCK_COSTS[unlockNumber - 1]
    }

    override fun albumTierReward(rarity: Rarity): Long = when (rarity) {
        Rarity.COMMON -> ALBUM_COMMON_REWARD
        Rarity.RARE -> ALBUM_RARE_REWARD
        Rarity.EPIC -> ALBUM_EPIC_REWARD
    }

    override fun consolationCoins(stolenBuddyCoinsPerSec: Double): Long =
        (stolenBuddyCoinsPerSec * SECONDS_PER_HOUR * CONSOLATION_FRACTION).toLong()

    override fun vaultCapCoins(totalCoinsPerSec: Double): Long =
        (totalCoinsPerSec * VAULT_CAP_HOURS * SECONDS_PER_HOUR).toLong()

    override fun accrueVaultCoins(
        currentCoins: Long,
        totalCoinsPerSec: Double,
        lastTickMs: Long,
        nowMs: Long,
    ): Long {
        val cap = vaultCapCoins(totalCoinsPerSec)
        if (currentCoins >= cap) {
            return currentCoins
        }
        val elapsedMs = (nowMs - lastTickMs).coerceAtLeast(0L)
        val earned = (totalCoinsPerSec * elapsedMs / MILLIS_PER_SECOND).toLong()
        return min(currentCoins + earned, cap)
    }

    override fun offlineEarningsCoins(totalCoinsPerSec: Double, awayMs: Long): Long {
        val cappedAwayMs = awayMs.coerceIn(0L, OFFLINE_CAP_MS)
        return (totalCoinsPerSec * cappedAwayMs / MILLIS_PER_SECOND).toLong()
    }

    // Upgrades (§4): multipliers L1 1x, L2 1.5x, L3 2.2x.
    private const val MAX_BUDDY_LEVEL = 3
    private const val LEVEL_TWO = 2
    private const val UPGRADE_COIN_FACTOR = 100L
    private const val UPGRADE_BITE_FACTOR = 5
    private const val UPGRADE_L1_MULTIPLIER = 1.0
    private const val UPGRADE_L2_MULTIPLIER = 1.5
    private const val UPGRADE_L3_MULTIPLIER = 2.2

    // Slots (§4): 20 unlocks; 50..100000 then 150000 x9.
    private const val MAX_SLOT_UNLOCKS = 20
    private val SLOT_UNLOCK_COSTS: List<Long> = listOf(
        50L, 100L, 250L, 500L, 1000L, 2500L, 5000L, 10000L, 25000L, 50000L, 100000L,
        150000L, 150000L, 150000L, 150000L, 150000L, 150000L, 150000L, 150000L, 150000L,
    )

    // Vault / offline (§4): 4-hour caps.
    private const val VAULT_CAP_HOURS = 4L
    private const val OFFLINE_CAP_MS = 4L * 60L * 60L * 1000L
    private const val MILLIS_PER_SECOND = 1000L
    private const val SECONDS_PER_HOUR = 3600L

    // Misc economy (§4).
    private const val CONSOLATION_FRACTION = 0.5
    private const val ALBUM_COMMON_REWARD = 500L
    private const val ALBUM_RARE_REWARD = 2500L
    private const val ALBUM_EPIC_REWARD = 10000L
}

internal object RaidWindowEngine : RaidWindowRules {

    override fun currentRaidWindow(now: ZonedDateTime): RaidWindow {
        val startHour = RAID_WINDOW_START_HOURS.filter { it <= now.hour }.maxOrNull()
        return if (startHour != null) {
            RaidWindow(now.toLocalDate(), startHour)
        } else {
            RaidWindow(now.toLocalDate().minusDays(1), RAID_WINDOW_START_HOURS.last())
        }
    }

    override fun raidWindowBucket(window: RaidWindow): String =
        window.date.toString() + "-" + window.startHour.toString().padStart(2, '0')

    override fun raidWindowBucket(now: ZonedDateTime): String =
        raidWindowBucket(currentRaidWindow(now))

    override fun millisUntilNextRaidWindow(now: ZonedDateTime): Long {
        val current = currentRaidWindow(now)
        val nextStart = current.date
            .atTime(current.startHour, 0)
            .atZone(now.zone)
            .plusHours(RAID_WINDOW_HOURS)
        return Duration.between(now, nextStart).toMillis().coerceAtLeast(0L)
    }

    // Raid windows (§4): 6-hour cycle at 2/8/14/20 local.
    private val RAID_WINDOW_START_HOURS = listOf(2, 8, 14, 20)
    private const val RAID_WINDOW_HOURS = 6L
}

internal object CalendarEngine : CalendarRules {

    override fun todayKey(zone: ZoneId, now: Instant): String =
        now.atZone(zone).format(DATE_KEY_FORMAT)

    override fun yesterdayKey(zone: ZoneId, now: Instant): String =
        now.atZone(zone).minusDays(1).format(DATE_KEY_FORMAT)

    override fun advanceCalendarDay(
        currentDay: Int,
        lastClaimDate: String,
        streakDays: Int,
        todayKey: String,
        yesterdayKey: String,
    ): CalendarAdvance {
        if (lastClaimDate == todayKey) {
            return CalendarAdvance(currentDay, lastClaimDate, streakDays, claimed = false)
        }
        val isFirstClaim = lastClaimDate.isBlank()
        val consecutive = !isFirstClaim && lastClaimDate == yesterdayKey
        return CalendarAdvance(
            day = if (consecutive) min(currentDay + 1, MAX_CALENDAR_DAY) else currentDay,
            lastClaimDate = todayKey,
            streakDays = if (isFirstClaim || consecutive) streakDays + 1 else streakDays,
            claimed = true,
        )
    }

    override fun dayReward(day: Int): DayReward = when (day) {
        1 -> DayReward(coins = DAY_1_COINS)
        DAY_7 -> DayReward(megaTickets = 1)
        DAY_14 -> DayReward(guaranteedRarity = Rarity.RARE)
        DAY_21 -> DayReward(coins = DAY_21_COINS)
        MAX_CALENDAR_DAY -> DayReward(guaranteedRarity = Rarity.EPIC)
        else -> DayReward()
    }

    // Calendar (§1): 30 days; day 1 = 200 coins, day 21 = 5000 coins.
    private const val MAX_CALENDAR_DAY = 30
    private const val DAY_1_COINS = 200L
    private const val DAY_7 = 7
    private const val DAY_14 = 14
    private const val DAY_21 = 21
    private const val DAY_21_COINS = 5000L
    private val DATE_KEY_FORMAT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
}

internal object RedeemEngine : RedeemRules {

    override fun normalizeRedeemCode(raw: String): String = raw.trim().uppercase()

    override fun validateRedeemCode(rawCode: String, usedCodes: Set<String>): RedeemResult {
        val code = normalizeRedeemCode(rawCode)
        return when {
            code in usedCodes -> RedeemResult.AlreadyUsed
            code == REDEEM_STEALFUN26 -> RedeemResult.Granted(
                coins = STEALFUN26_COINS,
                luckyTickets = 0,
                megaTickets = 0,
                emoteId = PARTY_EMOTE_ID,
            )
            code == REDEEM_BUDDYUP26 -> RedeemResult.Granted(
                coins = 0,
                luckyTickets = 1,
                megaTickets = 0,
                emoteId = null,
            )
            else -> RedeemResult.Invalid
        }
    }

    // Redeem codes (§1).
    private const val REDEEM_STEALFUN26 = "STEALFUN26"
    private const val REDEEM_BUDDYUP26 = "BUDDYUP26"
    private const val STEALFUN26_COINS = 500L
    private const val PARTY_EMOTE_ID = "party"
}

internal object EventEngine : EventRules {

    override fun isZoomRaceActive(now: ZonedDateTime): Boolean =
        now.dayOfWeek == DayOfWeek.SATURDAY &&
            now.hour == ZOOM_RACE_HOUR &&
            now.minute < ZOOM_RACE_MINUTES

    override fun millisUntilZoomRace(now: ZonedDateTime): Long {
        val daysAhead = (DayOfWeek.SATURDAY.value - now.dayOfWeek.value + DAYS_PER_WEEK) % DAYS_PER_WEEK
        val thisSaturday = now.toLocalDate()
            .plusDays(daysAhead.toLong())
            .atTime(ZOOM_RACE_HOUR, 0)
            .atZone(now.zone)
        val next = if (thisSaturday.isAfter(now)) thisSaturday else thisSaturday.plusWeeks(1)
        return Duration.between(now, next).toMillis()
    }

    // Saturday event (§2 V1.3): 11:00-11:20 local.
    private const val ZOOM_RACE_HOUR = 11
    private const val ZOOM_RACE_MINUTES = 20
    private const val DAYS_PER_WEEK = 7
}
