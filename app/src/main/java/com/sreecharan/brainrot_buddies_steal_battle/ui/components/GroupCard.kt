package com.sreecharan.brainrot_buddies_steal_battle.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.AppDimens
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.AppShapes
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.LocalAppColors

/**
 * The grouped card rows live in: 12dp radius, card surface, no shadow.
 * Concentric radii: inner content sits on the 16dp row padding, so the
 * visual radius steps down naturally inside the 12dp outer radius.
 */
@Composable
fun GroupCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = AppShapes.card,
        color = LocalAppColors.current.cardSurface,
        content = { Column(content = content) },
    )
}

/** iOS-style separator: starts after the 16dp row padding, hairline thin. */
@Composable
fun RowDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(start = AppDimens.rowHorizontalPadding),
        thickness = AppDimens.hairline,
        color = LocalAppColors.current.secondaryLabel.copy(alpha = 0.2f),
    )
}
