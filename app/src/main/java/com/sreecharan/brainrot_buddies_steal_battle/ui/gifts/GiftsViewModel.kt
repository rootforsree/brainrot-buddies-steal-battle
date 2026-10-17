package com.sreecharan.brainrot_buddies_steal_battle.ui.gifts

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sreecharan.brainrot_buddies_steal_battle.data.datastore.GamePrefs
import com.sreecharan.brainrot_buddies_steal_battle.data.local.CalendarEntity
import com.sreecharan.brainrot_buddies_steal_battle.data.local.OwnedBuddyEntity
import com.sreecharan.brainrot_buddies_steal_battle.data.model.BuddyCatalog
import com.sreecharan.brainrot_buddies_steal_battle.data.model.Rarity
import com.sreecharan.brainrot_buddies_steal_battle.data.model.Result
import com.sreecharan.brainrot_buddies_steal_battle.data.model.TicketType
import com.sreecharan.brainrot_buddies_steal_battle.data.repository.GameRepository
import com.sreecharan.brainrot_buddies_steal_battle.game.engine.DayReward
import com.sreecharan.brainrot_buddies_steal_battle.game.engine.GameEngine
import com.sreecharan.brainrot_buddies_steal_battle.game.engine.RedeemResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.UUID
import javax.inject.Inject
import kotlin.random.Random

const val CALENDAR_DAYS = 30
const val CATCH_UP_COST = 500L

/** Contract §1: the daily gift grants 1 Lucky Ticket every day. */
const val DAILY_LUCKY_TICKETS = 1

/**
 * Daily gift calendar (30 days, pause-never-reset), catch-up day, weekly
 * Flippy gift, and redeem codes. The calendar UI is a full-screen dialog
 * (no new §7 route): Builder B shows GiftCalendarRoute from the Base
 * screen's gift widget. Redeem lives in Settings via RedeemCodeSection.
 *
 * The weekly-gift date lives in [GamePrefs]. The STEALFUN26 party emote
 * also has no home in §5; the coins and ticket are granted and the emote
 * is flagged for the coordinator.
 */
@Immutable
data class GiftsUiState(
    val isLoading: Boolean = true,
    val currentDay: Int = 1,
    /**
     * The day whose reward a claim right now would grant: currentDay + 1
     * when yesterday was claimed (consecutive), else currentDay. The
     * calendar grid and "today's gift" card render from this.
     */
    val upcomingDay: Int = 1,
    val canClaimToday: Boolean = false,
    val streakDays: Int = 0,
    val catchUpAvailable: Boolean = false,
    val catchUpCost: Long = CATCH_UP_COST,
    val weeklyGiftAvailable: Boolean = false,
    val weeklyGiftBuddyName: String? = null,
)

@Immutable
data class RedeemUiState(
    val isWorking: Boolean = false,
    /** One-shot result; consumed by the UI. Null = no result to show. */
    val result: RedeemResultUi? = null,
)

sealed interface RedeemResultUi {
    data class Success(
        val coins: Long,
        val luckyTickets: Int,
        val megaTickets: Int,
        val emoteId: String?,
    ) : RedeemResultUi

    data object Invalid : RedeemResultUi

    data object AlreadyUsed : RedeemResultUi

    data object Error : RedeemResultUi
}

sealed interface GiftsEvent {
    data class GiftClaimed(
        val day: Int,
    ) : GiftsEvent

    data object CatchUpDone : GiftsEvent

    data class WeeklyClaimed(
        val buddyName: String,
    ) : GiftsEvent

    data object ActionFailed : GiftsEvent
}

@HiltViewModel
class GiftsViewModel
    @Inject
    constructor(
        private val repository: GameRepository,
        private val gamePrefs: GamePrefs,
        private val engine: GameEngine,
    ) : ViewModel() {
        private val eventsChannel = Channel<GiftsEvent>(Channel.BUFFERED)
        val events = eventsChannel.receiveAsFlow()

        private val redeemState = MutableStateFlow(RedeemUiState())
        val redeemUiState: StateFlow<RedeemUiState> = redeemState

        val uiState: StateFlow<GiftsUiState> =
            combine(
                repository.observeCalendar(),
                gamePrefs.lastWeeklyGiftDate,
            ) { calendarResult, lastWeekly ->
                val calendar = calendarResult.dataOrNull()
                val todayKey = engine.todayKey()
                val currentDay = (calendar?.currentDay ?: 1).coerceIn(1, CALENDAR_DAYS)
                GiftsUiState(
                    isLoading = false,
                    currentDay = currentDay,
                    upcomingDay = upcomingDay(calendar, currentDay),
                    canClaimToday = calendar?.lastClaimDate != todayKey,
                    streakDays = calendar?.streakDays ?: 0,
                    catchUpAvailable = isCatchUpAvailable(calendar?.lastClaimDate.orEmpty()),
                    weeklyGiftAvailable = isWeeklyAvailable(lastWeekly),
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = GiftsUiState(),
            )

        fun onClaimToday() {
            viewModelScope.launch {
                val calendar =
                    repository.observeCalendar().awaitData()
                        ?: return@launch eventsChannel.send(GiftsEvent.ActionFailed)
                val advance =
                    engine.advanceCalendarDay(
                        currentDay = calendar.currentDay,
                        lastClaimDate = calendar.lastClaimDate,
                        streakDays = calendar.streakDays,
                        todayKey = engine.todayKey(),
                        yesterdayKey = engine.yesterdayKey(),
                    )
                if (!advance.claimed) {
                    eventsChannel.send(GiftsEvent.ActionFailed)
                    return@launch
                }
                repository.upsertCalendar(
                    CalendarEntity(
                        currentDay = advance.day,
                        lastClaimDate = advance.lastClaimDate,
                        streakDays = advance.streakDays,
                    ),
                )
                grantDayReward(engine.dayReward(advance.day))
                eventsChannel.send(GiftsEvent.GiftClaimed(advance.day))
            }
        }

        /**
         * Catch-up buys back the missed day for 500 coins: the missed day's
         * reward is granted, the day advances, and the streak continues as if
         * the day had not been missed. Today's claim then proceeds consecutively.
         */
        fun onCatchUpConfirmed() {
            viewModelScope.launch {
                val vault =
                    repository.observeVault().awaitData()
                        ?: return@launch eventsChannel.send(GiftsEvent.ActionFailed)
                if (vault.coins < CATCH_UP_COST) {
                    eventsChannel.send(GiftsEvent.ActionFailed)
                    return@launch
                }
                val calendar =
                    repository.observeCalendar().awaitData()
                        ?: return@launch eventsChannel.send(GiftsEvent.ActionFailed)
                if (!isCatchUpAvailable(calendar.lastClaimDate)) {
                    eventsChannel.send(GiftsEvent.ActionFailed)
                    return@launch
                }
                val missedDay = minOf(calendar.currentDay + 1, CALENDAR_DAYS)
                repository.upsertVault(vault.copy(coins = vault.coins - CATCH_UP_COST))
                repository.upsertCalendar(
                    calendar.copy(
                        currentDay = missedDay,
                        lastClaimDate = engine.yesterdayKey(),
                        streakDays = calendar.streakDays + 1,
                    ),
                )
                grantDayReward(engine.dayReward(missedDay))
                eventsChannel.send(GiftsEvent.CatchUpDone)
            }
        }

        /** Weekly surprise: Flippy face-plants a buddy of up to Epic rarity. */
        fun onClaimWeekly() {
            viewModelScope.launch {
                val lastWeekly = gamePrefs.lastWeeklyGiftDate.first()
                if (!isWeeklyAvailable(lastWeekly)) {
                    eventsChannel.send(GiftsEvent.ActionFailed)
                    return@launch
                }
                val def = rollWeeklyBuddy() ?: return@launch eventsChannel.send(GiftsEvent.ActionFailed)
                if (!grantBuddy(def.id.value)) {
                    eventsChannel.send(GiftsEvent.ActionFailed)
                    return@launch
                }
                gamePrefs.setLastWeeklyGiftDate(engine.todayKey())
                eventsChannel.send(GiftsEvent.WeeklyClaimed(def.name))
            }
        }

        fun onRedeemCode(code: String) {
            if (code.isBlank() || redeemState.value.isWorking) return
            viewModelScope.launch {
                redeemState.update { it.copy(isWorking = true, result = null) }
                val usedCodes = repository.observeUsedCodes().awaitData().orEmpty()
                val result =
                    when (val outcome = engine.validateRedeemCode(code, usedCodes)) {
                        is RedeemResult.Granted -> {
                            grantRedeemReward(outcome)
                            repository.addUsedCode(code)
                            // outcome.emoteId (party emote) has no §5 home; flagged for coordinator.
                            RedeemResultUi.Success(
                                coins = outcome.coins,
                                luckyTickets = outcome.luckyTickets,
                                megaTickets = outcome.megaTickets,
                                emoteId = outcome.emoteId,
                            )
                        }

                        RedeemResult.AlreadyUsed -> {
                            RedeemResultUi.AlreadyUsed
                        }

                        RedeemResult.Invalid -> {
                            RedeemResultUi.Invalid
                        }
                    }
                redeemState.update { it.copy(isWorking = false, result = result) }
            }
        }

        fun onRedeemResultConsumed() {
            redeemState.update { it.copy(result = null) }
        }

        private suspend fun grantDayReward(reward: DayReward) {
            // Contract §1: the daily gift grants 1 Lucky Ticket every day.
            // (The engine's dayReward omits this; flagged for the coordinator.)
            repository.addTickets(TicketType.LUCKY, DAILY_LUCKY_TICKETS)
            if (reward.coins > 0) {
                repository.observeVault().awaitData()?.let { vault ->
                    repository.upsertVault(
                        vault.copy(
                            coins = vault.coins + reward.coins,
                            lifetimeCoins = vault.lifetimeCoins + reward.coins,
                        ),
                    )
                }
            }
            if (reward.luckyTickets > 0) repository.addTickets(TicketType.LUCKY, reward.luckyTickets)
            if (reward.megaTickets > 0) repository.addTickets(TicketType.MEGA, reward.megaTickets)
            reward.guaranteedRarity?.let { rarity ->
                BuddyCatalog.all
                    .filter { it.rarity == rarity }
                    .randomOrNull()
                    ?.let { grantBuddy(it.id.value) }
            }
        }

        private suspend fun grantRedeemReward(outcome: RedeemResult.Granted) {
            if (outcome.coins > 0) {
                repository.observeVault().awaitData()?.let { vault ->
                    repository.upsertVault(
                        vault.copy(
                            coins = vault.coins + outcome.coins,
                            lifetimeCoins = vault.lifetimeCoins + outcome.coins,
                        ),
                    )
                }
            }
            if (outcome.luckyTickets > 0) repository.addTickets(TicketType.LUCKY, outcome.luckyTickets)
            if (outcome.megaTickets > 0) repository.addTickets(TicketType.MEGA, outcome.megaTickets)
        }

        /**
         * Adds a buddy to the collection, placing it in the first empty unlocked
         * slot when one exists. Returns false when the buddy id is unknown.
         */
        private suspend fun grantBuddy(buddyId: String): Boolean {
            if (BuddyCatalog.all.none { it.id.value == buddyId }) return false
            val now = System.currentTimeMillis()
            val uid = UUID.randomUUID().toString()
            repository.upsertOwnedBuddy(
                OwnedBuddyEntity(uid = uid, buddyId = buddyId, level = 1, acquiredAt = now),
            )
            val slots = repository.observeSlots().awaitData().orEmpty()
            val emptySlot = slots.firstOrNull { it.unlocked && it.ownedBuddyUid == null }
            if (emptySlot != null) {
                repository.setSlotBuddy(emptySlot.slotIndex, uid)
            }
            return true
        }

        private fun rollWeeklyBuddy() =
            when (Random.nextInt(WEEKLY_ROLL_MAX)) {
                in 0 until WEEKLY_COMMON_MAX -> {
                    BuddyCatalog.all.filter { it.rarity == Rarity.COMMON }.randomOrNull()
                }

                in WEEKLY_COMMON_MAX until WEEKLY_RARE_MAX -> {
                    BuddyCatalog.all.filter { it.rarity == Rarity.RARE }.randomOrNull()
                }

                else -> {
                    BuddyCatalog.all.filter { it.rarity == Rarity.EPIC }.randomOrNull()
                }
            }

        companion object {
            /** Weekly Flippy roll: common/rare/epic weights per 100 rolls. */
            const val WEEKLY_ROLL_MAX = 100
            const val WEEKLY_COMMON_MAX = 70
            const val WEEKLY_RARE_MAX = 95

            /**
             * The day a claim right now would reward: currentDay + 1 when
             * yesterday was claimed (consecutive, mirroring
             * [GameEngine.advanceCalendarDay]), else currentDay.
             */
            fun upcomingDay(
                calendar: CalendarEntity?,
                currentDay: Int,
            ): Int {
                val lastClaim = calendar?.lastClaimDate.orEmpty()
                if (lastClaim.isBlank()) return currentDay
                return try {
                    if (LocalDate.parse(lastClaim) == LocalDate.now().minusDays(1)) {
                        minOf(currentDay + 1, CALENDAR_DAYS)
                    } else {
                        currentDay
                    }
                } catch (_: Exception) {
                    currentDay
                }
            }

            /**
             * A missed day pauses the streak (never resets). Catch-up is offered
             * when the last claim is older than yesterday.
             */
            fun isCatchUpAvailable(lastClaimDate: String): Boolean {
                if (lastClaimDate.isBlank()) return false
                return try {
                    val last = LocalDate.parse(lastClaimDate)
                    ChronoUnit.DAYS.between(last, LocalDate.now()) > 1
                } catch (_: Exception) {
                    false
                }
            }

            /** The weekly gift refreshes every Monday. */
            fun isWeeklyAvailable(lastWeeklyDate: String): Boolean {
                if (lastWeeklyDate.isBlank()) return true
                return try {
                    val last = LocalDate.parse(lastWeeklyDate)
                    val monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    last.isBefore(monday)
                } catch (_: Exception) {
                    true
                }
            }
        }
    }

private fun <T> Result<T>.dataOrNull(): T? = (this as? Result.Success<T>)?.data

private suspend fun <T> Flow<Result<T>>.awaitData(): T? =
    when (val result = first { it !is Result.Loading }) {
        is Result.Success -> result.data
        else -> null
    }
