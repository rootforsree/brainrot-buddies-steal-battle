package com.sreecharan.brainrot_buddies_steal_battle.ui.raids

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sreecharan.brainrot_buddies_steal_battle.data.repository.GameRepository
import com.sreecharan.brainrot_buddies_steal_battle.di.IoDispatcher
import com.sreecharan.brainrot_buddies_steal_battle.game.engine.GameEngine
import com.sreecharan.brainrot_buddies_steal_battle.p2p.NearbyManager
import com.sreecharan.brainrot_buddies_steal_battle.p2p.P2pPrefs
import com.sreecharan.brainrot_buddies_steal_battle.p2p.RaidRules
import com.sreecharan.brainrot_buddies_steal_battle.p2p.StealRejectReason
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class VisitConnection { SEARCHING, LIVE, SNAPSHOT, FAILED }

enum class StealOutcome { IDLE, WORKING, SUCCESS, FAILED }

@Immutable
data class RaidSlotUi(
    val slot: Int,
    val buddyId: String,
    val buddyName: String,
    val level: Int,
    val stealable: Boolean,
    val blockedBy: StealRejectReason?,
)

@Immutable
data class ChatUi(
    val id: Long = 0L,
    val mine: Boolean,
    val kind: String,
    val refId: String,
    val text: String,
)

@Immutable
data class RaidVisitUiState(
    val friendGuestId: String = "",
    val friendBaseName: String = "",
    val connection: VisitConnection = VisitConnection.SEARCHING,
    val raidsLeft: Int = RaidRules.MAX_RAIDS_PER_WINDOW,
    val alreadyRaidedFriend: Boolean = false,
    val pendingRevenge: Boolean = false,
    val revengeArmed: Boolean = false,
    val slots: ImmutableList<RaidSlotUi> = persistentListOf(),
    val selectedSlot: Int? = null,
    val stealState: StealOutcome = StealOutcome.IDLE,
    @StringRes val stealMessageRes: Int? = null,
    val stealBuddyName: String = "",
    val chat: ImmutableList<ChatUi> = persistentListOf(),
)

sealed interface RaidVisitEvent {
    data class PlayStealReplay(val buddyName: String, val attackerName: String) : RaidVisitEvent
    data class PlayRevengeReplay(val buddyName: String, val attackerName: String) : RaidVisitEvent
    data object PlayStealFail : RaidVisitEvent
    data class ShowMessage(@StringRes val resId: Int) : RaidVisitEvent
    data class ShowCeremony(val friendBaseName: String) : RaidVisitEvent
    data object NavigateBack : RaidVisitEvent
}

/**
 * Raid visit flow, per docs/contract.md §1 and §8.
 *
 * One screen covers practice visits (free, no stealing) and raid attempts:
 * during a raid window with slots left, the steal section is enabled.
 * Live raids go over Nearby (RaidRequest/RaidResult); otherwise the saved
 * base snapshot is used for a Shadow Heist (one per snapshot).
 *
 * Anti-grief enforcement: 3 raids/window, 1 per friend, 30-min steal
 * cooldowns, shadow-heist-once-per-snapshot. The rookie shield is
 * victim-side (enforced by LiveRaidResponder) and needs no attacker-side
 * check. Failed raids are refunded, never consumed.
 *
 * The staged 5s auto-replays are Builder B's juice: this VM emits
 * [RaidVisitEvent.PlayStealReplay] / [PlayRevengeReplay] / [PlayStealFail],
 * which the Route forwards to Builder B's player.
 *
 * The flow logic lives in the handler classes in RaidVisitHandlers.kt;
 * this class only wires the core, setup, steal flow, connection, and chat
 * handlers together behind the five public intents.
 */
@HiltViewModel
class RaidVisitViewModel @Inject constructor(
    nearbyManager: NearbyManager,
    gameRepository: GameRepository,
    gameEngine: GameEngine,
    p2pPrefs: P2pPrefs,
    @IoDispatcher ioDispatcher: CoroutineDispatcher,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val core = RaidVisitCore(
        deps = RaidVisitDepsImpl(
            nearbyManager = nearbyManager,
            gameRepository = gameRepository,
            gameEngine = gameEngine,
            p2pPrefs = p2pPrefs,
            ioDispatcher = ioDispatcher,
        ),
        scope = viewModelScope,
        friendGuestId = savedStateHandle.get<String>("friendGuestId").orEmpty(),
    )
    private val setup = RaidVisitSetup(core)
    private val stealFlow = RaidStealFlow(core, setup)
    private val chat = RaidVisitChat(core)

    val uiState: StateFlow<RaidVisitUiState> = core.uiState
    val events: Flow<RaidVisitEvent> = core.events

    init {
        viewModelScope.launch(ioDispatcher) { setup.enterVisit() }
    }

    // ---- intents ----

    fun onSlotSelected(slot: Int) {
        core.updateState { it.copy(selectedSlot = slot, stealState = StealOutcome.IDLE, stealMessageRes = null) }
    }

    fun onRevengeClicked() {
        core.updateState { it.copy(revengeArmed = true) }
    }

    fun onStealClicked() {
        val slot = uiState.value.selectedSlot ?: return
        if (uiState.value.stealState == StealOutcome.WORKING) return
        viewModelScope.launch(core.ioDispatcher) { stealFlow.attemptSteal(slot) }
    }

    fun onPhraseSelected(index: Int) {
        chat.onPhraseSelected(index)
    }

    fun onEmoteSelected(emoteId: String) {
        chat.onEmoteSelected(emoteId)
    }
}
