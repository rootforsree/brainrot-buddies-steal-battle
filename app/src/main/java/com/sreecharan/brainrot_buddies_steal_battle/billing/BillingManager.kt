package com.sreecharan.brainrot_buddies_steal_battle.billing

import android.app.Activity
import android.content.Context
import android.os.RemoteException
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryProductDetailsResult
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.sreecharan.brainrot_buddies_steal_battle.data.model.Result
import com.sreecharan.brainrot_buddies_steal_battle.data.repository.GameRepository
import com.sreecharan.brainrot_buddies_steal_battle.di.IoDispatcher
import com.sreecharan.brainrot_buddies_steal_battle.p2p.getOrDefault
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Play Billing 8.0.0 manager, per docs/contract.md §1.
 *
 * Products: remove_ads ($2.99 one-time), three cosmetic hats ($0.99 each,
 * non-consumable). All purchases are deterministic entitlements persisted in
 * DataStore; nothing random is purchasable, ever.
 *
 * PARENT GATE: the Shop UI (Builder C) shows ParentGate before calling
 * [startPurchase]. This manager never launches a purchase flow on its own.
 */
@Singleton
class BillingManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val entitlementStore: BillingEntitlementStore,
    private val gameRepository: GameRepository,
) {

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private var billingClient: BillingClient? = null
    private val productDetails = mutableMapOf<String, ProductDetails>()

    val entitlements: StateFlow<Entitlements> =
        combine(entitlementStore.ownedHats, gameRepository.observeAdsRemoved()) { hats, removed ->
            Entitlements(adsRemoved = removed.getOrDefault(false), ownedHats = hats.toImmutableSet())
        }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), Entitlements())

    private val _products = MutableStateFlow<List<ProductOffer>>(emptyList())
    val products: StateFlow<List<ProductOffer>> = _products.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _events = MutableSharedFlow<BillingEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<BillingEvent> = _events.asSharedFlow()

    private val purchasesListener =
        BillingClient.PurchasesUpdatedListener { result, purchases ->
            scope.launch {
                if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                    purchases.forEach { applyPurchase(it) }
                } else if (result.responseCode != BillingClient.BillingResponseCode.USER_CANCELED) {
                    Log.w(TAG, "Purchase failed: ${result.responseCode} ${result.debugMessage}")
                }
            }
        }

    /** Refreshes product titles/prices from Play. Safe to call repeatedly. */
    fun refreshProducts() {
        scope.launch {
            _busy.value = true
            try {
                guardBillingCall("refreshProducts failed", BillingEvent.StoreUnavailable) {
                    refreshProductsInternal()
                }
            } finally {
                _busy.value = false
            }
        }
    }

    private suspend fun refreshProductsInternal() {
        val client = ensureConnected()
        if (client == null) {
            _events.emit(BillingEvent.StoreUnavailable)
            return
        }
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                BillingProducts.ALL.map { id ->
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(id)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                },
            )
            .build()
        val result = client.queryProductDetails(params)
        if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            applyProductDetails(result)
            _events.emit(BillingEvent.ProductsRefreshed)
        } else {
            Log.w(TAG, "queryProductDetails failed: ${result.billingResult.responseCode}")
            _events.emit(BillingEvent.StoreUnavailable)
        }
    }

    private fun applyProductDetails(result: QueryProductDetailsResult) {
        productDetails.clear()
        result.productDetailsList.orEmpty().forEach { details ->
            productDetails[details.productId] = details
        }
        _products.value = BillingProducts.ALL.mapNotNull { id ->
            productDetails[id]?.let { details ->
                ProductOffer(
                    productId = id,
                    title = details.title.substringBefore(" ("),
                    priceText = details.oneTimePurchaseOfferDetails?.formattedPrice.orEmpty(),
                )
            }
        }
    }

    /**
     * Launches the Play purchase flow for [productId].
     *
     * Precondition: the caller has already passed Builder C's ParentGate.
     * Must be called from an Activity context owner.
     */
    fun startPurchase(activity: Activity, productId: String) {
        scope.launch {
            _busy.value = true
            try {
                guardBillingCall("startPurchase failed", BillingEvent.PurchaseFailed(productId)) {
                    startPurchaseInternal(activity, productId)
                }
            } finally {
                _busy.value = false
            }
        }
    }

    private suspend fun startPurchaseInternal(activity: Activity, productId: String) {
        val client = ensureConnected()
        if (client == null) {
            _events.emit(BillingEvent.StoreUnavailable)
            return
        }
        var details = productDetails[productId]
        if (details == null) {
            refreshProductsSync(client)
            details = productDetails[productId]
        }
        if (details == null) {
            _events.emit(BillingEvent.StoreUnavailable)
            return
        }
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .build(),
                ),
            )
            .build()
        val result = client.launchBillingFlow(activity, flowParams)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            Log.w(TAG, "launchBillingFlow failed: ${result.responseCode}")
            _events.emit(BillingEvent.PurchaseFailed(productId))
        }
        // Success/failure arrives via purchasesListener.
    }

    /** Re-applies entitlements for already-owned products (new device, reinstall). */
    fun restorePurchases() {
        scope.launch {
            _busy.value = true
            try {
                guardBillingCall("restorePurchases failed", BillingEvent.StoreUnavailable) {
                    val client = ensureConnected()
                    if (client == null) {
                        _events.emit(BillingEvent.StoreUnavailable)
                        return@guardBillingCall
                    }
                    val params = QueryPurchasesParams.newBuilder()
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                    val result = client.queryPurchasesAsync(params)
                    result.purchasesList.forEach { applyPurchase(it) }
                    _events.emit(BillingEvent.RestoreCompleted)
                }
            } finally {
                _busy.value = false
            }
        }
    }

    private suspend fun applyPurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        if (!purchase.isAcknowledged) {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            val result = billingClient?.acknowledgePurchase(params)
            if (result?.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.w(TAG, "acknowledgePurchase failed: ${result?.responseCode}")
                return
            }
        }
        purchase.products.forEach { productId ->
            when (BillingProducts.entitlementFor(productId)) {
                is ProductEntitlement.RemoveAds -> {
                    val result = gameRepository.setAdsRemoved(true)
                    if (result is Result.Error) {
                        Log.w(TAG, "setAdsRemoved failed")
                    }
                }
                is ProductEntitlement.Hat -> entitlementStore.grantHat(productId)
                null -> Log.w(TAG, "Unknown product id: $productId")
            }
            _events.emit(BillingEvent.PurchaseSucceeded(productId))
        }
    }

    private suspend fun refreshProductsSync(client: BillingClient) {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                BillingProducts.ALL.map { id ->
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(id)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                },
            )
            .build()
        val result = runCatching { client.queryProductDetails(params) }.getOrNull() ?: return
        if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            result.productDetailsList.orEmpty().forEach { details ->
                productDetails[details.productId] = details
            }
        }
    }

    private suspend fun ensureConnected(): BillingClient? {
        billingClient?.let { if (it.isReady) return it }
        val client = BillingClient.newBuilder(context)
            .setListener(purchasesListener)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build(),
            )
            .build()
        billingClient = client
        val connected = suspendCancellableCoroutine { cont ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (cont.isActive) {
                        cont.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
                    }
                }

                override fun onBillingServiceDisconnected() {
                    // Play will reconnect on the next ensureConnected() call.
                }
            })
        }
        return if (connected) client else null
    }

    companion object {
        private const val TAG = "BillingManager"
    }

    /**
     * Runs a billing body, converting Play/binder failure modes into the
     * given fallback event. Only the realistic failure modes of the
     * BillingClient are caught; anything else still surfaces.
     */
    private suspend fun guardBillingCall(
        logMessage: String,
        fallbackEvent: BillingEvent,
        body: suspend () -> Unit,
    ) {
        try {
            body()
        } catch (e: RemoteException) {
            Log.w(TAG, logMessage, e)
            _events.emit(fallbackEvent)
        } catch (e: IllegalStateException) {
            Log.w(TAG, logMessage, e)
            _events.emit(fallbackEvent)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, logMessage, e)
            _events.emit(fallbackEvent)
        } catch (e: IOException) {
            Log.w(TAG, logMessage, e)
            _events.emit(fallbackEvent)
        }
    }
}
