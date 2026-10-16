package com.sreecharan.brainrot_buddies_steal_battle.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "owned_buddies")
data class OwnedBuddyEntity(
    @PrimaryKey val uid: String,
    val buddyId: String,
    val level: Int = 1,
    val hatId: String? = null,
    val acquiredAt: Long = 0L,
    val stealCooldownUntil: Long = 0L,
)
