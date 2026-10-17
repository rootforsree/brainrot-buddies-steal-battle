package com.sreecharan.brainrot_buddies_steal_battle.ads

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.google.ads.mediation.admob.AdMobAdapter
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.OnUserEarnedRewardListener
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.sreecharan.brainrot_buddies_steal_battle.ads.di.AdsPreferences
import com.sreecharan.brainrot_buddies_steal_battle.ads.di.MainDispatcher
import com.sreecharan.brainrot_buddies_steal_battle.data.repository.GameRepository
import com.sreecharan.brainrot_buddies_steal_battle.di.IoDispatcher
import com.sreecharan.brainrot_buddies_steal_battle.p2p.firstData
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rewarded ads, per docs/contract.md §1.
 *
 * EXACTLY ONE placement, called only from the Shop "Free coins" button.
 * - Families self-certified SDK (AdMob 23.6.0) only.
 * - G-rated, non-personalized: tagForChildDirectedTreatment(true),
 *   max_ad_content_rating = "G", npa = 1.
 * - Max 1 show per 30 minutes (persisted across restarts).
 * - Closeable in <= 5s (the SDK close button; we never interfere).
 * - NEVER shown on launch: only the Shop calls [showAd].
 * - Starts muted, with a visible unmute control owned by the Shop UI.
 * - Honors remove-ads: when [GameRepository.observeAdsRemoved] is true,
 *   ads are never loaded at all.
 */
@Singleton
class RewardedAdManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
        @MainDispatcher private val mainDispatcher: CoroutineDispatcher,
        private val gameRepository: GameRepository,
        private val throttle: AdThrottle,
        @AdsPreferences private val prefs: DataStore<Preferences>,
    ) {

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private var rewardedAd: RewardedAd? = null
    private var configured = false

    private val _adReady = MutableStateFlow(false)
    val adReady: StateFlow<Boolean> = _adReady.asStateFlow()

    private val _events = MutableSharedFlow<AdEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<AdEvent> = _events.asSharedFlow()

    private val lastShownAt: Flow<Long> = prefs.data.map { it[KEY_LAST_SHOWN_AT] ?: 0L }

    /** Preloads the rewarded ad. No-op when ads were removed: nothing is loaded, ever. */
    fun loadAd() {
    scope.launch {
        if (gameRepository.observeAdsRemoved().firstData() ?: false) {
            _adReady.value = false
            return@launch
        }
        ensureConfigured()
        if (rewardedAd != null) {
            _adReady.value = true
            return@launch
        }
        val extras = Bundle().apply { putString("npa", "1") }
        val request = AdRequest.Builder()
            .addNetworkExtrasBundle(AdMobAdapter::class.java, extras)
            .build()
        withContext(mainDispatcher) {
            RewardedAd.load(context, REWARDED_AD_UNIT_ID, request, loadCallback)
        }
    }
    }

    /**
     * Shows the ad. The Shop calls this only from its "Free coins" button.
     * [onReward] grants 100 coins or 5 bites (Shop's choice, contract §4).
     */
    fun showAd(activity: Activity, onReward: () -> Unit) {
    scope.launch {
        if (gameRepository.observeAdsRemoved().firstData() ?: false) return@launch
        val now = System.currentTimeMillis()
        val lastShown = lastShownAt.first()
        if (!throttle.canShow(now, lastShown)) {
            _events.emit(AdEvent.CooldownActive(throttle.millisUntilNextShow(now, lastShown)))
            return@launch
        }
        val ad = rewardedAd ?: run {
            _events.emit(AdEvent.NotReady)
            loadAd()
            return@launch
        }
        withContext(mainDispatcher) {
            // Contract: ads start muted; the Shop shows the unmute chip.
            MobileAds.setAppMuted(true)
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    rewardedAd = null
                    _adReady.value = false
                    scope.launch {
                        prefs.edit { it[KEY_LAST_SHOWN_AT] = System.currentTimeMillis() }
                        _events.emit(AdEvent.Closed)
                        loadAd()
                    }
                }

                override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                    Log.w(TAG, "Ad failed to show: ${error.message}")
                    rewardedAd = null
                    _adReady.value = false
                    scope.launch {
                        _events.emit(AdEvent.NotReady)
                        loadAd()
                    }
                }
            }
            ad.show(activity, OnUserEarnedRewardListener { onReward() })
        }
    }
    }

    /** Called by the Shop's unmute chip. */
    fun setAdMuted(muted: Boolean) {
    MobileAds.setAppMuted(muted)
    }

    private val loadCallback = object : RewardedAdLoadCallback() {
    override fun onAdLoaded(ad: RewardedAd) {
        rewardedAd = ad
        _adReady.value = true
    }

    override fun onAdFailedToLoad(error: LoadAdError) {
        Log.w(TAG, "Rewarded ad failed to load: ${error.message}")
        rewardedAd = null
        _adReady.value = false
    }
    }

    private fun ensureConfigured() {
    if (configured) return
    configured = true
    MobileAds.setRequestConfiguration(
        RequestConfiguration.Builder()
            .setTagForChildDirectedTreatment(
                RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_TRUE,
            )
            .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_G)
            .build(),
    )
    MobileAds.initialize(context) { }
    }

    companion object {
    private const val TAG = "RewardedAdManager"

    /**
     * Google's official SAMPLE rewarded ad unit id. Replace with the real
     * rewarded ad unit id before any release build.
     */
    const val REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"

    private val KEY_LAST_SHOWN_AT = longPreferencesKey("last_rewarded_ad_shown_at")
    }
}

/** One-shot ad outcomes for the Shop UI. */
sealed interface AdEvent {
    data object NotReady : AdEvent
    data class CooldownActive(val millisRemaining: Long) : AdEvent
    data object Closed : AdEvent
}
