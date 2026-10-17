package com.sreecharan.brainrot_buddies_steal_battle.fakes

import com.sreecharan.brainrot_buddies_steal_battle.data.datastore.GamePrefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Functional fake of [GamePrefs]. Backs the Builder C additions
 * (buddy bites, claimed album bonuses, last weekly gift date) alongside
 * the frozen keys.
 */
class FakeGamePrefs : GamePrefs {
    val guestIdState = MutableStateFlow<String?>(null)
    val baseNameState = MutableStateFlow("")
    val musicVolumeState = MutableStateFlow(100)
    val sfxVolumeState = MutableStateFlow(100)
    val voiceVolumeState = MutableStateFlow(100)
    val hapticsState = MutableStateFlow(true)
    val onboardingDayState = MutableStateFlow(1)
    val tutorialDoneState = MutableStateFlow(false)
    val adsRemovedState = MutableStateFlow(false)
    val posterPathState = MutableStateFlow("")
    val lastRaidWindowState = MutableStateFlow("")
    val raidsUsedState = MutableStateFlow(0)
    val bennyHintsState = MutableStateFlow(true)
    val usedCodesState = MutableStateFlow<Set<String>>(emptySet())
    val buddyBitesState = MutableStateFlow(0)
    val claimedAlbumBonusesState = MutableStateFlow<Set<String>>(emptySet())
    val lastWeeklyGiftDateState = MutableStateFlow("")

    override val guestId: Flow<String?> = guestIdState
    override val baseName: Flow<String> = baseNameState
    override val musicVolume: Flow<Int> = musicVolumeState
    override val sfxVolume: Flow<Int> = sfxVolumeState
    override val voiceVolume: Flow<Int> = voiceVolumeState
    override val hapticsEnabled: Flow<Boolean> = hapticsState
    override val onboardingDay: Flow<Int> = onboardingDayState
    override val tutorialDone: Flow<Boolean> = tutorialDoneState
    override val adsRemoved: Flow<Boolean> = adsRemovedState
    override val posterPath: Flow<String> = posterPathState
    override val lastRaidWindow: Flow<String> = lastRaidWindowState
    override val raidsUsed: Flow<Int> = raidsUsedState
    override val bennyHintsEnabled: Flow<Boolean> = bennyHintsState
    override val usedCodes: Flow<Set<String>> = usedCodesState
    override val buddyBites: Flow<Int> = buddyBitesState
    override val claimedAlbumBonuses: Flow<Set<String>> = claimedAlbumBonusesState
    override val lastWeeklyGiftDate: Flow<String> = lastWeeklyGiftDateState

    override suspend fun ensureGuestId(): String {
        val current = guestIdState.value
        if (!current.isNullOrBlank()) return current
        val fresh = "guest-test"
        guestIdState.value = fresh
        return fresh
    }

    override suspend fun setBaseName(name: String) {
        baseNameState.value = name
    }

    override suspend fun setMusicVolume(volume: Int) {
        musicVolumeState.value = volume
    }

    override suspend fun setSfxVolume(volume: Int) {
        sfxVolumeState.value = volume
    }

    override suspend fun setVoiceVolume(volume: Int) {
        voiceVolumeState.value = volume
    }

    override suspend fun setHapticsEnabled(enabled: Boolean) {
        hapticsState.value = enabled
    }

    override suspend fun setOnboardingDay(day: Int) {
        onboardingDayState.value = day
    }

    override suspend fun setTutorialDone(done: Boolean) {
        tutorialDoneState.value = done
    }

    override suspend fun setAdsRemoved(removed: Boolean) {
        adsRemovedState.value = removed
    }

    override suspend fun setPosterPath(path: String) {
        posterPathState.value = path
    }

    override suspend fun setLastRaidWindow(bucket: String) {
        lastRaidWindowState.value = bucket
    }

    override suspend fun setRaidsUsed(count: Int) {
        raidsUsedState.value = count
    }

    override suspend fun setBennyHintsEnabled(enabled: Boolean) {
        bennyHintsState.value = enabled
    }

    override suspend fun addUsedCode(code: String) {
        usedCodesState.value = usedCodesState.value + code.trim().uppercase()
    }

    override suspend fun setBuddyBites(count: Int) {
        buddyBitesState.value = count.coerceAtLeast(0)
    }

    override suspend fun addClaimedAlbumBonus(tier: String) {
        claimedAlbumBonusesState.value = claimedAlbumBonusesState.value + tier
    }

    override suspend fun setLastWeeklyGiftDate(date: String) {
        lastWeeklyGiftDateState.value = date
    }

    override suspend fun clearAll() {
        guestIdState.value = null
        baseNameState.value = ""
        buddyBitesState.value = 0
        claimedAlbumBonusesState.value = emptySet()
        lastWeeklyGiftDateState.value = ""
        usedCodesState.value = emptySet()
    }
}
