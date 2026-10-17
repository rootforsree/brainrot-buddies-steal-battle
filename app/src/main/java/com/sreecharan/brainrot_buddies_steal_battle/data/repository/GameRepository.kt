package com.sreecharan.brainrot_buddies_steal_battle.data.repository

import com.sreecharan.brainrot_buddies_steal_battle.data.local.BaseSlotEntity
import com.sreecharan.brainrot_buddies_steal_battle.data.local.CalendarEntity
import com.sreecharan.brainrot_buddies_steal_battle.data.local.FriendEntity
import com.sreecharan.brainrot_buddies_steal_battle.data.local.OwnedBuddyEntity
import com.sreecharan.brainrot_buddies_steal_battle.data.local.RaidRecordEntity
import com.sreecharan.brainrot_buddies_steal_battle.data.local.ShieldEntity
import com.sreecharan.brainrot_buddies_steal_battle.data.local.TicketEntity
import com.sreecharan.brainrot_buddies_steal_battle.data.local.VaultEntity
import com.sreecharan.brainrot_buddies_steal_battle.data.model.Result
import com.sreecharan.brainrot_buddies_steal_battle.data.model.TicketType
import kotlinx.coroutines.flow.Flow

/**
 * The single entry into the game data layer. Cold [Flow]s of [Result] for
 * observed data, suspend one-shots returning [Result] for writes. The UI
 * layer never touches DAOs or DataStore directly.
 *
 * The surface is split into focused seams (reads, writes, lifecycle) so no
 * single interface exceeds the complexity budget; [GameRepository] composes
 * them all and stays the type the UI injects.
 */
interface GameRepository :
    BuddyReads,
    IdentityPrefsReads,
    SettingsPrefsReads,
    EntityWrites,
    EconomyWrites,
    RecordWrites,
    IdentityPrefsWrites,
    SettingsPrefsWrites,
    GameLifecycle

/** Observed Room game state. */
interface BuddyReads {
    fun observeOwnedBuddies(): Flow<Result<List<OwnedBuddyEntity>>>

    fun observeSlots(): Flow<Result<List<BaseSlotEntity>>>

    fun observeVault(): Flow<Result<VaultEntity>>

    fun observeTickets(): Flow<Result<List<TicketEntity>>>

    fun observeShield(): Flow<Result<ShieldEntity>>

    fun observeRaidRecords(): Flow<Result<List<RaidRecordEntity>>>

    fun observeFriends(): Flow<Result<List<FriendEntity>>>

    fun observeCalendar(): Flow<Result<CalendarEntity>>
}

/** Observed identity/onboarding preferences. */
interface IdentityPrefsReads {
    fun observeGuestId(): Flow<Result<String?>>

    fun observeBaseName(): Flow<Result<String>>

    fun observeOnboardingDay(): Flow<Result<Int>>

    fun observeTutorialDone(): Flow<Result<Boolean>>

    fun observePosterPath(): Flow<Result<String>>

    fun observeLastRaidWindow(): Flow<Result<String>>

    fun observeRaidsUsed(): Flow<Result<Int>>

    fun observeBennyHintsEnabled(): Flow<Result<Boolean>>
}

/** Observed settings preferences. */
interface SettingsPrefsReads {
    fun observeMusicVolume(): Flow<Result<Int>>

    fun observeSfxVolume(): Flow<Result<Int>>

    fun observeVoiceVolume(): Flow<Result<Int>>

    fun observeHapticsEnabled(): Flow<Result<Boolean>>

    fun observeAdsRemoved(): Flow<Result<Boolean>>

    fun observeUsedCodes(): Flow<Result<Set<String>>>
}

/** Buddy and slot writes. */
interface EntityWrites {
    suspend fun upsertOwnedBuddy(buddy: OwnedBuddyEntity): Result<Unit>

    suspend fun removeOwnedBuddy(uid: String): Result<Unit>

    suspend fun upsertSlot(slot: BaseSlotEntity): Result<Unit>

    suspend fun unlockSlot(slotIndex: Int): Result<Unit>

    suspend fun setSlotBuddy(
        slotIndex: Int,
        buddyUid: String?,
    ): Result<Unit>

    suspend fun setSlotShieldUntil(
        slotIndex: Int,
        shieldUntilMs: Long,
    ): Result<Unit>
}

/** Vault, ticket and shield writes. */
interface EconomyWrites {
    suspend fun upsertVault(vault: VaultEntity): Result<Unit>

    suspend fun setTicketCount(
        type: TicketType,
        count: Int,
    ): Result<Unit>

    suspend fun addTickets(
        type: TicketType,
        delta: Int,
    ): Result<Unit>

    suspend fun setBaseShieldUntil(untilMs: Long): Result<Unit>
}

/** Raid-record, friend and calendar writes. */
interface RecordWrites {
    suspend fun upsertRaidRecord(record: RaidRecordEntity): Result<Unit>

    suspend fun upsertFriend(friend: FriendEntity): Result<Unit>

    suspend fun removeFriend(guestId: String): Result<Unit>

    suspend fun upsertCalendar(calendar: CalendarEntity): Result<Unit>
}

/** Identity/onboarding preference writes. */
interface IdentityPrefsWrites {
    /** Returns the guest id, creating it on first call. */
    suspend fun ensureGuestId(): Result<String>

    suspend fun setBaseName(name: String): Result<Unit>

    suspend fun setOnboardingDay(day: Int): Result<Unit>

    suspend fun setTutorialDone(done: Boolean): Result<Unit>

    suspend fun setPosterPath(path: String): Result<Unit>

    suspend fun setLastRaidWindow(bucket: String): Result<Unit>

    suspend fun setRaidsUsed(count: Int): Result<Unit>

    suspend fun setBennyHintsEnabled(enabled: Boolean): Result<Unit>
}

/** Settings preference writes. */
interface SettingsPrefsWrites {
    suspend fun setMusicVolume(volume: Int): Result<Unit>

    suspend fun setSfxVolume(volume: Int): Result<Unit>

    suspend fun setVoiceVolume(volume: Int): Result<Unit>

    suspend fun setHapticsEnabled(enabled: Boolean): Result<Unit>

    suspend fun setAdsRemoved(removed: Boolean): Result<Unit>

    suspend fun addUsedCode(code: String): Result<Unit>
}

/** Game lifecycle: seeding and full wipe. */
interface GameLifecycle {
    /**
     * Seeds a fresh game: 24 slots (first 4 unlocked), empty vault, zeroed
     * tickets, shield row, calendar row, guest id and base name. Idempotent.
     */
    suspend fun initializeNewGame(baseName: String): Result<Unit>

    /** Deletes every game table and clears game prefs (data-deletion button). */
    suspend fun clearAllData(): Result<Unit>
}
