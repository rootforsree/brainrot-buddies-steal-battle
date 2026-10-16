package com.sreecharan.brainrot_buddies_steal_battle.ui.gifts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sreecharan.brainrot_buddies_steal_battle.R
import com.sreecharan.brainrot_buddies_steal_battle.ui.components.BuddyImage
import com.sreecharan.brainrot_buddies_steal_battle.ui.components.GroupCard
import com.sreecharan.brainrot_buddies_steal_battle.ui.components.HoldToConfirmButton
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.AppDimens
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.AppTypography
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.LocalAppColors
import kotlinx.coroutines.delay

/**
 * 30-day gift calendar as a full-screen dialog (no new §7 route).
 * Builder B: show this from the Base screen gift widget via
 * `GiftCalendarRoute(onDismiss = ...)`.
 */
@Composable
fun GiftCalendarRoute(
    onDismiss: () -> Unit,
    viewModel: GiftsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var toast by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            toast = when (event) {
                is GiftsEvent.GiftClaimed ->
                    context.getString(R.string.gifts_opened_day, event.day)
                GiftsEvent.CatchUpDone ->
                    context.getString(R.string.shop_bought)
                is GiftsEvent.WeeklyClaimed ->
                    context.getString(R.string.gifts_weekly_got, event.buddyName)
                GiftsEvent.ActionFailed ->
                    context.getString(R.string.shop_error)
            }
        }
    }
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(2_500)
            toast = null
        }
    }

    GiftCalendarContent(
        state = state,
        toast = toast,
        onClaimToday = viewModel::onClaimToday,
        onCatchUp = viewModel::onCatchUpConfirmed,
        onClaimWeekly = viewModel::onClaimWeekly,
        onDismiss = onDismiss,
    )
}

private fun dayRewardRes(day: Int): Int = when (day) {
    1 -> R.string.gifts_reward_day1
    7 -> R.string.gifts_reward_day7
    14 -> R.string.gifts_reward_day14
    21 -> R.string.gifts_reward_day21
    30 -> R.string.gifts_reward_day30
    else -> R.string.gifts_reward_surprise
}

@Composable
fun GiftCalendarContent(
    state: GiftsUiState,
    toast: String?,
    onClaimToday: () -> Unit,
    onCatchUp: () -> Unit,
    onClaimWeekly: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(colors.groupedBackground)
                .padding(AppDimens.spacing16)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(AppDimens.spacing16),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.gifts_title),
                    style = AppTypography.largeTitle,
                    color = colors.label,
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.action_close),
                        tint = colors.secondaryLabel,
                    )
                }
            }

            if (state.streakDays > 0) {
                Text(
                    text = stringResource(R.string.gifts_streak, state.streakDays),
                    style = AppTypography.headline,
                    color = colors.accent,
                )
            }

            if (toast != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = colors.accent.copy(alpha = 0.15f),
                    ),
                ) {
                    Text(
                        text = toast,
                        style = AppTypography.body,
                        color = colors.label,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(AppDimens.spacing12),
                    )
                }
            }

            // Today's gift card.
            GroupCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AppDimens.spacing16),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.spacing16),
                ) {
                    BuddyImage(
                        imageName = "hero_flip",
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.gifts_today),
                            style = AppTypography.headline,
                            color = colors.label,
                        )
                        Text(
                            text = stringResource(dayRewardRes(state.upcomingDay)),
                            style = AppTypography.body,
                            color = colors.secondaryLabel,
                        )
                    }
                }
                if (state.canClaimToday) {
                    Button(
                        onClick = onClaimToday,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = AppDimens.spacing16,
                                end = AppDimens.spacing16,
                                bottom = AppDimens.spacing16,
                            )
                            .heightIn(min = 56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                    ) {
                        Text(
                            text = stringResource(R.string.gifts_open),
                            style = AppTypography.headline,
                        )
                    }
                }
            }

            // 30-day grid: claimed = check, today = accent, future = gray.
            // Plain rows (not lazy): 30 fixed cells inside the scrolling column.
            Column(verticalArrangement = Arrangement.spacedBy(AppDimens.spacing8)) {
                (1..30).chunked(5).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AppDimens.spacing8),
                    ) {
                        row.forEach { day ->
                            Box(modifier = Modifier.weight(1f)) {
                                DayCell(
                                    day = day,
                                    claimed = day < state.upcomingDay ||
                                        (day == state.upcomingDay && !state.canClaimToday),
                                    isToday = day == state.upcomingDay,
                                )
                            }
                        }
                    }
                }
            }

            // Catch-up: missed day pauses, never resets; 500 coins to catch up.
            if (state.catchUpAvailable) {
                GroupCard {
                    Column(
                        modifier = Modifier.padding(AppDimens.spacing16),
                        verticalArrangement = Arrangement.spacedBy(AppDimens.spacing8),
                    ) {
                        Text(
                            text = stringResource(R.string.gifts_catchup_title),
                            style = AppTypography.headline,
                            color = colors.label,
                        )
                        Text(
                            text = stringResource(R.string.gifts_catchup_sub),
                            style = AppTypography.footnote,
                            color = colors.secondaryLabel,
                        )
                        HoldToConfirmButton(
                            text = stringResource(R.string.gifts_catchup_buy),
                            onConfirm = onCatchUp,
                            modifier = Modifier.fillMaxWidth(),
                            holdMillis = 1500,
                        )
                    }
                }
            }

            // Weekly Flippy gift.
            if (state.weeklyGiftAvailable) {
                GroupCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(AppDimens.spacing16),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppDimens.spacing16),
                    ) {
                        BuddyImage(
                            imageName = "hero_flip",
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.gifts_weekly_title),
                                style = AppTypography.headline,
                                color = colors.label,
                            )
                            Text(
                                text = stringResource(R.string.gifts_weekly_sub),
                                style = AppTypography.footnote,
                                color = colors.secondaryLabel,
                            )
                        }
                    }
                    Button(
                        onClick = onClaimWeekly,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = AppDimens.spacing16,
                                end = AppDimens.spacing16,
                                bottom = AppDimens.spacing16,
                            )
                            .heightIn(min = 56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                    ) {
                        Text(
                            text = stringResource(R.string.gifts_weekly_open),
                            style = AppTypography.headline,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(day: Int, claimed: Boolean, isToday: Boolean) {
    val colors = LocalAppColors.current
    val background = when {
        isToday -> colors.accent
        claimed -> colors.accent.copy(alpha = 0.25f)
        else -> colors.cardSurface
    }
    val contentColor = when {
        isToday -> Color.White
        claimed -> colors.accent
        else -> colors.secondaryLabel
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .padding(AppDimens.spacing4),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (claimed && !isToday) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.CardGiftcard,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = day.toString(),
                style = AppTypography.caption,
                color = contentColor,
            )
        }
    }
}
