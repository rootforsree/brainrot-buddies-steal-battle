package com.sreecharan.brainrot_buddies_steal_battle.ui.shop

import android.app.Activity
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sreecharan.brainrot_buddies_steal_battle.ads.AdEvent
import com.sreecharan.brainrot_buddies_steal_battle.ads.RewardedAdManager
import com.sreecharan.brainrot_buddies_steal_battle.billing.BillingEvent
import com.sreecharan.brainrot_buddies_steal_battle.billing.BillingManager
import com.sreecharan.brainrot_buddies_steal_battle.billing.BillingProducts
import com.sreecharan.brainrot_buddies_steal_battle.data.datastore.GamePrefs
import com.sreecharan.brainrot_buddies_steal_battle.data.local.BaseSlotEntity
import com.sreecharan.brainrot_buddies_steal_battle.data.local.OwnedBuddyEntity
import com.sreecharan.brainrot_buddies_steal_battle.data.local.ShieldEntity
import com.sreecharan.brainrot_buddies_steal_battle.data.local.TicketEntity
import com.sreecharan.brainrot_buddies_steal_battle.data.local.VaultEntity
import com.sreecharan.brainrot_buddies_steal_battle.data.model.BuddyCatalog
import com.sreecharan.brainrot_buddies_steal_battle.data.model.BuddyId
import com.sreecharan.brainrot_buddies_steal_battle.data.model.Result
import com.sreecharan.brainrot_buddies_steal_battle.data.model.TicketType
import com.sreecharan.brainrot_buddies_steal_battle.data.repository.GameRepository
import com.sreecharan.brainrot_buddies_steal_battle.game.engine.GameEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

const val MAX_SLOTS = 24
const val START_SLOTS = 4
const val MEGA_TICKET_COST = 2_500L
const val BASE_SHIELD_COST = 500L
const val SLOT_SHIELD_COST = 100L
const val MAX_BUDDY_LEVEL = 3
const val SHIELD_DURATION_MS = 8L * 60L * 60L * 1000L
const val AD_REWARD_COINS = 100L
const val AD_REWARD_BITES = 5

/** Onboarding ladder (§1): shop sections unlock one per day. */
const val TICKETS_UNLOCK_DAY = 2
const val UPGRADES_UNLOCK_DAY = 3
const val SHIELDS_UNLOCK_DAY = 4

enum class AdRewardKind { COINS, BITES }

@Immutable
data class UpgradeRow(
    val uid: String,
    val buddyId: String,
    val name: String,
    val level: Int,
    val coinCost: Long,
    val biteCost: Int,
    val incomePerSec: Double,
    val isMaxed: Boolean,
)

@Immutable
data class HatRow(
    val hatId: String,
    val owned: Boolean,
    val equippedOnUid: String?,
    val equippedOnName: String?,
)

@Immutable
data class SlotShieldRow(
    val slotIndex: Int,
    val buddyName: String?,
    val shieldActive: Boolean,
)

@Immutable
data class ShopUiState(
    val isLoading: Boolean = true,
    val coins: Long = 0L,
    val bites: Int = 0,
    val unlockedSlots: Int = START_SLOTS,
    val nextSlotCost: Long? = null,
    val luckyTickets: Int = 0,
    val megaTickets: Int = 0,
    val baseShieldUntil: Long = 0L,
    val onboardingDay: Int = 1,
    val upgrades: ImmutableList<UpgradeRow> = persistentListOf(),
    val hats: ImmutableList<HatRow> = persistentListOf(),
    val slotShieldRows: ImmutableList<SlotShieldRow> = persistentListOf(),
    val adsRemoved: Boolean = false,
    val adReady: Boolean = false,
)

sealed interface ShopEvent {
    data object SlotUnlocked : ShopEvent
    data object MegaTicketBought : ShopEvent
    data object BaseShieldBought : ShopEvent
    data class SlotShieldBought(val slotIndex: Int) : ShopEvent
    data class BuddyUpgraded(val name: String, val level: Int) : ShopEvent
    data object HatBought : ShopEvent
    data class HatEquipped(val buddyName: String) : ShopEvent
    data object HatRemoved : ShopEvent
    data object AdRewardGranted : ShopEvent
    data object AdNotReady : ShopEvent
    data class AdCooldown(val millisRemaining: Long) : ShopEvent
    data class PurchaseFailed(val messageKey: String) : ShopEvent
}

/**
 * Shop transactions are composed here from [GameEngine] math,
 * [GameRepository] CRUD, and [GamePrefs] (Buddy Bites balance): read the
 * balance, check the price, debit, apply. Every purchase is hold-to-confirm
 * in the UI, so mis-taps cost nothing.
 */
@HiltViewModel
class ShopViewModel @Inject constructor(
    private val repository: GameRepository,
    private val gamePrefs: GamePrefs,
    private val engine: GameEngine,
    private val billingManager: BillingManager,
    private val adsManager: RewardedAdManager,
) : ViewModel() {

    private val eventsChannel = Channel<ShopEvent>(Channel.BUFFERED)
    val events = eventsChannel.receiveAsFlow()

    private data class ShopSources(
        val vault: VaultEntity?,
        val tickets: List<TicketEntity>,
        val slots: List<BaseSlotEntity>,
        val shield: ShieldEntity?,
        val buddies: List<OwnedBuddyEntity>,
        val bites: Int,
        val day: Int?,
    )

    private val sources: Flow<ShopSources> = combine(
        repository.observeVault(),
        repository.observeTickets(),
        repository.observeSlots(),
        repository.observeShield(),
        repository.observeOwnedBuddies(),
        gamePrefs.buddyBites,
        repository.observeOnboardingDay(),
    ) { vault, tickets, slots, shield, buddies, bites, day ->
        ShopSources(
            vault = vault.dataOrNull(),
            tickets = tickets.dataOrNull().orEmpty(),
            slots = slots.dataOrNull().orEmpty(),
            shield = shield.dataOrNull(),
            buddies = buddies.dataOrNull().orEmpty(),
            bites = bites,
            day = day.dataOrNull(),
        )
    }

    val uiState: StateFlow<ShopUiState> = combine(
        sources,
        billingManager.entitlements,
        adsManager.adReady,
    ) { src, entitlements, adReady ->
        val unlocked = src.slots.count { it.unlocked }
        val now = System.currentTimeMillis()
        val byUid = src.buddies.associateBy { it.uid }
        ShopUiState(
            isLoading = false,
            coins = src.vault?.coins ?: 0L,
            bites = src.bites,
            unlockedSlots = unlocked,
            nextSlotCost = nextSlotCost(unlocked),
            luckyTickets = src.tickets.ticketCount(TicketType.LUCKY),
            megaTickets = src.tickets.ticketCount(TicketType.MEGA),
            baseShieldUntil = src.shield?.baseShieldUntil ?: 0L,
            onboardingDay = (src.day ?: 1).coerceAtLeast(1),
            upgrades = src.buddies.mapNotNull { it.toUpgradeRow() }.toImmutableList(),
            hats = BillingProducts.HATS.map { hatId ->
                val equipped = src.buddies.firstOrNull { it.hatId == hatId }
                HatRow(
                    hatId = hatId,
                    owned = hatId in entitlements.ownedHats,
                    equippedOnUid = equipped?.uid,
                    equippedOnName = equipped?.let {
                        BuddyCatalog.find(BuddyId(it.buddyId))?.name
                    },
                )
            }.toImmutableList(),
            slotShieldRows = src.slots.filter { it.unlocked }.map { slot ->
                val buddy = slot.ownedBuddyUid?.let { byUid[it] }
                SlotShieldRow(
                    slotIndex = slot.slotIndex,
                    buddyName = buddy?.let { BuddyCatalog.find(BuddyId(it.buddyId))?.name },
                    shieldActive = slot.shieldUntil > now,
                )
            }.toImmutableList(),
            adsRemoved = entitlements.adsRemoved,
            adReady = adReady,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ShopUiState(),
    )

    init {
        viewModelScope.launch {
            billingManager.events.collect { event ->
                when (event) {
                    is BillingEvent.PurchaseSucceeded ->
                        if (event.productId in BillingProducts.HATS) {
                            eventsChannel.send(ShopEvent.HatBought)
                        }
                    is BillingEvent.PurchaseFailed ->
                        if (event.productId in BillingProducts.HATS) {
                            eventsChannel.send(ShopEvent.PurchaseFailed(GENERIC))
                        }
                    else -> Unit
                }
            }
        }
        viewModelScope.launch {
            adsManager.events.collect { event ->
                when (event) {
                    AdEvent.NotReady -> eventsChannel.send(ShopEvent.AdNotReady)
                    is AdEvent.CooldownActive ->
                        eventsChannel.send(ShopEvent.AdCooldown(event.millisRemaining))
                    AdEvent.Closed -> Unit
                }
            }
        }
    }

    fun onBuySlotConfirmed() {
        viewModelScope.launch {
            val vault = repository.observeVault().awaitData()
                ?: return@launch fail(GENERIC)
            val slots = repository.observeSlots().awaitData()
                ?: return@launch fail(GENERIC)
            val unlocked = slots.count { it.unlocked }
            val cost = nextSlotCost(unlocked) ?: return@launch fail(MAXED)
            if (vault.coins < cost) return@launch fail(COINS_SHORT)
            repository.upsertVault(vault.copy(coins = vault.coins - cost))
            repository.unlockSlot(unlocked)
            eventsChannel.send(ShopEvent.SlotUnlocked)
        }
    }

    fun onBuyMegaTicketConfirmed() {
        viewModelScope.launch {
            val vault = repository.observeVault().awaitData()
                ?: return@launch fail(GENERIC)
            if (vault.coins < MEGA_TICKET_COST) return@launch fail(COINS_SHORT)
            repository.upsertVault(vault.copy(coins = vault.coins - MEGA_TICKET_COST))
            repository.addTickets(TicketType.MEGA, 1)
            eventsChannel.send(ShopEvent.MegaTicketBought)
        }
    }

    fun onBuyBaseShieldConfirmed() {
        viewModelScope.launch {
            val vault = repository.observeVault().awaitData()
                ?: return@launch fail(GENERIC)
            if (vault.coins < BASE_SHIELD_COST) return@launch fail(COINS_SHORT)
            repository.upsertVault(vault.copy(coins = vault.coins - BASE_SHIELD_COST))
            repository.setBaseShieldUntil(System.currentTimeMillis() + SHIELD_DURATION_MS)
            eventsChannel.send(ShopEvent.BaseShieldBought)
        }
    }

    fun onBuySlotShieldConfirmed(slotIndex: Int) {
        viewModelScope.launch {
            val vault = repository.observeVault().awaitData()
                ?: return@launch fail(GENERIC)
            if (vault.coins < SLOT_SHIELD_COST) return@launch fail(COINS_SHORT)
            repository.upsertVault(vault.copy(coins = vault.coins - SLOT_SHIELD_COST))
            repository.setSlotShieldUntil(slotIndex, System.currentTimeMillis() + SHIELD_DURATION_MS)
            eventsChannel.send(ShopEvent.SlotShieldBought(slotIndex))
        }
    }

    fun onUpgradeConfirmed(uid: String) {
        viewModelScope.launch {
            val buddies = repository.observeOwnedBuddies().awaitData()
                ?: return@launch fail(GENERIC)
            val buddy = buddies.firstOrNull { it.uid == uid }
                ?: return@launch fail(GENERIC)
            if (buddy.level >= MAX_BUDDY_LEVEL) return@launch fail(MAXED)
            val def = BuddyCatalog.find(BuddyId(buddy.buddyId))
                ?: return@launch fail(GENERIC)
            val targetLevel = buddy.level + 1
            val coinCost = engine.upgradeCostCoins(def.baseCoinsPerSec, targetLevel)
            val biteCost = engine.upgradeCostBites(targetLevel)
            val vault = repository.observeVault().awaitData()
                ?: return@launch fail(GENERIC)
            val bites = gamePrefs.buddyBites.first()
            if (vault.coins < coinCost) return@launch fail(COINS_SHORT)
            if (bites < biteCost) return@launch fail(BITES_SHORT)
            repository.upsertVault(vault.copy(coins = vault.coins - coinCost))
            gamePrefs.setBuddyBites(bites - biteCost)
            repository.upsertOwnedBuddy(buddy.copy(level = targetLevel))
            eventsChannel.send(ShopEvent.BuddyUpgraded(def.name, targetLevel))
        }
    }

    /** Called after the parent gate passes for a hat IAP. Never called otherwise. */
    fun onHatBuyConfirmed(hatId: String, activity: Activity) {
        billingManager.startPurchase(activity, hatId)
    }

    fun onEquipHatConfirmed(hatId: String, buddyUid: String) {
        viewModelScope.launch {
            val buddies = repository.observeOwnedBuddies().awaitData() ?: return@launch
            val buddy = buddies.firstOrNull { it.uid == buddyUid } ?: return@launch
            repository.upsertOwnedBuddy(buddy.copy(hatId = hatId))
            val name = BuddyCatalog.find(BuddyId(buddy.buddyId))?.name ?: return@launch
            eventsChannel.send(ShopEvent.HatEquipped(name))
        }
    }

    fun onRemoveHat(hatId: String) {
        viewModelScope.launch {
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
    fun onWatchAdConfirmed(kind: AdRewardKind, activity: Activity) {
        adsManager.showAd(activity) {
            viewModelScope.launch { grantAdReward(kind) }
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

    private fun nextSlotCost(unlocked: Int): Long? {
        if (unlocked >= MAX_SLOTS) return null
        return engine.slotUnlockCost(unlocked - START_SLOTS + 1)
    }

    private fun OwnedBuddyEntity.toUpgradeRow(): UpgradeRow? {
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

    private suspend fun fail(messageKey: String) {
        eventsChannel.send(ShopEvent.PurchaseFailed(messageKey))
    }

    companion object {
        // Message keys resolved to strings.xml in the UI layer.
        const val COINS_SHORT = "coins_short"
        const val BITES_SHORT = "bites_short"
        const val MAXED = "maxed"
        const val GENERIC = "generic"
    }
}

private fun <T> Result<T>.dataOrNull(): T? = (this as? Result.Success<T>)?.data

private fun List<TicketEntity>.ticketCount(type: TicketType): Int =
    firstOrNull { it.type == type.key }?.count ?: 0

private suspend fun <T> Flow<Result<T>>.awaitData(): T? {
    return when (val result = first { it !is Result.Loading }) {
        is Result.Success -> result.data
        else -> null
    }
}
