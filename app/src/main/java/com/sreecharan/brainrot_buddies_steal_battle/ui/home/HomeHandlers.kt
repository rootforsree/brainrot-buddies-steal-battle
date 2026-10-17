package com.sreecharan.brainrot_buddies_steal_battle.ui.home

import android.content.Context
import android.os.SystemClock
import com.sreecharan.brainrot_buddies_steal_battle.R
import com.sreecharan.brainrot_buddies_steal_battle.core.audio.AudioAssets
import com.sreecharan.brainrot_buddies_steal_battle.core.audio.SoundManager
import com.sreecharan.brainrot_buddies_steal_battle.data.model.BuddyCatalog
import com.sreecharan.brainrot_buddies_steal_battle.data.model.BuddyId
import com.sreecharan.brainrot_buddies_steal_battle.data.model.Rarity
import com.sreecharan.brainrot_buddies_steal_battle.data.model.TicketType
import com.sreecharan.brainrot_buddies_steal_battle.game.engine.GameEngine
import com.sreecharan.brainrot_buddies_steal_battle.game.engine.PullInput
import com.sreecharan.brainrot_buddies_steal_battle.ui.components.JuiceTimings
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.random.Random

/** Timing and tuning constants shared by the Home handlers. */
internal object HomeTimings {
    const val TICK_MS = 500L
    const val PRUNE_SLACK_MS = 200L
    const val BOING_MS = 450L
    const val WIGGLE_MS = 450L
    const val TOUR_SPOTLIGHT_MS = 3000L
    const val EPIC_TRAUMA = 0.45f
    const val CONVEYOR_LANES = 3
    const val MILLIS_PER_SECOND = 1000L
    const val SECONDS_PER_MINUTE = 60L
    const val SECONDS_PER_HOUR = 3600L

    /** Vault accrual runs every 5 s (every 10th 500 ms tick). */
    const val ACCRUE_EVERY_TICKS = 10L

    /** Contract §1: duplicates convert to 5 Buddy Bites each. */
    const val BITES_PER_DUPLICATE = 5

    /** The catchphrase sting in the staged replay, contract §2 V1.1. */
    const val REPLAY_CATCHPHRASE_AT_MS = 3000L
}

/**
 * Shared mutable state behind [HomeViewModel]'s intent handlers: the
 * dependencies, the UI state flow, the event channel and the session-only
 * counters (pity, float-up ids, tour/replay jobs). The handlers below are
 * thin intent groups over this core.
 */
internal class HomeVmCore(
    val gameData: BaseGameData,
    val engine: GameEngine,
    val soundManager: SoundManager,
    val appContext: Context,
    val ioDispatcher: CoroutineDispatcher,
    val scope: CoroutineScope,
) {
    val uiState = MutableStateFlow(HomeUiState())

    private val eventsChannel = Channel<HomeEvent>(Channel.BUFFERED)
    val events: Flow<HomeEvent> = eventsChannel.receiveAsFlow()

    val random: Random = Random.Default
    var floatUpId = 0L
    var actionToken = 0L
    var spawnCounter = 0
    var pityCommons = 0
    var pityEpics = 0
    var coachMarkDismissed = false
    var lastRaidMode: Boolean? = null
    var lastEventMode: Boolean? = null
    var lastTourAdvanceMs = 0L
    var replayJob: Job? = null
    var ceremonyJob: Job? = null
    var tickCount = 0L

    fun trySendEvent(event: HomeEvent) {
        eventsChannel.trySend(event)
    }

    /** First tap anywhere on Base: unlocks audio (contract §9) and starts music. */
    fun onFirstInteraction() {
        if (soundManager.isUnlocked()) {
            soundManager.pokeActivity()
            return
        }
        soundManager.unlockAudio()
        soundManager.startMusic()
    }

    fun addFloatUp(
        text: String,
        anchor: FloatUpAnchor,
    ) {
        val duration =
            JuiceTimings.FLOAT_UP_MIN_MS +
                (floatUpId % (JuiceTimings.FLOAT_UP_MAX_MS - JuiceTimings.FLOAT_UP_MIN_MS))
        uiState.update { state ->
            state.copy(
                floatUps =
                    (
                        state.floatUps +
                            FloatUpUi(
                                id = ++floatUpId,
                                text = text,
                                anchor = anchor,
                                startUptimeMs = SystemClock.uptimeMillis(),
                                durationMs = duration,
                            )
                    ).toImmutableList(),
            )
        }
    }

    fun addTrauma(amount: Float) {
        uiState.update { it.copy(trauma = TraumaUi(amount, ++actionToken)) }
    }

    fun formatDuration(ms: Long): String {
        val totalSeconds = ms / HomeTimings.MILLIS_PER_SECOND
        val hours = totalSeconds / HomeTimings.SECONDS_PER_HOUR
        val minutes = (totalSeconds % HomeTimings.SECONDS_PER_HOUR) / HomeTimings.SECONDS_PER_MINUTE
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }
}

/** Conveyor, ticket ceremony and claim intents. */
internal class ConveyorHandler(
    private val core: HomeVmCore,
) {
    fun onConveyorTap(instanceId: String) {
        core.onFirstInteraction()
        val state = core.uiState.value
        val item = state.conveyor.firstOrNull { it.id == instanceId } ?: return
        if (item.expiresAtMs <= System.currentTimeMillis()) return

        // <100 ms ack, same frame batch: sound + squash anim + haptic.
        core.soundManager.playClaimChime()
        core.trySendEvent(HomeEvent.Haptic(HapticKind.TAP))
        val startUptime = SystemClock.uptimeMillis()
        val targetSlot = state.slots.firstOrNull { it.unlocked && it.buddy == null }?.index
        core.uiState.update { current ->
            current.copy(
                conveyor = current.conveyor.filter { it.id != instanceId }.toImmutableList(),
                claimAnims =
                    (
                        current.claimAnims + (
                            instanceId to
                                ClaimAnimUi(
                                    instanceId = instanceId,
                                    startUptimeMs = startUptime,
                                    rarity = item.rarity,
                                    buddyId = item.buddyId,
                                    imageRes = item.imageRes,
                                    seed = item.seed,
                                    fromLane = item.lane,
                                    fromProgress = rideProgress(item, System.currentTimeMillis()),
                                    targetSlotIndex = targetSlot,
                                )
                        )
                    ).toImmutableMap(),
            )
        }
        core.coachMarkDismissed = true

        core.scope.launch(core.ioDispatcher) {
            when (val outcome = core.gameData.placeClaimedBuddy(item.buddyId)) {
                is ClaimPlacement.Placed -> onClaimPlaced(outcome, item)
                ClaimPlacement.BaseFull -> onClaimToBites()
            }
        }
    }

    fun onTicketTap(type: TicketType) {
        core.onFirstInteraction()
        val state = core.uiState.value
        if (state.ceremony != null) return
        val count = if (type == TicketType.LUCKY) state.luckyTickets else state.megaTickets
        if (count <= 0) {
            core.soundManager.playButton()
            core.trySendEvent(HomeEvent.NavigateToShop)
            core.addFloatUp(
                core.appContext.getString(R.string.base_float_no_tickets),
                FloatUpAnchor.Center,
            )
            return
        }
        val startUptime = SystemClock.uptimeMillis()
        core.uiState.update {
            it.copy(ceremony = MysteryCeremonyUi(type, CeremonyPhase.DRUMROLL, startUptime, null))
        }
        // Drumroll Please's own catchphrase IS the drumroll.
        core.soundManager.playVoice(AudioAssets.voiceResName("drumroll_please"))
        core.ceremonyJob?.cancel()
        core.ceremonyJob =
            core.scope.launch(core.ioDispatcher) {
                core.gameData.consumeTicket(type)
                delay(JuiceTimings.CEREMONY_DRUMROLL_MS)
                val buddy = spawnBuddy(ticket = type)
                core.uiState.update { current ->
                    current.copy(
                        ceremony =
                            current.ceremony?.copy(
                                phase = CeremonyPhase.REVEAL,
                                buddy = buddy,
                                phaseStartUptimeMs = SystemClock.uptimeMillis(),
                            ),
                    )
                }
                core.soundManager.playGiftOpen()
                core.trySendEvent(HomeEvent.Haptic(HapticKind.SUCCESS))
                if (buddy.rarity == Rarity.EPIC) {
                    core.addTrauma(HomeTimings.EPIC_TRAUMA)
                    core.trySendEvent(HomeEvent.BigMoment)
                }
            }
    }

    /** Rare+ celebrations are skippable after 1 s (contract §10). */
    fun onCeremonySkip() {
        core.ceremonyJob?.cancel()
        core.soundManager.playButton()
        core.uiState.update { it.copy(ceremony = null) }
    }

    fun spawnBuddy(ticket: TicketType? = null): ConveyorBuddyUi {
        val now = System.currentTimeMillis()
        val output =
            core.engine.resolvePull(
                PullInput(
                    hasLuckyTicket = ticket == TicketType.LUCKY,
                    hasMegaTicket = ticket == TicketType.MEGA,
                    commonsSinceRare = core.pityCommons,
                    pullsSinceEpic = core.pityEpics,
                    saturdayEvent = core.engine.isZoomRaceActive(),
                ),
                core.random,
            )
        core.pityCommons = output.commonsSinceRare
        core.pityEpics = output.pullsSinceEpic
        val pool =
            when (output.rarity) {
                Rarity.COMMON -> BuddyCatalog.commons
                Rarity.RARE -> BuddyCatalog.rares
                Rarity.EPIC -> BuddyCatalog.epics
            }
        val def = pool.random(core.random)
        val buddy =
            ConveyorBuddyUi(
                id = UUID.randomUUID().toString(),
                buddyId = def.id.value,
                name = def.name,
                rarity = def.rarity,
                imageRes = def.imageRes,
                catchphrase = def.catchphrase,
                voiceResName = def.voiceFile,
                coinsPerSec = def.baseCoinsPerSec,
                seed = core.random.nextLong(),
                lane = (core.spawnCounter++) % HomeTimings.CONVEYOR_LANES,
                spawnedAtMs = now,
                expiresAtMs = now + JuiceTimings.CONVEYOR_RIDE_MS,
                ticketPull = ticket != null,
            )
        core.uiState.update { state ->
            state.copy(conveyor = (state.conveyor + buddy).toImmutableList())
        }
        return buddy
    }

    private fun onClaimPlaced(
        outcome: ClaimPlacement.Placed,
        item: ConveyorBuddyUi,
    ) {
        val now = SystemClock.uptimeMillis()
        core.uiState.update { state ->
            state.copy(
                claimAnims =
                    state.claimAnims
                        .mapValues { (id, anim) ->
                            if (id == item.id) anim.copy(targetSlotIndex = outcome.slotIndex) else anim
                        }.toImmutableMap(),
                waddleAnims = (state.waddleAnims + (outcome.buddy.uid to now)).toImmutableMap(),
            )
        }
        core.addFloatUp(
            core.appContext.getString(R.string.base_float_cps, outcome.buddy.coinsPerSec),
            FloatUpAnchor.Slot(outcome.slotIndex),
        )
        // Catchphrase lands on the landing frame (250 ms), §10.
        core.scope.launch {
            delay(JuiceTimings.CLAIM_SQUASH_MS + JuiceTimings.CLAIM_ARC_MS)
            core.soundManager.playVoice(item.voiceResName)
        }
        if (item.rarity == Rarity.EPIC) {
            core.addTrauma(HomeTimings.EPIC_TRAUMA)
            core.trySendEvent(HomeEvent.BigMoment)
        }
    }

    private fun onClaimToBites() {
        core.uiState.update { it.copy(bites = it.bites + HomeTimings.BITES_PER_DUPLICATE) }
        core.addFloatUp(
            core.appContext.getString(R.string.base_float_bites, HomeTimings.BITES_PER_DUPLICATE),
            FloatUpAnchor.Center,
        )
        core.trySendEvent(HomeEvent.Haptic(HapticKind.SUCCESS))
    }

    /** Ride progress 0..1 at [wallMs], so the claim arc starts where the buddy was tapped. */
    private fun rideProgress(
        item: ConveyorBuddyUi,
        wallMs: Long,
    ): Float = ((wallMs - item.spawnedAtMs).toFloat() / JuiceTimings.CONVEYOR_RIDE_MS).coerceIn(0f, 1f)
}

/** Board intents: slots, vault, raid bell, coach mark. */
internal class BoardHandler(
    private val core: HomeVmCore,
) {
    fun onSlotTap(index: Int) {
        core.onFirstInteraction()
        val slot =
            core.uiState.value.slots
                .getOrNull(index) ?: return
        when {
            !slot.unlocked -> wiggleLockedSlot(slot)
            slot.buddy != null -> bounceBuddy(slot.buddy)
        }
    }

    fun onCollectVault() {
        core.onFirstInteraction()
        core.scope.launch(core.ioDispatcher) {
            val collected = core.gameData.collectVault()
            if (collected > 0) {
                core.soundManager.playVaultCollect()
                core.trySendEvent(HomeEvent.Haptic(HapticKind.SUCCESS))
                core.addFloatUp(
                    core.appContext.getString(R.string.base_float_coins, collected),
                    FloatUpAnchor.Vault,
                )
            } else {
                core.soundManager.playButton()
            }
        }
    }

    fun onRaidBellTap() {
        core.onFirstInteraction()
        core.soundManager.playButton()
        val bell = core.uiState.value.raidBell
        if (bell.windowActive && bell.raidsLeft > 0) {
            core.trySendEvent(HomeEvent.NavigateToFriends)
        } else {
            val remaining = (bell.nextWindowMs - System.currentTimeMillis()).coerceAtLeast(0L)
            core.addFloatUp(
                core.appContext.getString(R.string.base_float_next_raid, core.formatDuration(remaining)),
                FloatUpAnchor.Center,
            )
        }
    }

    fun onCoachMarkTap() {
        core.soundManager.playButton()
        core.coachMarkDismissed = true
        core.uiState.update { it.copy(coachMark = null) }
    }

    private fun wiggleLockedSlot(slot: SlotUi) {
        // §11: a locked slot wiggles and shows its price. Never silence.
        core.soundManager.playButton()
        core.trySendEvent(HomeEvent.Haptic(HapticKind.TAP))
        core.uiState.update {
            it.copy(wiggleSlot = WiggleUi(slot.index, SystemClock.uptimeMillis()))
        }
        core.addFloatUp(
            core.appContext.getString(R.string.base_float_locked_cost, slot.unlockCost),
            FloatUpAnchor.Slot(slot.index),
        )
    }

    private fun bounceBuddy(buddy: SlottedBuddyUi) {
        // Tapping your own buddy is free play: bounce + catchphrase.
        if (buddy.voiceResName.isNotEmpty()) core.soundManager.playVoice(buddy.voiceResName)
        core.uiState.update {
            it.copy(boingAnims = (it.boingAnims + (buddy.uid to SystemClock.uptimeMillis())).toImmutableMap())
        }
    }
}

/** Tour mode and staged-replay intents. */
internal class TourHandler(
    private val core: HomeVmCore,
) {
    fun onTourToggle() {
        core.onFirstInteraction()
        val state = core.uiState.value
        if (state.tour != null) {
            endTour()
        } else {
            startTour(state)
        }
    }

    fun onTourCaptionDone() {
        advanceTour(SystemClock.uptimeMillis(), force = true)
    }

    fun advanceTour(
        nowUptime: Long,
        force: Boolean = false,
    ) {
        val tour = core.uiState.value.tour ?: return
        if (force || nowUptime - core.lastTourAdvanceMs >= HomeTimings.TOUR_SPOTLIGHT_MS) {
            advanceTourToNext(tour, nowUptime)
        }
    }

    private fun endTour() {
        core.soundManager.playButton()
        core.uiState.update { it.copy(tour = null) }
    }

    private fun startTour(state: HomeUiState) {
        val first = state.slots.firstOrNull { it.buddy != null }
        val buddy = first?.buddy
        if (buddy == null) {
            core.addFloatUp(
                core.appContext.getString(R.string.base_float_claim_first),
                FloatUpAnchor.Center,
            )
            return
        }
        core.soundManager.playButton()
        core.lastTourAdvanceMs = SystemClock.uptimeMillis()
        core.uiState.update {
            it.copy(tour = TourModeUi(first.index, buddy.name, buddy.catchphrase))
        }
        if (buddy.voiceResName.isNotEmpty()) core.soundManager.playVoice(buddy.voiceResName)
    }

    private fun advanceTourToNext(
        tour: TourModeUi,
        nowUptime: Long,
    ) {
        core.lastTourAdvanceMs = nowUptime
        val occupied =
            core.uiState.value.slots
                .filter { it.buddy != null }
        if (occupied.isEmpty()) return
        val currentIdx = occupied.indexOfFirst { it.index == tour.spotlightSlot }.coerceAtLeast(0)
        val next = occupied[(currentIdx + 1) % occupied.size]
        val buddy = next.buddy ?: return
        core.uiState.update {
            it.copy(tour = TourModeUi(next.index, buddy.name, buddy.catchphrase))
        }
        if (buddy.voiceResName.isNotEmpty()) core.soundManager.playVoice(buddy.voiceResName)
    }
}

/** Staged raid replays (contract §2 V1.1). */
internal class ReplayHandler(
    private val core: HomeVmCore,
) {
    fun onReplaySkip() {
        dismissReplay()
    }

    fun startReplay(replay: StagedReplayUi) {
        val startUptime = SystemClock.uptimeMillis()
        core.uiState.update { it.copy(replay = replay.copy(startUptimeMs = startUptime)) }
        core.soundManager.playStealSuccess()
        core.replayJob?.cancel()
        core.replayJob =
            core.scope.launch {
                // The catchphrase sting lands on the victim-reaction beat (~3 s).
                delay(HomeTimings.REPLAY_CATCHPHRASE_AT_MS)
                val voice = BuddyCatalog.find(BuddyId(replay.buddyId))?.voiceFile.orEmpty()
                if (voice.isNotEmpty()) core.soundManager.playVoice(voice)
                delay(JuiceTimings.REPLAY_MS - HomeTimings.REPLAY_CATCHPHRASE_AT_MS)
                dismissReplay()
                core.trySendEvent(HomeEvent.BigMoment)
            }
    }

    fun dismissReplay() {
        val replay = core.uiState.value.replay ?: return
        core.replayJob?.cancel()
        core.uiState.update { it.copy(replay = null) }
        core.scope.launch(core.ioDispatcher) {
            core.gameData.acknowledgeReplay(replay.id)
        }
    }
}

/** Tick loop, snapshot application and raid/event mode sync. */
internal class HomeSync(
    private val core: HomeVmCore,
    private val conveyor: ConveyorHandler,
    private val tour: TourHandler,
    private val replay: ReplayHandler,
) {
    fun onTick() {
        val nowUptime = SystemClock.uptimeMillis()
        val nowWall = System.currentTimeMillis()
        core.tickCount++
        if (nowWall >= core.uiState.value.nextSpawnMs) {
            conveyor.spawnBuddy()
            core.uiState.update { it.copy(nextSpawnMs = nowWall + JuiceTimings.CONVEYOR_CADENCE_MS) }
        }
        if (core.tickCount % HomeTimings.ACCRUE_EVERY_TICKS == 0L) {
            core.scope.launch(core.ioDispatcher) { core.gameData.accrueVault() }
        }
        pruneAnims(nowUptime, nowWall)
        tour.advanceTour(nowUptime)
        syncRaidMode()
    }

    fun applySnapshot(snapshot: BaseSnapshot) {
        core.uiState.update { state ->
            state.copy(
                isLoading = false,
                baseName = snapshot.baseName,
                slots = snapshot.slots.toImmutableList(),
                vault = VaultUi(snapshot.vaultCoins, snapshot.vaultCap),
                raidBell =
                    RaidBellUi(
                        windowActive = snapshot.raidWindowActive,
                        raidsLeft = snapshot.raidsLeft,
                        nextWindowMs = snapshot.nextRaidWindowMs,
                    ),
                shieldActive = snapshot.shieldActive,
                shieldEndsAtMs = snapshot.shieldEndsAtMs,
                luckyTickets = snapshot.luckyTickets,
                megaTickets = snapshot.megaTickets,
                coachMark = if (core.coachMarkDismissed) null else snapshot.coachMark ?: state.coachMark,
            )
        }
        // Builder D's staged replays: a new pending raid record plays the 5 s
        // cinematic, then gets acknowledged.
        val pending = snapshot.pendingReplay
        if (pending != null && core.uiState.value.replay
                ?.id != pending.id && core.replayJob?.isActive != true
        ) {
            replay.startReplay(pending)
        }
    }

    private fun pruneAnims(
        nowUptime: Long,
        nowWall: Long,
    ) {
        core.uiState.update { state ->
            var next =
                state.copy(
                    nowMs = nowWall,
                    conveyor = state.conveyor.filter { it.expiresAtMs > nowWall }.toImmutableList(),
                )
            next =
                next.copy(
                    claimAnims =
                        next.claimAnims
                            .filterValues { anim ->
                                nowUptime - anim.startUptimeMs <
                                    JuiceTimings.CLAIM_TOTAL_MS + HomeTimings.PRUNE_SLACK_MS
                            }.toImmutableMap(),
                    waddleAnims =
                        next.waddleAnims
                            .filterValues { start ->
                                nowUptime - start < JuiceTimings.WADDLE_IN_MS + HomeTimings.PRUNE_SLACK_MS
                            }.toImmutableMap(),
                    boingAnims =
                        next.boingAnims
                            .filterValues { start ->
                                nowUptime - start < HomeTimings.BOING_MS + HomeTimings.PRUNE_SLACK_MS
                            }.toImmutableMap(),
                    floatUps =
                        next.floatUps
                            .filter { nowUptime - it.startUptimeMs < it.durationMs }
                            .toImmutableList(),
                )
            val wiggle = next.wiggleSlot
            if (wiggle != null && nowUptime - wiggle.startUptimeMs > HomeTimings.WIGGLE_MS) {
                next = next.copy(wiggleSlot = null)
            }
            next
        }
    }

    private fun syncRaidMode() {
        val active = core.uiState.value.raidBell.windowActive
        if (core.lastRaidMode != active) {
            core.lastRaidMode = active
            core.soundManager.setRaidMode(active)
            if (active) {
                core.soundManager.playRaidBell()
            }
        }
        val event = core.engine.isZoomRaceActive()
        if (core.lastEventMode != event) {
            core.lastEventMode = event
            core.soundManager.setEventMode(event)
        }
    }
}
