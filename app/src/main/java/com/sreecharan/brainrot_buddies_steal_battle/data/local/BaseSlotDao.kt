package com.sreecharan.brainrot_buddies_steal_battle.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BaseSlotDao {
    @Query("SELECT * FROM base_slots ORDER BY slotIndex ASC")
    fun observeAll(): Flow<List<BaseSlotEntity>>

    @Query("SELECT * FROM base_slots WHERE slotIndex = :index")
    suspend fun getByIndex(index: Int): BaseSlotEntity?

    @Upsert
    suspend fun upsert(slot: BaseSlotEntity)

    @Upsert
    suspend fun upsertAll(slots: List<BaseSlotEntity>)

    @Query("DELETE FROM base_slots")
    suspend fun deleteAll()
}
