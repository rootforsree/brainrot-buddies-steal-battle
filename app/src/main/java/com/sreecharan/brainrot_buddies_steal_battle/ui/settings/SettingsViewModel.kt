package com.sreecharan.brainrot_buddies_steal_battle.ui.settings

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sreecharan.brainrot_buddies_steal_battle.billing.BillingEvent
import com.sreecharan.brainrot_buddies_steal_battle.billing.BillingManager
import com.sreecharan.brainrot_buddies_steal_battle.billing.BillingProducts
import com.sreecharan.brainrot_buddies_steal_battle.data.datastore.SettingsRepository
import com.sreecharan.brainrot_buddies_steal_battle.data.model.Result
import com.sreecharan.brainrot_buddies_steal_battle.data.model.ThemeMode
import com.sreecharan.brainrot_buddies_steal_battle.data.repository.GameRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface SettingsEvent {
    data object RemoveAdsPurchased : SettingsEvent
    data object BillingFailed : SettingsEvent
    data object DataDeleted : SettingsEvent
    data object DeleteFailed : SettingsEvent
}

/**
 * Settings: theme + haptics (template SettingsRepository), the 3 audio
 * channels + Benny hints (GameRepository/GamePrefs), Remove Ads IAP
 * (Builder D's BillingManager, parent-gated in the UI), and the
 * delete-all-data button (parent-gated in the UI).
 *
 * NOTE for the coordinator: both the template Proto DataStore
 * (SettingsRepository.hapticsEnabled) and the frozen GamePrefs `haptics`
 * key (§5) describe the haptics toggle. This screen reads the template
 * repository (what MainActivity already uses); the two sources should be
 * unified to one.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val repository: GameRepository,
    private val billingManager: BillingManager,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val eventsChannel = Channel<SettingsEvent>(Channel.BUFFERED)
    val events = eventsChannel.receiveAsFlow()

    private val deletingState = MutableStateFlow(false)

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        repository.observeMusicVolume(),
        repository.observeSfxVolume(),
        repository.observeVoiceVolume(),
        repository.observeBennyHintsEnabled(),
        billingManager.entitlements,
        deletingState,
    ) { settings, music, sfx, voice, benny, entitlements, deleting ->
        SettingsUiState(
            themeMode = settings.themeMode,
            hapticsEnabled = settings.hapticsEnabled,
            musicVolume = music.dataOrNull()?.coerceIn(MIN_VOLUME, MAX_VOLUME) ?: DEFAULT_VOLUME,
            sfxVolume = sfx.dataOrNull()?.coerceIn(MIN_VOLUME, MAX_VOLUME) ?: DEFAULT_VOLUME,
            voiceVolume = voice.dataOrNull()?.coerceIn(MIN_VOLUME, MAX_VOLUME) ?: DEFAULT_VOLUME,
            bennyHints = benny.dataOrNull() ?: true,
            adsRemoved = entitlements.adsRemoved,
            isDeleting = deleting,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    init {
        viewModelScope.launch {
            billingManager.events.collect { event ->
                when (event) {
                    is BillingEvent.PurchaseSucceeded ->
                        if (event.productId == BillingProducts.REMOVE_ADS) {
                            eventsChannel.send(SettingsEvent.RemoveAdsPurchased)
                        }
                    is BillingEvent.PurchaseFailed ->
                        if (event.productId == BillingProducts.REMOVE_ADS) {
                            eventsChannel.send(SettingsEvent.BillingFailed)
                        }
                    BillingEvent.StoreUnavailable ->
                        eventsChannel.send(SettingsEvent.BillingFailed)
                    else -> Unit
                }
            }
        }
    }

    fun onThemeModeSelected(mode: ThemeMode) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(mode)
        }
    }

    fun onHapticsToggled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setHapticsEnabled(enabled)
        }
    }

    fun onMusicVolumeChanged(value: Int) {
        viewModelScope.launch {
            repository.setMusicVolume(value.coerceIn(MIN_VOLUME, MAX_VOLUME))
        }
    }

    fun onSfxVolumeChanged(value: Int) {
        viewModelScope.launch {
            repository.setSfxVolume(value.coerceIn(MIN_VOLUME, MAX_VOLUME))
        }
    }

    fun onVoiceVolumeChanged(value: Int) {
        viewModelScope.launch {
            repository.setVoiceVolume(value.coerceIn(MIN_VOLUME, MAX_VOLUME))
        }
    }

    fun onBennyHintsToggled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setBennyHintsEnabled(enabled)
        }
    }

    /**
     * Called after the parent gate passes for the Remove Ads IAP.
     * [BillingManager.startPurchase] is fire-and-forget; the result arrives
     * on [BillingManager.events] and is mapped to a [SettingsEvent] above.
     */
    fun onRemoveAdsConfirmed(activity: Activity) {
        billingManager.startPurchase(activity, BillingProducts.REMOVE_ADS)
    }

    /**
     * Called after the parent gate passes. Wipes Room + DataStore (via the
     * repository) and the app-private poster file, then the NavHost drops
     * back to first-launch onboarding on its own.
     */
    fun onDeleteDataConfirmed() {
        viewModelScope.launch {
            deletingState.update { true }
            try {
                val posterPath = repository.observePosterPath().awaitData().orEmpty()
                if (posterPath.isNotBlank()) {
                    File(context.filesDir, posterPath).delete()
                }
                repository.clearAllData()
                eventsChannel.send(SettingsEvent.DataDeleted)
            } catch (_: Exception) {
                eventsChannel.send(SettingsEvent.DeleteFailed)
            } finally {
                deletingState.update { false }
            }
        }
    }

    companion object {
        /** Volume sliders are 0-100 (contract §5 keys `music_vol`/`sfx_vol`/`voice_vol`). */
        const val MIN_VOLUME = 0
        const val MAX_VOLUME = 100
        const val DEFAULT_VOLUME = 80
    }
}

private fun <T> Result<T>.dataOrNull(): T? = (this as? Result.Success<T>)?.data

private suspend fun <T> Flow<Result<T>>.awaitData(): T? {
    return when (val result = first { it !is Result.Loading }) {
        is Result.Success -> result.data
        else -> null
    }
}
