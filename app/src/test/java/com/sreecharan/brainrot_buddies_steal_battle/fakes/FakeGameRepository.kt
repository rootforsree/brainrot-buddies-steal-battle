package com.sreecharan.brainrot_buddies_steal_battle.fakes

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
import com.sreecharan.brainrot_buddies_steal_battle.data.repository.GameRepository
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Functional fake of [GameRepository]. Every observation is a StateFlow of
 * [Result.Success]; every write applies to the backing state, so ViewModel
 * transactions (read -> check -> debit -> apply) can be tested end to end.
 * Call names are recorded in [calls] for interaction checks. GamePrefs
 * state (bites, album bonuses, weekly date) lives in [FakeGamePrefs].
 */
class FakeGameRepository : GameRepository {

    val vaultState = MutableStateFlow(VaultEntity())
    val ticketsState = MutableStateFlow<List<TicketEntity>>(emptyList())
    val slotsState = MutableStateFlow<List<BaseSlotEntity>>(emptyList())
    val shieldState = MutableStateFlow(ShieldEntity())
    val buddiesState = MutableStateFlow<List<OwnedBuddyEntity>>(emptyList())
    val raidRecordsState = MutableStateFlow<List<RaidRecordEntity>>(emptyList())
    val friendsState = MutableStateFlow<List<FriendEntity>>(emptyList())
    val calendarState = MutableStateFlow(CalendarEntity())

    val guestIdState = MutableStateFlow<String?>(null)
    val baseNameState = MutableStateFlow("")
    val musicVolumeState = MutableStateFlow(80)
    val sfxVolumeState = MutableStateFlow(80)
    val voiceVolumeState = MutableStateFlow(80)
    val hapticsState = MutableStateFlow(true)
    val onboardingDayState = MutableStateFlow(1)
    val tutorialDoneState = MutableStateFlow(false)
    val adsRemovedState = MutableStateFlow(false)
    val posterPathState = MutableStateFlow("")
    val lastRaidWindowState = MutableStateFlow("")
    val raidsUsedState = MutableStateFlow(0)
    val bennyHintsState = MutableStateFlow(true)
    val usedCodesState = MutableStateFlow<Set<String>>(emptySet())

    val calls = mutableListOf<String>()

    private fun <T> MutableStateFlow<T>.success(): Flow<Result<T>> = map { Result.Success(it) }

    override fun observeOwnedBuddies(): Flow<Result<List<OwnedBuddyEntity>>> = buddiesState.success()
    override fun observeSlots(): Flow<Result<List<BaseSlotEntity>>> = slotsState.success()
    override fun observeVault(): Flow<Result<VaultEntity>> = vaultState.success()
    override fun observeTickets(): Flow<Result<List<TicketEntity>>> = ticketsState.success()
    override fun observeShield(): Flow<Result<ShieldEntity>> = shieldState.success()
    override fun observeRaidRecords(): Flow<Result<List<RaidRecordEntity>>> =
        raidRecordsState.success()
    override fun observeFriends(): Flow<Result<List<FriendEntity>>> = friendsState.success()
    override fun observeCalendar(): Flow<Result<CalendarEntity>> = calendarState.success()

    override fun observeGuestId(): Flow<Result<String?>> = guestIdState.success()
    override fun observeBaseName(): Flow<Result<String>> = baseNameState.success()
    override fun observeMusicVolume(): Flow<Result<Int>> = musicVolumeState.success()
    override fun observeSfxVolume(): Flow<Result<Int>> = sfxVolumeState.success()
    override fun observeVoiceVolume(): Flow<Result<Int>> = voiceVolumeState.success()
    override fun observeHapticsEnabled(): Flow<Result<Boolean>> = hapticsState.success()
    override fun observeOnboardingDay(): Flow<Result<Int>> = onboardingDayState.success()
    override fun observeTutorialDone(): Flow<Result<Boolean>> = tutorialDoneState.success()
    override fun observeAdsRemoved(): Flow<Result<Boolean>> = adsRemovedState.success()
    override fun observePosterPath(): Flow<Result<String>> = posterPathState.success()
    override fun observeLastRaidWindow(): Flow<Result<String>> = lastRaidWindowState.success()
    override fun observeRaidsUsed(): Flow<Result<Int>> = raidsUsedState.success()
    override fun observeBennyHintsEnabled(): Flow<Result<Boolean>> = bennyHintsState.success()
    override fun observeUsedCodes(): Flow<Result<Set<String>>> = usedCodesState.success()

    override suspend fun upsertOwnedBuddy(buddy: OwnedBuddyEntity): Result<Unit> {
        calls += "upsertOwnedBuddy"
        buddiesState.value = buddiesState.value.filterNot { it.uid == buddy.uid } + buddy
        return Result.Success(Unit)
    }

    override suspend fun removeOwnedBuddy(uid: String): Result<Unit> {
        calls += "removeOwnedBuddy"
        buddiesState.value = buddiesState.value.filterNot { it.uid == uid }
        return Result.Success(Unit)
    }

    override suspend fun upsertSlot(slot: BaseSlotEntity): Result<Unit> {
        calls += "upsertSlot"
        slotsState.value = slotsState.value.filterNot { it.slotIndex == slot.slotIndex } + slot
        return Result.Success(Unit)
    }

    override suspend fun unlockSlot(slotIndex: Int): Result<Unit> {
        calls += "unlockSlot"
        slotsState.value = slotsState.value.map {
            if (it.slotIndex == slotIndex) it.copy(unlocked = true) else it
        }
        return Result.Success(Unit)
    }

    override suspend fun setSlotBuddy(slotIndex: Int, buddyUid: String?): Result<Unit> {
        calls += "setSlotBuddy"
        slotsState.value = slotsState.value.map {
            if (it.slotIndex == slotIndex) it.copy(ownedBuddyUid = buddyUid) else it
        }
        return Result.Success(Unit)
    }

    override suspend fun setSlotShieldUntil(slotIndex: Int, shieldUntilMs: Long): Result<Unit> {
        calls += "setSlotShieldUntil"
        slotsState.value = slotsState.value.map {
            if (it.slotIndex == slotIndex) it.copy(shieldUntil = shieldUntilMs) else it
        }
        return Result.Success(Unit)
    }

    override suspend fun upsertVault(vault: VaultEntity): Result<Unit> {
        calls += "upsertVault"
        vaultState.value = vault
        return Result.Success(Unit)
    }

    override suspend fun setTicketCount(type: TicketType, count: Int): Result<Unit> {
        calls += "setTicketCount"
        ticketsState.value = ticketsState.value.filterNot { it.type == type.key } +
            TicketEntity(type = type.key, count = count)
        return Result.Success(Unit)
    }

    override suspend fun addTickets(type: TicketType, delta: Int): Result<Unit> {
        calls += "addTickets"
        val current = ticketsState.value.firstOrNull { it.type == type.key }?.count ?: 0
        return setTicketCount(type, current + delta)
    }

    override suspend fun setBaseShieldUntil(untilMs: Long): Result<Unit> {
        calls += "setBaseShieldUntil"
        shieldState.value = shieldState.value.copy(baseShieldUntil = untilMs)
        return Result.Success(Unit)
    }

    override suspend fun upsertRaidRecord(record: RaidRecordEntity): Result<Unit> {
        calls += "upsertRaidRecord"
        return Result.Success(Unit)
    }

    override suspend fun upsertFriend(friend: FriendEntity): Result<Unit> {
        calls += "upsertFriend"
        return Result.Success(Unit)
    }

    override suspend fun removeFriend(guestId: String): Result<Unit> {
        calls += "removeFriend"
        return Result.Success(Unit)
    }

    override suspend fun upsertCalendar(calendar: CalendarEntity): Result<Unit> {
        calls += "upsertCalendar"
        calendarState.value = calendar
        return Result.Success(Unit)
    }

    override suspend fun ensureGuestId(): Result<String> {
        calls += "ensureGuestId"
        val current = guestIdState.value
        if (!current.isNullOrBlank()) return Result.Success(current)
        val fresh = "guest-" + UUID.randomUUID().toString().take(8)
        guestIdState.value = fresh
        return Result.Success(fresh)
    }

    override suspend fun setBaseName(name: String): Result<Unit> {
        calls += "setBaseName"
        baseNameState.value = name
        return Result.Success(Unit)
    }

    override suspend fun setMusicVolume(volume: Int): Result<Unit> {
        calls += "setMusicVolume"
        musicVolumeState.value = volume
        return Result.Success(Unit)
    }

    override suspend fun setSfxVolume(volume: Int): Result<Unit> {
        calls += "setSfxVolume"
        sfxVolumeState.value = volume
        return Result.Success(Unit)
    }

    override suspend fun setVoiceVolume(volume: Int): Result<Unit> {
        calls += "setVoiceVolume"
        voiceVolumeState.value = volume
        return Result.Success(Unit)
    }

    override suspend fun setHapticsEnabled(enabled: Boolean): Result<Unit> {
        calls += "setHapticsEnabled"
        hapticsState.value = enabled
        return Result.Success(Unit)
    }

    override suspend fun setOnboardingDay(day: Int): Result<Unit> {
        calls += "setOnboardingDay"
        onboardingDayState.value = day
        return Result.Success(Unit)
    }

    override suspend fun setTutorialDone(done: Boolean): Result<Unit> {
        calls += "setTutorialDone"
        tutorialDoneState.value = done
        return Result.Success(Unit)
    }

    override suspend fun setAdsRemoved(removed: Boolean): Result<Unit> {
        calls += "setAdsRemoved"
        adsRemovedState.value = removed
        return Result.Success(Unit)
    }

    override suspend fun setPosterPath(path: String): Result<Unit> {
        calls += "setPosterPath"
        posterPathState.value = path
        return Result.Success(Unit)
    }

    override suspend fun setLastRaidWindow(bucket: String): Result<Unit> {
        calls += "setLastRaidWindow"
        lastRaidWindowState.value = bucket
        return Result.Success(Unit)
    }

    override suspend fun setRaidsUsed(count: Int): Result<Unit> {
        calls += "setRaidsUsed"
        raidsUsedState.value = count
        return Result.Success(Unit)
    }

    override suspend fun setBennyHintsEnabled(enabled: Boolean): Result<Unit> {
        calls += "setBennyHintsEnabled"
        bennyHintsState.value = enabled
        return Result.Success(Unit)
    }

    override suspend fun addUsedCode(code: String): Result<Unit> {
        calls += "addUsedCode"
        usedCodesState.value = usedCodesState.value + code.trim().uppercase()
        return Result.Success(Unit)
    }

    override suspend fun initializeNewGame(baseName: String): Result<Unit> {
        calls += "initializeNewGame"
        slotsState.value = freshSlots()
        vaultState.value = VaultEntity()
        ticketsState.value = listOf(
            TicketEntity(type = TicketType.LUCKY.key, count = 0),
            TicketEntity(type = TicketType.MEGA.key, count = 0),
        )
        shieldState.value = ShieldEntity()
        calendarState.value = CalendarEntity()
        baseNameState.value = baseName
        ensureGuestId()
        return Result.Success(Unit)
    }

    override suspend fun clearAllData(): Result<Unit> {
        calls += "clearAllData"
        vaultState.value = VaultEntity()
        ticketsState.value = emptyList()
        slotsState.value = emptyList()
        shieldState.value = ShieldEntity()
        buddiesState.value = emptyList()
        calendarState.value = CalendarEntity()
        guestIdState.value = null
        baseNameState.value = ""
        posterPathState.value = ""
        usedCodesState.value = emptySet()
        tutorialDoneState.value = false
        onboardingDayState.value = 1
        return Result.Success(Unit)
    }

    /** 4 unlocked slots + 20 locked, mirroring a fresh player. */
    fun freshSlots(): List<BaseSlotEntity> =
        (0 until 24).map { index ->
            BaseSlotEntity(slotIndex = index, unlocked = index < 4)
        }
}
