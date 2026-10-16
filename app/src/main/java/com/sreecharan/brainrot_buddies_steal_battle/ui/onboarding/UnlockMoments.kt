package com.sreecharan.brainrot_buddies_steal_battle.ui.onboarding

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sreecharan.brainrot_buddies_steal_battle.R
import com.sreecharan.brainrot_buddies_steal_battle.data.model.Result
import com.sreecharan.brainrot_buddies_steal_battle.data.repository.GameRepository
import com.sreecharan.brainrot_buddies_steal_battle.ui.components.BuddyImage
import com.sreecharan.brainrot_buddies_steal_battle.ui.shop.SHIELD_DURATION_MS
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.AppDimens
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.AppTypography
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.LocalAppColors
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Day-based unlock moments (§1 ladder): systems arrive via gift moments, one
 * per day, only when the need is felt. Each moment is a gift-box dialog with
 * a Benny tip (≤8 words, ends in an action).
 *
 * - Day 2 TICKET: Lucky Ticket reveal (the daily gift already grants 1/day;
 *   this moment teaches using it).
 * - Day 3 UPGRADES: Benny points at upgrades.
 * - Day 4 SHIELD: practice raid (zero stakes) + free 8-hour shield granted
 *   on open. The practice raid target itself is Builder D's raid flow.
 * - Day 5 ALBUM: the Album tab appears (gated in AppNavHost).
 * - Day 7 RAID: first real raid window (Builder D).
 *
 * Builder B integration (BaseScreen): inject [UnlockMomentsViewModel],
 * collect [pendingMoment], and show [UnlockMomentDialog] when non-null.
 * onOpen grants the day-4 shield; the other moments are pure reveals.
 * Shown-state is session-scoped: an unopened moment re-appears next launch,
 * which is the desired behavior for kids.
 */
enum class UnlockMoment(
    val day: Int,
    @StringRes val titleRes: Int,
    @StringRes val tipRes: Int,
) {
    TICKET(2, R.string.moment_ticket_title, R.string.moment_ticket_tip),
    UPGRADES(3, R.string.moment_upgrades_title, R.string.moment_upgrades_tip),
    SHIELD(4, R.string.moment_shield_title, R.string.moment_shield_tip),
    ALBUM(5, R.string.moment_album_title, R.string.moment_album_tip),
    RAID(7, R.string.moment_raid_title, R.string.moment_raid_tip),
}

@HiltViewModel
class UnlockMomentsViewModel @Inject constructor(
    private val repository: GameRepository,
) : ViewModel() {

    private val shownThisSession = MutableStateFlow<Set<UnlockMoment>>(emptySet())

    val pendingMoment: StateFlow<UnlockMoment?> field = combine(
        repository.observeOnboardingDay(),
        shownThisSession,
    ) { dayResult, shown ->
        val day = dayResult.dataOrNull() ?: 1
        UnlockMoment.entries.firstOrNull { it.day <= day && it !in shown }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )

    /** Reveal opened: grant the day-4 shield, mark the moment shown. */
    fun onMomentOpened(moment: UnlockMoment) {
        viewModelScope.launch {
            if (moment == UnlockMoment.SHIELD) {
                repository.setBaseShieldUntil(System.currentTimeMillis() + SHIELD_DURATION_MS)
            }
            shownThisSession.value = shownThisSession.value + moment
        }
    }

    fun onMomentDismissed(moment: UnlockMoment) {
        shownThisSession.value = shownThisSession.value + moment
    }
}

private fun <T> Result<T>.dataOrNull(): T? = (this as? Result.Success<T>)?.data

@Composable
fun UnlockMomentDialog(
    moment: UnlockMoment,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = colors.cardSurface),
        ) {
            Column(
                modifier = Modifier.padding(AppDimens.spacing24),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(AppDimens.spacing16),
            ) {
                BuddyImage(
                    imageName = "hero_benny",
                    contentDescription = null,
                    modifier = Modifier.size(88.dp),
                )
                Text(
                    text = stringResource(moment.titleRes),
                    style = AppTypography.title2,
                    color = colors.label,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(moment.tipRes),
                    style = AppTypography.headline,
                    color = colors.accent,
                    textAlign = TextAlign.Center,
                )
                Button(
                    onClick = onOpen,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                ) {
                    Text(
                        text = stringResource(R.string.moment_open),
                        style = AppTypography.headline,
                    )
                }
            }
        }
    }
}

/** Convenience for Builder B: collect + show in one call. */
@Composable
fun UnlockMomentHost(
    viewModel: UnlockMomentsViewModel,
    modifier: Modifier = Modifier,
) {
    val moment by viewModel.pendingMoment.collectAsStateWithLifecycle()
    val current = moment
    if (current != null) {
        UnlockMomentDialog(
            moment = current,
            onOpen = { viewModel.onMomentOpened(current) },
            onDismiss = { viewModel.onMomentDismissed(current) },
            modifier = modifier,
        )
    }
}
