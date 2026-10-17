package com.sreecharan.brainrot_buddies_steal_battle.billing

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf

/**
 * Play Billing catalog, per docs/contract.md §1 and §4.
 *
 * - remove_ads: $2.99 one-time.
 * - hat_party_cone | hat_tiny_crown | hat_propeller_cap: $0.99 each, non-consumable.
 *
 * Paid IAP is deterministic: nothing random is purchasable, ever. No loot
 * boxes, no paid gacha. Parent gate (Builder C's ParentGate) is shown by the
 * Shop UI before [BillingManager.startPurchase] is ever called.
 */
object BillingProducts {
    const val REMOVE_ADS = "remove_ads"
    const val HAT_PARTY_CONE = "hat_party_cone"
    const val HAT_TINY_CROWN = "hat_tiny_crown"
    const val HAT_PROPELLER_CAP = "hat_propeller_cap"

    val ALL: List<String> = listOf(
        REMOVE_ADS,
        HAT_PARTY_CONE,
        HAT_TINY_CROWN,
        HAT_PROPELLER_CAP,
    )

    val HATS: List<String> = listOf(
        HAT_PARTY_CONE,
        HAT_TINY_CROWN,
        HAT_PROPELLER_CAP,
    )

    /** Pure mapping from Play product id to the entitlement it unlocks. Null for unknown ids. */
    fun entitlementFor(productId: String): ProductEntitlement? = when (productId) {
        REMOVE_ADS -> ProductEntitlement.RemoveAds
        HAT_PARTY_CONE, HAT_TINY_CROWN, HAT_PROPELLER_CAP -> ProductEntitlement.Hat(productId)
        else -> null
    }
}

/** What a purchased product unlocks. Deterministic, never random. */
sealed interface ProductEntitlement {
    data object RemoveAds : ProductEntitlement
    data class Hat(val hatId: String) : ProductEntitlement
}

/** The player's deterministic entitlements, persisted in DataStore. */
@Immutable
data class Entitlements(
    val adsRemoved: Boolean = false,
    val ownedHats: ImmutableSet<String> = persistentSetOf(),
) {
    fun ownsHat(hatId: String): Boolean = hatId in ownedHats
}

/** One purchasable product with its Play Store price text. */
@Immutable
data class ProductOffer(
    val productId: String,
    val title: String,
    val priceText: String,
)

/** One-shot billing outcomes for the UI. */
sealed interface BillingEvent {
    data class PurchaseSucceeded(val productId: String) : BillingEvent
    data class PurchaseFailed(val productId: String) : BillingEvent
    data object StoreUnavailable : BillingEvent
    data object ProductsRefreshed : BillingEvent
    data object RestoreCompleted : BillingEvent
}
