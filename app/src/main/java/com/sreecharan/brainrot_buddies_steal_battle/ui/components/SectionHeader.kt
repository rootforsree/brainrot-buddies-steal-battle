package com.sreecharan.brainrot_buddies_steal_battle.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.AppDimens
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.AppTypography
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.LocalAppColors
import java.util.Locale

/**
 * Grouped-list section header: 13sp uppercase secondary label, aligned with
 * the card content below it (16dp list inset + 16dp row padding).
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title.uppercase(Locale.getDefault()),
        style = AppTypography.footnote.copy(letterSpacing = 0.5.sp),
        color = LocalAppColors.current.secondaryLabel,
        modifier =
            modifier.padding(
                start = AppDimens.spacing16,
                top = AppDimens.spacing8,
                bottom = AppDimens.spacing8,
            ),
    )
}
