package com.sreecharan.brainrot_buddies_steal_battle.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sreecharan.brainrot_buddies_steal_battle.R
import com.sreecharan.brainrot_buddies_steal_battle.ui.components.BuddyImage
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.AppDimens
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.AppTypography
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.LocalAppColors

/**
 * First-launch flow entry (shown by AppNavHost while no base name is set).
 * No splash stall: straight to a giant Play button.
 */
@Composable
fun OnboardingFlow(
    viewModel: OnboardingViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    OnboardingScreenContent(
        state = state,
        onPlay = viewModel::onPlayClicked,
        onNameSelected = viewModel::onBaseNameSelected,
        onStart = viewModel::onStartClicked,
        onFailureDismissed = viewModel::onFailureDismissed,
        modifier = modifier,
    )
}

@Composable
fun OnboardingScreenContent(
    state: OnboardingUiState,
    onPlay: () -> Unit,
    onNameSelected: (String) -> Unit,
    onStart: () -> Unit,
    onFailureDismissed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.groupedBackground)
            .padding(AppDimens.spacing24),
        contentAlignment = Alignment.Center,
    ) {
        when (state.step) {
            OnboardingStep.PLAY -> PlayStep(
                isWorking = state.isWorking,
                onPlay = onPlay,
            )
            OnboardingStep.NAMING -> NamingStep(
                state = state,
                onNameSelected = onNameSelected,
                onStart = onStart,
            )
        }
        if (state.failed) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .clickable(onClick = onFailureDismissed),
                colors = CardDefaults.cardColors(
                    containerColor = colors.destructive.copy(alpha = 0.15f),
                ),
            ) {
                Text(
                    text = stringResource(R.string.shop_error),
                    style = AppTypography.body,
                    color = colors.destructive,
                    modifier = Modifier.padding(AppDimens.spacing16),
                )
            }
        }
    }
}

@Composable
private fun PlayStep(isWorking: Boolean, onPlay: () -> Unit) {
    val colors = LocalAppColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppDimens.spacing24),
    ) {
        BuddyImage(
            imageName = "hero_benny",
            contentDescription = null,
            modifier = Modifier.size(160.dp),
        )
        Text(
            text = stringResource(R.string.app_name),
            style = AppTypography.largeTitle,
            color = colors.label,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.onboarding_play_sub),
            style = AppTypography.body,
            color = colors.secondaryLabel,
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = onPlay,
            enabled = !isWorking,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
            contentPadding = PaddingValues(vertical = AppDimens.spacing16),
        ) {
            if (isWorking) {
                CircularProgressIndicator(color = colors.accent)
            } else {
                Text(
                    text = stringResource(R.string.onboarding_play),
                    style = AppTypography.title2,
                )
            }
        }
    }
}

@Composable
private fun NamingStep(
    state: OnboardingUiState,
    onNameSelected: (String) -> Unit,
    onStart: () -> Unit,
) {
    val colors = LocalAppColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppDimens.spacing16),
        modifier = Modifier.fillMaxSize(),
    ) {
        BuddyImage(
            imageName = "hero_benny",
            contentDescription = null,
            modifier = Modifier.size(96.dp),
        )
        Text(
            text = stringResource(R.string.onboarding_name_title),
            style = AppTypography.title2,
            color = colors.label,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.onboarding_name_sub),
            style = AppTypography.body,
            color = colors.secondaryLabel,
            textAlign = TextAlign.Center,
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(AppDimens.spacing8),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.spacing8),
        ) {
            items(state.baseNames, key = { it }) { name ->
                val selected = state.selectedName == name
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(role = Role.RadioButton, onClick = { onNameSelected(name) }),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) {
                            colors.accent
                        } else {
                            colors.cardSurface
                        },
                    ),
                ) {
                    Text(
                        text = name,
                        style = AppTypography.headline,
                        color = if (selected) {
                            Color.White
                        } else {
                            colors.label
                        },
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                vertical = AppDimens.spacing16,
                                horizontal = AppDimens.spacing8,
                            ),
                    )
                }
            }
        }
        Button(
            onClick = onStart,
            enabled = state.selectedName != null && !state.isWorking,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppDimens.spacing8),
            ) {
                if (state.isWorking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                    )
                }
                Text(
                    text = stringResource(R.string.onboarding_start),
                    style = AppTypography.headline,
                )
            }
        }
    }
}
