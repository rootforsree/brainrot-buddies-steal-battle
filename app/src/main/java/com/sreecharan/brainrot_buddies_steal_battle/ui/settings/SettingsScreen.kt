package com.sreecharan.brainrot_buddies_steal_battle.ui.settings

import android.app.Activity
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sreecharan.brainrot_buddies_steal_battle.R
import com.sreecharan.brainrot_buddies_steal_battle.data.model.ThemeMode
import com.sreecharan.brainrot_buddies_steal_battle.ui.components.GroupCard
import com.sreecharan.brainrot_buddies_steal_battle.ui.components.InsetGroupedList
import com.sreecharan.brainrot_buddies_steal_battle.ui.components.ParentGateDialog
import com.sreecharan.brainrot_buddies_steal_battle.ui.components.RowDivider
import com.sreecharan.brainrot_buddies_steal_battle.ui.components.SectionHeader
import com.sreecharan.brainrot_buddies_steal_battle.ui.components.SettingsRow
import com.sreecharan.brainrot_buddies_steal_battle.ui.gifts.GiftsViewModel
import com.sreecharan.brainrot_buddies_steal_battle.ui.gifts.RedeemCodeSection
import com.sreecharan.brainrot_buddies_steal_battle.ui.gifts.RedeemUiState
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.AppDimens
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.AppTypography
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.LocalAppColors
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun SettingsRoute(
    onForParents: () -> Unit,
    onPosterImport: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    giftsViewModel: GiftsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val redeemState by giftsViewModel.redeemUiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as Activity
    var message by remember { mutableStateOf<String?>(null) }
    var removeAdsGate by remember { mutableStateOf(false) }
    var deleteGate by remember { mutableStateOf(false) }

    SettingsEventEffect(viewModel) { message = it }
    MessageDismissEffect(message) { message = null }

    SettingsScreenContent(
        state = state,
        redeemState = redeemState,
        message = message,
        actions =
            SettingsActions(
                audio =
                    AudioSettingsActions(
                        onMusicVolume = viewModel::onMusicVolumeChanged,
                        onSfxVolume = viewModel::onSfxVolumeChanged,
                        onVoiceVolume = viewModel::onVoiceVolumeChanged,
                    ),
                appearance =
                    AppearanceSettingsActions(
                        onThemeModeSelected = viewModel::onThemeModeSelected,
                    ),
                general =
                    GeneralSettingsActions(
                        onHapticsToggled = viewModel::onHapticsToggled,
                        onBennyHintsToggled = viewModel::onBennyHintsToggled,
                    ),
                redeem =
                    RedeemSettingsActions(
                        onRedeem = giftsViewModel::onRedeemCode,
                        onRedeemConsumed = giftsViewModel::onRedeemResultConsumed,
                    ),
                grownUp =
                    GrownUpSettingsActions(
                        onForParents = onForParents,
                        onPosterImport = onPosterImport,
                        onRemoveAds = { removeAdsGate = true },
                        onDeleteData = { deleteGate = true },
                    ),
            ),
    )

    SettingsRouteDialogs(
        gates =
            SettingsGates(
                removeAdsGate = removeAdsGate,
                deleteGate = deleteGate,
                onRemoveAdsGateChange = { removeAdsGate = it },
                onDeleteGateChange = { deleteGate = it },
            ),
        viewModel = viewModel,
        activity = activity,
    )
}

/** Parent-gate dialog state for the route. */
private data class SettingsGates(
    val removeAdsGate: Boolean,
    val deleteGate: Boolean,
    val onRemoveAdsGateChange: (Boolean) -> Unit,
    val onDeleteGateChange: (Boolean) -> Unit,
)

@Composable
private fun SettingsEventEffect(
    viewModel: SettingsViewModel,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            onMessage(
                when (event) {
                    SettingsEvent.RemoveAdsPurchased -> {
                        context.getString(R.string.settings_remove_ads_done)
                    }

                    SettingsEvent.BillingFailed -> {
                        context.getString(R.string.shop_error)
                    }

                    SettingsEvent.DataDeleted -> {
                        context.getString(R.string.settings_delete_done)
                    }

                    SettingsEvent.DeleteFailed -> {
                        context.getString(R.string.settings_delete_failed)
                    }
                },
            )
        }
    }
}

@Composable
private fun MessageDismissEffect(
    message: String?,
    onDismiss: () -> Unit,
) {
    LaunchedEffect(message) {
        if (message != null) {
            delay(MESSAGE_DISMISS_MS)
            onDismiss()
        }
    }
}

@Composable
private fun SettingsRouteDialogs(
    gates: SettingsGates,
    viewModel: SettingsViewModel,
    activity: Activity,
) {
    if (gates.removeAdsGate) {
        ParentGateDialog(
            requireMath = true,
            onPassed = {
                gates.onRemoveAdsGateChange(false)
                viewModel.onRemoveAdsConfirmed(activity)
            },
            onDismiss = { gates.onRemoveAdsGateChange(false) },
        )
    }
    if (gates.deleteGate) {
        ParentGateDialog(
            title = stringResource(R.string.settings_delete_title),
            requireMath = true,
            onPassed = {
                gates.onDeleteGateChange(false)
                viewModel.onDeleteDataConfirmed()
            },
            onDismiss = { gates.onDeleteGateChange(false) },
        )
    }
}

/** Callbacks for the audio sliders. */
data class AudioSettingsActions(
    val onMusicVolume: (Int) -> Unit,
    val onSfxVolume: (Int) -> Unit,
    val onVoiceVolume: (Int) -> Unit,
)

/** Callbacks for the appearance section. */
data class AppearanceSettingsActions(
    val onThemeModeSelected: (ThemeMode) -> Unit,
)

/** Callbacks for the general section. */
data class GeneralSettingsActions(
    val onHapticsToggled: (Boolean) -> Unit,
    val onBennyHintsToggled: (Boolean) -> Unit,
)

/** Callbacks for the redeem-code section. */
data class RedeemSettingsActions(
    val onRedeem: (String) -> Unit,
    val onRedeemConsumed: () -> Unit,
)

/** Callbacks for the grown-ups section. */
data class GrownUpSettingsActions(
    val onForParents: () -> Unit,
    val onPosterImport: () -> Unit,
    val onRemoveAds: () -> Unit,
    val onDeleteData: () -> Unit,
)

/** All [SettingsScreenContent] callbacks, grouped by section. */
data class SettingsActions(
    val audio: AudioSettingsActions,
    val appearance: AppearanceSettingsActions,
    val general: GeneralSettingsActions,
    val redeem: RedeemSettingsActions,
    val grownUp: GrownUpSettingsActions,
)

/** How long a settings toast message stays visible. */
private const val MESSAGE_DISMISS_MS = 2_500L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenContent(
    state: SettingsUiState,
    redeemState: RedeemUiState,
    message: String?,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = AppTypography.largeTitle,
                    )
                },
                scrollBehavior = scrollBehavior,
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = colors.groupedBackground,
                        scrolledContainerColor = colors.groupedBackground,
                    ),
            )
        },
        containerColor = colors.groupedBackground,
    ) { padding ->
        InsetGroupedList(
            contentPadding =
                PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + AppDimens.tabBarClearance,
                ),
        ) {
            messageItem(message)
            audioSection(state, actions.audio)
            appearanceSection(state, actions.appearance)
            generalSection(state, actions.general)
            item(key = "redeem", contentType = "card") {
                RedeemCodeSection(
                    redeemState = redeemState,
                    onRedeem = actions.redeem.onRedeem,
                    onResultConsumed = actions.redeem.onRedeemConsumed,
                )
            }
            grownUpSection(state, actions.grownUp)
        }
    }
}
