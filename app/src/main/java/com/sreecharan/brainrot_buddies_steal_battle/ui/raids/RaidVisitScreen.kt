package com.sreecharan.brainrot_buddies_steal_battle.ui.raids

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sreecharan.brainrot_buddies_steal_battle.R
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.AppDimens
import kotlinx.collections.immutable.ImmutableList

/**
 * Stateless raid-visit content: connection badge, revenge banner, buddy
 * pick grid, steal flow, and preset phrase/emote chat. State in, events out.
 *
 * This screen is both the practice visit (free look-around, no stealing)
 * and the raid attempt: the steal section enables only during raid windows
 * with slots left and when every §1 anti-grief rule passes.
 *
 * LongParameterList is suppressed by design (top-level screen wiring every
 * raid intent; precedent: GameRepositoryImpl). LongMethod for the same
 * reason: the screen is a flat list of small section calls.
 */
@Composable
@Suppress("LongParameterList", "LongMethod")
fun RaidVisitScreen(
    state: RaidVisitUiState,
    onSlotSelected: (Int) -> Unit,
    onStealClicked: () -> Unit,
    onRevengeClicked: () -> Unit,
    onPhraseSelected: (Int) -> Unit,
    onEmoteSelected: (String) -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = AppDimens.screenMargin),
        verticalArrangement = Arrangement.spacedBy(AppDimens.spacing12),
        contentPadding = PaddingValues(bottom = AppDimens.tabBarClearance),
    ) {
        item { Spacer(Modifier.height(AppDimens.spacing4)) }

        item {
            ConnectionBadge(connection = state.connection, friendBaseName = state.friendBaseName)
        }

        if (state.pendingRevenge && !state.revengeArmed) {
            item { RevengeCard(onRevengeClicked) }
        }

        item {
            Text(
                text = stringResource(R.string.raid_window_raids_left, state.raidsLeft),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            Text(
                text = stringResource(R.string.raid_pick_buddy),
                style = MaterialTheme.typography.titleMedium,
            )
        }

        slotGridSection(
            slots = state.slots,
            selectedSlot = state.selectedSlot,
            onSlotSelected = onSlotSelected,
        )

        item {
            StealButton(
                stealState = state.stealState,
                selectedSlot = state.selectedSlot,
                revengeArmed = state.revengeArmed,
                onStealClicked = onStealClicked,
            )
        }

        stealResultSection(state)

        item {
            Text(
                text = stringResource(R.string.raid_chat_hint),
                style = MaterialTheme.typography.titleMedium,
            )
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(AppDimens.spacing8)) {
                items(RaidChatContent.PHRASES.size) { index ->
                    FilterChip(
                        selected = false,
                        onClick = { onPhraseSelected(index) },
                        label = { Text(RaidChatContent.PHRASES[index]) },
                    )
                }
            }
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(AppDimens.spacing8)) {
                items(RaidChatContent.EMOTES) { emoteId ->
                    EmoteButton(emoteId = emoteId, onClick = { onEmoteSelected(emoteId) })
                }
            }
        }

        chatSection(messages = state.chat)

        item {
            OutlinedButton(onClick = onLeave, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.raid_leave))
            }
        }
    }
}

@Composable
private fun ConnectionBadge(
    connection: VisitConnection,
    friendBaseName: String,
) {
    val text =
        when (connection) {
            VisitConnection.LIVE -> {
                stringResource(R.string.raid_live)
            }

            VisitConnection.SNAPSHOT -> {
                stringResource(R.string.raid_snapshot)
            }

            VisitConnection.SEARCHING, VisitConnection.FAILED -> {
                stringResource(R.string.raid_connecting, friendBaseName)
            }
        }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (connection == VisitConnection.SEARCHING) {
            CircularProgressIndicator(Modifier.size(AppDimens.iconSize))
            Spacer(Modifier.width(AppDimens.spacing8))
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun RevengeCard(onRevengeClicked: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            ),
    ) {
        Column(Modifier.padding(AppDimens.spacing16)) {
            Text(
                text = stringResource(R.string.raid_revenge_banner),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(AppDimens.spacing8))
            Button(onClick = onRevengeClicked, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.friend_revenge))
            }
        }
    }
}

private fun LazyListScope.slotGridSection(
    slots: ImmutableList<RaidSlotUi>,
    selectedSlot: Int?,
    onSlotSelected: (Int) -> Unit,
) {
    item {
        // The grid never scrolls on its own: it sizes to all 24 slots and
        // the outer column does the scrolling (no nested same-axis scroll).
        LazyVerticalGrid(
            columns = GridCells.Fixed(GRID_COLUMNS),
            modifier = Modifier.fillMaxWidth(),
            userScrollEnabled = false,
            horizontalArrangement = Arrangement.spacedBy(AppDimens.spacing8),
            verticalArrangement = Arrangement.spacedBy(AppDimens.spacing8),
        ) {
            items(slots, key = { it.slot }) { slot ->
                SlotCell(
                    slot = slot,
                    selected = selectedSlot == slot.slot,
                    onClick = { onSlotSelected(slot.slot) },
                )
            }
        }
    }
}

@Composable
private fun StealButton(
    stealState: StealOutcome,
    selectedSlot: Int?,
    revengeArmed: Boolean,
    onStealClicked: () -> Unit,
) {
    val working = stealState == StealOutcome.WORKING
    Button(
        onClick = onStealClicked,
        enabled = selectedSlot != null && !working,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (working) {
            CircularProgressIndicator(Modifier.size(AppDimens.iconSize))
            Spacer(Modifier.width(AppDimens.spacing8))
            Text(stringResource(R.string.raid_stealing))
        } else if (revengeArmed) {
            Text(stringResource(R.string.friend_revenge))
        } else {
            Text(stringResource(R.string.raid_steal))
        }
    }
}

private fun LazyListScope.stealResultSection(state: RaidVisitUiState) {
    when (state.stealState) {
        StealOutcome.SUCCESS -> {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                ) {
                    Column(Modifier.padding(AppDimens.spacing16)) {
                        Text(
                            text = stringResource(R.string.raid_success_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(R.string.raid_success_body, state.stealBuddyName),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        StealOutcome.FAILED -> {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                ) {
                    Column(Modifier.padding(AppDimens.spacing16)) {
                        Text(
                            text = state.stealMessageRes?.let { stringResource(it) }.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(AppDimens.spacing4))
                        Text(
                            text = stringResource(R.string.raid_refunded),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        else -> {}
    }
}

private fun LazyListScope.chatSection(messages: ImmutableList<ChatUi>) {
    if (messages.isEmpty()) return
    itemsIndexed(messages, key = { _, message -> message.id }) { _, message ->
        ChatBubble(message = message)
    }
}

@Composable
private fun SlotCell(
    slot: RaidSlotUi,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val occupied = slot.buddyId.isNotEmpty()
    Card(
        onClick = onClick,
        modifier =
            Modifier
                .aspectRatio(1f)
                .alpha(if (!slot.stealable && occupied) DIMMED_ALPHA else 1f),
        border =
            if (selected) {
                BorderStroke(SELECTED_BORDER_WIDTH, MaterialTheme.colorScheme.primary)
            } else {
                null
            },
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(AppDimens.spacing4),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (occupied) {
                if (!slot.stealable) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(AppDimens.spacing16),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = slot.buddyName,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
                Text(
                    text = stringResource(R.string.raid_slot_level, slot.level),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = "${slot.slot + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmoteButton(
    emoteId: String,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val resId =
        remember(emoteId) {
            context.resources.getIdentifier(emoteId, "drawable", context.packageName)
        }
    // Emote art ships with the coordinator; skip any that are missing.
    if (resId == 0) return
    OutlinedButton(onClick = onClick, contentPadding = PaddingValues(AppDimens.spacing8)) {
        Image(
            painter = painterResource(resId),
            contentDescription = emoteId,
            modifier = Modifier.size(EMOTE_BUTTON_SIZE),
        )
    }
}

@Composable
private fun ChatBubble(message: ChatUi) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.mine) Arrangement.End else Arrangement.Start,
    ) {
        Card(
            colors =
                CardDefaults.cardColors(
                    containerColor =
                        if (message.mine) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                ),
        ) {
            if (message.kind == RaidChatContent.KIND_PHRASE) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier =
                        Modifier.padding(
                            horizontal = AppDimens.spacing12,
                            vertical = AppDimens.spacing8,
                        ),
                )
            } else {
                val context = LocalContext.current
                val resId =
                    remember(message.refId) {
                        context.resources.getIdentifier(message.refId, "drawable", context.packageName)
                    }
                if (resId != 0) {
                    Image(
                        painter = painterResource(resId),
                        contentDescription = message.refId,
                        modifier = Modifier.size(EMOTE_CHAT_SIZE).padding(AppDimens.spacing4),
                    )
                }
            }
        }
    }
}

private const val GRID_COLUMNS = 4
private const val DIMMED_ALPHA = 0.55f
private val SELECTED_BORDER_WIDTH = 3.dp
private val EMOTE_BUTTON_SIZE = 28.dp
private val EMOTE_CHAT_SIZE = 40.dp
