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
import javax.inject.Inject

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

    data class SlotShieldBought(
        val slotIndex: Int,
    ) : ShopEvent

    data class BuddyUpgraded(
        val name: String,
        val level: Int,
    ) : ShopEvent

    data object HatBought : ShopEvent

    data class HatEquipped(
        val buddyName: String,
    ) : ShopEvent

    data object HatRemoved : ShopEvent

    data object AdRewardGranted : ShopEvent

    data object AdNotReady : ShopEvent

    data class AdCooldown(
        val millisRemaining: Long,
    ) : ShopEvent

    data class PurchaseFailed(
        val messageKey: String,
    ) : ShopEvent
}

/**
 * Shop transactions are composed here from [GameEngine] math,
 * [GameRepository] CRUD, and [GamePrefs] (Buddy Bites balance): read the
 * balance, check the price, debit, apply. Every purchase is hold-to-confirm
 * in the UI, so mis-taps cost nothing.
 */
@HiltViewModel
class ShopViewModel
    @Inject
    constructor(
        private val repository: GameRepository,
        private val gamePrefs: GamePrefs,
        private val engine: GameEngine,
        private val billingManager: BillingManager,
        private val adsManager: RewardedAdManager,
    ) : ViewModel() {
        private val eventsChannel = Channel<ShopEvent>(Channel.BUFFERED)
        val events = eventsChannel.receiveAsFlow()

        private val transactions =
            ShopTransactions(
                repository = repository,
                gamePrefs = gamePrefs,
                engine = engine,
                adsManager = adsManager,
                scope = viewModelScope,
                eventsChannel = eventsChannel,
            )

        private data class ShopSources(
            val vault: VaultEntity?,
            val tickets: List<TicketEntity>,
            val slots: List<BaseSlotEntity>,
            val shield: ShieldEntity?,
            val buddies: List<OwnedBuddyEntity>,
            val bites: Int,
            val day: Int?,
        )

        private val sources: Flow<ShopSources> =
            combine(
                combine(
                    repository.observeVault(),
                    repository.observeTickets(),
                ) { vault, tickets -> vault to tickets },
                combine(
                    repository.observeSlots(),
                    repository.observeShield(),
                ) { slots, shield -> slots to shield },
                combine(
                    repository.observeOwnedBuddies(),
                    gamePrefs.buddyBites,
                ) { buddies, bites -> buddies to bites },
            ) { first, second, third ->
                ShopSources(
                    vault = first.first.dataOrNull(),
                    tickets = first.second.dataOrNull().orEmpty(),
                    slots = second.first.dataOrNull().orEmpty(),
                    shield = second.second.dataOrNull(),
                    buddies = third.first.dataOrNull().orEmpty(),
                    bites = third.second,
                    day = null,
                )
            }.combine(repository.observeOnboardingDay()) { partial, day ->
                partial.copy(day = day.dataOrNull())
            }

        val uiState: StateFlow<ShopUiState> =
            combine(
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
                    nextSlotCost = engine.nextSlotCost(unlocked),
                    luckyTickets = src.tickets.ticketCount(TicketType.LUCKY),
                    megaTickets = src.tickets.ticketCount(TicketType.MEGA),
                    baseShieldUntil = src.shield?.baseShieldUntil ?: 0L,
                    onboardingDay = (src.day ?: 1).coerceAtLeast(1),
                    upgrades = src.buddies.mapNotNull { it.toUpgradeRow(engine) }.toImmutableList(),
                    hats =
                        BillingProducts.HATS
                            .map { hatId ->
                                val equipped = src.buddies.firstOrNull { it.hatId == hatId }
                                HatRow(
                                    hatId = hatId,
                                    owned = hatId in entitlements.ownedHats,
                                    equippedOnUid = equipped?.uid,
                                    equippedOnName =
                                        equipped?.let {
                                            BuddyCatalog.find(BuddyId(it.buddyId))?.name
                                        },
                                )
                            }.toImmutableList(),
                    slotShieldRows =
                        src.slots
                            .filter { it.unlocked }
                            .map { slot ->
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
                        is BillingEvent.PurchaseSucceeded -> {
                            if (event.productId in BillingProducts.HATS) {
                                eventsChannel.send(ShopEvent.HatBought)
                            }
                        }

                        is BillingEvent.PurchaseFailed -> {
                            if (event.productId in BillingProducts.HATS) {
                                eventsChannel.send(ShopEvent.PurchaseFailed(GENERIC))
                            }
                        }

                        else -> {}
                    }
                }
            }
            viewModelScope.launch {
                adsManager.events.collect { event ->
                    when (event) {
                        AdEvent.NotReady -> {
                            eventsChannel.send(ShopEvent.AdNotReady)
                        }

                        is AdEvent.CooldownActive -> {
                            eventsChannel.send(ShopEvent.AdCooldown(event.millisRemaining))
                        }

                        AdEvent.Closed -> {}
                    }
                }
            }
        }

        fun onBuySlotConfirmed() = transactions.buySlot()

        fun onBuyMegaTicketConfirmed() = transactions.buyMegaTicket()

        fun onBuyBaseShieldConfirmed() = transactions.buyBaseShield()

        fun onBuySlotShieldConfirmed(slotIndex: Int) = transactions.buySlotShield(slotIndex)

        fun onUpgradeConfirmed(uid: String) = transactions.upgrade(uid)

        /** Called after the parent gate passes for a hat IAP. Never called otherwise. */
        fun onHatBuyConfirmed(
            hatId: String,
            activity: Activity,
        ) {
            billingManager.startPurchase(activity, hatId)
        }

        fun onEquipHatConfirmed(
            hatId: String,
            buddyUid: String,
        ) = transactions.equipHat(hatId, buddyUid)

        fun onRemoveHat(hatId: String) = transactions.removeHat(hatId)

        fun onWatchAdConfirmed(
            kind: AdRewardKind,
            activity: Activity,
        ) = transactions.watchAd(kind, activity)

        companion object {
            // Message keys resolved to strings.xml in the UI layer.
            const val COINS_SHORT = "coins_short"
            const val BITES_SHORT = "bites_short"
            const val MAXED = "maxed"
            const val GENERIC = "generic"
        }
    }

private fun <T> Result<T>.dataOrNull(): T? = (this as? Result.Success<T>)?.data

private fun List<TicketEntity>.ticketCount(type: TicketType): Int = firstOrNull { it.type == type.key }?.count ?: 0
