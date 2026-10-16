package com.sreecharan.brainrot_buddies_steal_battle.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Game preferences backed by Preferences DataStore. Keys frozen by
 * docs/contract.md §5. This lives alongside the template's Proto-based
 * [SettingsRepository], which keeps owning theme settings.
 */
interface GamePrefs {

    val guestId: Flow<String?>
    val baseName: Flow<String>
    val musicVolume: Flow<Int>
    val sfxVolume: Flow<Int>
    val voiceVolume: Flow<Int>
    val hapticsEnabled: Flow<Boolean>
    val onboardingDay: Flow<Int>
    val tutorialDone: Flow<Boolean>
    val adsRemoved: Flow<Boolean>
    val posterPath: Flow<String>
    val lastRaidWindow: Flow<String>
    val raidsUsed: Flow<Int>
    val bennyHintsEnabled: Flow<Boolean>
    val usedCodes: Flow<Set<String>>

    /**
     * Buddy Bites balance (§1: duplicates → 5 bites, 20 bites = re-roll,
     * upgrade costs). Added for Builder C's shop/upgrades; the frozen §5
     * schema had no home for it.
     */
    val buddyBites: Flow<Int>

    /**
     * Album tier bonuses already claimed (§4). Tier keys are
     * [Rarity.name]. Added for Builder C's album screen.
     */
    val claimedAlbumBonuses: Flow<Set<String>>

    /**
     * ISO date of the last weekly-gift claim (§1). Added for Builder C's
     * gifts screen.
     */
    val lastWeeklyGiftDate: Flow<String>

    /** Returns the stored guest id, creating and storing a UUID on first call. */
    suspend fun ensureGuestId(): String
    suspend fun setBaseName(name: String)
    suspend fun setMusicVolume(volume: Int)
    suspend fun setSfxVolume(volume: Int)
    suspend fun setVoiceVolume(volume: Int)
    suspend fun setHapticsEnabled(enabled: Boolean)
    suspend fun setOnboardingDay(day: Int)
    suspend fun setTutorialDone(done: Boolean)
    suspend fun setAdsRemoved(removed: Boolean)
    suspend fun setPosterPath(path: String)
    suspend fun setLastRaidWindow(bucket: String)
    suspend fun setRaidsUsed(count: Int)
    suspend fun setBennyHintsEnabled(enabled: Boolean)

    /** Records a redeem code as used. Codes are stored upper-cased. */
    suspend fun addUsedCode(code: String)

    /** Sets the Buddy Bites balance (never negative in practice). */
    suspend fun setBuddyBites(count: Int)

    /** Records an album tier bonus as claimed. */
    suspend fun addClaimedAlbumBonus(tier: String)

    /** Stamps the weekly-gift claim date (ISO date). */
    suspend fun setLastWeeklyGiftDate(date: String)
    suspend fun clearAll()
}

@Singleton
class GamePrefsImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : GamePrefs {

    private object Keys {
        val GUEST_ID = stringPreferencesKey("guest_id")
        val BASE_NAME = stringPreferencesKey("base_name")
        val MUSIC_VOL = intPreferencesKey("music_vol")
        val SFX_VOL = intPreferencesKey("sfx_vol")
        val VOICE_VOL = intPreferencesKey("voice_vol")
        val HAPTICS = booleanPreferencesKey("haptics")
        val ONBOARDING_DAY = intPreferencesKey("onboarding_day")
        val TUTORIAL_DONE = booleanPreferencesKey("tutorial_done")
        val ADS_REMOVED = booleanPreferencesKey("ads_removed")
        val POSTER_PATH = stringPreferencesKey("poster_path")
        val LAST_RAID_WINDOW = stringPreferencesKey("last_raid_window")
        val RAIDS_USED = intPreferencesKey("raids_used")
        val BENNY_HINTS = booleanPreferencesKey("benny_hints")
        val USED_CODES = stringSetPreferencesKey("used_codes")
        val BUDDY_BITES = intPreferencesKey("buddy_bites")
        val CLAIMED_ALBUM_BONUSES = stringSetPreferencesKey("claimed_album_bonuses")
        val LAST_WEEKLY_GIFT = stringPreferencesKey("last_weekly_gift")
    }

    private val prefs: Flow<Preferences> = dataStore.data
        .catch { e ->
            if (e is IOException) {
                emit(emptyPreferences())
            } else {
                throw e
            }
        }

    override val guestId: Flow<String?> = prefs.map { it[Keys.GUEST_ID] }
    override val baseName: Flow<String> = prefs.map { it[Keys.BASE_NAME].orEmpty() }
    override val musicVolume: Flow<Int> = prefs.map { it[Keys.MUSIC_VOL] ?: FULL_VOLUME }
    override val sfxVolume: Flow<Int> = prefs.map { it[Keys.SFX_VOL] ?: FULL_VOLUME }
    override val voiceVolume: Flow<Int> = prefs.map { it[Keys.VOICE_VOL] ?: FULL_VOLUME }
    override val hapticsEnabled: Flow<Boolean> = prefs.map { it[Keys.HAPTICS] ?: true }
    override val onboardingDay: Flow<Int> = prefs.map { it[Keys.ONBOARDING_DAY] ?: FIRST_DAY }
    override val tutorialDone: Flow<Boolean> = prefs.map { it[Keys.TUTORIAL_DONE] ?: false }
    override val adsRemoved: Flow<Boolean> = prefs.map { it[Keys.ADS_REMOVED] ?: false }
    override val posterPath: Flow<String> = prefs.map { it[Keys.POSTER_PATH].orEmpty() }
    override val lastRaidWindow: Flow<String> = prefs.map { it[Keys.LAST_RAID_WINDOW].orEmpty() }
    override val raidsUsed: Flow<Int> = prefs.map { it[Keys.RAIDS_USED] ?: 0 }
    override val bennyHintsEnabled: Flow<Boolean> = prefs.map { it[Keys.BENNY_HINTS] ?: true }
    override val usedCodes: Flow<Set<String>> = prefs.map { it[Keys.USED_CODES].orEmpty() }
    override val buddyBites: Flow<Int> = prefs.map { it[Keys.BUDDY_BITES] ?: 0 }
    override val claimedAlbumBonuses: Flow<Set<String>> =
        prefs.map { it[Keys.CLAIMED_ALBUM_BONUSES].orEmpty() }
    override val lastWeeklyGiftDate: Flow<String> =
        prefs.map { it[Keys.LAST_WEEKLY_GIFT].orEmpty() }

    override suspend fun ensureGuestId(): String {
        val current = dataStore.data.first()[Keys.GUEST_ID]
        if (!current.isNullOrBlank()) {
            return current
        }
        val fresh = UUID.randomUUID().toString()
        dataStore.edit { it[Keys.GUEST_ID] = fresh }
        return fresh
    }

    override suspend fun setBaseName(name: String) {
        dataStore.edit { it[Keys.BASE_NAME] = name }
    }

    override suspend fun setMusicVolume(volume: Int) {
        dataStore.edit { it[Keys.MUSIC_VOL] = volume.coerceIn(MIN_VOLUME, FULL_VOLUME) }
    }

    override suspend fun setSfxVolume(volume: Int) {
        dataStore.edit { it[Keys.SFX_VOL] = volume.coerceIn(MIN_VOLUME, FULL_VOLUME) }
    }

    override suspend fun setVoiceVolume(volume: Int) {
        dataStore.edit { it[Keys.VOICE_VOL] = volume.coerceIn(MIN_VOLUME, FULL_VOLUME) }
    }

    override suspend fun setHapticsEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.HAPTICS] = enabled }
    }

    override suspend fun setOnboardingDay(day: Int) {
        dataStore.edit { it[Keys.ONBOARDING_DAY] = day.coerceAtLeast(FIRST_DAY) }
    }

    override suspend fun setTutorialDone(done: Boolean) {
        dataStore.edit { it[Keys.TUTORIAL_DONE] = done }
    }

    override suspend fun setAdsRemoved(removed: Boolean) {
        dataStore.edit { it[Keys.ADS_REMOVED] = removed }
    }

    override suspend fun setPosterPath(path: String) {
        dataStore.edit { it[Keys.POSTER_PATH] = path }
    }

    override suspend fun setLastRaidWindow(bucket: String) {
        dataStore.edit { it[Keys.LAST_RAID_WINDOW] = bucket }
    }

    override suspend fun setRaidsUsed(count: Int) {
        dataStore.edit { it[Keys.RAIDS_USED] = count.coerceAtLeast(0) }
    }

    override suspend fun setBennyHintsEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.BENNY_HINTS] = enabled }
    }

    override suspend fun addUsedCode(code: String) {
        val normalized = code.trim().uppercase()
        dataStore.edit { prefs ->
            prefs[Keys.USED_CODES] = prefs[Keys.USED_CODES].orEmpty() + normalized
        }
    }

    override suspend fun setBuddyBites(count: Int) {
        dataStore.edit { it[Keys.BUDDY_BITES] = count.coerceAtLeast(0) }
    }

    override suspend fun addClaimedAlbumBonus(tier: String) {
        dataStore.edit { prefs ->
            prefs[Keys.CLAIMED_ALBUM_BONUSES] = prefs[Keys.CLAIMED_ALBUM_BONUSES].orEmpty() + tier
        }
    }

    override suspend fun setLastWeeklyGiftDate(date: String) {
        dataStore.edit { it[Keys.LAST_WEEKLY_GIFT] = date }
    }

    override suspend fun clearAll() {
        dataStore.edit { it.clear() }
    }

    companion object {
        private const val FULL_VOLUME = 100
        private const val MIN_VOLUME = 0
        private const val FIRST_DAY = 1
    }
}
