package com.sreecharan.brainrot_buddies_steal_battle.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "friends")
data class FriendEntity(
    @PrimaryKey val guestId: String,
    val baseName: String,
    val code: String,
    val pairedAt: Long,
    val lastSeenAt: Long = 0L,
    val snapshotJson: String? = null,
    val snapshotAt: Long = 0L,
    val snapshotRaided: Boolean = false,
)
