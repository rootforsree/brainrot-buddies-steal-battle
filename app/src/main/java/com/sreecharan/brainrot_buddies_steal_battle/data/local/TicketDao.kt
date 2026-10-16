package com.sreecharan.brainrot_buddies_steal_battle.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TicketDao {

    @Query("SELECT * FROM tickets")
    fun observeAll(): Flow<List<TicketEntity>>

    @Query("SELECT * FROM tickets WHERE type = :type")
    suspend fun getByType(type: String): TicketEntity?

    @Upsert
    suspend fun upsert(ticket: TicketEntity)

    @Query("DELETE FROM tickets")
    suspend fun deleteAll()
}
