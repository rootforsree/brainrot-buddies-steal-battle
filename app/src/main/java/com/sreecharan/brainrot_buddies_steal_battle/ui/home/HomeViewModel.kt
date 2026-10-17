package com.sreecharan.brainrot_buddies_steal_battle.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sreecharan.brainrot_buddies_steal_battle.core.audio.SoundManager
import com.sreecharan.brainrot_buddies_steal_battle.data.model.TicketType
import com.sreecharan.brainrot_buddies_steal_battle.di.IoDispatcher
import com.sreecharan.brainrot_buddies_steal_battle.game.engine.GameEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
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
 *
 * The intent logic lives in the handler classes in HomeHandlers.kt; this
 * class only wires them together so the public intent surface stays exactly
 * what the UI and tests call.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    gameData: BaseGameData,
    engine: GameEngine,
    soundManager: SoundManager,
    @ApplicationContext appContext: Context,
    @IoDispatcher ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val core = HomeVmCore(
        gameData = gameData,
        engine = engine,
        soundManager = soundManager,
        appContext = appContext,
        ioDispatcher = ioDispatcher,
        scope = viewModelScope,
    )
    private val conveyor = ConveyorHandler(core)
    private val board = BoardHandler(core)
    private val tour = TourHandler(core)
    private val replay = ReplayHandler(core)
    private val sync = HomeSync(core, conveyor, tour, replay)

    val uiState: StateFlow<HomeUiState> = core.uiState
    val events: Flow<HomeEvent> = core.events

    init {
        viewModelScope.launch(ioDispatcher) {
            gameData.snapshot.collect { snapshot -> sync.applySnapshot(snapshot) }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(HomeTimings.TICK_MS)
                sync.onTick()
            }
        }
    }

    // ------------------------------------------------------------------
    // Public intents (contract §13: public functions, no sealed Intent)
    // ------------------------------------------------------------------

    /** First tap anywhere on Base: unlocks audio (contract §9) and starts music. */
    fun onFirstInteraction() = core.onFirstInteraction()

    fun onConveyorTap(instanceId: String) = conveyor.onConveyorTap(instanceId)

    fun onSlotTap(index: Int) = board.onSlotTap(index)

    fun onCollectVault() = board.onCollectVault()

    fun onTicketTap(type: TicketType) = conveyor.onTicketTap(type)

    /** Rare+ celebrations are skippable after 1 s (contract §10). */
    fun onCeremonySkip() = conveyor.onCeremonySkip()

    fun onTourToggle() = tour.onTourToggle()

    fun onUiEvent(event: HomeUiEvent) {
        when (event) {
            is HomeUiEvent.ConveyorTap -> onConveyorTap(event.instanceId)
            is HomeUiEvent.SlotTap -> onSlotTap(event.index)
            HomeUiEvent.VaultCollect -> onCollectVault()
            HomeUiEvent.RaidBellTap -> board.onRaidBellTap()
            is HomeUiEvent.TicketTap -> onTicketTap(event.type)
            is HomeUiEvent.CeremonyReveal -> Unit // Handled by the ceremony job; kept for symmetry.
            HomeUiEvent.DismissCelebration -> onCeremonySkip()
            HomeUiEvent.DismissReplay -> replay.onReplaySkip()
            HomeUiEvent.ReplayFinished -> replay.dismissReplay()
            HomeUiEvent.TourToggle -> onTourToggle()
            HomeUiEvent.TourCaptionDone -> tour.onTourCaptionDone()
            HomeUiEvent.FirstTap -> onFirstInteraction()
        }
    }
}
