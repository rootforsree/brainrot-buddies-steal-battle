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
    var toast by remember { mutableStateOf<String?>(null) }

    GiftToastEffect(viewModel) { toast = it }
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(TOAST_DISMISS_MS)
            toast = null
        }
    }

    GiftCalendarContent(
        state = state,
        toast = toast,
        actions =
            GiftCalendarActions(
                onClaimToday = viewModel::onClaimToday,
                onCatchUp = viewModel::onCatchUpConfirmed,
                onClaimWeekly = viewModel::onClaimWeekly,
                onDismiss = onDismiss,
            ),
    )
}

/** All user actions on the gift calendar dialog. */
data class GiftCalendarActions(
    val onClaimToday: () -> Unit,
    val onCatchUp: () -> Unit,
    val onClaimWeekly: () -> Unit,
    val onDismiss: () -> Unit,
)

/** How long a toast stays visible before dismissing. */
private const val TOAST_DISMISS_MS = 2_500L

/** Day cells per grid row. */
private const val DAYS_PER_ROW = 5

/** Milestone days with special reward text. */
private const val REWARD_DAY_1 = 1
private const val REWARD_DAY_7 = 7
private const val REWARD_DAY_14 = 14
private const val REWARD_DAY_21 = 21
private const val REWARD_DAY_30 = 30

@Composable
private fun GiftToastEffect(
    viewModel: GiftsViewModel,
    onToast: (String) -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            onToast(
                when (event) {
                    is GiftsEvent.GiftClaimed -> {
                        context.getString(R.string.gifts_opened_day, event.day)
                    }

                    GiftsEvent.CatchUpDone -> {
                        context.getString(R.string.shop_bought)
                    }

                    is GiftsEvent.WeeklyClaimed -> {
                        context.getString(R.string.gifts_weekly_got, event.buddyName)
                    }

                    GiftsEvent.ActionFailed -> {
                        context.getString(R.string.shop_error)
                    }
                },
            )
        }
    }
}

private fun dayRewardRes(day: Int): Int =
    when (day) {
        REWARD_DAY_1 -> R.string.gifts_reward_day1
        REWARD_DAY_7 -> R.string.gifts_reward_day7
        REWARD_DAY_14 -> R.string.gifts_reward_day14
        REWARD_DAY_21 -> R.string.gifts_reward_day21
        REWARD_DAY_30 -> R.string.gifts_reward_day30
        else -> R.string.gifts_reward_surprise
    }

@Composable
fun GiftCalendarContent(
    state: GiftsUiState,
    toast: String?,
    actions: GiftCalendarActions,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    Dialog(
        onDismissRequest = actions.onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier =
                modifier
                    .fillMaxSize()
                    .background(colors.groupedBackground)
                    .padding(AppDimens.spacing16)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(AppDimens.spacing16),
        ) {
            GiftCalendarHeader(onDismiss = actions.onDismiss)

            if (state.streakDays > 0) {
                Text(
                    text = stringResource(R.string.gifts_streak, state.streakDays),
                    style = AppTypography.headline,
                    color = colors.accent,
                )
            }

            CalendarToast(toast)

            TodayGiftCard(state, actions.onClaimToday)

            // 30-day grid: claimed = check, today = accent, future = gray.
            // Plain rows (not lazy): 30 fixed cells inside the scrolling column.
            Column(verticalArrangement = Arrangement.spacedBy(AppDimens.spacing8)) {
                for (row in (1..CALENDAR_DAYS).chunked(DAYS_PER_ROW)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AppDimens.spacing8),
                    ) {
                        row.forEach { day ->
                            Box(modifier = Modifier.weight(1f)) {
                                DayCell(
                                    day = day,
                                    claimed =
                                        day < state.upcomingDay ||
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
                CatchUpCard(onCatchUp = actions.onCatchUp)
            }

            // Weekly Flippy gift.
            if (state.weeklyGiftAvailable) {
                WeeklyGiftCard(onClaimWeekly = actions.onClaimWeekly)
            }
        }
    }
}

@Composable
private fun GiftCalendarHeader(onDismiss: () -> Unit) {
    val colors = LocalAppColors.current
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
}

@Composable
private fun CalendarToast(toast: String?) {
    if (toast == null) return
    val colors = LocalAppColors.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = colors.accent.copy(alpha = 0.15f),
            ),
    ) {
        Text(
            text = toast,
            style = AppTypography.body,
            color = colors.label,
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(AppDimens.spacing12),
        )
    }
}

@Composable
private fun TodayGiftCard(
    state: GiftsUiState,
    onClaimToday: () -> Unit,
) {
    val colors = LocalAppColors.current
    GroupCard {
        Row(
            modifier =
                Modifier
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
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            start = AppDimens.spacing16,
                            end = AppDimens.spacing16,
                            bottom = AppDimens.spacing16,
                        ).heightIn(min = 56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
            ) {
                Text(
                    text = stringResource(R.string.gifts_open),
                    style = AppTypography.headline,
                )
            }
        }
    }
}

@Composable
private fun CatchUpCard(onCatchUp: () -> Unit) {
    val colors = LocalAppColors.current
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

@Composable
private fun WeeklyGiftCard(onClaimWeekly: () -> Unit) {
    val colors = LocalAppColors.current
    GroupCard {
        Row(
            modifier =
                Modifier
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
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = AppDimens.spacing16,
                        end = AppDimens.spacing16,
                        bottom = AppDimens.spacing16,
                    ).heightIn(min = 56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
        ) {
            Text(
                text = stringResource(R.string.gifts_weekly_open),
                style = AppTypography.headline,
            )
        }
    }
}

@Composable
private fun DayCell(
    day: Int,
    claimed: Boolean,
    isToday: Boolean,
) {
    val colors = LocalAppColors.current
    val background =
        when {
            isToday -> colors.accent
            claimed -> colors.accent.copy(alpha = 0.25f)
            else -> colors.cardSurface
        }
    val contentColor =
        when {
            isToday -> Color.White
            claimed -> colors.accent
            else -> colors.secondaryLabel
        }
    Box(
        modifier =
            Modifier
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
