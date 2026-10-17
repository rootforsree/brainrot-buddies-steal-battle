package com.sreecharan.brainrot_buddies_steal_battle.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ShieldDao {
    @Query("SELECT * FROM shields WHERE id = 1")
    fun observe(): Flow<ShieldEntity?>

    @Query("SELECT * FROM shields WHERE id = 1")
    suspend fun get(): ShieldEntity?

    @Upsert
    suspend fun upsert(shield: ShieldEntity)

    @Query("DELETE FROM shields")
    suspend fun deleteAll()
}
