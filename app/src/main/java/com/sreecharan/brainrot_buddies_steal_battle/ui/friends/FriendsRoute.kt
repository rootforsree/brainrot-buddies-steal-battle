package com.sreecharan.brainrot_buddies_steal_battle.ui.friends

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sreecharan.brainrot_buddies_steal_battle.R
import com.sreecharan.brainrot_buddies_steal_battle.ui.components.ParentGateDialog

/**
 * Stateful Friends route. Builder C wires this into AppNavHost under the
 * §7 Friends route and passes [onNavigateToRaidVisit] as
 * `{ navController.navigate(RaidVisit(it)) }`.
 *
 * Bluetooth permissions are requested in-context here with a kid-friendly
 * explanation on the screen itself, never at launch. The friend code is
 * shown only behind Builder C's ParentGateDialog.
 */
@Composable
fun FriendsRoute(
    onNavigateToRaidVisit: (friendGuestId: String) -> Unit,
    viewModel: FriendsViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var gateForCode by remember { mutableStateOf(false) }
    var ceremonyFor by remember { mutableStateOf<String?>(null) }

    val requestBluetooth = rememberBluetoothRequest(
        bluetoothGranted = state.bluetoothGranted,
        onGranted = viewModel.pairing::onBluetoothGranted,
    )

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is FriendsEvent.NavigateToRaidVisit -> onNavigateToRaidVisit(event.friendGuestId)
                is FriendsEvent.ShowMessage -> snackbarHostState.showSnackbar(
                    context.getString(event.resId, event.arg),
                )
                is FriendsEvent.ShowCeremony -> ceremonyFor = event.friendBaseName
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier,
    ) { padding ->
        FriendsScreen(
            state = state,
            onTurnOnNearby = requestBluetooth,
            onFindNearby = viewModel.pairing::onFindNearby,
            onStopFinding = viewModel.pairing::onStopFinding,
            onPeerSelected = viewModel.pairing::onPeerSelected,
            onMatchConfirmed = viewModel.pairing::onMatchConfirmed,
            onPairingCancelled = viewModel.pairing::onPairingCancelled,
            onCodeConfirmed = viewModel.pairing::onCodeConfirmed,
            onCodeRejected = viewModel.pairing::onCodeRejected,
            onShowCode = { gateForCode = true },
            onHideCode = viewModel.pairing::onHideCode,
            onUnpair = viewModel.roster::onUnpair,
            onVisit = { viewModel.roster.onVisitFriend(it) },
            onRaid = { viewModel.roster.onVisitFriend(it) },
            onDismissRaid = viewModel.roster::onDismissRaid,
            onRevenge = viewModel.roster::onRevenge,
            modifier = Modifier.padding(padding),
        )
    }

    if (gateForCode) {
        ParentGateDialog(
            onPassed = {
                gateForCode = false
                viewModel.pairing.onRevealCode()
            },
            onDismiss = { gateForCode = false },
        )
    }

    ceremonyFor?.let { baseName ->
        CeremonyDialog(baseName = baseName, onDismiss = { ceremonyFor = null })
    }
}

/**
 * Bluetooth permission handling for P2P, kept out of the route body.
 * Permissions are requested in-context with a kid-friendly explanation on
 * the Friends screen itself, never at launch. Below Android 12 (API 31)
 * there is nothing to request: [FriendsViewModel.p2pAvailable] is false and
 * the screen shows the "needs Android 12" message instead.
 */
@Composable
private fun rememberBluetoothRequest(
    bluetoothGranted: Boolean,
    onGranted: () -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val permissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
            )
        } else {
            emptyArray()
        }
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants.values.all { it }) onGranted()
    }

    // If permissions were granted earlier (e.g. after rotation), pick up hosting.
    LaunchedEffect(Unit) {
        val granted = permissions.isNotEmpty() && permissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
        if (granted && !bluetoothGranted) onGranted()
    }
    return { launcher.launch(permissions) }
}

@Composable
private fun CeremonyDialog(baseName: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.friend_dismiss))
            }
        },
        title = { Text(stringResource(R.string.raid_ceremony_title)) },
        text = { Text(stringResource(R.string.raid_ceremony_body, baseName)) },
    )
}
