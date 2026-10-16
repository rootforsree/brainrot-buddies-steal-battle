package com.sreecharan.brainrot_buddies_steal_battle.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "raid_records")
data class RaidRecordEntity(
    @PrimaryKey val raidId: String,
    val direction: String,
    val otherGuestId: String,
    val otherBaseName: String,
    val buddyId: String,
    val buddyLevel: Int,
    val createdAt: Long,
    val revengeUsed: Boolean = false,
    val revengeDeadline: Long = 0L,
    val isShadow: Boolean = false,
)
