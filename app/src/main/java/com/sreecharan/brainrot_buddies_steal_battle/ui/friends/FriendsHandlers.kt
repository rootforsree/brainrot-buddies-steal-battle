package com.sreecharan.brainrot_buddies_steal_battle.ui.friends

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
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Shared mutable state behind [FriendsViewModel]'s intent handlers: the
 * dependencies, the UI state flow, the event channel and the pairing
 * session (endpoint, hellos, identity gate). The handlers below are thin
 * intent groups over this core.
 */
class FriendsVmCore(
    val nearbyManager: NearbyManager,
    val gameRepository: GameRepository,
    val gameEngine: GameEngine,
    val p2pPrefs: P2pPrefs,
    @IoDispatcher val ioDispatcher: CoroutineDispatcher,
    val scope: CoroutineScope,
) {
    val uiState = MutableStateFlow(FriendsUiState())

    private val eventsChannel = Channel<FriendsEvent>(Channel.BUFFERED)
    val events: Flow<FriendsEvent> = eventsChannel.receiveAsFlow()

    var myGuestId: String = ""
    var myBaseName: String = ""
    var myHello: Hello? = null
    var pairingEndpoint: String? = null
    var pairingHello: Hello? = null
    var ceremonyArmed = true
    val identityReady = CompletableDeferred<Unit>()

    suspend fun sendEvent(event: FriendsEvent) {
        eventsChannel.send(event)
    }

    suspend fun loadIdentity() {
        val guestId = gameRepository.ensureGuestId().getOrNull().orEmpty()
        myGuestId = guestId
        myBaseName = gameRepository.observeBaseName().firstData().orEmpty()
        val code = FriendCode.deriveFrom(guestId)
        myHello = Hello(guestId = guestId, baseName = myBaseName, code = code)
        uiState.update { it.copy(myCode = code) }
        identityReady.complete(Unit)
    }

    fun startHosting() {
        val hello = myHello ?: return
        if (myBaseName.isEmpty()) return
        nearbyManager.startHosting(myBaseName, hello, HostMode.VISIT)
    }

    suspend fun sendMySnapshot(endpointId: String?) {
        if (endpointId == null) return
        val snapshot = gameRepository.exportSnapshot(
            gameEngine = gameEngine,
            guestId = myGuestId,
            baseName = myBaseName,
            nowMs = System.currentTimeMillis(),
        ) ?: return
        nearbyManager.sendPayload(endpointId, snapshot)
    }
}

/** Pairing ceremony intents (§8). */
class PairingIntents(private val core: FriendsVmCore) {

    /** Called by the Route after the in-context Bluetooth permission grant. */
    fun onBluetoothGranted() {
        core.uiState.update { it.copy(bluetoothGranted = true) }
        core.scope.launch(core.ioDispatcher) {
            core.identityReady.await()
            core.startHosting()
        }
    }

    fun onFindNearby() {
        if (!core.uiState.value.bluetoothGranted) return
        core.scope.launch(core.ioDispatcher) {
            core.identityReady.await()
            core.startHosting()
            core.uiState.update {
                it.copy(pairingPhase = PairingPhase.Discovering, peers = persistentListOf())
            }
        }
    }

    fun onStopFinding() {
        core.uiState.update { it.copy(pairingPhase = PairingPhase.Idle, peers = persistentListOf()) }
    }

    fun onPeerSelected(peer: DiscoveredPeer) {
        core.pairingEndpoint = peer.endpointId
        core.pairingHello = null
        core.nearbyManager.requestPairing(peer)
        core.uiState.update {
            it.copy(pairingPhase = PairingPhase.AwaitingToken(peer.baseName, weInitiated = true))
        }
    }

    /**
     * The acceptor's "Match!" tap: the Nearby auth token matched on both
     * screens, so accept the connection. Hellos are exchanged automatically
     * on connect, then the code-verify step runs.
     */
    fun onMatchConfirmed() {
        val endpoint = core.pairingEndpoint ?: return
        core.nearbyManager.acceptConnection(endpoint)
    }

    fun onPairingCancelled() {
        core.pairingEndpoint?.let { core.nearbyManager.disconnect(it) }
        core.pairingEndpoint = null
        core.pairingHello = null
        core.uiState.update { it.copy(pairingPhase = PairingPhase.Idle, peers = persistentListOf()) }
    }

    /** "Yes, that's them!": the spoken code matches. Save the friend. */
    fun onCodeConfirmed() {
        val hello = core.pairingHello ?: return
        val endpoint = core.pairingEndpoint
        core.scope.launch(core.ioDispatcher) {
            val now = System.currentTimeMillis()
            core.gameRepository.upsertFriend(
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
            core.gameRepository.grantCoins(FriendsViewModel.PAIRING_GIFT_COINS)
            core.sendMySnapshot(endpoint)
            core.pairingEndpoint = null
            core.pairingHello = null
            core.uiState.update { it.copy(pairingPhase = PairingPhase.Paired(hello.baseName)) }
            core.sendEvent(FriendsEvent.ShowMessage(R.string.friend_paired))
        }
    }

    fun onCodeRejected() {
        onPairingCancelled()
    }

    /** The Route calls this only after Builder C's ParentGateDialog passes. */
    fun onRevealCode() {
        core.uiState.update { it.copy(codeRevealed = true) }
    }

    fun onHideCode() {
        core.uiState.update { it.copy(codeRevealed = false) }
    }
}

/** Friend-list and raid-inbox intents. */
class RosterIntents(private val core: FriendsVmCore) {

    fun onUnpair(friend: FriendItem) {
        core.scope.launch(core.ioDispatcher) {
            core.gameRepository.removeFriend(friend.guestId)
        }
    }

    fun onVisitFriend(friend: FriendItem) {
        core.scope.launch { core.sendEvent(FriendsEvent.NavigateToRaidVisit(friend.guestId)) }
    }

    fun onRevenge(raid: IncomingRaid) {
        core.scope.launch { core.sendEvent(FriendsEvent.NavigateToRaidVisit(raid.attackerGuestId)) }
    }

    fun onDismissRaid(raid: IncomingRaid) {
        core.scope.launch(core.ioDispatcher) { core.p2pPrefs.markRaidViewed(raid.raidId) }
    }
}

/** Nearby connection events and inbound payloads. */
internal class FriendsConnection(private val core: FriendsVmCore) {

    suspend fun collectConnectionEvents() {
        core.nearbyManager.connectionEvents.collect { event ->
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

    suspend fun collectInbound() {
        core.nearbyManager.inbound.collect { inbound ->
            when (val payload = inbound.payload) {
                is Hello -> onHelloReceived(inbound.endpointId, payload)
                is BaseSnapshot -> onSnapshotReceived(payload)
                else -> Unit
            }
        }
    }

    private fun onPeerFound(peer: DiscoveredPeer) {
        if (core.uiState.value.pairingPhase == PairingPhase.Discovering) {
            core.uiState.update { state ->
                val peers = state.peers.filter { it.endpointId != peer.endpointId } + peer
                state.copy(peers = peers.toImmutableList())
            }
        }
    }

    private fun onPeerLost(endpointId: String) {
        core.uiState.update { state ->
            state.copy(peers = state.peers.filter { it.endpointId != endpointId }.toImmutableList())
        }
    }

    private fun onTokenReady(event: ConnectionEvent.TokenReady) {
        val phase = core.uiState.value.pairingPhase
        if (event.incoming && phase == PairingPhase.Discovering) {
            // Acceptor side: show the auth token, wait for "Match!".
            core.pairingEndpoint = event.endpointId
            core.uiState.update {
                it.copy(pairingPhase = PairingPhase.ConfirmToken(event.baseName, event.authToken))
            }
        } else if (event.incoming && core.pairingEndpoint != null && core.pairingEndpoint != event.endpointId) {
            // Duplicate incoming while pairing: drop it, keep the first.
            core.nearbyManager.disconnect(event.endpointId)
        }
        // Requester side already moved to AwaitingToken in onPeerSelected.
    }

    private fun onPairingEndpointGone(endpointId: String) {
        if (endpointId == core.pairingEndpoint) {
            core.pairingEndpoint = null
            core.pairingHello = null
            core.uiState.update { it.copy(pairingPhase = PairingPhase.Idle) }
        }
    }

    private suspend fun onHelloReceived(endpointId: String, hello: Hello) {
        val phase = core.uiState.value.pairingPhase
        val isPairing = phase is PairingPhase.ConfirmToken ||
            phase is PairingPhase.AwaitingToken ||
            phase is PairingPhase.VerifyCode
        if (isPairing && endpointId == core.pairingEndpoint) {
            core.pairingHello = hello
            core.uiState.update {
                it.copy(pairingPhase = PairingPhase.VerifyCode(hello.baseName, hello.code))
            }
            return
        }
        // Visit-mode hosting: strangers are disconnected; friends get a
        // heartbeat and a fresh snapshot for the attacker's map.
        val friend = core.gameRepository.observeFriends().firstData()
            ?.firstOrNull { it.guestId == hello.guestId }
        if (friend == null) {
            core.nearbyManager.disconnect(endpointId)
        } else {
            core.gameRepository.upsertFriend(friend.copy(lastSeenAt = System.currentTimeMillis()))
            core.sendMySnapshot(endpointId)
        }
    }

    private suspend fun onSnapshotReceived(snapshot: BaseSnapshot) {
        val friend = core.gameRepository.observeFriends().firstData()
            ?.firstOrNull { it.guestId == snapshot.guestId } ?: return
        val now = System.currentTimeMillis()
        core.gameRepository.upsertFriend(
            friend.copy(
                snapshotJson = SnapshotCodec.encode(snapshot),
                snapshotAt = now,
                snapshotRaided = false,
                lastSeenAt = now,
            ),
        )
    }
}

/** Friend directory and the first-heist ceremony. */
internal class FriendsDirectory(private val core: FriendsVmCore) {

    suspend fun collectDirectory() {
        combine(
            core.gameRepository.observeFriends(),
            core.gameRepository.observeRaidRecords(),
            core.p2pPrefs.viewedRaidIds,
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
            core.uiState.update { it.copy(friends = items, incomingRaids = incoming) }
        }.collect { }
    }

    /**
     * First-heist ceremony: the first time any raid lands on this device,
     * both sides get a gift and the ceremony flag is set (contract §1).
     */
    private suspend fun maybeFireFirstHeistCeremony(
        hasIncoming: Boolean,
        incoming: List<IncomingRaid>,
    ) {
        if (!core.ceremonyArmed || !hasIncoming) return
        if (core.p2pPrefs.firstHeistCelebrated.first()) {
            core.ceremonyArmed = false
            return
        }
        core.ceremonyArmed = false
        core.p2pPrefs.setFirstHeistCelebrated()
        core.gameRepository.grantCoins(FriendsViewModel.FIRST_HEIST_GIFT_COINS)
        val name = incoming.firstOrNull()?.attackerBaseName.orEmpty()
        core.sendEvent(FriendsEvent.ShowCeremony(name))
    }
}
