package com.sreecharan.brainrot_buddies_steal_battle.ui.friends

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.sreecharan.brainrot_buddies_steal_battle.R
import com.sreecharan.brainrot_buddies_steal_battle.p2p.DiscoveredPeer
import com.sreecharan.brainrot_buddies_steal_battle.p2p.PairingPhase
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.AppDimens
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay

private const val VILLAIN_COUNTDOWN_TICK_MS = 30_000L
private const val CODE_GROUP_SIZE = 3
private const val MILLIS_PER_MINUTE = 60_000L
private val CODE_FONT_SIZE = 40.sp
private val CODE_LETTER_SPACING = 8.sp
private val TOKEN_FONT_SIZE = 28.sp
private val VERIFY_CODE_FONT_SIZE = 32.sp
private val VERIFY_CODE_LETTER_SPACING = 6.sp

/**
 * Stateless Friends content: pairing ceremony, friend list, snapshot
 * status, and incoming-raid villain cards. State in, events out.
 *
 * LongParameterList is suppressed here by design: this is the top-level
 * screen wiring every friend intent, and splitting it would only scatter
 * the wiring (precedent: GameRepositoryImpl). LongMethod for the same
 * reason: the screen is a flat list of small section calls.
 */
@Composable
@Suppress("LongParameterList", "LongMethod")
fun FriendsScreen(
    state: FriendsUiState,
    onTurnOnNearby: () -> Unit,
    onFindNearby: () -> Unit,
    onStopFinding: () -> Unit,
    onPeerSelected: (DiscoveredPeer) -> Unit,
    onMatchConfirmed: () -> Unit,
    onPairingCancelled: () -> Unit,
    onCodeConfirmed: () -> Unit,
    onCodeRejected: () -> Unit,
    onShowCode: () -> Unit,
    onHideCode: () -> Unit,
    onUnpair: (FriendItem) -> Unit,
    onVisit: (FriendItem) -> Unit,
    onRaid: (FriendItem) -> Unit,
    onRevenge: (IncomingRaid) -> Unit,
    onDismissRaid: (IncomingRaid) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.p2pAvailable) {
        Column(
            modifier = modifier.fillMaxSize().padding(AppDimens.spacing24),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.friend_needs_android12),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = AppDimens.screenMargin),
        verticalArrangement = Arrangement.spacedBy(AppDimens.spacing12),
    ) {
        item { Spacer(Modifier.height(AppDimens.spacing4)) }

        if (!state.bluetoothGranted) {
            item { BluetoothCard(onTurnOnNearby) }
        }

        item {
            CodeCard(
                code = state.myCode,
                revealed = state.codeRevealed,
                onShowCode = onShowCode,
                onHideCode = onHideCode,
            )
        }

        item {
            PairingCard(
                phase = state.pairingPhase,
                peers = state.peers,
                bluetoothGranted = state.bluetoothGranted,
                onFindNearby = onFindNearby,
                onStopFinding = onStopFinding,
                onPeerSelected = onPeerSelected,
                onMatchConfirmed = onMatchConfirmed,
                onPairingCancelled = onPairingCancelled,
                onCodeConfirmed = onCodeConfirmed,
                onCodeRejected = onCodeRejected,
            )
        }

        inboxSection(
            raids = state.incomingRaids,
            onRevenge = onRevenge,
            onDismiss = onDismissRaid,
        )

        friendListSection(
            friends = state.friends,
            onVisit = onVisit,
            onRaid = onRaid,
            onUnpair = onUnpair,
        )

        item { Spacer(Modifier.height(AppDimens.tabBarClearance)) }
    }
}

@Composable
private fun BluetoothCard(onTurnOnNearby: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AppDimens.spacing16)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Bluetooth, contentDescription = null)
                Spacer(Modifier.width(AppDimens.spacing8))
                Text(
                    text = stringResource(R.string.friend_bluetooth_title),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.height(AppDimens.spacing8))
            Text(
                text = stringResource(R.string.friend_bluetooth_body),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(AppDimens.spacing12))
            Button(onClick = onTurnOnNearby, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.friend_bluetooth_allow))
            }
        }
    }
}

@Composable
private fun CodeCard(
    code: String,
    revealed: Boolean,
    onShowCode: () -> Unit,
    onHideCode: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(AppDimens.spacing16),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.friend_my_code),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.friend_my_code_sub),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(AppDimens.spacing8))
            if (revealed) {
                Text(
                    text = code.chunked(CODE_GROUP_SIZE).joinToString(" "),
                    fontSize = CODE_FONT_SIZE,
                    letterSpacing = CODE_LETTER_SPACING,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(AppDimens.spacing8))
                TextButton(onClick = onHideCode) {
                    Text(stringResource(R.string.friend_dismiss))
                }
            } else {
                Button(onClick = onShowCode) {
                    Text(stringResource(R.string.friend_show_code))
                }
            }
        }
    }
}

private fun LazyListScope.inboxSection(
    raids: ImmutableList<IncomingRaid>,
    onRevenge: (IncomingRaid) -> Unit,
    onDismiss: (IncomingRaid) -> Unit,
) {
    if (raids.isEmpty()) return
    item {
        Text(
            text = stringResource(R.string.friend_inbox_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = AppDimens.spacing4),
        )
    }
    items(raids, key = { it.raidId }) { raid ->
        VillainCard(raid = raid, onRevenge = onRevenge, onDismiss = onDismiss)
    }
}

private fun LazyListScope.friendListSection(
    friends: ImmutableList<FriendItem>,
    onVisit: (FriendItem) -> Unit,
    onRaid: (FriendItem) -> Unit,
    onUnpair: (FriendItem) -> Unit,
) {
    item {
        Text(
            text = stringResource(R.string.friend_list_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = AppDimens.spacing4),
        )
    }
    if (friends.isEmpty()) {
        item {
            Text(
                text = stringResource(R.string.friend_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        items(friends, key = { it.guestId }) { friend ->
            FriendRow(
                friend = friend,
                onVisit = { onVisit(friend) },
                onRaid = { onRaid(friend) },
                onUnpair = { onUnpair(friend) },
            )
        }
    }
}

@Composable
@Suppress("LongParameterList")
private fun PairingCard(
    phase: PairingPhase,
    peers: ImmutableList<DiscoveredPeer>,
    bluetoothGranted: Boolean,
    onFindNearby: () -> Unit,
    onStopFinding: () -> Unit,
    onPeerSelected: (DiscoveredPeer) -> Unit,
    onMatchConfirmed: () -> Unit,
    onPairingCancelled: () -> Unit,
    onCodeConfirmed: () -> Unit,
    onCodeRejected: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AppDimens.spacing16)) {
            when (phase) {
                is PairingPhase.Idle -> PairIdle(bluetoothGranted, onFindNearby)
                is PairingPhase.Discovering -> PairDiscovering(peers, onStopFinding, onPeerSelected)
                is PairingPhase.AwaitingToken -> PairWaiting(onPairingCancelled)
                is PairingPhase.ConfirmToken -> PairConfirmToken(phase, onMatchConfirmed, onPairingCancelled)
                is PairingPhase.VerifyCode -> PairVerifyCode(phase, onCodeConfirmed, onCodeRejected)
                is PairingPhase.Paired -> PairPaired(onFindNearby)
                is PairingPhase.Failed -> PairFailed(phase, onFindNearby)
            }
        }
    }
}

@Composable
private fun PairIdle(bluetoothGranted: Boolean, onFindNearby: () -> Unit) {
    Button(
        onClick = onFindNearby,
        enabled = bluetoothGranted,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Filled.Radar, contentDescription = null)
        Spacer(Modifier.width(AppDimens.spacing8))
        Text(stringResource(R.string.friend_find_button))
    }
}

@Composable
private fun PairDiscovering(
    peers: ImmutableList<DiscoveredPeer>,
    onStopFinding: () -> Unit,
    onPeerSelected: (DiscoveredPeer) -> Unit,
) {
    SearchingRow(onStopFinding)
    Spacer(Modifier.height(AppDimens.spacing8))
    if (peers.isEmpty()) {
        Text(
            text = stringResource(R.string.friend_no_peers),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        Text(
            text = stringResource(R.string.friend_tap_peer),
            style = MaterialTheme.typography.labelLarge,
        )
        peers.forEach { peer ->
            Spacer(Modifier.height(AppDimens.spacing4))
            OutlinedButton(
                onClick = { onPeerSelected(peer) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.PersonAdd, contentDescription = null)
                Spacer(Modifier.width(AppDimens.spacing8))
                Text(peer.baseName)
            }
        }
    }
}

@Composable
private fun PairWaiting(onCancel: () -> Unit) {
    SearchingRow(onCancel)
}

@Composable
private fun SearchingRow(onStop: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(AppDimens.iconSize))
        Spacer(Modifier.width(AppDimens.spacing12))
        Text(
            text = stringResource(R.string.friend_searching),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onStop) {
            Text(stringResource(R.string.friend_stop))
        }
    }
}

@Composable
private fun PairConfirmToken(
    phase: PairingPhase.ConfirmToken,
    onMatchConfirmed: () -> Unit,
    onPairingCancelled: () -> Unit,
) {
    Text(
        text = stringResource(R.string.friend_token_title),
        style = MaterialTheme.typography.titleMedium,
    )
    Text(
        text = stringResource(R.string.friend_token_body),
        style = MaterialTheme.typography.bodyMedium,
    )
    Spacer(Modifier.height(AppDimens.spacing8))
    Text(
        text = phase.authToken.ifEmpty { "…" },
        fontSize = TOKEN_FONT_SIZE,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(AppDimens.spacing8))
    Row(horizontalArrangement = Arrangement.spacedBy(AppDimens.spacing8)) {
        Button(onClick = onMatchConfirmed, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.friend_match_button))
        }
        OutlinedButton(onClick = onPairingCancelled, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.friend_stop))
        }
    }
}

@Composable
private fun PairVerifyCode(
    phase: PairingPhase.VerifyCode,
    onCodeConfirmed: () -> Unit,
    onCodeRejected: () -> Unit,
) {
    Text(
        text = stringResource(R.string.friend_verify_title),
        style = MaterialTheme.typography.titleMedium,
    )
    Text(
        text = stringResource(R.string.friend_verify_body),
        style = MaterialTheme.typography.bodyMedium,
    )
    Spacer(Modifier.height(AppDimens.spacing8))
    Text(
        text = phase.peerCode.chunked(CODE_GROUP_SIZE).joinToString(" "),
        fontSize = VERIFY_CODE_FONT_SIZE,
        letterSpacing = VERIFY_CODE_LETTER_SPACING,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        text = phase.peerBaseName,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(AppDimens.spacing8))
    Row(horizontalArrangement = Arrangement.spacedBy(AppDimens.spacing8)) {
        Button(onClick = onCodeConfirmed, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.friend_verify_yes))
        }
        OutlinedButton(onClick = onCodeRejected, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.friend_verify_no))
        }
    }
}

@Composable
private fun PairPaired(onFindNearby: () -> Unit) {
    Text(
        text = stringResource(R.string.friend_paired),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(AppDimens.spacing8))
    OutlinedButton(onClick = onFindNearby, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.friend_find_button))
    }
}

@Composable
private fun PairFailed(phase: PairingPhase.Failed, onFindNearby: () -> Unit) {
    Text(
        text = phase.message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
    )
    Spacer(Modifier.height(AppDimens.spacing8))
    OutlinedButton(onClick = onFindNearby, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.friend_find_button))
    }
}

@Composable
private fun VillainCard(
    raid: IncomingRaid,
    onRevenge: (IncomingRaid) -> Unit,
    onDismiss: (IncomingRaid) -> Unit,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(raid.raidId) {
        while (true) {
            delay(VILLAIN_COUNTDOWN_TICK_MS)
            now = System.currentTimeMillis()
        }
    }
    val remaining = raid.revengeDeadlineMs - now
    val canRevenge = !raid.revengeUsed && remaining > 0

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(AppDimens.spacing16)) {
            Text(
                text = stringResource(R.string.raid_villain_title, raid.attackerBaseName, raid.buddyName),
                style = MaterialTheme.typography.titleMedium,
            )
            if (raid.isShadow) {
                Text(
                    text = stringResource(R.string.raid_snapshot),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Spacer(Modifier.height(AppDimens.spacing4))
            if (canRevenge) {
                Text(
                    text = stringResource(R.string.friend_revenge_left, formatCountdown(remaining)),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(AppDimens.spacing8))
                Row(horizontalArrangement = Arrangement.spacedBy(AppDimens.spacing8)) {
                    Button(onClick = { onRevenge(raid) }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.friend_revenge))
                    }
                    OutlinedButton(onClick = { onDismiss(raid) }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.friend_dismiss))
                    }
                }
            } else {
                OutlinedButton(onClick = { onDismiss(raid) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.friend_dismiss))
                }
            }
        }
    }
}

@Composable
private fun FriendRow(
    friend: FriendItem,
    onVisit: () -> Unit,
    onRaid: () -> Unit,
    onUnpair: () -> Unit,
) {
    var showUnpair by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AppDimens.spacing16)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = friend.baseName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                SnapshotChip(status = friend.snapshotStatus)
            }
            Spacer(Modifier.height(AppDimens.spacing8))
            Row(horizontalArrangement = Arrangement.spacedBy(AppDimens.spacing8)) {
                OutlinedButton(onClick = onVisit, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Visibility, contentDescription = null)
                    Spacer(Modifier.width(AppDimens.spacing4))
                    Text(stringResource(R.string.friend_visit))
                }
                Button(onClick = onRaid, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.friend_raid))
                }
            }
            TextButton(onClick = { showUnpair = true }) {
                Text(
                    text = stringResource(R.string.friend_unpair),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
    if (showUnpair) {
        UnpairDialog(
            baseName = friend.baseName,
            onConfirm = {
                showUnpair = false
                onUnpair()
            },
            onDismiss = { showUnpair = false },
        )
    }
}

@Composable
private fun UnpairDialog(baseName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.friend_unpair),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.friend_dismiss))
            }
        },
        title = { Text(baseName) },
        text = { Text(stringResource(R.string.friend_unpair_confirm, baseName)) },
    )
}

@Composable
private fun SnapshotChip(status: SnapshotStatus) {
    val text = when (status) {
        SnapshotStatus.FRESH -> stringResource(R.string.friend_snapshot_fresh)
        SnapshotStatus.RAIDED -> stringResource(R.string.friend_snapshot_raided)
        SnapshotStatus.NONE -> stringResource(R.string.friend_snapshot_none)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = AppDimens.spacing8),
    ) {
        Icon(
            Icons.Filled.Shield,
            contentDescription = null,
            modifier = Modifier.size(AppDimens.spacing16),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(AppDimens.spacing4))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatCountdown(millis: Long): String {
    val totalMinutes = (millis / MILLIS_PER_MINUTE).toInt().coerceAtLeast(0)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
