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
 *
 * The surface is split into focused seams (identity, audio, rewards,
 * raids) so no single interface exceeds the complexity budget; [GamePrefs]
 * composes them all and stays the type the UI injects.
 */
interface GamePrefs :
    GameIdentityPrefs,
    GameAudioPrefs,
    GameRewardPrefs,
    GameRaidPrefs {
    /** Clears every game preference (data-deletion button). */
    suspend fun clearAll()
}

/** Identity and onboarding state. */
interface GameIdentityPrefs {
    val guestId: Flow<String?>
    val baseName: Flow<String>
    val onboardingDay: Flow<Int>
    val tutorialDone: Flow<Boolean>
    val posterPath: Flow<String>
    val bennyHintsEnabled: Flow<Boolean>

    /** Returns the stored guest id, creating and storing a UUID on first call. */
    suspend fun ensureGuestId(): String

    suspend fun setBaseName(name: String)

    suspend fun setOnboardingDay(day: Int)

    suspend fun setTutorialDone(done: Boolean)

    suspend fun setPosterPath(path: String)

    suspend fun setBennyHintsEnabled(enabled: Boolean)
}

/** The three audio channels (§9) plus haptics. */
interface GameAudioPrefs {
    val musicVolume: Flow<Int>
    val sfxVolume: Flow<Int>
    val voiceVolume: Flow<Int>
    val hapticsEnabled: Flow<Boolean>

    suspend fun setMusicVolume(volume: Int)

    suspend fun setSfxVolume(volume: Int)

    suspend fun setVoiceVolume(volume: Int)

    suspend fun setHapticsEnabled(enabled: Boolean)
}

/** Rewards, entitlements and gift state. */
interface GameRewardPrefs {
    val adsRemoved: Flow<Boolean>

    /**
     * Buddy Bites balance (§1: duplicates → 5 bites, 20 bites = re-roll,
     * upgrade costs). Added for Builder C's shop/upgrades; the frozen §5
     * schema had no home for it.
     */
    val buddyBites: Flow<Int>
    val usedCodes: Flow<Set<String>>

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

    suspend fun setAdsRemoved(removed: Boolean)

    /** Records a redeem code as used. Codes are stored upper-cased. */
    suspend fun addUsedCode(code: String)

    /** Sets the Buddy Bites balance (never negative in practice). */
    suspend fun setBuddyBites(count: Int)

    /** Records an album tier bonus as claimed. */
    suspend fun addClaimedAlbumBonus(tier: String)

    /** Stamps the weekly-gift claim date (ISO date). */
    suspend fun setLastWeeklyGiftDate(date: String)
}

/** Raid-window bookkeeping (§1: three raids per 6-hour window). */
interface GameRaidPrefs {
    val lastRaidWindow: Flow<String>
    val raidsUsed: Flow<Int>

    suspend fun setLastRaidWindow(bucket: String)

    suspend fun setRaidsUsed(count: Int)
}

/**
 * Preference keys, frozen by docs/contract.md §5. Shared by the store
 * implementations below; the key STRINGS are the frozen contract.
 */
internal object GamePrefsKeys {
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

/**
 * DataStore reads that swallow only corruption/IO failures (emitting empty
 * preferences); anything else still crashes loudly.
 */
internal fun DataStore<Preferences>.safePreferences(): Flow<Preferences> =
    data
        .catch { e ->
            if (e is IOException) {
                emit(emptyPreferences())
            } else {
                throw e
            }
        }

internal class IdentityPrefsStore(
    private val dataStore: DataStore<Preferences>,
) : GameIdentityPrefs {
    private val prefs: Flow<Preferences> = dataStore.safePreferences()

    override val guestId: Flow<String?> = prefs.map { it[GamePrefsKeys.GUEST_ID] }
    override val baseName: Flow<String> = prefs.map { it[GamePrefsKeys.BASE_NAME].orEmpty() }
    override val onboardingDay: Flow<Int> = prefs.map { it[GamePrefsKeys.ONBOARDING_DAY] ?: FIRST_DAY }
    override val tutorialDone: Flow<Boolean> = prefs.map { it[GamePrefsKeys.TUTORIAL_DONE] ?: false }
    override val posterPath: Flow<String> = prefs.map { it[GamePrefsKeys.POSTER_PATH].orEmpty() }
    override val bennyHintsEnabled: Flow<Boolean> =
        prefs.map { it[GamePrefsKeys.BENNY_HINTS] ?: true }

    override suspend fun ensureGuestId(): String {
        val current = dataStore.data.first()[GamePrefsKeys.GUEST_ID]
        if (!current.isNullOrBlank()) {
            return current
        }
        val fresh = UUID.randomUUID().toString()
        dataStore.edit { it[GamePrefsKeys.GUEST_ID] = fresh }
        return fresh
    }

    override suspend fun setBaseName(name: String) {
        dataStore.edit { it[GamePrefsKeys.BASE_NAME] = name }
    }

    override suspend fun setOnboardingDay(day: Int) {
        dataStore.edit { it[GamePrefsKeys.ONBOARDING_DAY] = day.coerceAtLeast(FIRST_DAY) }
    }

    override suspend fun setTutorialDone(done: Boolean) {
        dataStore.edit { it[GamePrefsKeys.TUTORIAL_DONE] = done }
    }

    override suspend fun setPosterPath(path: String) {
        dataStore.edit { it[GamePrefsKeys.POSTER_PATH] = path }
    }

    override suspend fun setBennyHintsEnabled(enabled: Boolean) {
        dataStore.edit { it[GamePrefsKeys.BENNY_HINTS] = enabled }
    }

    companion object {
        private const val FIRST_DAY = 1
    }
}

internal class AudioPrefsStore(
    private val dataStore: DataStore<Preferences>,
) : GameAudioPrefs {
    private val prefs: Flow<Preferences> = dataStore.safePreferences()

    override val musicVolume: Flow<Int> = prefs.map { it[GamePrefsKeys.MUSIC_VOL] ?: FULL_VOLUME }
    override val sfxVolume: Flow<Int> = prefs.map { it[GamePrefsKeys.SFX_VOL] ?: FULL_VOLUME }
    override val voiceVolume: Flow<Int> = prefs.map { it[GamePrefsKeys.VOICE_VOL] ?: FULL_VOLUME }
    override val hapticsEnabled: Flow<Boolean> = prefs.map { it[GamePrefsKeys.HAPTICS] ?: true }

    override suspend fun setMusicVolume(volume: Int) {
        dataStore.edit { it[GamePrefsKeys.MUSIC_VOL] = volume.coerceIn(MIN_VOLUME, FULL_VOLUME) }
    }

    override suspend fun setSfxVolume(volume: Int) {
        dataStore.edit { it[GamePrefsKeys.SFX_VOL] = volume.coerceIn(MIN_VOLUME, FULL_VOLUME) }
    }

    override suspend fun setVoiceVolume(volume: Int) {
        dataStore.edit { it[GamePrefsKeys.VOICE_VOL] = volume.coerceIn(MIN_VOLUME, FULL_VOLUME) }
    }

    override suspend fun setHapticsEnabled(enabled: Boolean) {
        dataStore.edit { it[GamePrefsKeys.HAPTICS] = enabled }
    }

    companion object {
        private const val FULL_VOLUME = 100
        private const val MIN_VOLUME = 0
    }
}

internal class RewardPrefsStore(
    private val dataStore: DataStore<Preferences>,
) : GameRewardPrefs {
    private val prefs: Flow<Preferences> = dataStore.safePreferences()

    override val adsRemoved: Flow<Boolean> = prefs.map { it[GamePrefsKeys.ADS_REMOVED] ?: false }
    override val buddyBites: Flow<Int> = prefs.map { it[GamePrefsKeys.BUDDY_BITES] ?: 0 }
    override val usedCodes: Flow<Set<String>> = prefs.map { it[GamePrefsKeys.USED_CODES].orEmpty() }
    override val claimedAlbumBonuses: Flow<Set<String>> =
        prefs.map { it[GamePrefsKeys.CLAIMED_ALBUM_BONUSES].orEmpty() }
    override val lastWeeklyGiftDate: Flow<String> =
        prefs.map { it[GamePrefsKeys.LAST_WEEKLY_GIFT].orEmpty() }

    override suspend fun setAdsRemoved(removed: Boolean) {
        dataStore.edit { it[GamePrefsKeys.ADS_REMOVED] = removed }
    }

    override suspend fun addUsedCode(code: String) {
        val normalized = code.trim().uppercase()
        dataStore.edit { prefs ->
            prefs[GamePrefsKeys.USED_CODES] = prefs[GamePrefsKeys.USED_CODES].orEmpty() + normalized
        }
    }

    override suspend fun setBuddyBites(count: Int) {
        dataStore.edit { it[GamePrefsKeys.BUDDY_BITES] = count.coerceAtLeast(0) }
    }

    override suspend fun addClaimedAlbumBonus(tier: String) {
        dataStore.edit { prefs ->
            prefs[GamePrefsKeys.CLAIMED_ALBUM_BONUSES] =
                prefs[GamePrefsKeys.CLAIMED_ALBUM_BONUSES].orEmpty() + tier
        }
    }

    override suspend fun setLastWeeklyGiftDate(date: String) {
        dataStore.edit { it[GamePrefsKeys.LAST_WEEKLY_GIFT] = date }
    }
}

internal class RaidPrefsStore(
    private val dataStore: DataStore<Preferences>,
) : GameRaidPrefs {
    private val prefs: Flow<Preferences> = dataStore.safePreferences()

    override val lastRaidWindow: Flow<String> =
        prefs.map { it[GamePrefsKeys.LAST_RAID_WINDOW].orEmpty() }
    override val raidsUsed: Flow<Int> = prefs.map { it[GamePrefsKeys.RAIDS_USED] ?: 0 }

    override suspend fun setLastRaidWindow(bucket: String) {
        dataStore.edit { it[GamePrefsKeys.LAST_RAID_WINDOW] = bucket }
    }

    override suspend fun setRaidsUsed(count: Int) {
        dataStore.edit { it[GamePrefsKeys.RAIDS_USED] = count.coerceAtLeast(0) }
    }
}

@Singleton
class GamePrefsImpl
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : GamePrefs,
        GameIdentityPrefs by IdentityPrefsStore(dataStore),
        GameAudioPrefs by AudioPrefsStore(dataStore),
        GameRewardPrefs by RewardPrefsStore(dataStore),
        GameRaidPrefs by RaidPrefsStore(dataStore) {
        override suspend fun clearAll() {
            dataStore.edit { it.clear() }
        }
    }
