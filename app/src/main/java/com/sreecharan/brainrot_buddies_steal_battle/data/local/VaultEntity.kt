package com.sreecharan.brainrot_buddies_steal_battle.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vault")
data class VaultEntity(
    @PrimaryKey val id: Int = 1,
    val coins: Long = 0L,
    val lifetimeCoins: Long = 0L,
    val lastTickAt: Long = 0L,
)
