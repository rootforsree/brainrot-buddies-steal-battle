package com.sreecharan.brainrot_buddies_steal_battle.ui.shop

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sreecharan.brainrot_buddies_steal_battle.R
import com.sreecharan.brainrot_buddies_steal_battle.ui.components.BuddyImage
import com.sreecharan.brainrot_buddies_steal_battle.ui.components.GroupCard
import com.sreecharan.brainrot_buddies_steal_battle.ui.components.InsetGroupedList
import com.sreecharan.brainrot_buddies_steal_battle.ui.components.ParentGateDialog
import com.sreecharan.brainrot_buddies_steal_battle.ui.components.SectionHeader
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.AppDimens
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.AppTypography
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.LocalAppColors
import kotlinx.coroutines.delay

/**
 * Milo the shopkeeper hosts the Shop. One screen, one job: buy.
 * Every purchase is hold-to-confirm (mis-taps cost nothing); IAP hats add
 * the parent gate (hold 3s + math). Sections unlock on the §1 day ladder:
 * slots day 1, tickets day 2, upgrades day 3, shields day 4.
 */
@Composable
fun ShopRoute(viewModel: ShopViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as Activity
    var message by remember { mutableStateOf<String?>(null) }
    var pendingHatPurchase by remember { mutableStateOf<String?>(null) }
    var shieldSheetOpen by remember { mutableStateOf(false) }
    var hatEquipHatId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            message = when (event) {
                ShopEvent.SlotUnlocked -> context.getString(R.string.shop_bought)
                ShopEvent.MegaTicketBought -> context.getString(R.string.shop_bought)
                ShopEvent.BaseShieldBought -> context.getString(R.string.shop_bought)
                is ShopEvent.SlotShieldBought -> context.getString(R.string.shop_bought)
                is ShopEvent.BuddyUpgraded ->
                    context.getString(R.string.shop_upgraded, event.name, event.level)
                ShopEvent.HatBought -> context.getString(R.string.shop_bought)
                is ShopEvent.HatEquipped -> context.getString(R.string.shop_hat_on, event.buddyName)
                ShopEvent.HatRemoved -> context.getString(R.string.shop_hat_off)
                ShopEvent.AdRewardGranted -> context.getString(R.string.shop_ad_done)
                ShopEvent.AdNotReady -> context.getString(R.string.shop_ad_not_ready)
                is ShopEvent.AdCooldown ->
                    context.getString(R.string.shop_free_wait, formatDuration(event.millisRemaining))
                is ShopEvent.PurchaseFailed -> when (event.messageKey) {
                    ShopViewModel.COINS_SHORT -> context.getString(R.string.shop_not_enough_coins)
                    ShopViewModel.BITES_SHORT -> context.getString(R.string.shop_not_enough_bites)
                    ShopViewModel.MAXED -> context.getString(R.string.shop_maxed_out)
                    else -> context.getString(R.string.shop_error)
                }
            }
        }
    }
    LaunchedEffect(message) {
        if (message != null) {
            delay(2_500)
            message = null
        }
    }

    ShopScreenContent(
        state = state,
        message = message,
        onBuySlot = viewModel::onBuySlotConfirmed,
        onBuyMegaTicket = viewModel::onBuyMegaTicketConfirmed,
        onBuyBaseShield = viewModel::onBuyBaseShieldConfirmed,
        onOpenSlotShields = { shieldSheetOpen = true },
        onUpgrade = viewModel::onUpgradeConfirmed,
        onBuyHat = { pendingHatPurchase = it },
        onEquipHat = { hatEquipHatId = it },
        onWatchAd = { kind -> viewModel.onWatchAdConfirmed(kind, activity) },
    )

    if (shieldSheetOpen) {
        SlotShieldSheet(
            rows = state.slotShieldRows,
            onShieldSlot = viewModel::onBuySlotShieldConfirmed,
            onDismiss = { shieldSheetOpen = false },
        )
    }
    val equipHatId = hatEquipHatId
    if (equipHatId != null) {
        HatEquipSheet(
            hatId = equipHatId,
            buddies = state.upgrades,
            onPickBuddy = {
                viewModel.onEquipHatConfirmed(equipHatId, it)
                hatEquipHatId = null
            },
            onRemoveHat = {
                viewModel.onRemoveHat(equipHatId)
                hatEquipHatId = null
            },
            onDismiss = { hatEquipHatId = null },
        )
    }
    val hatId = pendingHatPurchase
    if (hatId != null) {
        ParentGateDialog(
            requireMath = true,
            onPassed = {
                viewModel.onHatBuyConfirmed(hatId, activity)
                pendingHatPurchase = null
            },
            onDismiss = { pendingHatPurchase = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopScreenContent(
    state: ShopUiState,
    message: String?,
    onBuySlot: () -> Unit,
    onBuyMegaTicket: () -> Unit,
    onBuyBaseShield: () -> Unit,
    onOpenSlotShields: () -> Unit,
    onUpgrade: (String) -> Unit,
    onBuyHat: (String) -> Unit,
    onEquipHat: (String) -> Unit,
    onWatchAd: (AdRewardKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.shop_title),
                        style = AppTypography.largeTitle,
                    )
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = colors.groupedBackground,
                    scrolledContainerColor = colors.groupedBackground,
                ),
            )
        },
        containerColor = colors.groupedBackground,
    ) { padding ->
        InsetGroupedList(
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + AppDimens.tabBarClearance,
            ),
        ) {
            item(key = "milo", contentType = "header") {
                MiloHeader(
                    coins = state.coins,
                    bites = state.bites,
                    message = message,
                )
            }
            item(key = "slots-header", contentType = "header") {
                SectionHeader(title = stringResource(R.string.shop_section_slots))
            }
            item(key = "slots", contentType = "card") {
                SlotSection(
                    unlocked = state.unlockedSlots,
                    nextCost = state.nextSlotCost,
                    onBuySlot = onBuySlot,
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
                        onBuyMegaTicket = onBuyMegaTicket,
                    )
                }
            }
            if (state.onboardingDay >= UPGRADES_UNLOCK_DAY) {
                item(key = "upgrades-header", contentType = "header") {
                    SectionHeader(title = stringResource(R.string.shop_section_upgrades))
                }
                item(key = "upgrades", contentType = "card") {
                    UpgradeSection(
                        upgrades = state.upgrades,
                        onUpgrade = onUpgrade,
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
                        onBuyBaseShield = onBuyBaseShield,
                        onOpenSlotShields = onOpenSlotShields,
                    )
                }
            }
            item(key = "hats-header", contentType = "header") {
                SectionHeader(title = stringResource(R.string.shop_section_hats))
            }
            item(key = "hats", contentType = "card") {
                HatSection(
                    hats = state.hats,
                    onBuyHat = onBuyHat,
                    onEquipHat = onEquipHat,
                )
            }
            if (!state.adsRemoved) {
                item(key = "free-header", contentType = "header") {
                    SectionHeader(title = stringResource(R.string.shop_section_free))
                }
                item(key = "free", contentType = "card") {
                    FreeCoinsSection(
                        adReady = state.adReady,
                        onWatchAd = onWatchAd,
                    )
                }
            }
        }
    }
}

@Composable
private fun MiloHeader(coins: Long, bites: Int, message: String?) {
    val colors = LocalAppColors.current
    Column(verticalArrangement = Arrangement.spacedBy(AppDimens.spacing8)) {
        GroupCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AppDimens.spacing16),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppDimens.spacing16),
            ) {
                BuddyImage(
                    imageName = "hero_milo",
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(AppDimens.spacing4)) {
                    Text(
                        text = stringResource(R.string.shop_milo_greeting),
                        style = AppTypography.headline,
                        color = colors.label,
                    )
                    Text(
                        text = stringResource(R.string.coins_count, coins),
                        style = AppTypography.title2,
                        color = colors.accent,
                    )
                    Text(
                        text = stringResource(R.string.bites_count, bites),
                        style = AppTypography.subheadline,
                        color = colors.secondaryLabel,
                    )
                }
            }
        }
        if (message != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = colors.accent.copy(alpha = 0.15f),
                ),
            ) {
                Row(
                    modifier = Modifier.padding(
                        horizontal = AppDimens.spacing16,
                        vertical = AppDimens.spacing12,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.spacing12),
                ) {
                    Icon(
                        imageVector = Icons.Filled.ShoppingCart,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        text = message,
                        style = AppTypography.body,
                        color = colors.label,
                    )
                }
            }
        }
    }
}
