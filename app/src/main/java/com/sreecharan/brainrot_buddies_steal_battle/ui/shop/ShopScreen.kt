package com.sreecharan.brainrot_buddies_steal_battle.ui.shop

import android.app.Activity
import android.content.Context
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

    ShopEventEffect(viewModel) { message = it }
    MessageDismissEffect(message) { message = null }

    ShopScreenContent(
        state = state,
        message = message,
        actions = ShopActions(
            purchase = ShopPurchaseActions(
                onBuySlot = viewModel::onBuySlotConfirmed,
                onBuyMegaTicket = viewModel::onBuyMegaTicketConfirmed,
                onBuyBaseShield = viewModel::onBuyBaseShieldConfirmed,
                onOpenSlotShields = { shieldSheetOpen = true },
                onUpgrade = viewModel::onUpgradeConfirmed,
                onBuyHat = { pendingHatPurchase = it },
                onEquipHat = { hatEquipHatId = it },
            ),
            engage = ShopEngageActions(
                onWatchAd = { kind -> viewModel.onWatchAdConfirmed(kind, activity) },
            ),
        ),
    )

    ShopRouteDialogs(
        dialogUi = ShopDialogUi(
            shieldSheetOpen = shieldSheetOpen,
            hatEquipHatId = hatEquipHatId,
            pendingHatPurchase = pendingHatPurchase,
            onShieldSheetDismiss = { shieldSheetOpen = false },
            onHatEquipDismiss = { hatEquipHatId = null },
            onHatPurchaseDismiss = { pendingHatPurchase = null },
        ),
        state = state,
        viewModel = viewModel,
        activity = activity,
    )
}

/** Callbacks that spend coins/bites. */
data class ShopPurchaseActions(
    val onBuySlot: () -> Unit,
    val onBuyMegaTicket: () -> Unit,
    val onBuyBaseShield: () -> Unit,
    val onOpenSlotShields: () -> Unit,
    val onUpgrade: (String) -> Unit,
    val onBuyHat: (String) -> Unit,
    val onEquipHat: (String) -> Unit,
)

/** Callbacks for rewarded ads. */
data class ShopEngageActions(
    val onWatchAd: (AdRewardKind) -> Unit,
)

/** All [ShopScreenContent] callbacks, grouped by kind. */
data class ShopActions(
    val purchase: ShopPurchaseActions,
    val engage: ShopEngageActions,
)

/** Dialog/sheet state for the route. */
data class ShopDialogUi(
    val shieldSheetOpen: Boolean,
    val hatEquipHatId: String?,
    val pendingHatPurchase: String?,
    val onShieldSheetDismiss: () -> Unit,
    val onHatEquipDismiss: () -> Unit,
    val onHatPurchaseDismiss: () -> Unit,
)

/** How long a shop toast message stays visible. */
private const val MESSAGE_DISMISS_MS = 2_500L

@Composable
private fun ShopEventEffect(
    viewModel: ShopViewModel,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            onMessage(shopEventMessage(event, context))
        }
    }
}

@Composable
private fun MessageDismissEffect(message: String?, onDismiss: () -> Unit) {
    LaunchedEffect(message) {
        if (message != null) {
            delay(MESSAGE_DISMISS_MS)
            onDismiss()
        }
    }
}

private fun shopEventMessage(event: ShopEvent, context: Context): String =
    when (event) {
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
        is ShopEvent.PurchaseFailed -> purchaseFailedMessage(event, context)
    }

private fun purchaseFailedMessage(
    event: ShopEvent.PurchaseFailed,
    context: Context,
): String = when (event.messageKey) {
    ShopViewModel.COINS_SHORT -> context.getString(R.string.shop_not_enough_coins)
    ShopViewModel.BITES_SHORT -> context.getString(R.string.shop_not_enough_bites)
    ShopViewModel.MAXED -> context.getString(R.string.shop_maxed_out)
    else -> context.getString(R.string.shop_error)
}

@Composable
private fun ShopRouteDialogs(
    dialogUi: ShopDialogUi,
    state: ShopUiState,
    viewModel: ShopViewModel,
    activity: Activity,
) {
    if (dialogUi.shieldSheetOpen) {
        SlotShieldSheet(
            rows = state.slotShieldRows,
            onShieldSlot = viewModel::onBuySlotShieldConfirmed,
            onDismiss = dialogUi.onShieldSheetDismiss,
        )
    }
    val equipHatId = dialogUi.hatEquipHatId
    if (equipHatId != null) {
        HatEquipSheet(
            buddies = state.upgrades,
            onPickBuddy = {
                viewModel.onEquipHatConfirmed(equipHatId, it)
                dialogUi.onHatEquipDismiss()
            },
            onRemoveHat = {
                viewModel.onRemoveHat(equipHatId)
                dialogUi.onHatEquipDismiss()
            },
            onDismiss = dialogUi.onHatEquipDismiss,
        )
    }
    val hatId = dialogUi.pendingHatPurchase
    if (hatId != null) {
        ParentGateDialog(
            requireMath = true,
            onPassed = {
                viewModel.onHatBuyConfirmed(hatId, activity)
                dialogUi.onHatPurchaseDismiss()
            },
            onDismiss = dialogUi.onHatPurchaseDismiss,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopScreenContent(
    state: ShopUiState,
    message: String?,
    actions: ShopActions,
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
                colors = TopAppBarDefaults.topAppBarColors(
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
            shopSections(state, actions)
        }
    }
}

@Composable
private fun MiloHeader(coins: Long, bites: Int, message: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(AppDimens.spacing8)) {
        MiloBalanceCard(coins = coins, bites = bites)
        if (message != null) {
            MiloMessageCard(message = message)
        }
    }
}

@Composable
private fun MiloBalanceCard(coins: Long, bites: Int) {
    val colors = LocalAppColors.current
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
}

@Composable
private fun MiloMessageCard(message: String) {
    val colors = LocalAppColors.current
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
