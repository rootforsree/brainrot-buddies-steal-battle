package com.sreecharan.brainrot_buddies_steal_battle.ui.home

import com.sreecharan.brainrot_buddies_steal_battle.data.model.TicketType
import kotlinx.coroutines.flow.Flow

/**
 * Events flowing UP from the stateless [HomeScreen] content into the route's
 * ViewModel (the skill's `onEvent` pattern). Every tap on the base board
 * answers in under 100 ms: the ViewModel acknowledges locally first and
 * persists after.
 */
sealed interface HomeUiEvent {
    /** A conveyor buddy was tapped. `instanceId` is the ride, not the species. */
    data class ConveyorTap(val instanceId: String) : HomeUiEvent

    /** A grid slot was tapped. */
    data class SlotTap(val index: Int) : HomeUiEvent

    /** Vault collect button tapped. */
    data object VaultCollect : HomeUiEvent

    /** Raid bell widget tapped. */
    data object RaidBellTap : HomeUiEvent

    /** Lucky/Mega ticket chip tapped: starts the mystery-pull ceremony. */
    data class TicketTap(val type: TicketType) : HomeUiEvent

    /** Mystery-pull ceremony finished its drumroll; reveal the pull now. */
    data class CeremonyReveal(val type: TicketType) : HomeUiEvent

    /** A celebration overlay was dismissed early (contract §10: skippable). */
    data object DismissCelebration : HomeUiEvent

    /** A staged replay was dismissed early (contract §2: freely skippable). */
    data object DismissReplay : HomeUiEvent

    /** A staged replay finished its 5 s run. */
    data object ReplayFinished : HomeUiEvent

    /** Tour mode FAB tapped: toggle the base tour. */
    data object TourToggle : HomeUiEvent

    /** A tour caption's catchphrase finished (or was skipped). */
    data object TourCaptionDone : HomeUiEvent

    /** First real tap anywhere: unlocks audio (contract §9). */
    data object FirstTap : HomeUiEvent
}

/**
 * One-shot events flowing DOWN to the route for side effects the UiState
 * cannot carry: haptics and navigation. Audio goes through SoundManager
 * directly from the ViewModel (it is not a UI event).
 */
sealed interface HomeEvent {
    /** Contract §11: every tap answers with haptic feedback. */
    data class Haptic(val kind: HapticKind) : HomeEvent

    /** A big moment (Epic claim, steal success) for a full-pattern buzz. */
    data object BigMoment : HomeEvent

    /** Raid bell tapped while a window is open: go find someone to raid. */
    data object NavigateToFriends : HomeEvent

    /** Ticket chip tapped: the shop sells the tickets. */
    data object NavigateToShop : HomeEvent
}

enum class HapticKind { TAP, SUCCESS, CELEBRATION, WARNING }

/**
 * The seam between the base board and the data layer. The conveyor spawner
 * lives in the ViewModel (presentation state: rides are never persisted),
 * so this interface only covers persisted state and writes. The production
 * implementation ([GameRepositoryAdapter]) maps Builder A's `GameRepository`
 * and `GameEngine` onto it; tests fake this interface instead.
 *
 * Kept as an explicit seam because the repository's shape was still
 * settling when the base board was built: only the adapter tracks it, the
 * ViewModel and its tests never touch the repository directly.
 */
interface BaseGameData {

    /** The full persisted base, re-emitted on every change. */
    val snapshot: Flow<BaseSnapshot>

    /**
     * Places a freshly claimed buddy into the first free unlocked slot,
     * creating its owned-buddy row. Returns [ClaimPlacement.BaseFull] when
     * no unlocked slot is free (the claim converts to Buddy Bites).
     */
    suspend fun placeClaimedBuddy(buddyId: String): ClaimPlacement

    /** Banks all vault coins now. Returns the collected amount. */
    suspend fun collectVault(): Long

    /** Ticks vault accrual (income since the last tick, capped). */
    suspend fun accrueVault()

    /** Buys and unlocks a slot, deducting coins. */
    suspend fun buySlot(index: Int): BuySlotOutcome

    /** Consumes one ticket. Returns the remaining count. */
    suspend fun consumeTicket(type: TicketType): Int

    /** Marks a staged replay as shown so it never auto-plays again. */
    suspend fun acknowledgeReplay(id: String)
}

/** A slice of persisted base state for one [BaseSnapshot]. */
data class BaseSnapshot(
    val baseName: String,
    val slots: List<SlotUi>,
    val vaultCoins: Long,
    val vaultCap: Long,
    val shieldActive: Boolean,
    val shieldEndsAtMs: Long,
    val raidWindowActive: Boolean,
    val raidsLeft: Int,
    val nextRaidWindowMs: Long,
    val luckyTickets: Int,
    val megaTickets: Int,
    val coachMark: CoachMarkUi?,
    val pendingReplay: StagedReplayUi?,
)

sealed interface ClaimPlacement {
    data class Placed(val slotIndex: Int, val buddy: SlottedBuddyUi) : ClaimPlacement
    data object BaseFull : ClaimPlacement
}

sealed interface BuySlotOutcome {
    data object Success : BuySlotOutcome
    data object NotEnoughCoins : BuySlotOutcome
    data object AlreadyUnlocked : BuySlotOutcome
}
