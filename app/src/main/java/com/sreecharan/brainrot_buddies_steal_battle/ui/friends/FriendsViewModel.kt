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
 * Route gates [onRevealCode]). Bluetooth permissions are requested
 * in-context by the Route; hosting starts only after the grant.
 */
@HiltViewModel
class FriendsViewModel @Inject constructor(
    private val nearbyManager: NearbyManager,
    private val gameRepository: GameRepository,
    private val gameEngine: GameEngine,
    private val p2pPrefs: P2pPrefs,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    val uiState: StateFlow<FriendsUiState> field = MutableStateFlow(FriendsUiState())

    private val _events = Channel<FriendsEvent>(Channel.BUFFERED)
    val events: Flow<FriendsEvent> = _events.receiveAsFlow()

    private var myGuestId: String = ""
    private var myBaseName: String = ""
    private var myHello: Hello? = null
    private var pairingEndpoint: String? = null
    private var pairingHello: Hello? = null
    private var ceremonyArmed = true
    private val identityReady = CompletableDeferred<Unit>()

    init {
        field.update { it.copy(p2pAvailable = nearbyManager.p2pAvailable) }
        viewModelScope.launch(ioDispatcher) { loadIdentity() }
        viewModelScope.launch(ioDispatcher) { collectDirectory() }
        viewModelScope.launch(ioDispatcher) { collectConnectionEvents() }
        viewModelScope.launch(ioDispatcher) { collectInbound() }
    }

    override fun onCleared() {
        nearbyManager.stopHosting()
    }

    // ---- intents ----

    /** Called by the Route after the in-context Bluetooth permission grant. */
    fun onBluetoothGranted() {
        field.update { it.copy(bluetoothGranted = true) }
        viewModelScope.launch(ioDispatcher) {
            identityReady.await()
            startHosting()
        }
    }

    fun onFindNearby() {
        if (!uiState.value.bluetoothGranted) return
        viewModelScope.launch(ioDispatcher) {
            identityReady.await()
            startHosting()
            field.update { it.copy(pairingPhase = PairingPhase.Discovering, peers = persistentListOf()) }
        }
    }

    fun onStopFinding() {
        field.update { it.copy(pairingPhase = PairingPhase.Idle, peers = persistentListOf()) }
    }

    fun onPeerSelected(peer: DiscoveredPeer) {
        pairingEndpoint = peer.endpointId
        pairingHello = null
        nearbyManager.requestPairing(peer)
        field.update { it.copy(pairingPhase = PairingPhase.AwaitingToken(peer.baseName, weInitiated = true)) }
    }

    /**
     * The acceptor's "Match!" tap: the Nearby auth token matched on both
     * screens, so accept the connection. Hellos are exchanged automatically
     * on connect, then the code-verify step runs.
     */
    fun onMatchConfirmed() {
        val endpoint = pairingEndpoint ?: return
        nearbyManager.acceptConnection(endpoint)
    }

    fun onPairingCancelled() {
        pairingEndpoint?.let { nearbyManager.disconnect(it) }
        pairingEndpoint = null
        pairingHello = null
        field.update { it.copy(pairingPhase = PairingPhase.Idle, peers = persistentListOf()) }
    }

    /** "Yes, that's them!": the spoken code matches. Save the friend. */
    fun onCodeConfirmed() {
        val hello = pairingHello ?: return
        val endpoint = pairingEndpoint
        viewModelScope.launch(ioDispatcher) {
            val now = System.currentTimeMillis()
            gameRepository.upsertFriend(
                FriendEntity(
                    guestId = hello.guestId,
                    baseName = hello.baseName,
                    code = hello.code,
                    pairedAt = now,
                    lastSeenAt = now,
                    snapshotJson = null,
                    snapshotAt = 0L,
                    snapshotRaided = false,
                ),
            )
            // Pairing ritual gift: both sides grant their own (contract §1).
            gameRepository.grantCoins(PAIRING_GIFT_COINS)
            sendMySnapshot(endpoint)
            pairingEndpoint = null
            pairingHello = null
            field.update { it.copy(pairingPhase = PairingPhase.Paired(hello.baseName)) }
            _events.send(FriendsEvent.ShowMessage(R.string.friend_paired))
        }
    }

    fun onCodeRejected() {
        onPairingCancelled()
    }

    /** The Route calls this only after Builder C's ParentGateDialog passes. */
    fun onRevealCode() {
        field.update { it.copy(codeRevealed = true) }
    }

    fun onHideCode() {
        field.update { it.copy(codeRevealed = false) }
    }

    fun onUnpair(friend: FriendItem) {
        viewModelScope.launch(ioDispatcher) {
            gameRepository.removeFriend(friend.guestId)
        }
    }

    fun onVisitFriend(friend: FriendItem) {
        viewModelScope.launch { _events.send(FriendsEvent.NavigateToRaidVisit(friend.guestId)) }
    }

    fun onRevenge(raid: IncomingRaid) {
        viewModelScope.launch { _events.send(FriendsEvent.NavigateToRaidVisit(raid.attackerGuestId)) }
    }

    fun onDismissRaid(raid: IncomingRaid) {
        viewModelScope.launch(ioDispatcher) { p2pPrefs.markRaidViewed(raid.raidId) }
    }

    // ---- private ----

    private suspend fun loadIdentity() {
        val guestId = gameRepository.ensureGuestId().getOrNull().orEmpty()
        myGuestId = guestId
        myBaseName = gameRepository.observeBaseName().firstData().orEmpty()
        val code = FriendCode.deriveFrom(guestId)
        myHello = Hello(guestId = guestId, baseName = myBaseName, code = code)
        field.update { it.copy(myCode = code) }
        identityReady.complete(Unit)
    }

    private suspend fun collectDirectory() {
        combine(
            gameRepository.observeFriends(),
            gameRepository.observeRaidRecords(),
            p2pPrefs.viewedRaidIds,
        ) { friendsResult, recordsResult, viewed ->
            val friends = friendsResult.getOrNull().orEmpty()
            val records = recordsResult.getOrNull().orEmpty()
            val items = friends.map { friend ->
                FriendItem(
                    guestId = friend.guestId,
                    baseName = friend.baseName,
                    snapshotStatus = when {
                        friend.snapshotJson == null -> SnapshotStatus.NONE
                        friend.snapshotRaided -> SnapshotStatus.RAIDED
                        else -> SnapshotStatus.FRESH
                    },
                    lastSeenAt = friend.lastSeenAt,
                )
            }.toImmutableList()
            val incoming = records
                .filter { it.direction == "in" && it.raidId !in viewed }
                .sortedByDescending { it.createdAt }
                .map { record ->
                    IncomingRaid(
                        raidId = record.raidId,
                        attackerGuestId = record.otherGuestId,
                        attackerBaseName = record.otherBaseName,
                        buddyName = BuddyCatalog.find(BuddyId(record.buddyId))?.name.orEmpty(),
                        isShadow = record.isShadow,
                        revengeDeadlineMs = record.revengeDeadline,
                        revengeUsed = record.revengeUsed,
                        createdAt = record.createdAt,
                    )
                }.toImmutableList()
            maybeFireFirstHeistCeremony(records.any { it.direction == "in" }, incoming)
            field.update { it.copy(friends = items, incomingRaids = incoming) }
        }.collect { }
    }

    private fun startHosting() {
        val hello = myHello ?: return
        if (myBaseName.isEmpty()) return
        nearbyManager.startHosting(myBaseName, hello, HostMode.VISIT)
    }

    private suspend fun collectConnectionEvents() {
        nearbyManager.connectionEvents.collect { event ->
            when (event) {
                is ConnectionEvent.PeerFound -> onPeerFound(event.peer)
                is ConnectionEvent.PeerLost -> onPeerLost(event.endpointId)
                is ConnectionEvent.TokenReady -> onTokenReady(event)
                is ConnectionEvent.Connected -> Unit
                is ConnectionEvent.ConnectFailed -> onPairingEndpointGone(event.endpointId)
                is ConnectionEvent.Disconnected -> onPairingEndpointGone(event.endpointId)
            }
        }
    }

    private fun onPeerFound(peer: DiscoveredPeer) {
        if (uiState.value.pairingPhase == PairingPhase.Discovering) {
            field.update { state ->
                val peers = state.peers.filter { it.endpointId != peer.endpointId } + peer
                state.copy(peers = peers.toImmutableList())
            }
        }
    }

    private fun onPeerLost(endpointId: String) {
        field.update { state ->
            state.copy(peers = state.peers.filter { it.endpointId != endpointId }.toImmutableList())
        }
    }

    private fun onTokenReady(event: ConnectionEvent.TokenReady) {
        val phase = uiState.value.pairingPhase
        if (event.incoming && phase == PairingPhase.Discovering) {
            // Acceptor side: show the auth token, wait for "Match!".
            pairingEndpoint = event.endpointId
            field.update {
                it.copy(pairingPhase = PairingPhase.ConfirmToken(event.baseName, event.authToken))
            }
        } else if (event.incoming && pairingEndpoint != null && pairingEndpoint != event.endpointId) {
            // Duplicate incoming while pairing: drop it, keep the first.
            nearbyManager.disconnect(event.endpointId)
        }
        // Requester side already moved to AwaitingToken in onPeerSelected.
    }

    private fun onPairingEndpointGone(endpointId: String) {
        if (endpointId == pairingEndpoint) {
            pairingEndpoint = null
            pairingHello = null
            field.update { it.copy(pairingPhase = PairingPhase.Idle) }
        }
    }

    private suspend fun collectInbound() {
        nearbyManager.inbound.collect { inbound ->
            when (val payload = inbound.payload) {
                is Hello -> onHelloReceived(inbound.endpointId, payload)
                is BaseSnapshot -> onSnapshotReceived(payload)
                else -> Unit
            }
        }
    }

    private suspend fun onHelloReceived(endpointId: String, hello: Hello) {
        val phase = uiState.value.pairingPhase
        val isPairing = phase is PairingPhase.ConfirmToken ||
            phase is PairingPhase.AwaitingToken ||
            phase is PairingPhase.VerifyCode
        if (isPairing && endpointId == pairingEndpoint) {
            pairingHello = hello
            field.update { it.copy(pairingPhase = PairingPhase.VerifyCode(hello.baseName, hello.code)) }
            return
        }
        // Visit-mode hosting: strangers are disconnected; friends get a
        // heartbeat and a fresh snapshot for the attacker's map.
        val friend = gameRepository.observeFriends().firstData()
            ?.firstOrNull { it.guestId == hello.guestId }
        if (friend == null) {
            nearbyManager.disconnect(endpointId)
        } else {
            gameRepository.upsertFriend(friend.copy(lastSeenAt = System.currentTimeMillis()))
            sendMySnapshot(endpointId)
        }
    }

    private suspend fun onSnapshotReceived(snapshot: BaseSnapshot) {
        val friend = gameRepository.observeFriends().firstData()
            ?.firstOrNull { it.guestId == snapshot.guestId } ?: return
        val now = System.currentTimeMillis()
        gameRepository.upsertFriend(
            friend.copy(
                snapshotJson = SnapshotCodec.encode(snapshot),
                snapshotAt = now,
                snapshotRaided = false,
                lastSeenAt = now,
            ),
        )
    }

    private suspend fun sendMySnapshot(endpointId: String?) {
        if (endpointId == null) return
        val snapshot = gameRepository.exportSnapshot(
            gameEngine = gameEngine,
            guestId = myGuestId,
            baseName = myBaseName,
            nowMs = System.currentTimeMillis(),
        ) ?: return
        nearbyManager.sendPayload(endpointId, snapshot)
    }

    /**
     * First-heist ceremony: the first time any raid lands on this device,
     * both sides get a gift and the ceremony flag is set (contract §1).
     */
    private suspend fun maybeFireFirstHeistCeremony(hasIncoming: Boolean, incoming: List<IncomingRaid>) {
        if (!ceremonyArmed || !hasIncoming) return
        if (p2pPrefs.firstHeistCelebrated.first()) {
            ceremonyArmed = false
            return
        }
        ceremonyArmed = false
        p2pPrefs.setFirstHeistCelebrated()
        gameRepository.grantCoins(FIRST_HEIST_GIFT_COINS)
        val name = incoming.firstOrNull()?.attackerBaseName.orEmpty()
        _events.send(FriendsEvent.ShowCeremony(name))
    }

    companion object {
        /** Pairing ritual gift, each side grants its own. */
        const val PAIRING_GIFT_COINS = 200L

        /** First-heist ceremony gift. */
        const val FIRST_HEIST_GIFT_COINS = 200L
    }
}
