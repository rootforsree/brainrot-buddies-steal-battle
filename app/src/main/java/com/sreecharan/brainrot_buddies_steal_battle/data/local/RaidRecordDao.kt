package com.sreecharan.brainrot_buddies_steal_battle.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface RaidRecordDao {
    @Query("SELECT * FROM raid_records ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<RaidRecordEntity>>

    @Query("SELECT * FROM raid_records WHERE raidId = :raidId")
    suspend fun getById(raidId: String): RaidRecordEntity?

    @Upsert
    suspend fun upsert(record: RaidRecordEntity)

    @Query("DELETE FROM raid_records")
    suspend fun deleteAll()
}
