package com.sreecharan.brainrot_buddies_steal_battle.ui.friends

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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

private const val CODE_GROUP_SIZE = 3
private val TOKEN_FONT_SIZE = 28.sp
private val VERIFY_CODE_FONT_SIZE = 32.sp
private val VERIFY_CODE_LETTER_SPACING = 6.sp

/**
 * The pairing ceremony card: idle / discovering / waiting / token confirm /
 * code verify / paired / failed. Extracted from FriendsScreen so the screen
 * file stays within the complexity budget.
 */
@Composable
@Suppress("LongParameterList")
internal fun PairingCard(
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
private fun PairIdle(
    bluetoothGranted: Boolean,
    onFindNearby: () -> Unit,
) {
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
private fun PairFailed(
    phase: PairingPhase.Failed,
    onFindNearby: () -> Unit,
) {
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
