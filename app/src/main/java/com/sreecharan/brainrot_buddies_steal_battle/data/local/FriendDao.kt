package com.sreecharan.brainrot_buddies_steal_battle.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FriendDao {
    @Query("SELECT * FROM friends ORDER BY baseName ASC")
    fun observeAll(): Flow<List<FriendEntity>>

    @Query("SELECT * FROM friends WHERE guestId = :guestId")
    suspend fun getByGuestId(guestId: String): FriendEntity?

    @Upsert
    suspend fun upsert(friend: FriendEntity)

    @Query("DELETE FROM friends WHERE guestId = :guestId")
    suspend fun deleteByGuestId(guestId: String)

    @Query("DELETE FROM friends")
    suspend fun deleteAll()
}
