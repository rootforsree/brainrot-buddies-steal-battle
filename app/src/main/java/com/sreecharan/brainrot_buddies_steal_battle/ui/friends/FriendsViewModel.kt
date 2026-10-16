package com.sreecharan.brainrot_buddies_steal_battle.ui.friends

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sreecharan.brainrot_buddies_steal_battle.R
import com.sreecharan.brainrot_buddies_steal_battle.data.local.FriendEntity
import com.sreecharan.brainrot_buddies_steal_battle.data.model.BuddyCatalog
import com.sreecharan.brainrot_buddies_steal_battle.data.model.BuddyId
import com.sreecharan.brainrot_buddies_steal_battle.data.repository.GameRepository
import com.sreecharan.brainrot_buddies_steal_battle.di.IoDispatcher
import com.sreecharan.brainrot_buddies_steal_battle.game.engine.GameEngine
import com.sreecharan.brainrot_buddies_steal_battle.p2p.BaseSnapshot
import com.sreecharan.brainrot_buddies_steal_battle.p2p.ConnectionEvent
import com.sreecharan.brainrot_buddies_steal_battle.p2p.DiscoveredPeer
import com.sreecharan.brainrot_buddies_steal_battle.p2p.FriendCode
import com.sreecharan.brainrot_buddies_steal_battle.p2p.Hello
import com.sreecharan.brainrot_buddies_steal_battle.p2p.HostMode
import com.sreecharan.brainrot_buddies_steal_battle.p2p.NearbyManager
import com.sreecharan.brainrot_buddies_steal_battle.p2p.P2pPrefs
import com.sreecharan.brainrot_buddies_steal_battle.p2p.PairingPhase
import com.sreecharan.brainrot_buddies_steal_battle.p2p.SnapshotCodec
import com.sreecharan.brainrot_buddies_steal_battle.p2p.exportSnapshot
import com.sreecharan.brainrot_buddies_steal_battle.p2p.firstData
import com.sreecharan.brainrot_buddies_steal_battle.p2p.getOrNull
import com.sreecharan.brainrot_buddies_steal_battle.p2p.grantCoins
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Snapshot status shown per friend, per contract: "raided" / "fresh". */
enum class SnapshotStatus { FRESH, RAIDED, NONE }

@Immutable
data class FriendItem(
    val guestId: String,
    val baseName: String,
    val snapshotStatus: SnapshotStatus,
    val lastSeenAt: Long,
)

@Immutable
data class IncomingRaid(
    val raidId: String,
    val attackerGuestId: String,
    val attackerBaseName: String,
    val buddyName: String,
    val isShadow: Boolean,
    val revengeDeadlineMs: Long,
    val revengeUsed: Boolean,
    val createdAt: Long,
)

@Immutable
data class FriendsUiState(
    val p2pAvailable: Boolean = true,
    val bluetoothGranted: Boolean = false,
    val myCode: String = "",
    val codeRevealed: Boolean = false,
    val pairingPhase: PairingPhase = PairingPhase.Idle,
    val peers: ImmutableList<DiscoveredPeer> = persistentListOf(),
    val friends: ImmutableList<FriendItem> = persistentListOf(),
    val incomingRaids: ImmutableList<IncomingRaid> = persistentListOf(),
)

sealed interface FriendsEvent {
    data class NavigateToRaidVisit(val friendGuestId: String) : FriendsEvent
    data class ShowMessage(@StringRes val resId: Int, val arg: String = "") : FriendsEvent
    data class ShowCeremony(val friendBaseName: String) : FriendsEvent
}

/**
 * Friends screen ViewModel: pairing ceremony (§8), friend list, snapshot
 * status, incoming-raid villain cards, and practice-visit entry.
 *
 * The 6-letter code is shown only behind Builder C's ParentGateDialog (the
 * Route gates [PairingIntents.onRevealCode]). Bluetooth permissions are
 * requested in-context by the Route; hosting starts only after the grant.
 *
 * The intent logic lives in the handler classes in FriendsHandlers.kt;
 * this class only wires them together behind the [pairing] and [roster]
 * namespaces.
 */
@HiltViewModel
class FriendsViewModel @Inject constructor(
    nearbyManager: NearbyManager,
    gameRepository: GameRepository,
    gameEngine: GameEngine,
    p2pPrefs: P2pPrefs,
    @IoDispatcher ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val core = FriendsVmCore(
        nearbyManager = nearbyManager,
        gameRepository = gameRepository,
        gameEngine = gameEngine,
        p2pPrefs = p2pPrefs,
        ioDispatcher = ioDispatcher,
        scope = viewModelScope,
    )

    /** Pairing ceremony intents (§8). */
    val pairing = PairingIntents(core)

    /** Friend-list and raid-inbox intents. */
    val roster = RosterIntents(core)

    private val connection = FriendsConnection(core)
    private val directory = FriendsDirectory(core)

    val uiState: StateFlow<FriendsUiState> = core.uiState
    val events: Flow<FriendsEvent> = core.events

    init {
        core.uiState.update { it.copy(p2pAvailable = nearbyManager.p2pAvailable) }
        viewModelScope.launch(ioDispatcher) { core.loadIdentity() }
        viewModelScope.launch(ioDispatcher) { directory.collectDirectory() }
        viewModelScope.launch(ioDispatcher) { connection.collectConnectionEvents() }
        viewModelScope.launch(ioDispatcher) { connection.collectInbound() }
    }

    override fun onCleared() {
        nearbyManager.stopHosting()
    }

    companion object {
        /** Pairing ritual gift, each side grants its own. */
        const val PAIRING_GIFT_COINS = 200L

        /** First-heist ceremony gift. */
        const val FIRST_HEIST_GIFT_COINS = 200L
    }
}
