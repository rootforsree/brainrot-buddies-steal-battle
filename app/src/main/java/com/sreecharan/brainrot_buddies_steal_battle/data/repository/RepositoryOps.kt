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
import javax.inject.Inject

/**
 * The repository's operation groups, one cohesive seam each. [GameRepositoryImpl]
 * composes them with interface delegation; the UI keeps injecting the single
 * [GameRepository]. Wiring helper below.
 */
internal class BuddyReadsOps(
    private val core: CoreDaos,
    private val meta: MetaDaos,
    private val helpers: RepositoryHelpers,
) : BuddyReads {

    override fun observeOwnedBuddies(): Flow<Result<List<OwnedBuddyEntity>>> =
        helpers.observe(core.ownedBuddyDao.observeAll())

    override fun observeSlots(): Flow<Result<List<BaseSlotEntity>>> =
        helpers.observe(core.slotDao.observeAll())

    override fun observeVault(): Flow<Result<VaultEntity>> =
        helpers.observe(core.vaultDao.observe().map { it ?: VaultEntity() })

    override fun observeTickets(): Flow<Result<List<TicketEntity>>> =
        helpers.observe(core.ticketDao.observeAll())

    override fun observeShield(): Flow<Result<ShieldEntity>> =
        helpers.observe(meta.shieldDao.observe().map { it ?: ShieldEntity() })

    override fun observeRaidRecords(): Flow<Result<List<RaidRecordEntity>>> =
        helpers.observe(meta.raidRecordDao.observeAll())

    override fun observeFriends(): Flow<Result<List<FriendEntity>>> =
        helpers.observe(meta.friendDao.observeAll())

    override fun observeCalendar(): Flow<Result<CalendarEntity>> =
        helpers.observe(meta.calendarDao.observe().map { it ?: CalendarEntity() })
}

internal class IdentityPrefsReadsOps(
    private val helpers: RepositoryHelpers,
) : IdentityPrefsReads {

    override fun observeGuestId(): Flow<Result<String?>> = helpers.observe(helpers.gamePrefs.guestId)
    override fun observeBaseName(): Flow<Result<String>> = helpers.observe(helpers.gamePrefs.baseName)
    override fun observeOnboardingDay(): Flow<Result<Int>> =
        helpers.observe(helpers.gamePrefs.onboardingDay)
    override fun observeTutorialDone(): Flow<Result<Boolean>> =
        helpers.observe(helpers.gamePrefs.tutorialDone)
    override fun observePosterPath(): Flow<Result<String>> =
        helpers.observe(helpers.gamePrefs.posterPath)
    override fun observeLastRaidWindow(): Flow<Result<String>> =
        helpers.observe(helpers.gamePrefs.lastRaidWindow)
    override fun observeRaidsUsed(): Flow<Result<Int>> = helpers.observe(helpers.gamePrefs.raidsUsed)
    override fun observeBennyHintsEnabled(): Flow<Result<Boolean>> =
        helpers.observe(helpers.gamePrefs.bennyHintsEnabled)
}

internal class SettingsPrefsReadsOps(
    private val helpers: RepositoryHelpers,
) : SettingsPrefsReads {

    override fun observeMusicVolume(): Flow<Result<Int>> =
        helpers.observe(helpers.gamePrefs.musicVolume)
    override fun observeSfxVolume(): Flow<Result<Int>> = helpers.observe(helpers.gamePrefs.sfxVolume)
    override fun observeVoiceVolume(): Flow<Result<Int>> =
        helpers.observe(helpers.gamePrefs.voiceVolume)
    override fun observeHapticsEnabled(): Flow<Result<Boolean>> =
        helpers.observe(helpers.gamePrefs.hapticsEnabled)
    override fun observeAdsRemoved(): Flow<Result<Boolean>> =
        helpers.observe(helpers.gamePrefs.adsRemoved)
    override fun observeUsedCodes(): Flow<Result<Set<String>>> =
        helpers.observe(helpers.gamePrefs.usedCodes)
}

internal class EntityWritesOps(
    private val core: CoreDaos,
    private val helpers: RepositoryHelpers,
) : EntityWrites {

    override suspend fun upsertOwnedBuddy(buddy: OwnedBuddyEntity): Result<Unit> =
        helpers.write { core.ownedBuddyDao.upsert(buddy) }

    override suspend fun removeOwnedBuddy(uid: String): Result<Unit> =
        helpers.write { core.ownedBuddyDao.deleteByUid(uid) }

    override suspend fun upsertSlot(slot: BaseSlotEntity): Result<Unit> =
        helpers.write { core.slotDao.upsert(slot) }

    override suspend fun unlockSlot(slotIndex: Int): Result<Unit> = helpers.write {
        val current = core.slotDao.getByIndex(slotIndex)
        core.slotDao.upsert((current ?: BaseSlotEntity(slotIndex)).copy(unlocked = true))
    }

    override suspend fun setSlotBuddy(slotIndex: Int, buddyUid: String?): Result<Unit> = helpers.write {
        val current = core.slotDao.getByIndex(slotIndex)
        core.slotDao.upsert((current ?: BaseSlotEntity(slotIndex)).copy(ownedBuddyUid = buddyUid))
    }

    override suspend fun setSlotShieldUntil(slotIndex: Int, shieldUntilMs: Long): Result<Unit> =
        helpers.write {
            val current = core.slotDao.getByIndex(slotIndex)
            core.slotDao.upsert((current ?: BaseSlotEntity(slotIndex)).copy(shieldUntil = shieldUntilMs))
        }
}

internal class EconomyWritesOps(
    private val core: CoreDaos,
    private val meta: MetaDaos,
    private val helpers: RepositoryHelpers,
) : EconomyWrites {

    override suspend fun upsertVault(vault: VaultEntity): Result<Unit> =
        helpers.write { core.vaultDao.upsert(vault) }

    override suspend fun setTicketCount(type: TicketType, count: Int): Result<Unit> = helpers.write {
        core.ticketDao.upsert(TicketEntity(type = type.key, count = count.coerceAtLeast(0)))
    }

    override suspend fun addTickets(type: TicketType, delta: Int): Result<Unit> = helpers.write {
        val current = core.ticketDao.getByType(type.key)?.count ?: 0
        core.ticketDao.upsert(TicketEntity(type = type.key, count = (current + delta).coerceAtLeast(0)))
    }

    override suspend fun setBaseShieldUntil(untilMs: Long): Result<Unit> = helpers.write {
        val current = meta.shieldDao.get()
        meta.shieldDao.upsert((current ?: ShieldEntity()).copy(baseShieldUntil = untilMs))
    }
}

internal class RecordWritesOps(
    private val meta: MetaDaos,
    private val helpers: RepositoryHelpers,
) : RecordWrites {

    override suspend fun upsertRaidRecord(record: RaidRecordEntity): Result<Unit> =
        helpers.write { meta.raidRecordDao.upsert(record) }

    override suspend fun upsertFriend(friend: FriendEntity): Result<Unit> =
        helpers.write { meta.friendDao.upsert(friend) }

    override suspend fun removeFriend(guestId: String): Result<Unit> =
        helpers.write { meta.friendDao.deleteByGuestId(guestId) }

    override suspend fun upsertCalendar(calendar: CalendarEntity): Result<Unit> =
        helpers.write { meta.calendarDao.upsert(calendar) }
}

internal class IdentityPrefsWritesOps(
    private val helpers: RepositoryHelpers,
) : IdentityPrefsWrites {

    override suspend fun ensureGuestId(): Result<String> =
        helpers.write { helpers.gamePrefs.ensureGuestId() }
    override suspend fun setBaseName(name: String): Result<Unit> =
        helpers.write { helpers.gamePrefs.setBaseName(name) }
    override suspend fun setOnboardingDay(day: Int): Result<Unit> =
        helpers.write { helpers.gamePrefs.setOnboardingDay(day) }
    override suspend fun setTutorialDone(done: Boolean): Result<Unit> =
        helpers.write { helpers.gamePrefs.setTutorialDone(done) }
    override suspend fun setPosterPath(path: String): Result<Unit> =
        helpers.write { helpers.gamePrefs.setPosterPath(path) }
    override suspend fun setLastRaidWindow(bucket: String): Result<Unit> =
        helpers.write { helpers.gamePrefs.setLastRaidWindow(bucket) }
    override suspend fun setRaidsUsed(count: Int): Result<Unit> =
        helpers.write { helpers.gamePrefs.setRaidsUsed(count) }
    override suspend fun setBennyHintsEnabled(enabled: Boolean): Result<Unit> =
        helpers.write { helpers.gamePrefs.setBennyHintsEnabled(enabled) }
}

internal class SettingsPrefsWritesOps(
    private val helpers: RepositoryHelpers,
) : SettingsPrefsWrites {

    override suspend fun setMusicVolume(volume: Int): Result<Unit> =
        helpers.write { helpers.gamePrefs.setMusicVolume(volume) }
    override suspend fun setSfxVolume(volume: Int): Result<Unit> =
        helpers.write { helpers.gamePrefs.setSfxVolume(volume) }
    override suspend fun setVoiceVolume(volume: Int): Result<Unit> =
        helpers.write { helpers.gamePrefs.setVoiceVolume(volume) }
    override suspend fun setHapticsEnabled(enabled: Boolean): Result<Unit> =
        helpers.write { helpers.gamePrefs.setHapticsEnabled(enabled) }
    override suspend fun setAdsRemoved(removed: Boolean): Result<Unit> =
        helpers.write { helpers.gamePrefs.setAdsRemoved(removed) }
    override suspend fun addUsedCode(code: String): Result<Unit> =
        helpers.write { helpers.gamePrefs.addUsedCode(code) }
}

internal class GameLifecycleOps(
    private val core: CoreDaos,
    private val meta: MetaDaos,
    private val helpers: RepositoryHelpers,
) : GameLifecycle {

    override suspend fun initializeNewGame(baseName: String): Result<Unit> = helpers.write {
        val slots = (0 until MAX_BASE_SLOTS).map { index ->
            BaseSlotEntity(slotIndex = index, unlocked = index < STARTING_UNLOCKED_SLOTS)
        }
        core.slotDao.upsertAll(slots)
        core.vaultDao.upsert(VaultEntity())
        core.ticketDao.upsert(TicketEntity(type = TicketType.LUCKY.key))
        core.ticketDao.upsert(TicketEntity(type = TicketType.MEGA.key))
        meta.shieldDao.upsert(ShieldEntity())
        meta.calendarDao.upsert(CalendarEntity())
        helpers.gamePrefs.ensureGuestId()
        helpers.gamePrefs.setBaseName(baseName)
    }

    override suspend fun clearAllData(): Result<Unit> = helpers.write {
        core.ownedBuddyDao.deleteAll()
        core.slotDao.deleteAll()
        core.vaultDao.deleteAll()
        core.ticketDao.deleteAll()
        meta.shieldDao.deleteAll()
        meta.raidRecordDao.deleteAll()
        meta.friendDao.deleteAll()
        meta.calendarDao.deleteAll()
        helpers.gamePrefs.clearAll()
    }

    companion object {
        private const val MAX_BASE_SLOTS = 24
        private const val STARTING_UNLOCKED_SLOTS = 4
    }
}

/**
 * Builds the nine operation groups from the injected DAO holders. Kept as
 * one holder so [GameRepositoryImpl]'s delegating constructor stays readable.
 */
internal class RepositoryOps @Inject constructor(
    private val core: CoreDaos,
    private val meta: MetaDaos,
    private val helpers: RepositoryHelpers,
) {
    val buddyReads: BuddyReads = BuddyReadsOps(core, meta, helpers)
    val identityReads: IdentityPrefsReads = IdentityPrefsReadsOps(helpers)
    val settingsReads: SettingsPrefsReads = SettingsPrefsReadsOps(helpers)
    val entityWrites: EntityWrites = EntityWritesOps(core, helpers)
    val economyWrites: EconomyWrites = EconomyWritesOps(core, meta, helpers)
    val recordWrites: RecordWrites = RecordWritesOps(meta, helpers)
    val identityWrites: IdentityPrefsWrites = IdentityPrefsWritesOps(helpers)
    val settingsWrites: SettingsPrefsWrites = SettingsPrefsWritesOps(helpers)
    val lifecycle: GameLifecycle = GameLifecycleOps(core, meta, helpers)
}
