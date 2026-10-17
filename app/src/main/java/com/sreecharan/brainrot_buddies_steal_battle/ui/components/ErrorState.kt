package com.sreecharan.brainrot_buddies_steal_battle.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.sreecharan.brainrot_buddies_steal_battle.R
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.AppDimens
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.AppShapes
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.AppTypography
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.LocalAppColors
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.rememberHapticTick

/**
 * Plain-language error with one retry button. The ViewModel already mapped
 * the failure into a human message; raw codes never reach this screen.
 */
@Composable
fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val tick = rememberHapticTick()
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(AppDimens.spacing32),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = colors.secondaryLabel,
            modifier = Modifier.size(AppDimens.emptyStateIcon),
        )
        Spacer(modifier = Modifier.height(AppDimens.spacing16))
        Text(
            text = message,
            style = AppTypography.body,
            color = colors.secondaryLabel,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(AppDimens.spacing16))
        Button(
            onClick = {
                tick()
                onRetry()
            },
            shape = AppShapes.capsule,
            modifier = Modifier.heightIn(min = AppDimens.rowMinHeight),
        ) {
            Text(
                text = stringResource(R.string.action_retry),
                style = AppTypography.headline,
            )
        }
    }
}
