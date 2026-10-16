package com.sreecharan.brainrot_buddies_steal_battle.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "base_slots")
data class BaseSlotEntity(
    @PrimaryKey val slotIndex: Int,
    val unlocked: Boolean = false,
    val ownedBuddyUid: String? = null,
    val shieldUntil: Long = 0L,
)
