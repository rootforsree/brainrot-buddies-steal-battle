package com.sreecharan.brainrot_buddies_steal_battle.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.sreecharan.brainrot_buddies_steal_battle.R
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.AppDimens
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.AppTypography
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.LocalAppColors

/** Ticket chip icon size. */
private val TICKET_ICON_SIZE = 20.dp

@Composable
internal fun TicketChipsRow(
    luckyTickets: Int,
    megaTickets: Int,
    onEvent: (HomeUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (luckyTickets <= 0 && megaTickets <= 0) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = AppDimens.screenMargin,
                end = AppDimens.screenMargin,
                top = AppDimens.spacing8,
            ),
        horizontalArrangement = Arrangement.spacedBy(AppDimens.spacing8),
    ) {
        if (luckyTickets > 0) {
            TicketChip(
                label = stringResource(R.string.base_lucky_ticket, luckyTickets),
                contentDescription = stringResource(R.string.base_ticket_lucky_desc),
                onClick = { onEvent(HomeUiEvent.TicketTap(TicketType.LUCKY)) },
            )
        }
        if (megaTickets > 0) {
            TicketChip(
                label = stringResource(R.string.base_mega_ticket, megaTickets),
                contentDescription = stringResource(R.string.base_ticket_mega_desc),
                onClick = { onEvent(HomeUiEvent.TicketTap(TicketType.MEGA)) },
            )
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
