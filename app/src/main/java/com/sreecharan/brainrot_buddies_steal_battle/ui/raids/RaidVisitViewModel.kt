package com.sreecharan.brainrot_buddies_steal_battle.ui.raids

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
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
 */
@HiltViewModel
class RaidVisitViewModel @Inject constructor(
    private val nearbyManager: NearbyManager,
    private val gameRepository: GameRepository,
    private val gameEngine: GameEngine,
    private val p2pPrefs: P2pPrefs,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val friendGuestId: String = savedStateHandle.get<String>("friendGuestId").orEmpty()

    val uiState: StateFlow<RaidVisitUiState> field = MutableStateFlow(
        RaidVisitUiState(friendGuestId = friendGuestId),
    )

    private val _events = Channel<RaidVisitEvent>(Channel.BUFFERED)
    val events: Flow<RaidVisitEvent> = _events.receiveAsFlow()

    private var myGuestId: String = ""
    private var myBaseName: String = ""
    private var friendBaseName: String = ""
    private var liveEndpoint: String? = null
    private var friendSnapshot: BaseSnapshot? = null
    private var revengeRecord: RaidRecordEntity? = null
    private var chatSeq = 0L
    private val buddyNames = mutableMapOf<String, String>()
    private val pendingResults = ConcurrentHashMap<String, CompletableDeferred<RaidResult>>()

    init {
        viewModelScope.launch(ioDispatcher) { enterVisit() }
    }

    // ---- intents ----

    fun onSlotSelected(slot: Int) {
        field.update { it.copy(selectedSlot = slot, stealState = StealOutcome.IDLE, stealMessageRes = null) }
    }

    fun onRevengeClicked() {
        field.update { it.copy(revengeArmed = true) }
    }

    fun onStealClicked() {
        val slot = uiState.value.selectedSlot ?: return
        if (uiState.value.stealState == StealOutcome.WORKING) return
        viewModelScope.launch(ioDispatcher) { attemptSteal(slot) }
    }

    fun onPhraseSelected(index: Int) {
        val text = RaidChatContent.phraseText(index) ?: return
        sendChat(RaidChatContent.KIND_PHRASE, index.toString())
        appendChat(ChatUi(mine = true, kind = RaidChatContent.KIND_PHRASE, refId = index.toString(), text = text))
    }

    fun onEmoteSelected(emoteId: String) {
        if (!RaidChatContent.isValidEmoteRef(emoteId)) return
        sendChat(RaidChatContent.KIND_EMOTE, emoteId)
        appendChat(ChatUi(mine = true, kind = RaidChatContent.KIND_EMOTE, refId = emoteId, text = emoteId))
    }

    // ---- setup ----

    private suspend fun enterVisit() {
        val friend = gameRepository.observeFriends().firstData()
            ?.firstOrNull { it.guestId == friendGuestId }
        if (friend == null) {
            _events.send(RaidVisitEvent.NavigateBack)
            return
        }
        friendBaseName = friend.baseName
        myGuestId = gameRepository.ensureGuestId().getOrNull().orEmpty()
        myBaseName = gameRepository.observeBaseName().firstData().orEmpty()
        val now = System.currentTimeMillis()
        val (raidsUsed, alreadyRaided) = raidUsage(now)
        val revenge = findPendingRevenge(now)

        friend.snapshotJson?.let { SnapshotCodec.decode(it) }?.let { snapshot ->
            friendSnapshot = snapshot
        }
        field.update {
            it.copy(
                friendBaseName = friendBaseName,
                raidsLeft = (RaidRules.MAX_RAIDS_PER_WINDOW - raidsUsed).coerceAtLeast(0),
                alreadyRaidedFriend = alreadyRaided,
                pendingRevenge = revenge != null,
            )
        }
        rebuildSlots()

        val hello = Hello(
            guestId = myGuestId,
            baseName = myBaseName,
            code = FriendCode.deriveFrom(myGuestId),
        )
        nearbyManager.startHosting(myBaseName, hello, HostMode.VISIT)
        viewModelScope.launch(ioDispatcher) { collectConnectionEvents() }
        viewModelScope.launch(ioDispatcher) { collectInbound() }
    }

    /** Raid budget for the current window, rolling the bucket when it changes. */
    private suspend fun currentWindowUsage(): Pair<String, Int> {
        val bucket = gameEngine.raidWindowBucket()
        val last = gameRepository.observeLastRaidWindow().firstData().orEmpty()
        return if (last == bucket) {
            bucket to gameRepository.observeRaidsUsed().firstData() ?: 0
        } else {
            gameRepository.setLastRaidWindow(bucket)
            gameRepository.setRaidsUsed(0)
            bucket to 0
        }
    }

    private suspend fun raidUsage(now: Long): Pair<Int, Boolean> {
        val window = gameEngine.currentRaidWindow()
        val windowStartMs = RaidRules.windowStartMs(window.date, window.startHour)
        val (_, used) = currentWindowUsage()
        val alreadyRaided = gameRepository.observeRaidRecords().firstData().orEmpty()
            .any { it.direction == "out" && it.otherGuestId == friendGuestId && it.createdAt >= windowStartMs }
        return used to alreadyRaided
    }

    private suspend fun findPendingRevenge(now: Long): RaidRecordEntity? =
        gameRepository.observeRaidRecords().firstData().orEmpty()
            .filter {
                it.direction == "in" && it.otherGuestId == friendGuestId &&
                    !it.revengeUsed && now - it.createdAt < RaidRules.REVENGE_WINDOW_MS
            }
            .maxByOrNull { it.createdAt }
            .also { revengeRecord = it }

    // ---- steal flow ----

    private suspend fun attemptSteal(slot: Int) {
        field.update { it.copy(stealState = StealOutcome.WORKING, stealMessageRes = null) }
        val now = System.currentTimeMillis()
        val isRevenge = uiState.value.revengeArmed && revengeRecord != null
        val snapshot = friendSnapshot
        val live = liveEndpoint != null
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
        val window = gameEngine.currentRaidWindow()
        val windowStartMs = RaidRules.windowStartMs(window.date, window.startHour)
        val records = gameRepository.observeRaidRecords().firstData().orEmpty()
        val (_, raidsUsed) = currentWindowUsage()
        val alreadyRaided = records.any {
            it.direction == "out" && it.otherGuestId == friendGuestId && it.createdAt >= windowStartMs
        }
        val friend = gameRepository.observeFriends().firstData()
            ?.firstOrNull { it.guestId == friendGuestId }
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
        val endpoint = liveEndpoint
        if (endpoint == null) {
            finishFailed(StealRejectReason.EMPTY)
            return
        }
        val raidId = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<RaidResult>()
        pendingResults[raidId] = deferred
        nearbyManager.sendPayload(
            endpoint,
            RaidRequest(
                v = 1,
                raidId = raidId,
                attackerGuestId = myGuestId,
                attackerBaseName = myBaseName,
                slot = slot,
            ),
        )
        val result = withTimeoutOrNull(RAID_RESULT_TIMEOUT_MS) { deferred.await() }
        pendingResults.remove(raidId)
        handleRaidResult(result, raidId, isRevenge, now)
    }

    private suspend fun handleRaidResult(result: RaidResult?, raidId: String, isRevenge: Boolean, now: Long) {
        val buddyId = result?.takeIf { it.accepted }?.buddyId
        if (result != null && result.accepted && buddyId != null) {
            onStealSuccess(
                buddyId = buddyId,
                buddyLevel = result.buddyLevel,
                isShadow = false,
                raidId = raidId,
                isRevenge = isRevenge,
                now = now,
            )
        } else {
            finishFailed(result?.reason?.let(::wireToReason))
        }
    }

    private suspend fun shadowSteal(slot: Int, snapshot: BaseSnapshot?, isRevenge: Boolean, now: Long) {
        val verdict = snapshot?.let {
            RaidRules.validateShadowSteal(it, slot, snapshotRaidedNow(), now)
        }
        val target = snapshot?.let { SnapshotCodec.slotOf(it, slot) }
        if (verdict is StealVerdict.Accepted && target != null) {
            onStealSuccess(
                buddyId = target.buddyId,
                buddyLevel = target.level,
                isShadow = true,
                raidId = UUID.randomUUID().toString(),
                isRevenge = isRevenge,
                now = now,
            )
        } else {
            val reason = (verdict as? StealVerdict.Rejected)?.reason ?: StealRejectReason.EMPTY
            finishFailed(reason)
        }
    }

    private suspend fun onStealSuccess(
        buddyId: String,
        buddyLevel: Int,
        isShadow: Boolean,
        raidId: String,
        isRevenge: Boolean,
        now: Long,
    ) {
        val consumed = if (isRevenge) true else consumeRaidSlot()
        val placed = if (consumed) addStolenBuddy(buddyId, buddyLevel, now) else false
        if (!consumed || !placed) {
            val reason = if (!consumed) StealRejectReason.WINDOW_EXHAUSTED else StealRejectReason.BASE_FULL
            finishFailed(reason)
            return
        }
        gameRepository.upsertRaidRecord(
            RaidRecordEntity(
                raidId = raidId,
                direction = "out",
                otherGuestId = friendGuestId,
                otherBaseName = friendBaseName,
                buddyId = buddyId,
                buddyLevel = buddyLevel,
                createdAt = now,
                revengeUsed = false,
                revengeDeadline = 0L,
                isShadow = isShadow,
            ),
        )
        if (isShadow) {
            markSnapshotRaided()
        }
        if (isRevenge) {
            revengeRecord?.let { gameRepository.upsertRaidRecord(it.copy(revengeUsed = true)) }
            revengeRecord = null
        }
        val buddyName = buddyDisplayName(buddyId)
        _events.send(RaidVisitEvent.PlayStealReplay(buddyName, myBaseName))
        if (isRevenge) {
            _events.send(RaidVisitEvent.PlayRevengeReplay(buddyName, myBaseName))
        }
        maybeFirstHeistCeremony()
        val (_, raidsUsed) = currentWindowUsage()
        field.update {
            it.copy(
                stealState = StealOutcome.SUCCESS,
                stealMessageRes = null,
                stealBuddyName = buddyName,
                selectedSlot = null,
                raidsLeft = (RaidRules.MAX_RAIDS_PER_WINDOW - raidsUsed).coerceAtLeast(0),
                // Revenge raids never count as raiding the friend this window.
                alreadyRaidedFriend = if (isRevenge) it.alreadyRaidedFriend else true,
                pendingRevenge = false,
                revengeArmed = false,
            )
        }
        rebuildSlots()
    }

    private suspend fun consumeRaidSlot(): Boolean {
        val (_, used) = currentWindowUsage()
        if (!RaidRules.canStartRaid(used)) return false
        gameRepository.setRaidsUsed(used + 1)
        return true
    }

    private suspend fun addStolenBuddy(buddyId: String, level: Int, now: Long): Boolean {
        val free = gameRepository.observeSlots().firstData().orEmpty()
            .firstOrNull { it.unlocked && it.ownedBuddyUid == null }
        return free != null && placeBuddy(free.slotIndex, buddyId, level, now)
    }

    private suspend fun placeBuddy(slotIndex: Int, buddyId: String, level: Int, now: Long): Boolean {
        val uid = UUID.randomUUID().toString()
        val upserted = gameRepository.upsertOwnedBuddy(
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
            gameRepository.setSlotBuddy(slotIndex, uid)
        }
        return upserted
    }

    private suspend fun markSnapshotRaided() {
        val friend = gameRepository.observeFriends().firstData()
            ?.firstOrNull { it.guestId == friendGuestId } ?: return
        gameRepository.upsertFriend(friend.copy(snapshotRaided = true))
    }

    private suspend fun finishFailed(reason: StealRejectReason?) {
        val resId = when (reason) {
            StealRejectReason.SHIELDED -> R.string.raid_failed_shielded
            StealRejectReason.COOLDOWN -> R.string.raid_failed_cooldown
            StealRejectReason.EMPTY -> R.string.raid_failed_empty
            StealRejectReason.ALREADY_RAIDED -> R.string.raid_failed_already
            StealRejectReason.WINDOW_EXHAUSTED -> R.string.raid_no_raids_left
            StealRejectReason.SNAPSHOT_SPENT -> R.string.friend_snapshot_raided
            StealRejectReason.BASE_FULL -> R.string.raid_base_full
            null -> R.string.raid_timeout
        }
        field.update { it.copy(stealState = StealOutcome.FAILED, stealMessageRes = resId) }
        _events.send(RaidVisitEvent.PlayStealFail)
    }

    // ---- connection / inbound ----

    private suspend fun collectConnectionEvents() {
        nearbyManager.connectionEvents.collect { event ->
            when (event) {
                is ConnectionEvent.PeerFound -> nearbyManager.connectTo(event.peer.endpointId)
                is ConnectionEvent.Disconnected -> onEndpointLost(event.endpointId)
                else -> Unit
            }
        }
    }

    private fun onEndpointLost(endpointId: String) {
        if (endpointId == liveEndpoint) {
            liveEndpoint = null
            field.update {
                it.copy(
                    connection = if (friendSnapshot != null) {
                        VisitConnection.SNAPSHOT
                    } else {
                        VisitConnection.SEARCHING
                    },
                )
            }
        }
    }

    private suspend fun collectInbound() {
        nearbyManager.inbound.collect { inbound ->
            when (val payload = inbound.payload) {
                is Hello -> onHelloReceived(inbound.endpointId, payload)
                is BaseSnapshot -> onSnapshotReceived(payload)
                is RaidResult -> pendingResults[payload.raidId]?.complete(payload)
                is P2PChat -> onChatReceived(payload)
                else -> Unit
            }
        }
    }

    private suspend fun onHelloReceived(endpointId: String, hello: Hello) {
        if (hello.guestId != friendGuestId) {
            nearbyManager.disconnect(endpointId)
            return
        }
        if (liveEndpoint == null) {
            liveEndpoint = endpointId
            field.update { it.copy(connection = VisitConnection.LIVE) }
            sendMySnapshot(endpointId)
            syncPendingShadowNotices(endpointId)
        } else if (endpointId != liveEndpoint) {
            nearbyManager.disconnect(endpointId)
        }
    }

    private suspend fun onSnapshotReceived(snapshot: BaseSnapshot) {
        if (snapshot.guestId != friendGuestId) return
        friendSnapshot = snapshot
        val friend = gameRepository.observeFriends().firstData()
            ?.firstOrNull { it.guestId == friendGuestId }
        if (friend != null) {
            gameRepository.upsertFriend(
                friend.copy(
                    snapshotJson = SnapshotCodec.encode(snapshot),
                    snapshotAt = System.currentTimeMillis(),
                    snapshotRaided = false,
                    lastSeenAt = System.currentTimeMillis(),
                ),
            )
        }
        if (uiState.value.connection == VisitConnection.SEARCHING) {
            field.update { it.copy(connection = VisitConnection.SNAPSHOT) }
        }
        rebuildSlots()
    }

    private suspend fun sendMySnapshot(endpointId: String) {
        val snapshot = gameRepository.exportSnapshot(
            gameEngine = gameEngine,
            guestId = myGuestId,
            baseName = myBaseName,
            nowMs = System.currentTimeMillis(),
        ) ?: return
        nearbyManager.sendPayload(endpointId, snapshot)
    }

    /**
     * §8 shadow-heist sync: replay pending shadow raid records to the victim
     * on the next P2P connection, exactly once each.
     */
    private suspend fun syncPendingShadowNotices(endpointId: String) {
        val pending = gameRepository.observeRaidRecords().firstData().orEmpty()
            .filter { it.direction == "out" && it.isShadow && it.otherGuestId == friendGuestId }
            .filter { !p2pPrefs.isShadowSynced(it.raidId) }
        pending.forEach { record ->
            nearbyManager.sendPayload(
                endpointId,
                ShadowNotice(
                    v = 1,
                    raidId = record.raidId,
                    buddyId = record.buddyId,
                    buddyLevel = record.buddyLevel,
                    attackerGuestId = myGuestId,
                    attackerBaseName = myBaseName,
                    createdAt = record.createdAt,
                ),
            )
            p2pPrefs.markShadowSynced(record.raidId)
        }
    }

    private fun onChatReceived(chat: P2PChat) {
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
        val endpoint = liveEndpoint ?: return
        viewModelScope.launch(ioDispatcher) {
            nearbyManager.sendPayload(endpoint, P2PChat(v = 1, kind = kind, refId = refId))
        }
    }

    private fun appendChat(message: ChatUi) {
        field.update { state ->
            val withId = message.copy(id = chatSeq++)
            state.copy(chat = (state.chat + withId).takeLast(MAX_CHAT_MESSAGES).toImmutableList())
        }
    }

    // ---- helpers ----

    private suspend fun rebuildSlots() {
        val now = System.currentTimeMillis()
        val state = uiState.value
        val snapshot = friendSnapshot
        val window = gameEngine.currentRaidWindow()
        val windowStartMs = RaidRules.windowStartMs(window.date, window.startHour)
        val (_, raidsUsed) = currentWindowUsage()
        val alreadyRaided = gameRepository.observeRaidRecords().firstData().orEmpty()
            .any { it.direction == "out" && it.otherGuestId == friendGuestId && it.createdAt >= windowStartMs }
        val snapshotRaided = snapshotRaidedNow()
        val slots = (0 until BASE_SLOT_COUNT).map { slot ->
            val target = snapshot?.let { SnapshotCodec.slotOf(it, slot) }
            val blockedBy = when {
                target == null -> StealRejectReason.EMPTY
                alreadyRaided -> StealRejectReason.ALREADY_RAIDED
                !RaidRules.canStartRaid(raidsUsed) -> StealRejectReason.WINDOW_EXHAUSTED
                snapshotRaided && liveEndpoint == null -> StealRejectReason.SNAPSHOT_SPENT
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
        field.update { it.copy(slots = slots) }
    }

    private suspend fun snapshotRaidedNow(): Boolean =
        gameRepository.observeFriends().firstData()
            ?.firstOrNull { it.guestId == friendGuestId }?.snapshotRaided == true

    private suspend fun buddyDisplayName(buddyId: String): String =
        buddyNames.getOrPut(buddyId) {
            BuddyCatalog.find(BuddyId(buddyId))?.name.orEmpty()
        }

    private suspend fun maybeFirstHeistCeremony() {
        if (p2pPrefs.firstHeistCelebrated.first()) return
        p2pPrefs.setFirstHeistCelebrated()
        gameRepository.grantCoins(FIRST_HEIST_GIFT_COINS)
        _events.send(RaidVisitEvent.ShowCeremony(friendBaseName))
    }

    private fun wireToReason(wire: String): StealRejectReason? =
        StealRejectReason.entries.firstOrNull { it.wire == wire }

    companion object {
        const val BASE_SLOT_COUNT = 24
        const val MAX_CHAT_MESSAGES = 30
        const val RAID_RESULT_TIMEOUT_MS = 10_000L
        const val FIRST_HEIST_GIFT_COINS = 200L
    }
}
