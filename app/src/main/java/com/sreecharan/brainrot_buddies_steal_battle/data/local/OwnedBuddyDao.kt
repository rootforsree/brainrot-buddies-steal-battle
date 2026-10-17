package com.sreecharan.brainrot_buddies_steal_battle.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface OwnedBuddyDao {
    @Query("SELECT * FROM owned_buddies ORDER BY acquiredAt ASC")
    fun observeAll(): Flow<List<OwnedBuddyEntity>>

    @Query("SELECT * FROM owned_buddies WHERE uid = :uid")
    suspend fun getByUid(uid: String): OwnedBuddyEntity?

    @Upsert
    suspend fun upsert(buddy: OwnedBuddyEntity)

    @Query("DELETE FROM owned_buddies WHERE uid = :uid")
    suspend fun deleteByUid(uid: String)

    @Query("DELETE FROM owned_buddies")
    suspend fun deleteAll()
}
