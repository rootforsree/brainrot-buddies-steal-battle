package com.sreecharan.brainrot_buddies_steal_battle.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {
    @Query("SELECT * FROM vault WHERE id = 1")
    fun observe(): Flow<VaultEntity?>

    @Query("SELECT * FROM vault WHERE id = 1")
    suspend fun get(): VaultEntity?

    @Upsert
    suspend fun upsert(vault: VaultEntity)

    @Query("DELETE FROM vault")
    suspend fun deleteAll()
}
