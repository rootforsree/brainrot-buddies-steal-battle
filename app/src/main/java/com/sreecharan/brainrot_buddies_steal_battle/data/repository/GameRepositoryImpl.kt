package com.sreecharan.brainrot_buddies_steal_battle.data.repository

import com.sreecharan.brainrot_buddies_steal_battle.data.datastore.GamePrefs
import com.sreecharan.brainrot_buddies_steal_battle.data.local.BaseSlotDao
import com.sreecharan.brainrot_buddies_steal_battle.data.local.BaseSlotEntity
import com.sreecharan.brainrot_buddies_steal_battle.data.local.CalendarDao
import com.sreecharan.brainrot_buddies_steal_battle.data.local.CalendarEntity
import com.sreecharan.brainrot_buddies_steal_battle.data.local.FriendDao
import com.sreecharan.brainrot_buddies_steal_battle.data.local.FriendEntity
import com.sreecharan.brainrot_buddies_steal_battle.data.local.OwnedBuddyDao
import com.sreecharan.brainrot_buddies_steal_battle.data.local.OwnedBuddyEntity
import com.sreecharan.brainrot_buddies_steal_battle.data.local.RaidRecordDao
import com.sreecharan.brainrot_buddies_steal_battle.data.local.RaidRecordEntity
import com.sreecharan.brainrot_buddies_steal_battle.data.local.ShieldDao
import com.sreecharan.brainrot_buddies_steal_battle.data.local.ShieldEntity
import com.sreecharan.brainrot_buddies_steal_battle.data.local.TicketDao
import com.sreecharan.brainrot_buddies_steal_battle.data.local.TicketEntity
import com.sreecharan.brainrot_buddies_steal_battle.data.local.VaultDao
import com.sreecharan.brainrot_buddies_steal_battle.data.local.VaultEntity
import com.sreecharan.brainrot_buddies_steal_battle.data.model.DataError
import com.sreecharan.brainrot_buddies_steal_battle.data.model.Result
import com.sreecharan.brainrot_buddies_steal_battle.data.model.TicketType
import com.sreecharan.brainrot_buddies_steal_battle.di.IoDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single entry into the game data layer. Room is the source of truth for
 * game state; GamePrefs (Preferences DataStore) holds flat flags. Every
 * failure surfaces as [Result.Error]; nothing raw crosses the boundary.
 */
@Singleton
@Suppress("LongParameterList")
class GameRepositoryImpl @Inject constructor(
    private val ownedBuddyDao: OwnedBuddyDao,
    private val slotDao: BaseSlotDao,
    private val vaultDao: VaultDao,
    private val ticketDao: TicketDao,
    private val shieldDao: ShieldDao,
    private val raidRecordDao: RaidRecordDao,
    private val friendDao: FriendDao,
    private val calendarDao: CalendarDao,
    private val gamePrefs: GamePrefs,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : GameRepository {

    // ---------- Observed game state ----------

    override fun observeOwnedBuddies(): Flow<Result<List<OwnedBuddyEntity>>> =
        observe(ownedBuddyDao.observeAll())

    override fun observeSlots(): Flow<Result<List<BaseSlotEntity>>> =
        observe(slotDao.observeAll())

    override fun observeVault(): Flow<Result<VaultEntity>> =
        observe(vaultDao.observe().map { it ?: VaultEntity() })

    override fun observeTickets(): Flow<Result<List<TicketEntity>>> =
        observe(ticketDao.observeAll())

    override fun observeShield(): Flow<Result<ShieldEntity>> =
        observe(shieldDao.observe().map { it ?: ShieldEntity() })

    override fun observeRaidRecords(): Flow<Result<List<RaidRecordEntity>>> =
        observe(raidRecordDao.observeAll())

    override fun observeFriends(): Flow<Result<List<FriendEntity>>> =
        observe(friendDao.observeAll())

    override fun observeCalendar(): Flow<Result<CalendarEntity>> =
        observe(calendarDao.observe().map { it ?: CalendarEntity() })

    // ---------- Observed game prefs ----------

    override fun observeGuestId(): Flow<Result<String?>> = observe(gamePrefs.guestId)
    override fun observeBaseName(): Flow<Result<String>> = observe(gamePrefs.baseName)
    override fun observeMusicVolume(): Flow<Result<Int>> = observe(gamePrefs.musicVolume)
    override fun observeSfxVolume(): Flow<Result<Int>> = observe(gamePrefs.sfxVolume)
    override fun observeVoiceVolume(): Flow<Result<Int>> = observe(gamePrefs.voiceVolume)
    override fun observeHapticsEnabled(): Flow<Result<Boolean>> = observe(gamePrefs.hapticsEnabled)
    override fun observeOnboardingDay(): Flow<Result<Int>> = observe(gamePrefs.onboardingDay)
    override fun observeTutorialDone(): Flow<Result<Boolean>> = observe(gamePrefs.tutorialDone)
    override fun observeAdsRemoved(): Flow<Result<Boolean>> = observe(gamePrefs.adsRemoved)
    override fun observePosterPath(): Flow<Result<String>> = observe(gamePrefs.posterPath)
    override fun observeLastRaidWindow(): Flow<Result<String>> = observe(gamePrefs.lastRaidWindow)
    override fun observeRaidsUsed(): Flow<Result<Int>> = observe(gamePrefs.raidsUsed)
    override fun observeBennyHintsEnabled(): Flow<Result<Boolean>> = observe(gamePrefs.bennyHintsEnabled)
    override fun observeUsedCodes(): Flow<Result<Set<String>>> = observe(gamePrefs.usedCodes)

    // ---------- Buddy writes ----------

    override suspend fun upsertOwnedBuddy(buddy: OwnedBuddyEntity): Result<Unit> =
        write { ownedBuddyDao.upsert(buddy) }

    override suspend fun removeOwnedBuddy(uid: String): Result<Unit> =
        write { ownedBuddyDao.deleteByUid(uid) }

    // ---------- Slot writes ----------

    override suspend fun upsertSlot(slot: BaseSlotEntity): Result<Unit> =
        write { slotDao.upsert(slot) }

    override suspend fun unlockSlot(slotIndex: Int): Result<Unit> = write {
        val current = slotDao.getByIndex(slotIndex)
        slotDao.upsert((current ?: BaseSlotEntity(slotIndex)).copy(unlocked = true))
    }

    override suspend fun setSlotBuddy(slotIndex: Int, buddyUid: String?): Result<Unit> = write {
        val current = slotDao.getByIndex(slotIndex)
        slotDao.upsert((current ?: BaseSlotEntity(slotIndex)).copy(ownedBuddyUid = buddyUid))
    }

    override suspend fun setSlotShieldUntil(slotIndex: Int, shieldUntilMs: Long): Result<Unit> = write {
        val current = slotDao.getByIndex(slotIndex)
        slotDao.upsert((current ?: BaseSlotEntity(slotIndex)).copy(shieldUntil = shieldUntilMs))
    }

    // ---------- Vault / ticket / shield writes ----------

    override suspend fun upsertVault(vault: VaultEntity): Result<Unit> =
        write { vaultDao.upsert(vault) }

    override suspend fun setTicketCount(type: TicketType, count: Int): Result<Unit> = write {
        ticketDao.upsert(TicketEntity(type = type.key, count = count.coerceAtLeast(0)))
    }

    override suspend fun addTickets(type: TicketType, delta: Int): Result<Unit> = write {
        val current = ticketDao.getByType(type.key)?.count ?: 0
        ticketDao.upsert(TicketEntity(type = type.key, count = (current + delta).coerceAtLeast(0)))
    }

    override suspend fun setBaseShieldUntil(untilMs: Long): Result<Unit> = write {
        val current = shieldDao.get()
        shieldDao.upsert((current ?: ShieldEntity()).copy(baseShieldUntil = untilMs))
    }

    // ---------- Raid / friend / calendar writes ----------

    override suspend fun upsertRaidRecord(record: RaidRecordEntity): Result<Unit> =
        write { raidRecordDao.upsert(record) }

    override suspend fun upsertFriend(friend: FriendEntity): Result<Unit> =
        write { friendDao.upsert(friend) }

    override suspend fun removeFriend(guestId: String): Result<Unit> =
        write { friendDao.deleteByGuestId(guestId) }

    override suspend fun upsertCalendar(calendar: CalendarEntity): Result<Unit> =
        write { calendarDao.upsert(calendar) }

    // ---------- Game prefs writes ----------

    override suspend fun ensureGuestId(): Result<String> = write { gamePrefs.ensureGuestId() }
    override suspend fun setBaseName(name: String): Result<Unit> = write { gamePrefs.setBaseName(name) }
    override suspend fun setMusicVolume(volume: Int): Result<Unit> = write { gamePrefs.setMusicVolume(volume) }
    override suspend fun setSfxVolume(volume: Int): Result<Unit> = write { gamePrefs.setSfxVolume(volume) }
    override suspend fun setVoiceVolume(volume: Int): Result<Unit> = write { gamePrefs.setVoiceVolume(volume) }
    override suspend fun setHapticsEnabled(enabled: Boolean): Result<Unit> =
        write { gamePrefs.setHapticsEnabled(enabled) }
    override suspend fun setOnboardingDay(day: Int): Result<Unit> = write { gamePrefs.setOnboardingDay(day) }
    override suspend fun setTutorialDone(done: Boolean): Result<Unit> = write { gamePrefs.setTutorialDone(done) }
    override suspend fun setAdsRemoved(removed: Boolean): Result<Unit> = write { gamePrefs.setAdsRemoved(removed) }
    override suspend fun setPosterPath(path: String): Result<Unit> = write { gamePrefs.setPosterPath(path) }
    override suspend fun setLastRaidWindow(bucket: String): Result<Unit> = write { gamePrefs.setLastRaidWindow(bucket) }
    override suspend fun setRaidsUsed(count: Int): Result<Unit> = write { gamePrefs.setRaidsUsed(count) }
    override suspend fun setBennyHintsEnabled(enabled: Boolean): Result<Unit> =
        write { gamePrefs.setBennyHintsEnabled(enabled) }
    override suspend fun addUsedCode(code: String): Result<Unit> = write { gamePrefs.addUsedCode(code) }

    // ---------- Lifecycle ----------

    override suspend fun initializeNewGame(baseName: String): Result<Unit> = write {
        val slots = (0 until MAX_BASE_SLOTS).map { index ->
            BaseSlotEntity(slotIndex = index, unlocked = index < STARTING_UNLOCKED_SLOTS)
        }
        slotDao.upsertAll(slots)
        vaultDao.upsert(VaultEntity())
        ticketDao.upsert(TicketEntity(type = TicketType.LUCKY.key))
        ticketDao.upsert(TicketEntity(type = TicketType.MEGA.key))
        shieldDao.upsert(ShieldEntity())
        calendarDao.upsert(CalendarEntity())
        gamePrefs.ensureGuestId()
        gamePrefs.setBaseName(baseName)
    }

    override suspend fun clearAllData(): Result<Unit> = write {
        ownedBuddyDao.deleteAll()
        slotDao.deleteAll()
        vaultDao.deleteAll()
        ticketDao.deleteAll()
        shieldDao.deleteAll()
        raidRecordDao.deleteAll()
        friendDao.deleteAll()
        calendarDao.deleteAll()
        gamePrefs.clearAll()
    }

    // ---------- Helpers ----------

    private fun <T> observe(source: Flow<T>): Flow<Result<T>> = source
        .map<T, Result<T>> { Result.Success(it) }
        .onStart { emit(Result.Loading) }
        .catch { e ->
            if (e is CancellationException) {
                throw e
            }
            emit(Result.Error(DataError.Storage(e.message ?: "Read failed")))
        }
        .flowOn(ioDispatcher)

    private suspend fun <T> write(block: suspend () -> T): Result<T> =
        withContext(ioDispatcher) {
            try {
                Result.Success(block())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.Error(DataError.Storage(e.message ?: "Write failed"))
            }
        }

    companion object {
        private const val MAX_BASE_SLOTS = 24
        private const val STARTING_UNLOCKED_SLOTS = 4
    }
}
