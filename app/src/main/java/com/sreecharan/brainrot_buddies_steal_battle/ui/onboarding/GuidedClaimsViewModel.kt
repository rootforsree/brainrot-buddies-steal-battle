package com.sreecharan.brainrot_buddies_steal_battle.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sreecharan.brainrot_buddies_steal_battle.data.model.Result
import com.sreecharan.brainrot_buddies_steal_battle.data.repository.GameRepository
import com.sreecharan.brainrot_buddies_steal_battle.game.engine.GameEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Guided claims (§1 hour-1 ladder): claim Frogboot -> claim Pip McSnacks ->
 * buy slot (50 coins) -> claim SPLORCH! -> vault collect -> daily gift day 1.
 *
 * The step is DERIVED from game state (owned buddies, unlocked slots, vault
 * coins, today's gift), so it resumes cleanly if the app is killed
 * mid-tutorial. No extra DataStore keys needed. When the last step completes,
 * tutorial_done is set and the overlay disappears for good.
 *
 * Builder B integration (BaseScreen): inject this ViewModel with
 * hiltViewModel(), collect [step], and place [GuidedClaimsCoachMark] above
 * the relevant UI (conveyor for claim steps, slots row for BUY_SLOT, vault
 * for COLLECT_VAULT, gift widget for OPEN_GIFT). The overlay never blocks
 * input: it is a coach card, not a modal.
 *
 * Assumptions on Builder A: the first three conveyor pulls for a fresh
 * player are scripted as Frogboot, Pip McSnacks, SPLORCH! (in that order),
 * and a new player can afford the 50-coin slot by the BUY_SLOT step.
 */
enum class GuidedStep {
    CLAIM_FROGBOOT,
    CLAIM_PIP,
    BUY_SLOT,
    CLAIM_SPLORCH,
    COLLECT_VAULT,
    OPEN_GIFT,
}

@HiltViewModel
class GuidedClaimsViewModel @Inject constructor(
    private val repository: GameRepository,
    private val engine: GameEngine,
) : ViewModel() {

    /** Steps dismissed with "Later" this session (in-memory only). */
    private val dismissed = MutableStateFlow<Set<GuidedStep>>(emptySet())

    val step: StateFlow<GuidedStep?> field = combine(
        repository.observeOwnedBuddies(),
        repository.observeSlots(),
        repository.observeVault(),
        repository.observeCalendar(),
        repository.observeTutorialDone(),
        dismissed,
    ) { buddiesResult, slotsResult, vaultResult, calendarResult, tutorialResult, dismissedSteps ->
        val tutorialDone = tutorialResult.dataOrNull() == true
        if (tutorialDone) {
            null
        } else {
            deriveStep(
                ownedIds = buddiesResult.dataOrNull().orEmpty().map { it.buddyId }.toSet(),
                unlockedSlots = slotsResult.dataOrNull().orEmpty().count { it.unlocked },
                vaultCoins = vaultResult.dataOrNull()?.coins ?: 0L,
                giftClaimableToday = calendarResult.dataOrNull()?.lastClaimDate != engine.todayKey(),
            )?.takeUnless { it in dismissedSteps }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )

    fun onStepDismissed(step: GuidedStep) {
        dismissed.value = dismissed.value + step
    }

    /** Called when the derived step becomes null: the ladder is complete. */
    fun onLadderComplete() {
        viewModelScope.launch {
            repository.setTutorialDone(true)
        }
    }

    companion object {
        fun deriveStep(
            ownedIds: Set<String>,
            unlockedSlots: Int,
            vaultCoins: Long,
            giftClaimableToday: Boolean,
        ): GuidedStep? = when {
            "frogboot" !in ownedIds -> GuidedStep.CLAIM_FROGBOOT
            "pip_mcsnacks" !in ownedIds -> GuidedStep.CLAIM_PIP
            unlockedSlots < 5 -> GuidedStep.BUY_SLOT
            "splorch" !in ownedIds -> GuidedStep.CLAIM_SPLORCH
            vaultCoins > 0 -> GuidedStep.COLLECT_VAULT
            giftClaimableToday -> GuidedStep.OPEN_GIFT
            else -> null
        }
    }
}

private fun <T> Result<T>.dataOrNull(): T? = (this as? Result.Success<T>)?.data
