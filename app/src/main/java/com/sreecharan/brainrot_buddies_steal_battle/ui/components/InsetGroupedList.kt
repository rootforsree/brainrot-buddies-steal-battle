package com.sreecharan.brainrot_buddies_steal_battle.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.AppDimens
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.LocalAppColors

/**
 * iOS-style inset grouped list: 16dp horizontal inset on the grouped
 * background, 24dp between sections. Screens pass their inset padding in;
 * the horizontal margin always comes from tokens.
 */
@Composable
fun InsetGroupedList(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: LazyListScope.() -> Unit,
) {
    val colors = LocalAppColors.current
    LazyColumn(
        modifier =
            modifier
                .fillMaxSize()
                .background(colors.groupedBackground),
        contentPadding =
            PaddingValues(
                start = AppDimens.screenMargin,
                top = contentPadding.calculateTopPadding(),
                end = AppDimens.screenMargin,
                bottom = contentPadding.calculateBottomPadding(),
            ),
        verticalArrangement = Arrangement.spacedBy(AppDimens.spacing24),
        content = content,
    )
}
