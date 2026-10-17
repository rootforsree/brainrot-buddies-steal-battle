@file:Suppress("KotlinConstantConditionIf")
package com.sreecharan.brainrot_buddies_steal_battle.ui.raids

import androidx.annotation.StringRes
import com.sreecharan.brainrot_buddies_steal_battle.R
import com.sreecharan.brainrot_buddies_steal_battle.data.local.OwnedBuddyEntity
import com.sreecharan.brainrot_buddies_steal_battle.data.local.RaidRecordEntity
import com.sreecharan.brainrot_buddies_steal_battle.data.model.BuddyCatalog
import com.sreecharan.brainrot_buddies_steal_battle.data.model.BuddyId
import com.sreecharan.brainrot_buddies_steal_battle.data.model.Result
import com.sreecharan.brainrot_buddies_steal_battle.data.repository.GameRepository
import com.sreecharan.brainrot_buddies_steal_battle.di.IoDispatcher
import com.sreecharan.brainrot_buddies_steal_battle.game.engine.GameEngine
import com.sreecharan.brainrot_buddies_steal_battle.p2p.BaseSnapshot
import com.sreecharan.brainrot_buddies_steal_battle.p2p.ConnectionEvent
import com.sreecharan.brainrot_buddies_steal_battle.p2p.FriendCode
import com.sreecharan.brainrot_buddies_steal_battle.p2p.Hello
import com.sreecharan.brainrot_buddies_steal_battle.p2p.HostMode
import com.sreecharan.brainrot_buddies_steal_battle.p2p.NearbyManager
import com.sreecharan.brainrot_buddies_steal_battle.p2p.P2PChat
import com.sreecharan.brainrot_buddies_steal_battle.p2p.P2pPrefs
import com.sreecharan.brainrot_buddies_steal_battle.p2p.RaidRequest
import com.sreecharan.brainrot_buddies_steal_battle.p2p.RaidResult
import com.sreecharan.brainrot_buddies_steal_battle.p2p.RaidRules
import com.sreecharan.brainrot_buddies_steal_battle.p2p.ShadowNotice
import com.sreecharan.brainrot_buddies_steal_battle.p2p.SnapshotCodec
import com.sreecharan.brainrot_buddies_steal_battle.p2p.StealRejectReason
import com.sreecharan.brainrot_buddies_steal_battle.p2p.StealVerdict
import com.sreecharan.brainrot_buddies_steal_battle.p2p.exportSnapshot
import com.sreecharan.brainrot_buddies_steal_battle.p2p.firstData
import com.sreecharan.brainrot_buddies_steal_battle.p2p.getOrNull
import com.sreecharan.brainrot_buddies_steal_battle.p2p.grantCoins
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Returns true if the snapshot was already raided and there's no live connection. */
private fun isSnapshotSpent(snapshotRaided: Boolean, liveEndpoint: String?): Boolean =
    snapshotRaided && liveEndpoint == null

/** Inputs for a successful steal, grouped to keep the signature small. */
data class StealSuccessInput(
    val buddyId: String,
    val buddyLevel: Int,
    val isShadow: Boolean,
    val raidId: String,
    val isRevenge: Boolean,
    val now: Long,
)

/** Injectable dependencies for the raid-visit handlers. */
interface RaidVisitDeps {
    val nearbyManager: NearbyManager
    val gameRepository: GameRepository
    val gameEngine: GameEngine
    val p2pPrefs: P2pPrefs
    val ioDispatcher: CoroutineDispatcher
}

/** Injectable dependencies for the raid-visit handlers. */
data class RaidVisitDepsImpl(
    override val nearbyManager: NearbyManager,
    override val gameRepository: GameRepository,
    override val gameEngine: GameEngine,
    override val p2pPrefs: P2pPrefs,
    override val ioDispatcher: CoroutineDispatcher,
) : RaidVisitDeps

/**
 * Shared state for the raid-visit handlers: dependencies, the UI state
 * holder, the event channel, and the mutable session variables.
 */
class RaidVisitCore(
    deps: RaidVisitDeps,
    val scope: CoroutineScope,
    val friendGuestId: String,
) : RaidVisitDeps by deps {
    private val _uiState = MutableStateFlow(RaidVisitUiState(friendGuestId = friendGuestId))
    val uiState: StateFlow<RaidVisitUiState> = _uiState

    private val _events = Channel<RaidVisitEvent>(Channel.BUFFERED)
    val events: Flow<RaidVisitEvent> = _events.receiveAsFlow()

    var myGuestId: String = ""
    var myBaseName: String = ""
    var friendBaseName: String = ""
    var liveEndpoint: String? = null
    var friendSnapshot: BaseSnapshot? = null
    var revengeRecord: RaidRecordEntity? = null
    var chatSeq = 0L
    val buddyNames = mutableMapOf<String, String>()
    val pendingResults = ConcurrentHashMap<String, CompletableDeferred<RaidResult>>()

    fun updateState(transform: (RaidVisitUiState) -> RaidVisitUiState) = _uiState.update(transform)

    fun sendEvent(event: RaidVisitEvent) {
        scope.launch(ioDispatcher) { _events.send(event) }
    }

    companion object {
        const val BASE_SLOT_COUNT = 24
        const val MAX_CHAT_MESSAGES = 30
        const val RAID_RESULT_TIMEOUT_MS = 10_000L
        const val FIRST_HEIST_GIFT_COINS = 200L
    }
}

/** Visit setup: identity, raid budget, pending revenge, hosting. */
class RaidVisitSetup(private val core: RaidVisitCore) {
    private val helpers = RaidVisitHelpers(core, this)

    suspend fun enterVisit() {
        val repo = core.gameRepository
        val friend = repo.observeFriends().firstData()
            ?.firstOrNull { it.guestId == core.friendGuestId }
        if (friend == null) {
            core.sendEvent(RaidVisitEvent.NavigateBack)
            return
        }
        core.friendBaseName = friend.baseName
        core.myGuestId = repo.ensureGuestId().getOrNull().orEmpty()
        core.myBaseName = repo.observeBaseName().firstData().orEmpty()
        val (raidsUsed, alreadyRaided) = raidUsage()
        val revenge = findPendingRevenge(System.currentTimeMillis())

        friend.snapshotJson?.let { SnapshotCodec.decode(it) }?.let { snapshot ->
            core.friendSnapshot = snapshot
        }
        core.updateState {
            it.copy(
                friendBaseName = core.friendBaseName,
                raidsLeft = (RaidRules.MAX_RAIDS_PER_WINDOW - raidsUsed).coerceAtLeast(0),
                alreadyRaidedFriend = alreadyRaided,
                pendingRevenge = revenge != null,
            )
        }
        helpers.rebuildSlots()

        val hello = Hello(
            guestId = core.myGuestId,
            baseName = core.myBaseName,
            code = FriendCode.deriveFrom(core.myGuestId),
        )
        core.nearbyManager.startHosting(core.myBaseName, hello, HostMode.VISIT)
        val connection = RaidVisitConnection(core, this)
        core.scope.launch(core.ioDispatcher) { connection.collectConnectionEvents() }
        core.scope.launch(core.ioDispatcher) { connection.collectInbound() }
    }

    /** Raid budget for the current window, rolling the bucket when it changes. */
    suspend fun currentWindowUsage(): Pair<String, Int> {
        val bucket = core.gameEngine.raidWindowBucket()
        val last = core.gameRepository.observeLastRaidWindow().firstData().orEmpty()
        return if (last == bucket) {
            bucket to (core.gameRepository.observeRaidsUsed().firstData() ?: 0)
        } else {
            core.gameRepository.setLastRaidWindow(bucket)
            core.gameRepository.setRaidsUsed(0)
            bucket to 0
        }
    }

    private suspend fun raidUsage(): Pair<Int, Boolean> {
        val window = core.gameEngine.currentRaidWindow()
        val windowStartMs = RaidRules.windowStartMs(window.date, window.startHour)
        val (_, used) = currentWindowUsage()
        val alreadyRaided = core.gameRepository.observeRaidRecords().firstData().orEmpty()
            .any { it.direction == "out" && it.otherGuestId == core.friendGuestId && it.createdAt >= windowStartMs }
        return used to alreadyRaided
    }

    private suspend fun findPendingRevenge(now: Long): RaidRecordEntity? =
        core.gameRepository.observeRaidRecords().firstData().orEmpty()
            .filter {
                it.direction == "in" && it.otherGuestId == core.friendGuestId &&
                    !it.revengeUsed && now - it.createdAt < RaidRules.REVENGE_WINDOW_MS
            }
            .maxByOrNull { it.createdAt }
            .also { core.revengeRecord = it }

}

/** The steal flow: pre-checks, live and shadow steals, success/failure. */
class RaidStealFlow(
    private val core: RaidVisitCore,
    private val setup: RaidVisitSetup,
) {
    private val helpers = RaidVisitHelpers(core, setup)

    suspend fun attemptSteal(slot: Int) {
        core.updateState { it.copy(stealState = StealOutcome.WORKING, stealMessageRes = null) }
        val now = System.currentTimeMillis()
        val isRevenge = core.uiState.value.revengeArmed && core.revengeRecord != null
        val snapshot = core.friendSnapshot
        val live = core.liveEndpoint != null
        val blocked: StealRejectReason? = if (isRevenge) {
            null
        } else {
            attackerPreCheck(slot, snapshot, live, now)
        }
        if (blocked != null) {
            finishFailed(blocked)
        } else if (live) {
            liveSteal(slot, isRevenge, now)
        } else {
            shadowSteal(slot, snapshot, isRevenge, now)
        }
    }

    private suspend fun attackerPreCheck(
        slot: Int,
        snapshot: BaseSnapshot?,
        live: Boolean,
        now: Long,
    ): StealRejectReason? {
        val window = core.gameEngine.currentRaidWindow()
        val windowStartMs = RaidRules.windowStartMs(window.date, window.startHour)
        val records = core.gameRepository.observeRaidRecords().firstData().orEmpty()
        val (_, raidsUsed) = setup.currentWindowUsage()
        val alreadyRaided = records.any {
            it.direction == "out" && it.otherGuestId == core.friendGuestId && it.createdAt >= windowStartMs
        }
        val friend = core.gameRepository.observeFriends().firstData()
            ?.firstOrNull { it.guestId == core.friendGuestId }
        return when (
            val verdict = RaidRules.canAttemptSteal(
                raidsUsed = raidsUsed,
                alreadyRaidedFriend = alreadyRaided,
                snapshotRaided = friend?.snapshotRaided == true,
                isShadow = !live,
                slotOccupied = snapshot?.slots?.any { it.slot == slot } == true,
                baseShielded = snapshot != null && now < snapshot.shieldUntil,
                buddyOnCooldown = false,
            )
        ) {
            is StealVerdict.Rejected -> verdict.reason
            is StealVerdict.Accepted -> null
        }
    }

    private suspend fun liveSteal(slot: Int, isRevenge: Boolean, now: Long) {
        val endpoint = core.liveEndpoint
        if (endpoint == null) {
            finishFailed(StealRejectReason.EMPTY)
            return
        }
        val raidId = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<RaidResult>()
        core.pendingResults[raidId] = deferred
        core.nearbyManager.sendPayload(
            endpoint,
            RaidRequest(
                v = 1,
                raidId = raidId,
                attackerGuestId = core.myGuestId,
                attackerBaseName = core.myBaseName,
                slot = slot,
            ),
        )
        val result = withTimeoutOrNull(RaidVisitCore.RAID_RESULT_TIMEOUT_MS) { deferred.await() }
        core.pendingResults.remove(raidId)
        handleRaidResult(result, raidId, isRevenge, now)
    }

    private suspend fun handleRaidResult(result: RaidResult?, raidId: String, isRevenge: Boolean, now: Long) {
        val buddyId = result?.takeIf { it.accepted }?.buddyId
        if (result != null && result.accepted && buddyId != null) {
            onStealSuccess(
                StealSuccessInput(
                    buddyId = buddyId,
                    buddyLevel = result.buddyLevel,
                    isShadow = false,
                    raidId = raidId,
                    isRevenge = isRevenge,
                    now = now,
                ),
            )
        } else {
            finishFailed(result?.reason?.let(helpers::wireToReason))
        }
    }

    private suspend fun shadowSteal(slot: Int, snapshot: BaseSnapshot?, isRevenge: Boolean, now: Long) {
        val helpers = helpers
        val verdict = snapshot?.let {
            RaidRules.validateShadowSteal(it, slot, helpers.snapshotRaidedNow(), now)
        }
        val target = snapshot?.let { SnapshotCodec.slotOf(it, slot) }
        if (verdict is StealVerdict.Accepted && target != null) {
            onStealSuccess(
                StealSuccessInput(
                    buddyId = target.buddyId,
                    buddyLevel = target.level,
                    isShadow = true,
                    raidId = UUID.randomUUID().toString(),
                    isRevenge = isRevenge,
                    now = now,
                ),
            )
        } else {
            val reason = (verdict as? StealVerdict.Rejected)?.reason ?: StealRejectReason.EMPTY
            finishFailed(reason)
        }
    }

    private suspend fun onStealSuccess(input: StealSuccessInput) {
        val helpers = helpers
        val consumed = if (input.isRevenge) true else consumeRaidSlot()
        val placed = if (consumed) addStolenBuddy(input.buddyId, input.buddyLevel, input.now) else false
        if (!consumed || !placed) {
            val reason = if (!consumed) StealRejectReason.WINDOW_EXHAUSTED else StealRejectReason.BASE_FULL
            finishFailed(reason)
            return
        }
        core.gameRepository.upsertRaidRecord(
            RaidRecordEntity(
                raidId = input.raidId,
                direction = "out",
                otherGuestId = core.friendGuestId,
                otherBaseName = core.friendBaseName,
                buddyId = input.buddyId,
                buddyLevel = input.buddyLevel,
                createdAt = input.now,
                revengeUsed = false,
                revengeDeadline = 0L,
                isShadow = input.isShadow,
            ),
        )
        if (input.isShadow) {
            markSnapshotRaided()
        }
        if (input.isRevenge) {
            core.revengeRecord?.let { core.gameRepository.upsertRaidRecord(it.copy(revengeUsed = true)) }
            core.revengeRecord = null
        }
        val buddyName = helpers.buddyDisplayName(input.buddyId)
        core.sendEvent(RaidVisitEvent.PlayStealReplay(buddyName, core.myBaseName))
        if (input.isRevenge) {
            core.sendEvent(RaidVisitEvent.PlayRevengeReplay(buddyName, core.myBaseName))
        }
        helpers.maybeFirstHeistCeremony()
        val (_, raidsUsed) = setup.currentWindowUsage()
        core.updateState {
            it.copy(
                stealState = StealOutcome.SUCCESS,
                stealMessageRes = null,
                stealBuddyName = buddyName,
                selectedSlot = null,
                raidsLeft = (RaidRules.MAX_RAIDS_PER_WINDOW - raidsUsed).coerceAtLeast(0),
                // Revenge raids never count as raiding the friend this window.
                alreadyRaidedFriend = if (input.isRevenge) it.alreadyRaidedFriend else true,
                pendingRevenge = false,
                revengeArmed = false,
            )
        }
        helpers.rebuildSlots()
    }

    private suspend fun consumeRaidSlot(): Boolean {
        val (_, used) = setup.currentWindowUsage()
        if (!RaidRules.canStartRaid(used)) return false
        core.gameRepository.setRaidsUsed(used + 1)
        return true
    }

    private suspend fun addStolenBuddy(buddyId: String, level: Int, now: Long): Boolean {
        val free = core.gameRepository.observeSlots().firstData().orEmpty()
            .firstOrNull { it.unlocked && it.ownedBuddyUid == null } ?: return false
        val uid = UUID.randomUUID().toString()
        val upserted = core.gameRepository.upsertOwnedBuddy(
            OwnedBuddyEntity(
                uid = uid,
                buddyId = buddyId,
                level = level,
                hatId = null,
                acquiredAt = now,
                stealCooldownUntil = now + RaidRules.STEAL_COOLDOWN_MS,
            ),
        ) !is Result.Error
        if (upserted) {
            core.gameRepository.setSlotBuddy(free.slotIndex, uid)
        }
        return upserted
    }

    private suspend fun markSnapshotRaided() {
        val friend = core.gameRepository.observeFriends().firstData()
            ?.firstOrNull { it.guestId == core.friendGuestId } ?: return
        core.gameRepository.upsertFriend(friend.copy(snapshotRaided = true))
    }

    suspend fun finishFailed(reason: StealRejectReason?) {
        val resId = helpers.failedMessageRes(reason)
        core.updateState { it.copy(stealState = StealOutcome.FAILED, stealMessageRes = resId) }
        core.sendEvent(RaidVisitEvent.PlayStealFail)
    }

}

/** Chat intents for the raid visit screen. */
class RaidVisitChat(private val core: RaidVisitCore) {

    fun onPhraseSelected(index: Int) {
        val text = RaidChatContent.phraseText(index) ?: return
        val refId = index.toString()
        sendChat(RaidChatContent.KIND_PHRASE, refId)
        appendChat(ChatUi(mine = true, kind = RaidChatContent.KIND_PHRASE, refId = refId, text = text))
    }

    fun onEmoteSelected(emoteId: String) {
        if (!RaidChatContent.isValidEmoteRef(emoteId)) return
        sendChat(RaidChatContent.KIND_EMOTE, emoteId)
        appendChat(ChatUi(mine = true, kind = RaidChatContent.KIND_EMOTE, refId = emoteId, text = emoteId))
    }

    fun onChatReceived(chat: P2PChat) {
        val valid = when (chat.kind) {
            RaidChatContent.KIND_PHRASE -> RaidChatContent.isValidPhraseRef(chat.refId)
            RaidChatContent.KIND_EMOTE -> RaidChatContent.isValidEmoteRef(chat.refId)
            else -> false
        }
        if (!valid) return
        val text = if (chat.kind == RaidChatContent.KIND_PHRASE) {
            RaidChatContent.phraseText(chat.refId.toInt()) ?: return
        } else {
            chat.refId
        }
        appendChat(ChatUi(mine = false, kind = chat.kind, refId = chat.refId, text = text))
    }

    private fun sendChat(kind: String, refId: String) {
        val endpoint = core.liveEndpoint ?: return
        core.scope.launch(core.ioDispatcher) {
            core.nearbyManager.sendPayload(endpoint, P2PChat(v = 1, kind = kind, refId = refId))
        }
    }

    private fun appendChat(message: ChatUi) {
        core.updateState { state ->
            val withId = message.copy(id = core.chatSeq++)
            state.copy(chat = (state.chat + withId).takeLast(RaidVisitCore.MAX_CHAT_MESSAGES).toImmutableList())
        }
    }
}

/** Nearby connection and inbound payload handling for the raid visit. */
class RaidVisitConnection(
    private val core: RaidVisitCore,
    private val setup: RaidVisitSetup,
) {

    suspend fun collectConnectionEvents() {
        core.nearbyManager.connectionEvents.collect { event ->
            when (event) {
                is ConnectionEvent.PeerFound -> core.nearbyManager.connectTo(event.peer.endpointId)
                is ConnectionEvent.Disconnected -> onEndpointLost(event.endpointId)
                else -> Unit
            }
        }
    }

    private fun onEndpointLost(endpointId: String) {
        if (endpointId == core.liveEndpoint) {
            core.liveEndpoint = null
            core.updateState {
                it.copy(
                    connection = if (core.friendSnapshot != null) {
                        VisitConnection.SNAPSHOT
                    } else {
                        VisitConnection.SEARCHING
                    },
                )
            }
        }
    }

    suspend fun collectInbound() {
        core.nearbyManager.inbound.collect { inbound ->
            when (val payload = inbound.payload) {
                is Hello -> onHelloReceived(inbound.endpointId, payload)
                is BaseSnapshot -> onSnapshotReceived(payload)
                is RaidResult -> core.pendingResults[payload.raidId]?.complete(payload)
                is P2PChat -> RaidVisitChat(core).onChatReceived(payload)
                else -> Unit
            }
        }
    }

    private suspend fun onHelloReceived(endpointId: String, hello: Hello) {
        if (hello.guestId != core.friendGuestId) {
            core.nearbyManager.disconnect(endpointId)
            return
        }
        if (core.liveEndpoint == null) {
            core.liveEndpoint = endpointId
            core.updateState { it.copy(connection = VisitConnection.LIVE) }
            sendMySnapshot(endpointId)
            syncPendingShadowNotices(endpointId)
        } else if (endpointId != core.liveEndpoint) {
            core.nearbyManager.disconnect(endpointId)
        }
    }

    private suspend fun onSnapshotReceived(snapshot: BaseSnapshot) {
        if (snapshot.guestId != core.friendGuestId) return
        core.friendSnapshot = snapshot
        val friend = core.gameRepository.observeFriends().firstData()
            ?.firstOrNull { it.guestId == core.friendGuestId }
        if (friend != null) {
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
        if (core.uiState.value.connection == VisitConnection.SEARCHING) {
            core.updateState { it.copy(connection = VisitConnection.SNAPSHOT) }
        }
        RaidVisitHelpers(core, setup).rebuildSlots()
    }

    private suspend fun sendMySnapshot(endpointId: String) {
        val snapshot = core.gameRepository.exportSnapshot(
            gameEngine = core.gameEngine,
            guestId = core.myGuestId,
            baseName = core.myBaseName,
            nowMs = System.currentTimeMillis(),
        ) ?: return
        core.nearbyManager.sendPayload(endpointId, snapshot)
    }

    /**
     * §8 shadow-heist sync: replay pending shadow raid records to the victim
     * on the next P2P connection, exactly once each.
     */
    private suspend fun syncPendingShadowNotices(endpointId: String) {
        val pending = core.gameRepository.observeRaidRecords().firstData().orEmpty()
            .filter { it.direction == "out" && it.isShadow && it.otherGuestId == core.friendGuestId }
            .filter { !core.p2pPrefs.isShadowSynced(it.raidId) }
        pending.forEach { record ->
            core.nearbyManager.sendPayload(
                endpointId,
                ShadowNotice(
                    v = 1,
                    raidId = record.raidId,
                    buddyId = record.buddyId,
                    buddyLevel = record.buddyLevel,
                    attackerGuestId = core.myGuestId,
                    attackerBaseName = core.myBaseName,
                    createdAt = record.createdAt,
                ),
            )
            core.p2pPrefs.markShadowSynced(record.raidId)
        }
    }
}

/** Small helpers shared by the steal flow and the connection handlers. */
class RaidVisitHelpers(
    private val core: RaidVisitCore,
    private val setup: RaidVisitSetup,
) {

    suspend fun rebuildSlots() {
        val now = System.currentTimeMillis()
        val snapshot = core.friendSnapshot
        val window = core.gameEngine.currentRaidWindow()
        val windowStartMs = RaidRules.windowStartMs(window.date, window.startHour)
        val (_, raidsUsed) = setup.currentWindowUsage()
        val alreadyRaided = core.gameRepository.observeRaidRecords().firstData().orEmpty()
            .any { it.direction == "out" && it.otherGuestId == core.friendGuestId && it.createdAt >= windowStartMs }
        val snapshotRaided = snapshotRaidedNow()
        val slots = (0 until RaidVisitCore.BASE_SLOT_COUNT).map { slot ->
            val target = snapshot?.let { SnapshotCodec.slotOf(it, slot) }
            val blockedBy = when {
                target == null -> StealRejectReason.EMPTY
                alreadyRaided -> StealRejectReason.ALREADY_RAIDED
                !RaidRules.canStartRaid(raidsUsed) -> StealRejectReason.WINDOW_EXHAUSTED
                isSnapshotSpent(snapshotRaided, core.liveEndpoint) -> StealRejectReason.SNAPSHOT_SPENT
                snapshot != null && now < snapshot.shieldUntil -> StealRejectReason.SHIELDED
                else -> null
            }
            RaidSlotUi(
                slot = slot,
                buddyId = target?.buddyId.orEmpty(),
                buddyName = target?.let { buddyDisplayName(it.buddyId) }.orEmpty(),
                level = target?.level ?: 1,
                stealable = blockedBy == null,
                blockedBy = blockedBy,
            )
        }.toImmutableList()
        core.updateState { it.copy(slots = slots) }
    }

    suspend fun snapshotRaidedNow(): Boolean =
        core.gameRepository.observeFriends().firstData()
            ?.firstOrNull { it.guestId == core.friendGuestId }?.snapshotRaided == true

    suspend fun buddyDisplayName(buddyId: String): String =
        core.buddyNames.getOrPut(buddyId) {
            BuddyCatalog.find(BuddyId(buddyId))?.name.orEmpty()
        }

    suspend fun maybeFirstHeistCeremony() {
        if (core.p2pPrefs.firstHeistCelebrated.first()) return
        core.p2pPrefs.setFirstHeistCelebrated()
        core.gameRepository.grantCoins(RaidVisitCore.FIRST_HEIST_GIFT_COINS)
        core.sendEvent(RaidVisitEvent.ShowCeremony(core.friendBaseName))
    }

    fun wireToReason(wire: String): StealRejectReason? =
        StealRejectReason.entries.firstOrNull { it.wire == wire }

    @StringRes
    fun failedMessageRes(reason: StealRejectReason?): Int = when (reason) {
        StealRejectReason.SHIELDED -> R.string.raid_failed_shielded
        StealRejectReason.COOLDOWN -> R.string.raid_failed_cooldown
        StealRejectReason.EMPTY -> R.string.raid_failed_empty
        StealRejectReason.ALREADY_RAIDED -> R.string.raid_failed_already
        StealRejectReason.WINDOW_EXHAUSTED -> R.string.raid_no_raids_left
        StealRejectReason.SNAPSHOT_SPENT -> R.string.friend_snapshot_raided
        StealRejectReason.BASE_FULL -> R.string.raid_base_full
        null -> R.string.raid_timeout
    }
}
