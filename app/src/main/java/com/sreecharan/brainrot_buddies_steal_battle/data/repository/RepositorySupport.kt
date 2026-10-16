package com.sreecharan.brainrot_buddies_steal_battle.data.repository

import android.database.sqlite.SQLiteException
import com.sreecharan.brainrot_buddies_steal_battle.data.datastore.GamePrefs
import com.sreecharan.brainrot_buddies_steal_battle.data.local.BaseSlotDao
import com.sreecharan.brainrot_buddies_steal_battle.data.local.CalendarDao
import com.sreecharan.brainrot_buddies_steal_battle.data.local.FriendDao
import com.sreecharan.brainrot_buddies_steal_battle.data.local.OwnedBuddyDao
import com.sreecharan.brainrot_buddies_steal_battle.data.local.RaidRecordDao
import com.sreecharan.brainrot_buddies_steal_battle.data.local.ShieldDao
import com.sreecharan.brainrot_buddies_steal_battle.data.local.TicketDao
import com.sreecharan.brainrot_buddies_steal_battle.data.local.VaultDao
import com.sreecharan.brainrot_buddies_steal_battle.data.model.DataError
import com.sreecharan.brainrot_buddies_steal_battle.data.model.Result
import com.sreecharan.brainrot_buddies_steal_battle.di.IoDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

/**
 * Shared plumbing for the repository operation groups: DAO holders plus
 * the [Result]-wrapping observe/write helpers. Room is the source of truth
 * for game state; GamePrefs (Preferences DataStore) holds flat flags. Every
 * failure surfaces as [Result.Error]; nothing raw crosses the boundary.
 */
internal class CoreDaos(
    val ownedBuddyDao: OwnedBuddyDao,
    val slotDao: BaseSlotDao,
    val vaultDao: VaultDao,
    val ticketDao: TicketDao,
)

internal class MetaDaos(
    val shieldDao: ShieldDao,
    val raidRecordDao: RaidRecordDao,
    val friendDao: FriendDao,
    val calendarDao: CalendarDao,
)

internal class RepositoryHelpers(
    val gamePrefs: GamePrefs,
    @IoDispatcher val ioDispatcher: CoroutineDispatcher,
) {

    fun <T> observe(source: Flow<T>): Flow<Result<T>> = source
        .map<T, Result<T>> { Result.Success(it) }
        .onStart { emit(Result.Loading) }
        .catch { e ->
            if (e is CancellationException) {
                throw e
            }
            emit(Result.Error(DataError.Storage(e.message ?: "Read failed")))
        }
        .flowOn(ioDispatcher)

    suspend fun <T> write(block: suspend () -> T): Result<T> =
        withContext(ioDispatcher) {
            try {
                Result.Success(block())
            } catch (e: CancellationException) {
                throw e
            } catch (e: SQLiteException) {
                Result.Error(DataError.Storage(e.message ?: "Write failed"))
            } catch (e: IOException) {
                Result.Error(DataError.Storage(e.message ?: "Write failed"))
            } catch (e: IllegalStateException) {
                Result.Error(DataError.Storage(e.message ?: "Write failed"))
            } catch (e: IllegalArgumentException) {
                Result.Error(DataError.Storage(e.message ?: "Write failed"))
            }
        }
}
