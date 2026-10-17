package com.sreecharan.brainrot_buddies_steal_battle.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.sreecharan.brainrot_buddies_steal_battle.ui.components.RowDivider
import com.sreecharan.brainrot_buddies_steal_battle.ui.components.SectionHeader
import kotlinx.collections.immutable.persistentListOf
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.sreecharan.brainrot_buddies_steal_battle.R
import com.sreecharan.brainrot_buddies_steal_battle.ui.components.GroupCard
import com.sreecharan.brainrot_buddies_steal_battle.ui.components.SettingsRow
import com.sreecharan.brainrot_buddies_steal_battle.ui.gifts.RedeemUiState
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.AppDimens
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.AppTypography
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.LocalAppColors
import com.sreecharan.brainrot_buddies_steal_battle.data.model.ThemeMode

/**
 * Settings list sections, extracted from SettingsScreen.kt to keep the
 * file under the function-count ceiling.
 */
internal fun LazyListScope.messageItem(message: String?) {
    if (message != null) {
        item(key = "message", contentType = "message") {
            Text(
                text = message,
                style = AppTypography.body,
                color = LocalAppColors.current.label,
                modifier = Modifier.padding(horizontal = AppDimens.spacing16),
            )
        }
    }
}

internal fun LazyListScope.audioSection(
    state: SettingsUiState,
    actions: AudioSettingsActions,
) {
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
                onCommit = actions.onMusicVolume,
            )
            RowDivider()
            AudioSliderRow(
                title = stringResource(R.string.settings_sounds),
                subtitle = stringResource(R.string.settings_sounds_sub),
                icon = Icons.Filled.VolumeUp,
                value = state.sfxVolume,
                onCommit = actions.onSfxVolume,
            )
            RowDivider()
            AudioSliderRow(
                title = stringResource(R.string.settings_voices),
                subtitle = stringResource(R.string.settings_voices_sub),
                icon = Icons.Filled.RecordVoiceOver,
                value = state.voiceVolume,
                onCommit = actions.onVoiceVolume,
            )
        }
    }
}

internal fun LazyListScope.appearanceSection(
    state: SettingsUiState,
    actions: AppearanceSettingsActions,
) {
    item(key = "appearance-header", contentType = "header") {
        SectionHeader(title = stringResource(R.string.section_appearance))
    }
    item(key = "appearance-card", contentType = "card") {
        GroupCard {
            ThemeOptions.forEachIndexed { index, option ->
                if (index > 0) {
                    RowDivider()
                }
                ThemeOptionRow(
                    option = option,
                    selected = state.themeMode == option.mode,
                    onSelect = { actions.onThemeModeSelected(option.mode) },
                )
            }
        }
    }
}

@Composable
internal fun ThemeOptionRow(
    option: ThemeOption,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val colors = LocalAppColors.current
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
        onClick = onSelect,
    )
}

internal fun LazyListScope.generalSection(
    state: SettingsUiState,
    actions: GeneralSettingsActions,
) {
    item(key = "general-header", contentType = "header") {
        SectionHeader(title = stringResource(R.string.section_general))
    }
    item(key = "general-card", contentType = "card") {
        GroupCard {
            GeneralToggleRow(
                titleRes = R.string.settings_haptics_title,
                subtitleRes = R.string.settings_haptics_sub,
                icon = Icons.Filled.TouchApp,
                checked = state.hapticsEnabled,
                onToggle = { actions.onHapticsToggled(!state.hapticsEnabled) },
            )
            RowDivider()
            GeneralToggleRow(
                titleRes = R.string.settings_benny_title,
                subtitleRes = R.string.settings_benny_sub,
                icon = Icons.Filled.SentimentSatisfied,
                checked = state.bennyHints,
                onToggle = { actions.onBennyHintsToggled(!state.bennyHints) },
            )
            RowDivider()
            SettingsRow(
                title = stringResource(R.string.settings_notifications_title),
                subtitle = stringResource(R.string.settings_notifications_soon),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Notifications,
                        contentDescription = null,
                        tint = LocalAppColors.current.secondaryLabel.copy(alpha = 0.4f),
                        modifier = Modifier.size(AppDimens.iconSize),
                    )
                },
            )
        }
    }
}

@Composable
internal fun GeneralToggleRow(
    titleRes: Int,
    subtitleRes: Int,
    icon: ImageVector,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    val colors = LocalAppColors.current
    SettingsRow(
        title = stringResource(titleRes),
        subtitle = stringResource(subtitleRes),
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.secondaryLabel,
                modifier = Modifier.size(AppDimens.iconSize),
            )
        },
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = null,
            )
        },
        toggleableValue = checked,
        onClick = onToggle,
    )
}

internal fun LazyListScope.grownUpSection(
    state: SettingsUiState,
    actions: GrownUpSettingsActions,
) {
    item(key = "grownups-header", contentType = "header") {
        SectionHeader(title = stringResource(R.string.settings_section_grownups))
    }
    item(key = "grownups-card", contentType = "card") {
        GroupCard {
            GrownUpLinkRow(
                titleRes = R.string.settings_parents_title,
                subtitleRes = R.string.settings_parents_sub,
                icon = Icons.Filled.Shield,
                onClick = actions.onForParents,
            )
            RowDivider()
            GrownUpLinkRow(
                titleRes = R.string.settings_poster_title,
                subtitleRes = R.string.settings_poster_sub,
                icon = Icons.Filled.Image,
                onClick = actions.onPosterImport,
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
                    onClick = actions.onRemoveAds,
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
                            color = LocalAppColors.current.destructive,
                        )
                    }
                } else {
                    null
                },
                onClick = actions.onDeleteData,
            )
        }
    }
}

@Composable
internal fun GrownUpLinkRow(
    titleRes: Int,
    subtitleRes: Int,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val colors = LocalAppColors.current
    SettingsRow(
        title = stringResource(titleRes),
        subtitle = stringResource(subtitleRes),
        leadingIcon = {
            Icon(
                imageVector = icon,
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
        onClick = onClick,
    )
}

/**
 * 0-100 slider. Drags update locally for instant feedback; the value commits
 * to DataStore once when the finger lifts.
 */
@Composable
internal fun AudioSliderRow(
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

internal data class ThemeOption(
    val mode: ThemeMode,
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    val icon: ImageVector,
)
