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
import kotlin.math.roundToInt
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.delay

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

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            message = when (event) {
                SettingsEvent.RemoveAdsPurchased ->
                    context.getString(R.string.settings_remove_ads_done)
                SettingsEvent.BillingFailed ->
                    context.getString(R.string.shop_error)
                SettingsEvent.DataDeleted ->
                    context.getString(R.string.settings_delete_done)
                SettingsEvent.DeleteFailed ->
                    context.getString(R.string.settings_delete_failed)
            }
        }
    }
    LaunchedEffect(message) {
        if (message != null) {
            delay(2_500)
            message = null
        }
    }

    SettingsScreenContent(
        state = state,
        redeemState = redeemState,
        message = message,
        onThemeModeSelected = viewModel::onThemeModeSelected,
        onHapticsToggled = viewModel::onHapticsToggled,
        onMusicVolume = viewModel::onMusicVolumeChanged,
        onSfxVolume = viewModel::onSfxVolumeChanged,
        onVoiceVolume = viewModel::onVoiceVolumeChanged,
        onBennyHintsToggled = viewModel::onBennyHintsToggled,
        onRedeem = giftsViewModel::onRedeemCode,
        onRedeemConsumed = giftsViewModel::onRedeemResultConsumed,
        onForParents = onForParents,
        onPosterImport = onPosterImport,
        onRemoveAds = { removeAdsGate = true },
        onDeleteData = { deleteGate = true },
    )

    if (removeAdsGate) {
        ParentGateDialog(
            requireMath = true,
            onPassed = {
                removeAdsGate = false
                viewModel.onRemoveAdsConfirmed(activity)
            },
            onDismiss = { removeAdsGate = false },
        )
    }
    if (deleteGate) {
        ParentGateDialog(
            title = stringResource(R.string.settings_delete_title),
            requireMath = true,
            onPassed = {
                deleteGate = false
                viewModel.onDeleteDataConfirmed()
            },
            onDismiss = { deleteGate = false },
        )
    }
}

private data class ThemeOption(
    val mode: ThemeMode,
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    val icon: ImageVector,
)

private val ThemeOptions = persistentListOf(
    ThemeOption(
        mode = ThemeMode.SYSTEM,
        titleRes = R.string.theme_system,
        subtitleRes = R.string.theme_system_sub,
        icon = Icons.Filled.SettingsBrightness,
    ),
    ThemeOption(
        mode = ThemeMode.LIGHT,
        titleRes = R.string.theme_light,
        subtitleRes = R.string.theme_light_sub,
        icon = Icons.Filled.LightMode,
    ),
    ThemeOption(
        mode = ThemeMode.DARK,
        titleRes = R.string.theme_dark,
        subtitleRes = R.string.theme_dark_sub,
        icon = Icons.Filled.DarkMode,
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenContent(
    state: SettingsUiState,
    redeemState: RedeemUiState,
    message: String?,
    onThemeModeSelected: (ThemeMode) -> Unit,
    onHapticsToggled: (Boolean) -> Unit,
    onMusicVolume: (Int) -> Unit,
    onSfxVolume: (Int) -> Unit,
    onVoiceVolume: (Int) -> Unit,
    onBennyHintsToggled: (Boolean) -> Unit,
    onRedeem: (String) -> Unit,
    onRedeemConsumed: () -> Unit,
    onForParents: () -> Unit,
    onPosterImport: () -> Unit,
    onRemoveAds: () -> Unit,
    onDeleteData: () -> Unit,
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
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = colors.groupedBackground,
                    scrolledContainerColor = colors.groupedBackground,
                ),
            )
        },
        containerColor = colors.groupedBackground,
    ) { padding ->
        InsetGroupedList(
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + AppDimens.tabBarClearance,
            ),
        ) {
            if (message != null) {
                item(key = "message", contentType = "message") {
                    Text(
                        text = message,
                        style = AppTypography.body,
                        color = colors.label,
                        modifier = Modifier.padding(horizontal = AppDimens.spacing16),
                    )
                }
            }
            item(key = "audio-header", contentType = "header") {
                SectionHeader(title = stringResource(R.string.settings_section_audio))
            }
            item(key = "audio-card", contentType = "card") {
                GroupCard {
                    AudioSliderRow(
                        title = stringResource(R.string.settings_music),
                        subtitle = stringResource(R.string.settings_music_sub),
                        icon = Icons.Filled.MusicNote,
                        value = state.musicVolume,
                        onCommit = onMusicVolume,
                    )
                    RowDivider()
                    AudioSliderRow(
                        title = stringResource(R.string.settings_sounds),
                        subtitle = stringResource(R.string.settings_sounds_sub),
                        icon = Icons.Filled.VolumeUp,
                        value = state.sfxVolume,
                        onCommit = onSfxVolume,
                    )
                    RowDivider()
                    AudioSliderRow(
                        title = stringResource(R.string.settings_voices),
                        subtitle = stringResource(R.string.settings_voices_sub),
                        icon = Icons.Filled.RecordVoiceOver,
                        value = state.voiceVolume,
                        onCommit = onVoiceVolume,
                    )
                }
            }
            item(key = "appearance-header", contentType = "header") {
                SectionHeader(title = stringResource(R.string.section_appearance))
            }
            item(key = "appearance-card", contentType = "card") {
                GroupCard {
                    ThemeOptions.forEachIndexed { index, option ->
                        if (index > 0) {
                            RowDivider()
                        }
                        val selected = state.themeMode == option.mode
                        SettingsRow(
                            title = stringResource(option.titleRes),
                            subtitle = stringResource(option.subtitleRes),
                            leadingIcon = {
                                Icon(
                                    imageVector = option.icon,
                                    contentDescription = null,
                                    tint = colors.secondaryLabel,
                                    modifier = Modifier.size(AppDimens.iconSize),
                                )
                            },
                            trailing = {
                                RadioButton(
                                    selected = selected,
                                    onClick = null,
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = colors.accent,
                                    ),
                                )
                            },
                            selectableSelected = selected,
                            onClick = { onThemeModeSelected(option.mode) },
                        )
                    }
                }
            }
            item(key = "general-header", contentType = "header") {
                SectionHeader(title = stringResource(R.string.section_general))
            }
            item(key = "general-card", contentType = "card") {
                GroupCard {
                    SettingsRow(
                        title = stringResource(R.string.settings_haptics_title),
                        subtitle = stringResource(R.string.settings_haptics_sub),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Vibration,
                                contentDescription = null,
                                tint = colors.secondaryLabel,
                                modifier = Modifier.size(AppDimens.iconSize),
                            )
                        },
                        trailing = {
                            Switch(
                                checked = state.hapticsEnabled,
                                onCheckedChange = null,
                            )
                        },
                        toggleableValue = state.hapticsEnabled,
                        onClick = { onHapticsToggled(!state.hapticsEnabled) },
                    )
                    RowDivider()
                    SettingsRow(
                        title = stringResource(R.string.settings_benny_title),
                        subtitle = stringResource(R.string.settings_benny_sub),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Face,
                                contentDescription = null,
                                tint = colors.secondaryLabel,
                                modifier = Modifier.size(AppDimens.iconSize),
                            )
                        },
                        trailing = {
                            Switch(
                                checked = state.bennyHints,
                                onCheckedChange = null,
                            )
                        },
                        toggleableValue = state.bennyHints,
                        onClick = { onBennyHintsToggled(!state.bennyHints) },
                    )
                    RowDivider()
                    SettingsRow(
                        title = stringResource(R.string.settings_notifications_title),
                        subtitle = stringResource(R.string.settings_notifications_soon),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Notifications,
                                contentDescription = null,
                                tint = colors.secondaryLabel.copy(alpha = 0.4f),
                                modifier = Modifier.size(AppDimens.iconSize),
                            )
                        },
                    )
                }
            }
            item(key = "redeem", contentType = "card") {
                RedeemCodeSection(
                    redeemState = redeemState,
                    onRedeem = onRedeem,
                    onResultConsumed = onRedeemConsumed,
                )
            }
            item(key = "grownups-header", contentType = "header") {
                SectionHeader(title = stringResource(R.string.settings_section_grownups))
            }
            item(key = "grownups-card", contentType = "card") {
                GroupCard {
                    SettingsRow(
                        title = stringResource(R.string.settings_parents_title),
                        subtitle = stringResource(R.string.settings_parents_sub),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Shield,
                                contentDescription = null,
                                tint = colors.secondaryLabel,
                                modifier = Modifier.size(AppDimens.iconSize),
                            )
                        },
                        trailing = {
                            Icon(
                                imageVector = Icons.Filled.ChevronRight,
                                contentDescription = null,
                                tint = colors.secondaryLabel,
                                modifier = Modifier.size(AppDimens.iconSize),
                            )
                        },
                        onClick = onForParents,
                    )
                    RowDivider()
                    SettingsRow(
                        title = stringResource(R.string.settings_poster_title),
                        subtitle = stringResource(R.string.settings_poster_sub),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Image,
                                contentDescription = null,
                                tint = colors.secondaryLabel,
                                modifier = Modifier.size(AppDimens.iconSize),
                            )
                        },
                        trailing = {
                            Icon(
                                imageVector = Icons.Filled.ChevronRight,
                                contentDescription = null,
                                tint = colors.secondaryLabel,
                                modifier = Modifier.size(AppDimens.iconSize),
                            )
                        },
                        onClick = onPosterImport,
                    )
                    RowDivider()
                    if (state.adsRemoved) {
                        SettingsRow(
                            title = stringResource(R.string.settings_remove_ads),
                            subtitle = stringResource(R.string.settings_remove_ads_done),
                        )
                    } else {
                        SettingsRow(
                            title = stringResource(R.string.settings_remove_ads),
                            subtitle = stringResource(R.string.settings_remove_ads_sub),
                            onClick = onRemoveAds,
                        )
                    }
                    RowDivider()
                    SettingsRow(
                        title = stringResource(R.string.settings_delete_title),
                        subtitle = stringResource(R.string.settings_delete_sub),
                        trailing = if (state.isDeleting) {
                            {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = colors.destructive,
                                )
                            }
                        } else {
                            null
                        },
                        onClick = onDeleteData,
                    )
                }
            }
        }
    }
}

/**
 * 0-100 slider. Drags update locally for instant feedback; the value commits
 * to DataStore once when the finger lifts.
 */
@Composable
private fun AudioSliderRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    value: Int,
    onCommit: (Int) -> Unit,
) {
    val colors = LocalAppColors.current
    var slider by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = AppDimens.rowHorizontalPadding,
                vertical = AppDimens.rowVerticalPadding,
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppDimens.spacing12),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.secondaryLabel,
                modifier = Modifier.size(AppDimens.iconSize),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = AppTypography.body,
                    color = colors.label,
                )
                Text(
                    text = subtitle,
                    style = AppTypography.footnote,
                    color = colors.secondaryLabel,
                )
            }
            Text(
                text = slider.roundToInt().toString(),
                style = AppTypography.headline,
                color = colors.accent,
            )
        }
        Slider(
            value = slider,
            onValueChange = { slider = it },
            valueRange = 0f..100f,
            steps = 99,
            onValueChangeFinished = { onCommit(slider.roundToInt()) },
        )
    }
}
