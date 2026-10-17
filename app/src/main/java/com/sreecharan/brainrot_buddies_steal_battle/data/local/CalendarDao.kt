package com.sreecharan.brainrot_buddies_steal_battle.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarDao {

    @Query("SELECT * FROM calendar WHERE id = 1")
    fun observe(): Flow<CalendarEntity?>

    @Query("SELECT * FROM calendar WHERE id = 1")
    suspend fun get(): CalendarEntity?

    @Upsert
    suspend fun upsert(calendar: CalendarEntity)

    @Query("DELETE FROM calendar")
    suspend fun deleteAll()
}
