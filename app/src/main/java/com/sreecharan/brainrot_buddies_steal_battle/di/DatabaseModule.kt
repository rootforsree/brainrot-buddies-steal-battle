package com.sreecharan.brainrot_buddies_steal_battle.di

import android.content.Context
import androidx.room.Room
import com.sreecharan.brainrot_buddies_steal_battle.data.local.AppDatabase
import com.sreecharan.brainrot_buddies_steal_battle.data.local.BaseSlotDao
import com.sreecharan.brainrot_buddies_steal_battle.data.local.CalendarDao
import com.sreecharan.brainrot_buddies_steal_battle.data.local.FriendDao
import com.sreecharan.brainrot_buddies_steal_battle.data.local.OwnedBuddyDao
import com.sreecharan.brainrot_buddies_steal_battle.data.local.RaidRecordDao
import com.sreecharan.brainrot_buddies_steal_battle.data.local.ShieldDao
import com.sreecharan.brainrot_buddies_steal_battle.data.local.TicketDao
import com.sreecharan.brainrot_buddies_steal_battle.data.local.VaultDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "stealbattle.db")
            .build()

    @Provides
    fun provideOwnedBuddyDao(db: AppDatabase): OwnedBuddyDao = db.ownedBuddyDao()

    @Provides
    fun provideBaseSlotDao(db: AppDatabase): BaseSlotDao = db.baseSlotDao()

    @Provides
    fun provideVaultDao(db: AppDatabase): VaultDao = db.vaultDao()

    @Provides
    fun provideTicketDao(db: AppDatabase): TicketDao = db.ticketDao()

    @Provides
    fun provideShieldDao(db: AppDatabase): ShieldDao = db.shieldDao()

    @Provides
    fun provideRaidRecordDao(db: AppDatabase): RaidRecordDao = db.raidRecordDao()

    @Provides
    fun provideFriendDao(db: AppDatabase): FriendDao = db.friendDao()

    @Provides
    fun provideCalendarDao(db: AppDatabase): CalendarDao = db.calendarDao()
}
