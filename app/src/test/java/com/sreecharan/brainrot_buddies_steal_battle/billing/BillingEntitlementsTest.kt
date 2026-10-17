package com.sreecharan.brainrot_buddies_steal_battle.billing

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BillingEntitlementsTest {
    @TempDir
    lateinit var tempDir: Path

    // ---- pure product-to-entitlement mapping ----

    @Test
    fun `remove_ads maps to the remove-ads entitlement`() {
        assertEquals(
            ProductEntitlement.RemoveAds,
            BillingProducts.entitlementFor(BillingProducts.REMOVE_ADS),
        )
    }

    @Test
    fun `each hat maps to its own hat entitlement`() {
        BillingProducts.HATS.forEach { hatId ->
            assertEquals(ProductEntitlement.Hat(hatId), BillingProducts.entitlementFor(hatId))
        }
    }

    @Test
    fun `unknown product ids map to null`() {
        assertNull(BillingProducts.entitlementFor("mega_coins_pack"))
        assertNull(BillingProducts.entitlementFor(""))
    }

    @Test
    fun `catalog holds exactly the four frozen products`() {
        assertEquals(
            listOf("remove_ads", "hat_party_cone", "hat_tiny_crown", "hat_propeller_cap"),
            BillingProducts.ALL,
        )
        assertEquals(3, BillingProducts.HATS.size)
    }

    @Test
    fun `nothing random is purchasable`() {
        // Every catalog product resolves to a deterministic entitlement.
        BillingProducts.ALL.forEach { productId ->
            assertTrue(
                BillingProducts.entitlementFor(productId) != null,
                "$productId must map to a deterministic entitlement",
            )
        }
    }

    // ---- DataStore persistence of hat entitlements ----

    @Test
    fun `granted hats persist across store instances`() =
        runTest {
            fun newStore() =
                DataStoreBillingEntitlementStore(
                    PreferenceDataStoreFactory.create(
                        scope = backgroundScope,
                        produceFile = { tempDir.resolve("billing_prefs.preferences_pb").toFile() },
                    ),
                )

            val first = newStore()
            assertEquals(emptySet(), first.ownedHats.first())
            first.grantHat(BillingProducts.HAT_PARTY_CONE)
            assertEquals(setOf(BillingProducts.HAT_PARTY_CONE), first.ownedHats.first())

            // A fresh store over the same file sees the grant: deterministic.
            val second = newStore()
            assertEquals(setOf(BillingProducts.HAT_PARTY_CONE), second.ownedHats.first())

            second.grantHat(BillingProducts.HAT_TINY_CROWN)
            assertEquals(
                setOf(BillingProducts.HAT_PARTY_CONE, BillingProducts.HAT_TINY_CROWN),
                second.ownedHats.first(),
            )
        }

    @Test
    fun `entitlements model reports hat ownership`() {
        val entitlements =
            Entitlements(
                adsRemoved = true,
                ownedHats = kotlinx.collections.immutable.persistentSetOf(BillingProducts.HAT_PARTY_CONE),
            )
        assertTrue(entitlements.adsRemoved)
        assertTrue(entitlements.ownsHat(BillingProducts.HAT_PARTY_CONE))
        assertTrue(!entitlements.ownsHat(BillingProducts.HAT_TINY_CROWN))
    }
}
