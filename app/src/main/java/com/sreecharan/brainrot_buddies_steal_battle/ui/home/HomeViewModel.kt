package com.sreecharan.brainrot_buddies_steal_battle.ui.home

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sreecharan.brainrot_buddies_steal_battle.R
import com.sreecharan.brainrot_buddies_steal_battle.core.audio.AudioAssets
import com.sreecharan.brainrot_buddies_steal_battle.core.audio.SoundManager
import com.sreecharan.brainrot_buddies_steal_battle.data.model.BuddyCatalog
import com.sreecharan.brainrot_buddies_steal_battle.data.model.BuddyId
import com.sreecharan.brainrot_buddies_steal_battle.data.model.Rarity
import com.sreecharan.brainrot_buddies_steal_battle.data.model.TicketType
import com.sreecharan.brainrot_buddies_steal_battle.di.IoDispatcher
import com.sreecharan.brainrot_buddies_steal_battle.game.engine.GameEngine
import com.sreecharan.brainrot_buddies_steal_battle.game.engine.PullInput
import com.sreecharan.brainrot_buddies_steal_battle.ui.components.JuiceTimings
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Base screen ViewModel (contract §7 route `Base`, rendered by `BaseScreen`).
 *
 * Owns all presentation state for the game board: the conveyor spawner
 * (8 s cadence, 15 s rides, pity via [GameEngine.resolvePull]), claim
 * acks with the §10 claim sequence, vault collection, the raid-bell
 * widget, tour mode, the mystery-pull ceremony and staged replays.
 * Persisted state and writes go through [BaseGameData]; game rules through
 * [GameEngine]; audio through [SoundManager].
 *
 * Pity counters and the Buddy Bites balance are session state (they have
 * no §5 storage): they reset on process death. The coordinator may want
 * DataStore keys for them.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val gameData: BaseGameData,
    private val engine: GameEngine,
    private val soundManager: SoundManager,
    @ApplicationContext private val appContext: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> field = MutableStateFlow(HomeUiState())

    private val eventsChannel = Channel<HomeEvent>(Channel.BUFFERED)
    val events: Flow<HomeEvent> = eventsChannel.receiveAsFlow()

    private val random = Random.Default
    private var floatUpId = 0L
    private var actionToken = 0L
    private var spawnCounter = 0
    private var pityCommons = 0
    private var pityEpics = 0
    private var coachMarkDismissed = false
    private var lastRaidMode: Boolean? = null
    private var lastEventMode: Boolean? = null
    private var lastTourAdvanceMs = 0L
    private var replayJob: Job? = null
    private var ceremonyJob: Job? = null
    private var tickCount = 0L

    init {
        viewModelScope.launch(ioDispatcher) {
            gameData.snapshot.collect { snapshot -> applySnapshot(snapshot) }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(TICK_MS)
                onTick()
            }
        }
    }

    // ------------------------------------------------------------------
    // Public intents (contract §13: public functions, no sealed Intent)
    // ------------------------------------------------------------------

    /** First tap anywhere on Base: unlocks audio (contract §9) and starts music. */
    fun onFirstInteraction() {
        if (soundManager.isUnlocked()) {
            soundManager.pokeActivity()
            return
        }
        soundManager.unlockAudio()
        soundManager.startMusic()
    }

    fun onConveyorTap(instanceId: String) {
        onFirstInteraction()
        val state = uiState.value
        val item = state.conveyor.firstOrNull { it.id == instanceId } ?: return
        if (item.expiresAtMs <= System.currentTimeMillis()) return

        // <100 ms ack, same frame batch: sound + squash anim + haptic.
        soundManager.playClaimChime()
        eventsChannel.trySend(HomeEvent.Haptic(HapticKind.TAP))
        val startUptime = SystemClock.uptimeMillis()
        val targetSlot = state.slots.firstOrNull { it.unlocked && it.buddy == null }?.index
        uiState.update { current ->
            current.copy(
                conveyor = current.conveyor.filter { it.id != instanceId }.toImmutableList(),
                claimAnims = current.claimAnims + (
                    instanceId to ClaimAnimUi(
                        instanceId = instanceId,
                        startUptimeMs = startUptime,
                        rarity = item.rarity,
                        imageRes = item.imageRes,
                        seed = item.seed,
                        fromLane = item.lane,
                        fromProgress = rideProgress(item, System.currentTimeMillis()),
                        targetSlotIndex = targetSlot,
                    )
                    ),
            )
        }
        coachMarkDismissed = true

        viewModelScope.launch(ioDispatcher) {
            when (val outcome = gameData.placeClaimedBuddy(item.buddyId)) {
                is ClaimPlacement.Placed -> onClaimPlaced(outcome, item)
                ClaimPlacement.BaseFull -> onClaimToBites(item)
            }
        }
    }

    fun onSlotTap(index: Int) {
        onFirstInteraction()
        val slot = uiState.value.slots.getOrNull(index) ?: return
        if (!slot.unlocked) {
            // §11: a locked slot wiggles and shows its price. Never silence.
            soundManager.playButton()
            eventsChannel.trySend(HomeEvent.Haptic(HapticKind.TAP))
            uiState.update { it.copy(wiggleSlot = WiggleUi(index, SystemClock.uptimeMillis())) }
            addFloatUp(
                appContext.getString(R.string.base_float_locked_cost, slot.unlockCost),
                FloatUpAnchor.Slot(index),
            )
            return
        }
        val buddy = slot.buddy ?: return
        // Tapping your own buddy is free play: bounce + catchphrase.
        if (buddy.voiceResName.isNotEmpty()) soundManager.playVoice(buddy.voiceResName)
        uiState.update {
            it.copy(boingAnims = it.boingAnims + (buddy.uid to SystemClock.uptimeMillis()))
        }
    }

    fun onCollectVault() {
        onFirstInteraction()
        viewModelScope.launch(ioDispatcher) {
            val collected = gameData.collectVault()
            if (collected > 0) {
                soundManager.playVaultCollect()
                eventsChannel.trySend(HomeEvent.Haptic(HapticKind.SUCCESS))
                addFloatUp(
                    appContext.getString(R.string.base_float_coins, collected),
                    FloatUpAnchor.Vault,
                )
            } else {
                soundManager.playButton()
            }
        }
    }

    fun onRaidBellTap() {
        onFirstInteraction()
        soundManager.playButton()
        val bell = uiState.value.raidBell
        if (bell.windowActive && bell.raidsLeft > 0) {
            eventsChannel.trySend(HomeEvent.NavigateToFriends)
        } else {
            val remaining = (bell.nextWindowMs - System.currentTimeMillis()).coerceAtLeast(0L)
            addFloatUp(
                appContext.getString(R.string.base_float_next_raid, formatDuration(remaining)),
                FloatUpAnchor.Center,
            )
        }
    }

    fun onTicketTap(type: TicketType) {
        onFirstInteraction()
        val state = uiState.value
        if (state.ceremony != null) return
        val count = if (type == TicketType.LUCKY) state.luckyTickets else state.megaTickets
        if (count <= 0) {
            soundManager.playButton()
            eventsChannel.trySend(HomeEvent.NavigateToShop)
            addFloatUp(appContext.getString(R.string.base_float_no_tickets), FloatUpAnchor.Center)
            return
        }
        val startUptime = SystemClock.uptimeMillis()
        uiState.update {
            it.copy(ceremony = MysteryCeremonyUi(type, CeremonyPhase.DRUMROLL, startUptime, null))
        }
        // Drumroll Please's own catchphrase IS the drumroll.
        soundManager.playVoice(AudioAssets.voiceResName("drumroll_please"))
        ceremonyJob?.cancel()
        ceremonyJob = viewModelScope.launch(ioDispatcher) {
            gameData.consumeTicket(type)
            delay(JuiceTimings.CEREMONY_DRUMROLL_MS)
            val buddy = spawnBuddy(ticket = type)
            uiState.update { current ->
                current.copy(
                    ceremony = current.ceremony?.copy(
                        phase = CeremonyPhase.REVEAL,
                        buddy = buddy,
                        phaseStartUptimeMs = SystemClock.uptimeMillis(),
                    ),
                )
            }
            soundManager.playGiftOpen()
            eventsChannel.trySend(HomeEvent.Haptic(HapticKind.SUCCESS))
            if (buddy.rarity == Rarity.EPIC) {
                addTrauma(EPIC_TRAUMA)
                eventsChannel.trySend(HomeEvent.BigMoment)
            }
        }
    }

    /** Rare+ celebrations are skippable after 1 s (contract §10). */
    fun onCeremonySkip() {
        ceremonyJob?.cancel()
        soundManager.playButton()
        uiState.update { it.copy(ceremony = null) }
    }

    fun onReplaySkip() {
        dismissReplay()
    }

    fun onCoachMarkTap() {
        soundManager.playButton()
        coachMarkDismissed = true
        uiState.update { it.copy(coachMark = null) }
    }

    fun onTourToggle() {
        onFirstInteraction()
        val state = uiState.value
        if (state.tour != null) {
            soundManager.playButton()
            uiState.update { it.copy(tour = null) }
            return
        }
        val first = state.slots.firstOrNull { it.buddy != null } ?: run {
            addFloatUp(appContext.getString(R.string.base_float_claim_first), FloatUpAnchor.Center)
            return
        }
        val buddy = first.buddy ?: return
        soundManager.playButton()
        lastTourAdvanceMs = SystemClock.uptimeMillis()
        uiState.update {
            it.copy(tour = TourModeUi(first.index, buddy.name, buddy.catchphrase))
        }
        if (buddy.voiceResName.isNotEmpty()) soundManager.playVoice(buddy.voiceResName)
    }

    fun onTourCaptionDone() {
        advanceTour(SystemClock.uptimeMillis(), force = true)
    }

    fun onUiEvent(event: HomeUiEvent) {
        when (event) {
            is HomeUiEvent.ConveyorTap -> onConveyorTap(event.instanceId)
            is HomeUiEvent.SlotTap -> onSlotTap(event.index)
            HomeUiEvent.VaultCollect -> onCollectVault()
            HomeUiEvent.RaidBellTap -> onRaidBellTap()
            is HomeUiEvent.TicketTap -> onTicketTap(event.type)
            is HomeUiEvent.CeremonyReveal -> Unit // Handled by the ceremony job; kept for symmetry.
            HomeUiEvent.DismissCelebration -> onCeremonySkip()
            HomeUiEvent.DismissReplay -> onReplaySkip()
            HomeUiEvent.ReplayFinished -> dismissReplay()
            HomeUiEvent.TourToggle -> onTourToggle()
            HomeUiEvent.TourCaptionDone -> onTourCaptionDone()
            HomeUiEvent.FirstTap -> onFirstInteraction()
        }
    }

    // ------------------------------------------------------------------
    // Conveyor spawner (presentation state: rides are never persisted)
    // ------------------------------------------------------------------

    private fun spawnBuddy(ticket: TicketType? = null): ConveyorBuddyUi {
        val now = System.currentTimeMillis()
        val output = engine.resolvePull(
            PullInput(
                hasLuckyTicket = ticket == TicketType.LUCKY,
                hasMegaTicket = ticket == TicketType.MEGA,
                commonsSinceRare = pityCommons,
                pullsSinceEpic = pityEpics,
                saturdayEvent = engine.isZoomRaceActive(),
            ),
            random,
        )
        pityCommons = output.commonsSinceRare
        pityEpics = output.pullsSinceEpic
        val pool = when (output.rarity) {
            Rarity.COMMON -> BuddyCatalog.commons
            Rarity.RARE -> BuddyCatalog.rares
            Rarity.EPIC -> BuddyCatalog.epics
        }
        val def = pool.random(random)
        val buddy = ConveyorBuddyUi(
            id = UUID.randomUUID().toString(),
            buddyId = def.id.value,
            name = def.name,
            rarity = def.rarity,
            imageRes = def.imageRes,
            catchphrase = def.catchphrase,
            voiceResName = def.voiceFile,
            coinsPerSec = def.baseCoinsPerSec,
            seed = random.nextLong(),
            lane = (spawnCounter++) % CONVEYOR_LANES,
            spawnedAtMs = now,
            expiresAtMs = now + JuiceTimings.CONVEYOR_RIDE_MS,
            ticketPull = ticket != null,
        )
        uiState.update { state ->
            state.copy(conveyor = state.conveyor + buddy)
        }
        return buddy
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private fun onClaimPlaced(outcome: ClaimPlacement.Placed, item: ConveyorBuddyUi) {
        val now = SystemClock.uptimeMillis()
        uiState.update { state ->
            state.copy(
                claimAnims = state.claimAnims.mapValues { (id, anim) ->
                    if (id == item.id) anim.copy(targetSlotIndex = outcome.slotIndex) else anim
                }.toImmutableMap(),
                waddleAnims = state.waddleAnims + (outcome.buddy.uid to now),
            )
        }
        addFloatUp(
            appContext.getString(R.string.base_float_cps, outcome.buddy.coinsPerSec),
            FloatUpAnchor.Slot(outcome.slotIndex),
        )
        // Catchphrase lands on the landing frame (250 ms), §10.
        viewModelScope.launch {
            delay(JuiceTimings.CLAIM_SQUASH_MS + JuiceTimings.CLAIM_ARC_MS)
            soundManager.playVoice(item.voiceResName)
        }
        if (item.rarity == Rarity.EPIC) {
            addTrauma(EPIC_TRAUMA)
            eventsChannel.trySend(HomeEvent.BigMoment)
        }
    }

    private fun onClaimToBites(item: ConveyorBuddyUi) {
        uiState.update { it.copy(bites = it.bites + BITES_PER_DUPLICATE) }
        addFloatUp(
            appContext.getString(R.string.base_float_bites, BITES_PER_DUPLICATE),
            FloatUpAnchor.Center,
        )
        eventsChannel.trySend(HomeEvent.Haptic(HapticKind.SUCCESS))
    }

    private fun onTick() {
        val nowUptime = SystemClock.uptimeMillis()
        val nowWall = System.currentTimeMillis()
        tickCount++
        if (nowWall >= uiState.value.nextSpawnMs) {
            spawnBuddy()
            uiState.update { it.copy(nextSpawnMs = nowWall + JuiceTimings.CONVEYOR_CADENCE_MS) }
        }
        if (tickCount % ACCRUE_EVERY_TICKS == 0L) {
            viewModelScope.launch(ioDispatcher) { gameData.accrueVault() }
        }
        uiState.update { state ->
            var next = state.copy(
                nowMs = nowWall,
                conveyor = state.conveyor.filter { it.expiresAtMs > nowWall }.toImmutableList(),
            )
            next = next.copy(
                claimAnims = next.claimAnims.filterValues { anim ->
                    nowUptime - anim.startUptimeMs < JuiceTimings.CLAIM_TOTAL_MS + PRUNE_SLACK_MS
                }.toImmutableMap(),
                waddleAnims = next.waddleAnims.filterValues { start ->
                    nowUptime - start < JuiceTimings.WADDLE_IN_MS + PRUNE_SLACK_MS
                }.toImmutableMap(),
                boingAnims = next.boingAnims.filterValues { start ->
                    nowUptime - start < BOING_MS + PRUNE_SLACK_MS
                }.toImmutableMap(),
                floatUps = next.floatUps.filter { nowUptime - it.startUptimeMs < it.durationMs }
                    .toImmutableList(),
            )
            val wiggle = next.wiggleSlot
            if (wiggle != null && nowUptime - wiggle.startUptimeMs > WIGGLE_MS) {
                next = next.copy(wiggleSlot = null)
            }
            next
        }
        advanceTour(nowUptime)
        syncRaidMode()
    }

    private fun advanceTour(nowUptime: Long, force: Boolean = false) {
        val tour = uiState.value.tour ?: return
        if (!force && nowUptime - lastTourAdvanceMs < TOUR_SPOTLIGHT_MS) return
        lastTourAdvanceMs = nowUptime
        val occupied = uiState.value.slots.filter { it.buddy != null }
        if (occupied.isEmpty()) return
        val currentIdx = occupied.indexOfFirst { it.index == tour.spotlightSlot }.coerceAtLeast(0)
        val next = occupied[(currentIdx + 1) % occupied.size]
        val buddy = next.buddy ?: return
        uiState.update {
            it.copy(tour = TourModeUi(next.index, buddy.name, buddy.catchphrase))
        }
        if (buddy.voiceResName.isNotEmpty()) soundManager.playVoice(buddy.voiceResName)
    }

    private fun syncRaidMode() {
        val active = uiState.value.raidBell.windowActive
        if (lastRaidMode != active) {
            lastRaidMode = active
            soundManager.setRaidMode(active)
            if (active) {
                soundManager.playRaidBell()
            }
        }
        val event = engine.isZoomRaceActive()
        if (lastEventMode != event) {
            lastEventMode = event
            soundManager.setEventMode(event)
        }
    }

    private fun applySnapshot(snapshot: BaseSnapshot) {
        uiState.update { state ->
            state.copy(
                isLoading = false,
                baseName = snapshot.baseName,
                slots = snapshot.slots.toImmutableList(),
                vault = VaultUi(snapshot.vaultCoins, snapshot.vaultCap),
                raidBell = RaidBellUi(
                    windowActive = snapshot.raidWindowActive,
                    raidsLeft = snapshot.raidsLeft,
                    nextWindowMs = snapshot.nextRaidWindowMs,
                ),
                shieldActive = snapshot.shieldActive,
                shieldEndsAtMs = snapshot.shieldEndsAtMs,
                luckyTickets = snapshot.luckyTickets,
                megaTickets = snapshot.megaTickets,
                coachMark = if (coachMarkDismissed) null else snapshot.coachMark ?: state.coachMark,
            )
        }
        // Builder D's staged replays: a new pending raid record plays the 5 s
        // cinematic, then gets acknowledged.
        val pending = snapshot.pendingReplay
        if (pending != null && uiState.value.replay?.id != pending.id && replayJob?.isActive != true) {
            startReplay(pending)
        }
    }

    private fun startReplay(replay: StagedReplayUi) {
        val startUptime = SystemClock.uptimeMillis()
        uiState.update { it.copy(replay = replay.copy(startUptimeMs = startUptime)) }
        soundManager.playStealSuccess()
        replayJob?.cancel()
        replayJob = viewModelScope.launch {
            // The catchphrase sting lands on the victim-reaction beat (~3 s).
            delay(REPLAY_CATCHPHRASE_AT_MS)
            val voice = BuddyCatalog.find(BuddyId(replay.buddyId))?.voiceFile.orEmpty()
            if (voice.isNotEmpty()) soundManager.playVoice(voice)
            delay(JuiceTimings.REPLAY_MS - REPLAY_CATCHPHRASE_AT_MS)
            dismissReplay()
            eventsChannel.trySend(HomeEvent.BigMoment)
        }
    }

    private fun dismissReplay() {
        val replay = uiState.value.replay ?: return
        replayJob?.cancel()
        uiState.update { it.copy(replay = null) }
        viewModelScope.launch(ioDispatcher) {
            gameData.acknowledgeReplay(replay.id)
        }
    }

    private fun addFloatUp(text: String, anchor: FloatUpAnchor) {
        val duration = JuiceTimings.FLOAT_UP_MIN_MS +
            (floatUpId % (JuiceTimings.FLOAT_UP_MAX_MS - JuiceTimings.FLOAT_UP_MIN_MS))
        uiState.update { state ->
            state.copy(
                floatUps = state.floatUps + FloatUpUi(
                    id = ++floatUpId,
                    text = text,
                    anchor = anchor,
                    startUptimeMs = SystemClock.uptimeMillis(),
                    durationMs = duration,
                ),
            )
        }
    }

    private fun addTrauma(amount: Float) {
        uiState.update { it.copy(trauma = TraumaUi(amount, ++actionToken)) }
    }

    /** Ride progress 0..1 at [wallMs], so the claim arc starts where the buddy was tapped. */
    private fun rideProgress(item: ConveyorBuddyUi, wallMs: Long): Float =
        ((wallMs - item.spawnedAtMs).toFloat() / JuiceTimings.CONVEYOR_RIDE_MS).coerceIn(0f, 1f)

    private fun formatDuration(ms: Long): String {
        val totalSeconds = ms / MILLIS_PER_SECOND
        val hours = totalSeconds / SECONDS_PER_HOUR
        val minutes = (totalSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    companion object {
        private const val TICK_MS = 500L
        private const val PRUNE_SLACK_MS = 200L
        private const val BOING_MS = 450L
        private const val WIGGLE_MS = 450L
        private const val TOUR_SPOTLIGHT_MS = 3000L
        private const val EPIC_TRAUMA = 0.45f
        private const val CONVEYOR_LANES = 3
        private const val MILLIS_PER_SECOND = 1000L
        private const val SECONDS_PER_MINUTE = 60L
        private const val SECONDS_PER_HOUR = 3600L
        /** Vault accrual runs every 5 s (every 10th 500 ms tick). */
        private const val ACCRUE_EVERY_TICKS = 10L
        /** Contract §1: duplicates convert to 5 Buddy Bites each. */
        private const val BITES_PER_DUPLICATE = 5
        /** The catchphrase sting in the staged replay, contract §2 V1.1. */
        private const val REPLAY_CATCHPHRASE_AT_MS = 3000L
    }
}
