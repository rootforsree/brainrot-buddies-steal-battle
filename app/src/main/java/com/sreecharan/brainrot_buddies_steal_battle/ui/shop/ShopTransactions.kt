package com.sreecharan.brainrot_buddies_steal_battle.ui.shop

import android.app.Activity
import com.sreecharan.brainrot_buddies_steal_battle.ads.RewardedAdManager
import com.sreecharan.brainrot_buddies_steal_battle.billing.BillingManager
import com.sreecharan.brainrot_buddies_steal_battle.data.datastore.GamePrefs
import com.sreecharan.brainrot_buddies_steal_battle.data.local.OwnedBuddyEntity
import com.sreecharan.brainrot_buddies_steal_battle.data.model.BuddyCatalog
import com.sreecharan.brainrot_buddies_steal_battle.data.model.BuddyId
import com.sreecharan.brainrot_buddies_steal_battle.data.model.Result
import com.sreecharan.brainrot_buddies_steal_battle.data.model.TicketType
import com.sreecharan.brainrot_buddies_steal_battle.data.repository.GameRepository
import com.sreecharan.brainrot_buddies_steal_battle.game.engine.GameEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Shop purchase implementations, extracted from [ShopViewModel] so the
 * ViewModel stays a thin wiring layer. Every purchase is hold-to-confirm
 * in the UI, so mis-taps cost nothing: read the balance, check the price,
 * debit, apply.
 */
internal class ShopTransactions(
    private val repository: GameRepository,
    private val gamePrefs: GamePrefs,
    private val engine: GameEngine,
    private val adsManager: RewardedAdManager,
    private val scope: CoroutineScope,
    private val eventsChannel: Channel<ShopEvent>,
) {

    fun buySlot() {
        scope.launch {
            val vault = repository.observeVault().awaitData()
                ?: return@launch fail(ShopViewModel.GENERIC)
            val slots = repository.observeSlots().awaitData()
                ?: return@launch fail(ShopViewModel.GENERIC)
            val unlocked = slots.count { it.unlocked }
            val cost = engine.nextSlotCost(unlocked) ?: return@launch fail(ShopViewModel.MAXED)
            if (vault.coins < cost) return@launch fail(ShopViewModel.COINS_SHORT)
            repository.upsertVault(vault.copy(coins = vault.coins - cost))
            repository.unlockSlot(unlocked)
            eventsChannel.send(ShopEvent.SlotUnlocked)
        }
    }

    fun buyMegaTicket() {
        scope.launch {
            val vault = repository.observeVault().awaitData()
                ?: return@launch fail(ShopViewModel.GENERIC)
            if (vault.coins < MEGA_TICKET_COST) return@launch fail(ShopViewModel.COINS_SHORT)
            repository.upsertVault(vault.copy(coins = vault.coins - MEGA_TICKET_COST))
            repository.addTickets(TicketType.MEGA, 1)
            eventsChannel.send(ShopEvent.MegaTicketBought)
        }
    }

    fun buyBaseShield() {
        scope.launch {
            val vault = repository.observeVault().awaitData()
                ?: return@launch fail(ShopViewModel.GENERIC)
            if (vault.coins < BASE_SHIELD_COST) return@launch fail(ShopViewModel.COINS_SHORT)
            repository.upsertVault(vault.copy(coins = vault.coins - BASE_SHIELD_COST))
            repository.setBaseShieldUntil(System.currentTimeMillis() + SHIELD_DURATION_MS)
            eventsChannel.send(ShopEvent.BaseShieldBought)
        }
    }

    fun buySlotShield(slotIndex: Int) {
        scope.launch {
            val vault = repository.observeVault().awaitData()
                ?: return@launch fail(ShopViewModel.GENERIC)
            if (vault.coins < SLOT_SHIELD_COST) return@launch fail(ShopViewModel.COINS_SHORT)
            repository.upsertVault(vault.copy(coins = vault.coins - SLOT_SHIELD_COST))
            repository.setSlotShieldUntil(slotIndex, System.currentTimeMillis() + SHIELD_DURATION_MS)
            eventsChannel.send(ShopEvent.SlotShieldBought(slotIndex))
        }
    }

    fun upgrade(uid: String) {
        scope.launch {
            val buddies = repository.observeOwnedBuddies().awaitData()
                ?: return@launch fail(ShopViewModel.GENERIC)
            val buddy = buddies.firstOrNull { it.uid == uid }
                ?: return@launch fail(ShopViewModel.GENERIC)
            if (buddy.level >= MAX_BUDDY_LEVEL) return@launch fail(ShopViewModel.MAXED)
            val def = BuddyCatalog.find(BuddyId(buddy.buddyId))
                ?: return@launch fail(ShopViewModel.GENERIC)
            val targetLevel = buddy.level + 1
            val coinCost = engine.upgradeCostCoins(def.baseCoinsPerSec, targetLevel)
            val biteCost = engine.upgradeCostBites(targetLevel)
            val vault = repository.observeVault().awaitData()
                ?: return@launch fail(ShopViewModel.GENERIC)
            val bites = gamePrefs.buddyBites.first()
            if (vault.coins < coinCost) return@launch fail(ShopViewModel.COINS_SHORT)
            if (bites < biteCost) return@launch fail(ShopViewModel.BITES_SHORT)
            repository.upsertVault(vault.copy(coins = vault.coins - coinCost))
            gamePrefs.setBuddyBites(bites - biteCost)
            repository.upsertOwnedBuddy(buddy.copy(level = targetLevel))
            eventsChannel.send(ShopEvent.BuddyUpgraded(def.name, targetLevel))
        }
    }

    fun equipHat(hatId: String, buddyUid: String) {
        scope.launch {
            val buddies = repository.observeOwnedBuddies().awaitData() ?: return@launch
            val buddy = buddies.firstOrNull { it.uid == buddyUid } ?: return@launch
            repository.upsertOwnedBuddy(buddy.copy(hatId = hatId))
            val name = BuddyCatalog.find(BuddyId(buddy.buddyId))?.name ?: return@launch
            eventsChannel.send(ShopEvent.HatEquipped(name))
        }
    }

    fun removeHat(hatId: String) {
        scope.launch {
            val buddies = repository.observeOwnedBuddies().awaitData() ?: return@launch
            val buddy = buddies.firstOrNull { it.hatId == hatId } ?: return@launch
            repository.upsertOwnedBuddy(buddy.copy(hatId = null))
            eventsChannel.send(ShopEvent.HatRemoved)
        }
    }

    /**
     * Called after hold-to-confirm on the Free Coins button. This is the ONE
     * rewarded-ad placement in the game; the manager enforces G-rated,
     * non-personalized, max 1 show per 30 minutes.
     */
    fun watchAd(kind: AdRewardKind, activity: Activity) {
        adsManager.showAd(activity) {
            scope.launch { grantAdReward(kind) }
        }
    }

    private suspend fun grantAdReward(kind: AdRewardKind) {
        when (kind) {
            AdRewardKind.COINS -> {
                val vault = repository.observeVault().awaitData() ?: return
                repository.upsertVault(
                    vault.copy(
                        coins = vault.coins + AD_REWARD_COINS,
                        lifetimeCoins = vault.lifetimeCoins + AD_REWARD_COINS,
                    ),
                )
            }
            AdRewardKind.BITES -> {
                val bites = gamePrefs.buddyBites.first()
                gamePrefs.setBuddyBites(bites + AD_REWARD_BITES)
            }
        }
        eventsChannel.send(ShopEvent.AdRewardGranted)
    }

    private suspend fun fail(messageKey: String) {
        eventsChannel.send(ShopEvent.PurchaseFailed(messageKey))
    }
}

/** Cost of the next slot unlock, or null when all [MAX_SLOTS] are open. */
internal fun GameEngine.nextSlotCost(unlocked: Int): Long? {
    if (unlocked >= MAX_SLOTS) return null
    return slotUnlockCost(unlocked - START_SLOTS + 1)
}

internal fun OwnedBuddyEntity.toUpgradeRow(engine: GameEngine): UpgradeRow? {
    val def = BuddyCatalog.find(BuddyId(buddyId)) ?: return null
    val maxed = level >= MAX_BUDDY_LEVEL
    val targetLevel = (level + 1).coerceAtMost(MAX_BUDDY_LEVEL)
    return UpgradeRow(
        uid = uid,
        buddyId = buddyId,
        name = def.name,
        level = level,
        coinCost = if (maxed) 0L else engine.upgradeCostCoins(def.baseCoinsPerSec, targetLevel),
        biteCost = if (maxed) 0 else engine.upgradeCostBites(targetLevel),
        incomePerSec = engine.buddyCoinsPerSec(def.baseCoinsPerSec, level),
        isMaxed = maxed,
    )
}

private suspend fun <T> Flow<Result<T>>.awaitData(): T? {
    return when (val result = first { it !is Result.Loading }) {
        is Result.Success -> result.data
        else -> null
    }
}
