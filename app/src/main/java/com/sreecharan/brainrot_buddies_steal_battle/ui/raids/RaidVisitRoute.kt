package com.sreecharan.brainrot_buddies_steal_battle.ui.raids

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sreecharan.brainrot_buddies_steal_battle.R

/**
 * Stateful raid-visit route. Builder C wires this into AppNavHost under the
 * §7 `RaidVisit(friendGuestId)` route and passes [onNavigateBack] as
 * `{ navController.popBackStack() }`.
 *
 * Staged 5s auto-replays (§2 V1.1) are Builder B's juice: [onPlayStealReplay],
 * [onPlayRevengeReplay], and [onPlayStealFail] are the seams where Builder B
 * plugs the cinematic player. They default to no-ops so this screen works
 * before Builder B lands.
 */
@Composable
fun RaidVisitRoute(
    onNavigateBack: () -> Unit,
    onPlayStealReplay: (buddyName: String, attackerName: String) -> Unit = {},
    onPlayRevengeReplay: (buddyName: String, attackerName: String) -> Unit = {},
    onPlayStealFail: () -> Unit = {},
    viewModel: RaidVisitViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var ceremonyFor by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is RaidVisitEvent.PlayStealReplay -> onPlayStealReplay(event.buddyName, event.attackerName)
                is RaidVisitEvent.PlayRevengeReplay -> onPlayRevengeReplay(event.buddyName, event.attackerName)
                is RaidVisitEvent.PlayStealFail -> onPlayStealFail()
                is RaidVisitEvent.ShowMessage -> snackbarHostState.showSnackbar(
                    context.getString(event.resId),
                )
                is RaidVisitEvent.ShowCeremony -> ceremonyFor = event.friendBaseName
                is RaidVisitEvent.NavigateBack -> onNavigateBack()
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier,
    ) { padding ->
        RaidVisitScreen(
            state = state,
            onSlotSelected = viewModel::onSlotSelected,
            onStealClicked = viewModel::onStealClicked,
            onRevengeClicked = viewModel::onRevengeClicked,
            onPhraseSelected = viewModel::onPhraseSelected,
            onEmoteSelected = viewModel::onEmoteSelected,
            onLeave = onNavigateBack,
            modifier = Modifier.padding(padding),
        )
    }

    ceremonyFor?.let { baseName ->
        AlertDialog(
            onDismissRequest = { ceremonyFor = null },
            confirmButton = {
                TextButton(onClick = { ceremonyFor = null }) {
                    Text(stringResource(R.string.friend_dismiss))
                }
            },
            title = { Text(stringResource(R.string.raid_ceremony_title)) },
            text = { Text(stringResource(R.string.raid_ceremony_body, baseName)) },
        )
    }
}
