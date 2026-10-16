package com.sreecharan.brainrot_buddies_steal_battle.ui.shop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
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
import com.sreecharan.brainrot_buddies_steal_battle.billing.BillingProducts
import com.sreecharan.brainrot_buddies_steal_battle.R
import com.sreecharan.brainrot_buddies_steal_battle.ui.components.BuddyImage
import com.sreecharan.brainrot_buddies_steal_battle.ui.components.GroupCard
import com.sreecharan.brainrot_buddies_steal_battle.ui.components.HoldToConfirmButton
import com.sreecharan.brainrot_buddies_steal_battle.ui.components.RowDivider
import com.sreecharan.brainrot_buddies_steal_battle.ui.components.SectionHeader
import com.sreecharan.brainrot_buddies_steal_battle.ui.components.SettingsRow
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.AppDimens
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.AppShapes
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.AppTypography
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.LocalAppColors
import kotlinx.collections.immutable.ImmutableList

/** Hat product id -> display name resource. */
private val HatNameRes = mapOf(
    BillingProducts.HAT_PARTY_CONE to R.string.billing_hat_party_cone,
    BillingProducts.HAT_TINY_CROWN to R.string.billing_hat_tiny_crown,
)

/** All shop list sections, in order. Day-gated sections hide until their §1 day. */
fun LazyListScope.shopSections(state: ShopUiState, actions: ShopActions) {
    item(key = "slots-header", contentType = "header") {
        SectionHeader(title = stringResource(R.string.shop_section_slots))
    }
    item(key = "slots", contentType = "card") {
        SlotSection(
            unlocked = state.unlockedSlots,
            nextCost = state.nextSlotCost,
            onBuySlot = actions.purchase.onBuySlot,
        )
    }
    if (state.onboardingDay >= TICKETS_UNLOCK_DAY) {
        item(key = "tickets-header", contentType = "header") {
            SectionHeader(title = stringResource(R.string.shop_section_tickets))
        }
        item(key = "tickets", contentType = "card") {
            TicketSection(
                lucky = state.luckyTickets,
                mega = state.megaTickets,
                onBuyMegaTicket = actions.purchase.onBuyMegaTicket,
            )
        }
    }
    dayGatedSections(state, actions.purchase)
    item(key = "hats-header", contentType = "header") {
        SectionHeader(title = stringResource(R.string.shop_section_hats))
    }
    item(key = "hats", contentType = "card") {
        HatSection(
            hats = state.hats,
            onBuyHat = actions.purchase.onBuyHat,
            onEquipHat = actions.purchase.onEquipHat,
        )
    }
    if (!state.adsRemoved) {
        item(key = "free-header", contentType = "header") {
            SectionHeader(title = stringResource(R.string.shop_section_free))
        }
        item(key = "free", contentType = "card") {
            FreeCoinsSection(
                adReady = state.adReady,
                onWatchAd = actions.engage.onWatchAd,
            )
        }
    }
}

/** Day-gated upgrade/shield sections (§1 ladder). */
private fun LazyListScope.dayGatedSections(
    state: ShopUiState,
    purchase: ShopPurchaseActions,
) {
    if (state.onboardingDay >= UPGRADES_UNLOCK_DAY) {
        item(key = "upgrades-header", contentType = "header") {
            SectionHeader(title = stringResource(R.string.shop_section_upgrades))
        }
        item(key = "upgrades", contentType = "card") {
            UpgradeSection(
                upgrades = state.upgrades,
                onUpgrade = purchase.onUpgrade,
            )
        }
    }
    if (state.onboardingDay >= SHIELDS_UNLOCK_DAY) {
        item(key = "shields-header", contentType = "header") {
            SectionHeader(title = stringResource(R.string.shop_section_shields))
        }
        item(key = "shields", contentType = "card") {
            ShieldSection(
                baseShieldUntil = state.baseShieldUntil,
                onBuyBaseShield = purchase.onBuyBaseShield,
                onOpenSlotShields = purchase.onOpenSlotShields,
            )
        }
    }
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
            UpgradeRowCard(row = row, onUpgrade = onUpgrade)
        }
    }
}

@Composable
private fun UpgradeRowCard(row: UpgradeRow, onUpgrade: (String) -> Unit) {
    val colors = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(AppDimens.spacing16),
        verticalArrangement = Arrangement.spacedBy(AppDimens.spacing8),
    ) {
        UpgradeRowHeader(row = row)
        if (!row.isMaxed) {
            Text(
                text = stringResource(
                    R.string.shop_upgrade_cost,
                    row.coinCost,
                    row.biteCost,
                ),
                style = AppTypography.body,
                color = colors.accent,
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

@Composable
private fun UpgradeRowHeader(row: UpgradeRow) {
    val colors = LocalAppColors.current
    val incomeText = if (row.incomePerSec == row.incomePerSec.toLong().toDouble()) {
        row.incomePerSec.toLong().toString()
    } else {
        "%.1f".format(row.incomePerSec)
    }
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
                color = colors.label,
            )
            Text(
                text = stringResource(R.string.shop_upgrade_to, row.level),
                style = AppTypography.footnote,
                color = colors.secondaryLabel,
            )
            Text(
                text = stringResource(
                    R.string.shop_per_sec,
                    incomeText,
                ),
                style = AppTypography.footnote,
                color = colors.secondaryLabel,
            )
        }
        if (row.isMaxed) {
            Text(
                text = stringResource(R.string.shop_max_level),
                style = AppTypography.headline,
                color = colors.success,
            )
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

@Composable
fun HatSection(
    hats: ImmutableList<HatRow>,
    onBuyHat: (String) -> Unit,
    onEquipHat: (String) -> Unit,
) {
    GroupCard {
        hats.forEachIndexed { index, hat ->
            if (index > 0) RowDivider()
            HatRowContent(
                hat = hat,
                onBuyHat = onBuyHat,
                onEquipHat = onEquipHat,
            )
        }
    }
}

@Composable
private fun HatRowContent(
    hat: HatRow,
    onBuyHat: (String) -> Unit,
    onEquipHat: (String) -> Unit,
) {
    val colors = LocalAppColors.current
    val nameRes = HatNameRes[hat.hatId] ?: R.string.billing_hat_propeller_cap
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(AppDimens.spacing16),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.spacing12),
    ) {
        BuddyImage(
            imageName = hat.hatId,
            contentDescription = stringResource(nameRes),
            modifier = Modifier.size(56.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(nameRes),
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
