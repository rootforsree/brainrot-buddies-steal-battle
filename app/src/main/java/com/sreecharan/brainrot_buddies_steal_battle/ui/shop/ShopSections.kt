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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sreecharan.brainrot_buddies_steal_battle.billing.BillingProducts
import com.sreecharan.brainrot_buddies_steal_battle.R
import com.sreecharan.brainrot_buddies_steal_battle.ui.components.BuddyImage
import com.sreecharan.brainrot_buddies_steal_battle.ui.components.GroupCard
import com.sreecharan.brainrot_buddies_steal_battle.ui.components.HoldToConfirmButton
import com.sreecharan.brainrot_buddies_steal_battle.ui.components.RowDivider
import com.sreecharan.brainrot_buddies_steal_battle.ui.components.SettingsRow
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.AppDimens
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.AppShapes
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.AppTypography
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.LocalAppColors
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay

/** Re-ticks every second for shield/ad countdown labels. */
@Composable
private fun rememberNowMs(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }
    return now
}

internal fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1_000).coerceAtLeast(0)
    val h = totalSec / 3_600
    val m = (totalSec % 3_600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

private fun formatIncome(income: Double): String =
    if (income == income.toLong().toDouble()) {
        income.toLong().toString()
    } else {
        "%.1f".format(income)
    }

private fun hatNameRes(hatId: String): Int = when (hatId) {
    BillingProducts.HAT_PARTY_CONE -> R.string.billing_hat_party_cone
    BillingProducts.HAT_TINY_CROWN -> R.string.billing_hat_tiny_crown
    else -> R.string.billing_hat_propeller_cap
}

@Composable
fun SlotSection(unlocked: Int, nextCost: Long?, onBuySlot: () -> Unit) {
    GroupCard {
        SettingsRow(
            title = stringResource(R.string.shop_slot_next),
            subtitle = stringResource(R.string.shop_slot_count, unlocked, MAX_SLOTS),
            leadingIcon = {
                BuddyImage(
                    imageName = "buddy_wobbles",
                    contentDescription = null,
                    modifier = Modifier.size(AppDimens.iconSize),
                )
            },
        )
        if (nextCost != null) {
            RowDivider()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AppDimens.spacing16),
                verticalArrangement = Arrangement.spacedBy(AppDimens.spacing12),
            ) {
                Text(
                    text = stringResource(R.string.coins_count, nextCost),
                    style = AppTypography.title2,
                    color = LocalAppColors.current.accent,
                )
                HoldToConfirmButton(
                    text = stringResource(R.string.shop_hold_to_unlock),
                    onConfirm = onBuySlot,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            RowDivider()
            Text(
                text = stringResource(R.string.shop_slot_maxed),
                style = AppTypography.body,
                color = LocalAppColors.current.secondaryLabel,
                modifier = Modifier.padding(AppDimens.spacing16),
            )
        }
    }
}

@Composable
fun TicketSection(lucky: Int, mega: Int, onBuyMegaTicket: () -> Unit) {
    GroupCard {
        SettingsRow(
            title = stringResource(R.string.shop_lucky_title),
            subtitle = stringResource(R.string.shop_lucky_sub, lucky) + "\n" +
                stringResource(R.string.shop_lucky_earn),
        )
        RowDivider()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppDimens.spacing16),
            verticalArrangement = Arrangement.spacedBy(AppDimens.spacing12),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.shop_mega_title),
                        style = AppTypography.headline,
                        color = LocalAppColors.current.label,
                    )
                    Text(
                        text = stringResource(R.string.shop_mega_sub),
                        style = AppTypography.footnote,
                        color = LocalAppColors.current.secondaryLabel,
                    )
                    Text(
                        text = stringResource(R.string.shop_mega_owned, mega),
                        style = AppTypography.footnote,
                        color = LocalAppColors.current.secondaryLabel,
                    )
                }
                Text(
                    text = stringResource(R.string.coins_count, MEGA_TICKET_COST),
                    style = AppTypography.headline,
                    color = LocalAppColors.current.accent,
                )
            }
            HoldToConfirmButton(
                text = stringResource(R.string.shop_hold_to_buy),
                onConfirm = onBuyMegaTicket,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun UpgradeSection(upgrades: ImmutableList<UpgradeRow>, onUpgrade: (String) -> Unit) {
    GroupCard {
        upgrades.forEachIndexed { index, row ->
            if (index > 0) RowDivider()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AppDimens.spacing16),
                verticalArrangement = Arrangement.spacedBy(AppDimens.spacing8),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.spacing12),
                ) {
                    BuddyImage(
                        imageName = "buddy_${row.buddyId}",
                        contentDescription = row.name,
                        modifier = Modifier.size(56.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = row.name,
                            style = AppTypography.headline,
                            color = LocalAppColors.current.label,
                        )
                        Text(
                            text = stringResource(R.string.shop_upgrade_level, row.level),
                            style = AppTypography.footnote,
                            color = LocalAppColors.current.secondaryLabel,
                        )
                        Text(
                            text = stringResource(
                                R.string.shop_per_sec,
                                formatIncome(row.incomePerSec),
                            ),
                            style = AppTypography.footnote,
                            color = LocalAppColors.current.secondaryLabel,
                        )
                    }
                    if (row.isMaxed) {
                        Text(
                            text = stringResource(R.string.shop_max_level),
                            style = AppTypography.headline,
                            color = LocalAppColors.current.success,
                        )
                    }
                }
                if (!row.isMaxed) {
                    Text(
                        text = stringResource(
                            R.string.shop_upgrade_cost,
                            row.coinCost,
                            row.biteCost,
                        ),
                        style = AppTypography.body,
                        color = LocalAppColors.current.accent,
                    )
                    HoldToConfirmButton(
                        text = stringResource(R.string.shop_upgrade_to, row.level + 1),
                        onConfirm = { onUpgrade(row.uid) },
                        modifier = Modifier.fillMaxWidth(),
                        holdMillis = 1500,
                    )
                }
            }
        }
    }
}

@Composable
fun ShieldSection(
    baseShieldUntil: Long,
    onBuyBaseShield: () -> Unit,
    onOpenSlotShields: () -> Unit,
) {
    val now = rememberNowMs()
    val baseActive = baseShieldUntil > now
    GroupCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppDimens.spacing16),
            verticalArrangement = Arrangement.spacedBy(AppDimens.spacing12),
        ) {
            Text(
                text = stringResource(R.string.shop_shield_base_title),
                style = AppTypography.headline,
                color = LocalAppColors.current.label,
            )
            Text(
                text = stringResource(R.string.shop_shield_base_sub),
                style = AppTypography.footnote,
                color = LocalAppColors.current.secondaryLabel,
            )
            if (baseActive) {
                Text(
                    text = stringResource(R.string.shop_shield_active) + " " +
                        formatDuration(baseShieldUntil - now),
                    style = AppTypography.body,
                    color = LocalAppColors.current.success,
                )
            } else {
                Text(
                    text = stringResource(R.string.coins_count, BASE_SHIELD_COST),
                    style = AppTypography.headline,
                    color = LocalAppColors.current.accent,
                )
                HoldToConfirmButton(
                    text = stringResource(R.string.shop_hold_to_buy),
                    onConfirm = onBuyBaseShield,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        RowDivider()
        SettingsRow(
            title = stringResource(R.string.shop_shield_slot_title),
            subtitle = stringResource(R.string.shop_shield_slot_sub),
            onClick = onOpenSlotShields,
        )
    }
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
                                color = LocalAppColors.current.label,
                            )
                            Text(
                                text = if (row.shieldActive) {
                                    stringResource(R.string.shop_shield_active)
                                } else {
                                    stringResource(R.string.coins_count, SLOT_SHIELD_COST)
                                },
                                style = AppTypography.footnote,
                                color = if (row.shieldActive) {
                                    LocalAppColors.current.success
                                } else {
                                    LocalAppColors.current.secondaryLabel
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
        }
    }
}

@Composable
fun HatSection(
    hats: ImmutableList<HatRow>,
    onBuyHat: (String) -> Unit,
    onEquipHat: (String) -> Unit,
) {
    val colors = LocalAppColors.current
    GroupCard {
        hats.forEachIndexed { index, hat ->
            if (index > 0) RowDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AppDimens.spacing16),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppDimens.spacing12),
            ) {
                BuddyImage(
                    imageName = hat.hatId,
                    contentDescription = stringResource(hatNameRes(hat.hatId)),
                    modifier = Modifier.size(56.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(hatNameRes(hat.hatId)),
                        style = AppTypography.headline,
                        color = colors.label,
                    )
                    val equippedName = hat.equippedOnName
                    Text(
                        text = when {
                            equippedName != null ->
                                stringResource(R.string.shop_hat_equipped, equippedName)
                            hat.owned -> stringResource(R.string.shop_hat_owned)
                            else -> stringResource(R.string.shop_hat_buy)
                        },
                        style = AppTypography.footnote,
                        color = colors.secondaryLabel,
                    )
                }
                if (!hat.owned) {
                    Button(
                        onClick = { onBuyHat(hat.hatId) },
                        modifier = Modifier.heightIn(min = 48.dp),
                        shape = AppShapes.capsule,
                    ) {
                        Text(text = stringResource(R.string.shop_hat_buy))
                    }
                } else {
                    Button(
                        onClick = { onEquipHat(hat.hatId) },
                        modifier = Modifier.heightIn(min = 48.dp),
                        shape = AppShapes.capsule,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accent.copy(alpha = 0.15f),
                            contentColor = colors.accent,
                        ),
                    ) {
                        Text(text = stringResource(R.string.shop_hat_equip))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HatEquipSheet(
    hatId: String,
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
                        subtitle = stringResource(R.string.shop_upgrade_level, buddy.level),
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
                Text(
                    text = stringResource(R.string.shop_free_pick),
                    style = AppTypography.body,
                    color = colors.label,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(AppDimens.spacing8)) {
                    AdRewardKind.entries.forEach { option ->
                        val selected = kind == option
                        Button(
                            onClick = { kind = option },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp),
                            shape = AppShapes.capsule,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selected) {
                                    colors.accent
                                } else {
                                    colors.accent.copy(alpha = 0.15f)
                                },
                                contentColor = if (selected) {
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
                HoldToConfirmButton(
                    text = stringResource(R.string.shop_free_title),
                    onConfirm = { onWatchAd(kind) },
                    modifier = Modifier.fillMaxWidth(),
                    holdMillis = 1500,
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
