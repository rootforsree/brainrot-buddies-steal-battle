package com.sreecharan.brainrot_buddies_steal_battle.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.AppDimens
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.AppTypography
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.LocalAppColors
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.rememberHapticTick

// Public API: SettingsScreen and AboutScreen call with all 9 arguments, so
// the parameter list cannot be regrouped without breaking those call sites.

/**
 * One settings row: at least 48dp tall, optional leading icon, title,
 * subtitle and trailing slot. Exactly one interaction mode is active:
 * toggleable (switch rows), selectable (radio rows) or clickable.
 * Children merge into one TalkBack node so the row announces as a single
 * control, and every tap plays a light haptic tick unless haptics are off.
 */
@Suppress("LongParameterList")
@Composable
fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    toggleableValue: Boolean? = null,
    selectableSelected: Boolean? = null,
    contentDescription: String? = null,
) {
    val colors = LocalAppColors.current
    val tick = rememberHapticTick()
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = AppDimens.rowMinHeight)
                .then(rowInteractionModifier(tick, onClick, toggleableValue, selectableSelected))
                .semantics(mergeDescendants = true) {
                    this.contentDescription = rowDescription(title, subtitle, contentDescription)
                }.padding(
                    horizontal = AppDimens.rowHorizontalPadding,
                    vertical = AppDimens.rowVerticalPadding,
                ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.spacing12),
    ) {
        if (leadingIcon != null) {
            Box(modifier = Modifier.size(AppDimens.iconSize)) {
                leadingIcon()
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = AppTypography.body,
                color = colors.label,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = AppTypography.footnote,
                    color = colors.secondaryLabel,
                )
            }
        }
        if (trailing != null) {
            trailing()
        }
    }
}

private fun rowDescription(
    title: String,
    subtitle: String?,
    contentDescription: String?,
): String =
    contentDescription ?: buildString {
        append(title)
        if (subtitle != null) {
            append(", ")
            append(subtitle)
        }
    }

/** Exactly one interaction mode wins: toggleable, selectable, or clickable. */
private fun rowInteractionModifier(
    tick: () -> Unit,
    onClick: (() -> Unit)?,
    toggleableValue: Boolean?,
    selectableSelected: Boolean?,
): Modifier =
    when {
        toggleableValue != null -> {
            Modifier.toggleable(
                value = toggleableValue,
                role = Role.Switch,
                onValueChange = {
                    tick()
                    onClick?.invoke()
                },
            )
        }

        selectableSelected != null -> {
            Modifier.selectable(
                selected = selectableSelected,
                role = Role.RadioButton,
                onClick = {
                    tick()
                    onClick?.invoke()
                },
            )
        }

        onClick != null -> {
            Modifier.clickable(
                role = Role.Button,
                onClick = {
                    tick()
                    onClick()
                },
            )
        }

        else -> {
            Modifier
        }
    }
