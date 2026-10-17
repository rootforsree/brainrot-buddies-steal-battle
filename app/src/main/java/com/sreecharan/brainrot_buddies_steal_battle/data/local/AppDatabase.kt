package com.sreecharan.brainrot_buddies_steal_battle.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        OwnedBuddyEntity::class,
        BaseSlotEntity::class,
        VaultEntity::class,
        TicketEntity::class,
        ShieldEntity::class,
        RaidRecordEntity::class,
        FriendEntity::class,
        CalendarEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ownedBuddyDao(): OwnedBuddyDao

    abstract fun baseSlotDao(): BaseSlotDao

    abstract fun vaultDao(): VaultDao

    abstract fun ticketDao(): TicketDao

    abstract fun shieldDao(): ShieldDao

    abstract fun raidRecordDao(): RaidRecordDao

    abstract fun friendDao(): FriendDao

    abstract fun calendarDao(): CalendarDao
}
