package com.sreecharan.brainrot_buddies_steal_battle.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "calendar")
data class CalendarEntity(
    @PrimaryKey val id: Int = 1,
    val currentDay: Int = 1,
    val lastClaimDate: String = "",
    val streakDays: Int = 0,
)
