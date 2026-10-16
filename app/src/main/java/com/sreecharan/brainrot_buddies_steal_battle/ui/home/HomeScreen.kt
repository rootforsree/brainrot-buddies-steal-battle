package com.sreecharan.brainrot_buddies_steal_battle.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.sreecharan.brainrot_buddies_steal_battle.R
import com.sreecharan.brainrot_buddies_steal_battle.data.model.TicketType
import com.sreecharan.brainrot_buddies_steal_battle.ui.components.BaseBoardCanvas
import com.sreecharan.brainrot_buddies_steal_battle.ui.components.BoardCanvasState
import com.sreecharan.brainrot_buddies_steal_battle.ui.components.CameraState
import com.sreecharan.brainrot_buddies_steal_battle.ui.components.JuiceController
import com.sreecharan.brainrot_buddies_steal_battle.ui.components.MysteryCeremonyOverlay
import com.sreecharan.brainrot_buddies_steal_battle.ui.components.ReplayCanvas
import com.sreecharan.brainrot_buddies_steal_battle.ui.components.TourModeOverlay
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.AppDimens
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.AppTypography
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.LocalAppColors
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.animationsEnabled
import kotlinx.coroutines.launch

/** §11: every gameplay target is at least 56 dp. */
private val GAMEPLAY_TARGET_SIZE = 56.dp
private val VAULT_ICON_SIZE = 28.dp
private val TICKET_ICON_SIZE = 20.dp
private val CARD_CORNER_RADIUS = 16.dp
private val PROGRESS_BAR_HEIGHT = 8.dp
private const val TRACK_ALPHA = 0.2f
private const val TOUR_PAN_COLUMNS = 6
private const val TOUR_PAN_SWING = 0.7f
private const val TOUR_ZOOM_IN = 1.15f
private const val MILLIS_PER_SECOND = 1000L
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3600L

/**
 * Stateless Base content: vault HUD, raid-bell widget, the game-board
 * Canvas and the virality overlays (staged replays, mystery ceremony, tour
 * mode). State in, [HomeUiEvent] out; the route owns the ViewModel.
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    frameTime: State<Long>,
    juice: JuiceController,
    onEvent: (HomeUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val reduceMotion = !animationsEnabled(LocalContext.current)

    // Tour camera: pan toward the spotlight slot, gentle zoom in.
    val camPan = remember { Animatable(0f) }
    val camZoom = remember { Animatable(1f) }
    val tourSlot = state.tour?.spotlightSlot
    val touring = state.tour != null
    LaunchedEffect(tourSlot, touring) {
        launch {
            val col = if (touring && tourSlot != null) tourSlot % TOUR_PAN_COLUMNS else TOUR_PAN_COLUMNS / 2
            val frac = (col + 0.5f) / TOUR_PAN_COLUMNS
            camPan.animateTo(
                (frac - 0.5f) * TOUR_PAN_SWING,
                spring(stiffness = Spring.StiffnessLow),
            )
        }
        launch {
            camZoom.animateTo(if (touring) TOUR_ZOOM_IN else 1f, spring(stiffness = Spring.StiffnessLow))
        }
    }

    val board = BoardCanvasState(
        conveyor = state.conveyor,
        slots = state.slots,
        claimAnims = state.claimAnims,
        waddleAnims = state.waddleAnims,
        boingAnims = state.boingAnims,
        floatUps = state.floatUps,
        wiggleSlot = state.wiggleSlot,
        shieldDome = state.shieldActive,
        shieldEndsAtMs = state.shieldEndsAtMs,
        tour = state.tour,
        coachMark = state.coachMark,
        nowMs = state.nowMs,
        nextSpawnMs = state.nextSpawnMs,
    )

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            VaultHudRow(
                state = state,
                onEvent = onEvent,
            )
            Box(modifier = Modifier.weight(1f)) {
                BaseBoardCanvas(
                    board = board,
                    frameTime = frameTime,
                    camera = CameraState(camPan.value, camZoom.value),
                    juice = juice,
                    reduceMotion = reduceMotion,
                    onConveyorTap = { onEvent(HomeUiEvent.ConveyorTap(it)) },
                    onSlotTap = { onEvent(HomeUiEvent.SlotTap(it)) },
                    modifier = Modifier.fillMaxSize(),
                )
                if (state.tour != null) {
                    TourModeOverlay(
                        tour = state.tour,
                        onEndTour = { onEvent(HomeUiEvent.TourToggle) },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = AppDimens.screenMargin),
                    )
                }
            }
            Spacer(modifier = Modifier.height(AppDimens.tabBarClearance))
        }

        // Tour FAB: 56 dp gameplay target (§11).
        IconButton(
            onClick = { onEvent(HomeUiEvent.TourToggle) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = AppDimens.screenMargin, bottom = AppDimens.tabBarClearance + AppDimens.screenMargin)
                .size(GAMEPLAY_TARGET_SIZE)
                .background(colors.accent, CircleShape),
        ) {
            Icon(
                imageVector = if (touring) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                contentDescription = stringResource(
                    if (touring) R.string.base_tour_end else R.string.base_tour,
                ),
                tint = Color.White,
            )
        }

        // Full-screen virality overlays sit above everything.
        if (state.ceremony != null) {
            MysteryCeremonyOverlay(
                ceremony = state.ceremony,
                frameTime = frameTime,
                juice = juice,
                reduceMotion = reduceMotion,
                onSkip = { onEvent(HomeUiEvent.DismissCelebration) },
                onRevealDone = { onEvent(HomeUiEvent.DismissCelebration) },
            )
        }
        if (state.replay != null) {
            ReplayCanvas(
                replay = state.replay,
                frameTime = frameTime,
                juice = juice,
                reduceMotion = reduceMotion,
                onSkip = { onEvent(HomeUiEvent.DismissReplay) },
            )
        }
    }
}

/**
 * Vault HUD: coins, fill meter, collect button + raid-bell widget with
 * countdown. One screen, one job (§11): watch + collect.
 */
@Composable
private fun VaultHudRow(
    state: HomeUiState,
    onEvent: (HomeUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = AppDimens.screenMargin,
                    end = AppDimens.screenMargin,
                    top = AppDimens.spacing12,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppDimens.spacing12),
        ) {
            // Vault card.
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(CARD_CORNER_RADIUS))
                    .background(colors.cardSurface)
                    .padding(AppDimens.spacing12),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppDimens.spacing8),
            ) {
                Icon(
                    imageVector = Icons.Filled.MonetizationOn,
                    contentDescription = null,
                    tint = colors.warning,
                    modifier = Modifier.size(VAULT_ICON_SIZE),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.base_vault_coins, state.vault.coins),
                        style = AppTypography.headline,
                        color = colors.label,
                    )
                    LinearProgressIndicator(
                        progress = { state.vault.fillFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(PROGRESS_BAR_HEIGHT)
                            .clip(CircleShape),
                        color = colors.warning,
                        trackColor = colors.secondaryLabel.copy(alpha = TRACK_ALPHA),
                    )
                }
                Button(
                    onClick = { onEvent(HomeUiEvent.VaultCollect) },
                    enabled = state.vault.coins > 0,
                    modifier = Modifier.height(GAMEPLAY_TARGET_SIZE),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                ) {
                    Text(
                        text = stringResource(R.string.base_collect),
                        style = AppTypography.headline,
                    )
                }
            }

            // Raid-bell widget: countdown + raids left, 56 dp target.
            val bell = state.raidBell
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = { onEvent(HomeUiEvent.RaidBellTap) },
                    modifier = Modifier
                        .size(GAMEPLAY_TARGET_SIZE)
                        .background(
                            if (bell.windowActive) colors.accent else colors.cardSurface,
                            CircleShape,
                        ),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Notifications,
                        contentDescription = stringResource(R.string.base_raid_bell),
                        tint = if (bell.windowActive) {
                            Color.White
                        } else {
                            colors.secondaryLabel
                        },
                    )
                }
                Text(
                    text = if (bell.windowActive) {
                        stringResource(R.string.base_raids_left, bell.raidsLeft)
                    } else {
                        val remaining = (bell.nextWindowMs - System.currentTimeMillis()).coerceAtLeast(0L)
                        stringResource(R.string.base_raid_in, formatShortDuration(remaining))
                    },
                    style = AppTypography.tabLabel,
                    color = colors.secondaryLabel,
                )
            }
        }

        // Ticket chips (only when the kid holds tickets).
        if (state.luckyTickets > 0 || state.megaTickets > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = AppDimens.screenMargin,
                        end = AppDimens.screenMargin,
                        top = AppDimens.spacing8,
                    ),
                horizontalArrangement = Arrangement.spacedBy(AppDimens.spacing8),
            ) {
                if (state.luckyTickets > 0) {
                    TicketChip(
                        label = stringResource(R.string.base_lucky_ticket, state.luckyTickets),
                        contentDescription = stringResource(R.string.base_ticket_lucky_desc),
                        onClick = { onEvent(HomeUiEvent.TicketTap(TicketType.LUCKY)) },
                    )
                }
                if (state.megaTickets > 0) {
                    TicketChip(
                        label = stringResource(R.string.base_mega_ticket, state.megaTickets),
                        contentDescription = stringResource(R.string.base_ticket_mega_desc),
                        onClick = { onEvent(HomeUiEvent.TicketTap(TicketType.MEGA)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TicketChip(
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(colors.cardSurface)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClickLabel = contentDescription,
                onClick = onClick,
            )
            .padding(horizontal = AppDimens.spacing16, vertical = AppDimens.spacing12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.spacing4),
    ) {
        Icon(
            imageVector = Icons.Filled.ConfirmationNumber,
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier.size(TICKET_ICON_SIZE),
        )
        Text(text = label, style = AppTypography.subheadline, color = colors.label)
    }
}

private fun formatShortDuration(ms: Long): String {
    val totalSeconds = ms / MILLIS_PER_SECOND
    val hours = totalSeconds / SECONDS_PER_HOUR
    val minutes = (totalSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m ${totalSeconds % SECONDS_PER_MINUTE}s"
}
