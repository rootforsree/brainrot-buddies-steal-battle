package com.sreecharan.brainrot_buddies_steal_battle.data.repository

import com.sreecharan.brainrot_buddies_steal_battle.data.datastore.GamePrefs
import com.sreecharan.brainrot_buddies_steal_battle.data.local.BaseSlotDao
import com.sreecharan.brainrot_buddies_steal_battle.data.local.CalendarDao
import com.sreecharan.brainrot_buddies_steal_battle.data.local.FriendDao
import com.sreecharan.brainrot_buddies_steal_battle.data.local.OwnedBuddyDao
import com.sreecharan.brainrot_buddies_steal_battle.data.local.RaidRecordDao
import com.sreecharan.brainrot_buddies_steal_battle.data.local.ShieldDao
import com.sreecharan.brainrot_buddies_steal_battle.data.local.TicketDao
import com.sreecharan.brainrot_buddies_steal_battle.data.local.VaultDao
import com.sreecharan.brainrot_buddies_steal_battle.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single entry into the game data layer. Room is the source of truth for
 * game state; GamePrefs (Preferences DataStore) holds flat flags. Every
 * failure surfaces as [Result.Error]; nothing raw crosses the boundary.
 *
 * The implementation is split into focused operation groups (see
 * [RepositoryOps]); this class only wires them together with interface
 * delegation, so the UI keeps injecting the single [GameRepository].
 */
@Singleton
@Suppress("LongParameterList")
class GameRepositoryImpl @Inject constructor(
    ownedBuddyDao: OwnedBuddyDao,
    slotDao: BaseSlotDao,
    vaultDao: VaultDao,
    ticketDao: TicketDao,
    shieldDao: ShieldDao,
    raidRecordDao: RaidRecordDao,
    friendDao: FriendDao,
    calendarDao: CalendarDao,
    gamePrefs: GamePrefs,
    @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ops: RepositoryOps = RepositoryOps(
        ownedBuddyDao = ownedBuddyDao,
        slotDao = slotDao,
        vaultDao = vaultDao,
        ticketDao = ticketDao,
        shieldDao = shieldDao,
        raidRecordDao = raidRecordDao,
        friendDao = friendDao,
        calendarDao = calendarDao,
        gamePrefs = gamePrefs,
        ioDispatcher = ioDispatcher,
    ),
) : GameRepository,
    BuddyReads by ops.buddyReads,
    IdentityPrefsReads by ops.identityReads,
    SettingsPrefsReads by ops.settingsReads,
    EntityWrites by ops.entityWrites,
    EconomyWrites by ops.economyWrites,
    RecordWrites by ops.recordWrites,
    IdentityPrefsWrites by ops.identityWrites,
    SettingsPrefsWrites by ops.settingsWrites,
    GameLifecycle by ops.lifecycle
