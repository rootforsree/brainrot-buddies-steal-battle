package com.sreecharan.brainrot_buddies_steal_battle.ui.shop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sreecharan.brainrot_buddies_steal_battle.R
import com.sreecharan.brainrot_buddies_steal_battle.ui.components.BuddyImage
import com.sreecharan.brainrot_buddies_steal_battle.ui.components.GroupCard
import com.sreecharan.brainrot_buddies_steal_battle.ui.components.HoldToConfirmButton
import com.sreecharan.brainrot_buddies_steal_battle.ui.components.SettingsRow
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.AppDimens
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.AppShapes
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.AppTypography
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.LocalAppColors
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay

/** Re-tick cadence for shield/ad countdown labels. */
private const val TICK_MS = 1_000L
private const val MILLIS_PER_SECOND = 1000L
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3600L

/** Re-ticks every second for shield/ad countdown labels. */
@Composable
internal fun rememberNowMs(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(TICK_MS)
            now = System.currentTimeMillis()
        }
    }
    return now
}

internal fun formatDuration(ms: Long): String {
    val totalSec = (ms / MILLIS_PER_SECOND).coerceAtLeast(0)
    val h = totalSec / SECONDS_PER_HOUR
    val m = (totalSec % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
    val s = totalSec % SECONDS_PER_MINUTE
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlotShieldSheet(
    rows: ImmutableList<SlotShieldRow>,
    onShieldSlot: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
    ) {
        Column(
            modifier = Modifier.padding(
                start = AppDimens.spacing16,
                end = AppDimens.spacing16,
                bottom = AppDimens.spacing32,
            ),
            verticalArrangement = Arrangement.spacedBy(AppDimens.spacing8),
        ) {
            Text(
                text = stringResource(R.string.shop_shield_pick_slot),
                style = AppTypography.title2,
                color = LocalAppColors.current.label,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            rows.forEach { row ->
                SlotShieldRowCard(
                    row = row,
                    onShieldSlot = onShieldSlot,
                )
            }
        }
    }
}

@Composable
private fun SlotShieldRowCard(
    row: SlotShieldRow,
    onShieldSlot: (Int) -> Unit,
) {
    val colors = LocalAppColors.current
    GroupCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppDimens.spacing12),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppDimens.spacing12),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.buddyName
                        ?: stringResource(R.string.shop_slot_next),
                    style = AppTypography.headline,
                    color = colors.label,
                )
                Text(
                    text = if (row.shieldActive) {
                        stringResource(R.string.shop_shield_active)
                    } else {
                        stringResource(R.string.coins_count, SLOT_SHIELD_COST)
                    },
                    style = AppTypography.footnote,
                    color = if (row.shieldActive) {
                        colors.success
                    } else {
                        colors.secondaryLabel
                    },
                )
            }
            if (!row.shieldActive) {
                HoldToConfirmButton(
                    text = stringResource(R.string.shop_hold_to_buy),
                    onConfirm = { onShieldSlot(row.slotIndex) },
                    holdMillis = 1500,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HatEquipSheet(
    buddies: ImmutableList<UpgradeRow>,
    onPickBuddy: (String) -> Unit,
    onRemoveHat: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
    ) {
        Column(
            modifier = Modifier.padding(
                start = AppDimens.spacing16,
                end = AppDimens.spacing16,
                bottom = AppDimens.spacing32,
            ),
            verticalArrangement = Arrangement.spacedBy(AppDimens.spacing8),
        ) {
            Text(
                text = stringResource(R.string.shop_hat_pick_buddy),
                style = AppTypography.title2,
                color = LocalAppColors.current.label,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            buddies.forEach { buddy ->
                GroupCard {
                    SettingsRow(
                        title = buddy.name,
                        subtitle = stringResource(R.string.shop_upgrade_to, buddy.level),
                        leadingIcon = {
                            BuddyImage(
                                imageName = "buddy_${buddy.buddyId}",
                                contentDescription = buddy.name,
                                modifier = Modifier.size(AppDimens.iconSize),
                            )
                        },
                        onClick = { onPickBuddy(buddy.uid) },
                    )
                }
            }
            Button(
                onClick = onRemoveHat,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                shape = AppShapes.capsule,
                colors = ButtonDefaults.buttonColors(
                    containerColor = LocalAppColors.current.destructive.copy(alpha = 0.15f),
                    contentColor = LocalAppColors.current.destructive,
                ),
            ) {
                Text(text = stringResource(R.string.shop_hat_remove))
            }
        }
    }
}
