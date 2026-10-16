package com.sreecharan.brainrot_buddies_steal_battle.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shields")
data class ShieldEntity(
    @PrimaryKey val id: Int = 1,
    val baseShieldUntil: Long = 0L,
)
