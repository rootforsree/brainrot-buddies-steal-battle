package com.sreecharan.brainrot_buddies_steal_battle.navigation

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sreecharan.brainrot_buddies_steal_battle.data.model.Result
import com.sreecharan.brainrot_buddies_steal_battle.data.repository.GameRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Decides which top-level flow the NavHost shows:
 * - no base name yet -> first-launch onboarding (Play -> naming)
 * - base name set -> the five-tab main app (Album tab appears on day 5+)
 */
@Immutable
data class NavGateUiState(
    val loading: Boolean = true,
    val hasBaseName: Boolean = false,
    val onboardingDay: Int = 1,
    val tutorialDone: Boolean = false,
)

@HiltViewModel
class NavGateViewModel
    @Inject
    constructor(
        repository: GameRepository,
    ) : ViewModel() {
        val uiState: StateFlow<NavGateUiState> =
            combine(
                repository.observeBaseName(),
                repository.observeOnboardingDay(),
                repository.observeTutorialDone(),
            ) { baseNameResult, dayResult, tutorialResult ->
                NavGateUiState(
                    loading = false,
                    hasBaseName = baseNameResult.dataOrNull().orEmpty().isNotBlank(),
                    onboardingDay = (dayResult.dataOrNull() ?: 1).coerceAtLeast(1),
                    tutorialDone = tutorialResult.dataOrNull() == true,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = NavGateUiState(),
            )
    }

private fun <T> Result<T>.dataOrNull(): T? = (this as? Result.Success<T>)?.data
