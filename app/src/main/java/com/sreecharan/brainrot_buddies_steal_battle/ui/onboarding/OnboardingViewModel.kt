package com.sreecharan.brainrot_buddies_steal_battle.ui.onboarding

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sreecharan.brainrot_buddies_steal_battle.R
import com.sreecharan.brainrot_buddies_steal_battle.data.model.Result
import com.sreecharan.brainrot_buddies_steal_battle.data.repository.GameRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class OnboardingStep { PLAY, NAMING }

@Immutable
data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.PLAY,
    val baseNames: ImmutableList<String> = persistentListOf(),
    val selectedName: String? = null,
    val isWorking: Boolean = false,
    val failed: Boolean = false,
)

/**
 * First-launch flow (§1): splash-free Play button -> guest ID created on
 * device -> base naming from the 12 frozen presets. Confirming the name
 * calls [GameRepository.initializeNewGame], which creates the starting
 * slots, vault, tickets, shield, and calendar; the NavHost observes the
 * base name and flips into the main tabs. The guided claims continue there
 * via GuidedClaimsViewModel (owned by this package, placed by Builder B).
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val repository: GameRepository,
    @ApplicationContext context: Context,
) : ViewModel() {

    private val mutableState = MutableStateFlow(
        OnboardingUiState(
            baseNames = context.resources
                .getStringArray(R.array.onboarding_base_names)
                .toImmutableList(),
        ),
    )
    val uiState: StateFlow<OnboardingUiState> = mutableState.asStateFlow()

    fun onPlayClicked() {
        if (mutableState.value.isWorking) return
        viewModelScope.launch {
            mutableState.update { it.copy(isWorking = true, failed = false) }
            val ok = repository.ensureGuestId() is Result.Success
            mutableState.update {
                if (ok) {
                    it.copy(step = OnboardingStep.NAMING, isWorking = false)
                } else {
                    it.copy(isWorking = false, failed = true)
                }
            }
        }
    }

    fun onBaseNameSelected(name: String) {
        mutableState.update { it.copy(selectedName = name) }
    }

    fun onStartClicked() {
        val name = mutableState.value.selectedName ?: return
        if (mutableState.value.isWorking) return
        viewModelScope.launch {
            mutableState.update { it.copy(isWorking = true, failed = false) }
            val ok = repository.initializeNewGame(name) is Result.Success
            // On success the NavHost observes baseName and switches to the main tabs.
            mutableState.update { it.copy(isWorking = false, failed = !ok) }
        }
    }

    fun onFailureDismissed() {
        mutableState.update { it.copy(failed = false) }
    }
}
