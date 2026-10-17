package com.sreecharan.brainrot_buddies_steal_battle.ui.shop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sreecharan.brainrot_buddies_steal_battle.R
import com.sreecharan.brainrot_buddies_steal_battle.ui.components.GroupCard
import com.sreecharan.brainrot_buddies_steal_battle.ui.components.HoldToConfirmButton
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.AppDimens
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.AppShapes
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.AppTypography
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.LocalAppColors

@Composable
fun FreeCoinsSection(
    adReady: Boolean,
    onWatchAd: (AdRewardKind) -> Unit,
) {
    val colors = LocalAppColors.current
    var kind by remember { mutableStateOf(AdRewardKind.COINS) }
    GroupCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppDimens.spacing16),
            verticalArrangement = Arrangement.spacedBy(AppDimens.spacing12),
        ) {
            Text(
                text = stringResource(R.string.shop_free_title),
                style = AppTypography.headline,
                color = colors.label,
            )
            Text(
                text = stringResource(R.string.shop_free_sub),
                style = AppTypography.footnote,
                color = colors.secondaryLabel,
            )
            if (adReady) {
                FreeCoinsReady(
                    kind = kind,
                    onKindChange = { kind = it },
                    onWatchAd = onWatchAd,
                )
            } else {
                Text(
                    text = stringResource(R.string.shop_ad_not_ready),
                    style = AppTypography.body,
                    color = colors.secondaryLabel,
                )
            }
        }
    }
}

@Composable
private fun FreeCoinsReady(
    kind: AdRewardKind,
    onKindChange: (AdRewardKind) -> Unit,
    onWatchAd: (AdRewardKind) -> Unit,
) {
    val colors = LocalAppColors.current
    Text(
        text = stringResource(R.string.shop_free_pick),
        style = AppTypography.body,
        color = colors.label,
    )
    RewardKindPicker(
        selected = kind,
        onSelect = onKindChange,
    )
    HoldToConfirmButton(
        text = stringResource(R.string.shop_free_title),
        onConfirm = { onWatchAd(kind) },
        modifier = Modifier.fillMaxWidth(),
        holdMillis = 1500,
    )
}

@Composable
private fun RewardKindPicker(
    selected: AdRewardKind,
    onSelect: (AdRewardKind) -> Unit,
) {
    val colors = LocalAppColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(AppDimens.spacing8)) {
        AdRewardKind.entries.forEach { option ->
            val isSelected = selected == option
            Button(
                onClick = { onSelect(option) },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
                shape = AppShapes.capsule,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSelected) {
                        colors.accent
                    } else {
                        colors.accent.copy(alpha = 0.15f)
                    },
                    contentColor = if (isSelected) {
                        Color.White
                    } else {
                        colors.accent
                    },
                ),
            ) {
                Text(
                    text = stringResource(
                        if (option == AdRewardKind.COINS) {
                            R.string.shop_free_coins
                        } else {
                            R.string.shop_free_bites
                        },
                    ),
                )
            }
        }
    }
}
