package com.sreecharan.brainrot_buddies_steal_battle.data.repository

import app.cash.turbine.test
import com.sreecharan.brainrot_buddies_steal_battle.data.datastore.GamePrefs
import com.sreecharan.brainrot_buddies_steal_battle.data.local.BaseSlotDao
import com.sreecharan.brainrot_buddies_steal_battle.data.local.BaseSlotEntity
import com.sreecharan.brainrot_buddies_steal_battle.data.local.CalendarDao
import com.sreecharan.brainrot_buddies_steal_battle.data.local.CalendarEntity
import com.sreecharan.brainrot_buddies_steal_battle.data.local.FriendDao
import com.sreecharan.brainrot_buddies_steal_battle.data.local.FriendEntity
import com.sreecharan.brainrot_buddies_steal_battle.data.local.OwnedBuddyDao
import com.sreecharan.brainrot_buddies_steal_battle.data.local.OwnedBuddyEntity
import com.sreecharan.brainrot_buddies_steal_battle.data.local.RaidRecordDao
import com.sreecharan.brainrot_buddies_steal_battle.data.local.RaidRecordEntity
import com.sreecharan.brainrot_buddies_steal_battle.data.local.ShieldDao
import com.sreecharan.brainrot_buddies_steal_battle.data.local.ShieldEntity
import com.sreecharan.brainrot_buddies_steal_battle.data.local.TicketDao
import com.sreecharan.brainrot_buddies_steal_battle.data.local.TicketEntity
import com.sreecharan.brainrot_buddies_steal_battle.data.local.VaultDao
import com.sreecharan.brainrot_buddies_steal_battle.data.local.VaultEntity
import com.sreecharan.brainrot_buddies_steal_battle.data.model.DataError
import com.sreecharan.brainrot_buddies_steal_battle.data.model.Result
import com.sreecharan.brainrot_buddies_steal_battle.data.model.TicketType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("MagicNumber")
class GameRepositoryTest {
    private val testDispatcher = StandardTestDispatcher()

    private val ownedBuddyDao: OwnedBuddyDao = mockk()
    private val slotDao: BaseSlotDao = mockk()
    private val vaultDao: VaultDao = mockk()
    private val ticketDao: TicketDao = mockk()
    private val shieldDao: ShieldDao = mockk()
    private val raidRecordDao: RaidRecordDao = mockk()
    private val friendDao: FriendDao = mockk()
    private val calendarDao: CalendarDao = mockk()
    private val gamePrefs: GamePrefs = mockk()

    private lateinit var repository: GameRepository

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository =
            GameRepositoryImpl(
                ownedBuddyDao = ownedBuddyDao,
                slotDao = slotDao,
                vaultDao = vaultDao,
                ticketDao = ticketDao,
                shieldDao = shieldDao,
                raidRecordDao = raidRecordDao,
                friendDao = friendDao,
                calendarDao = calendarDao,
                gamePrefs = gamePrefs,
                ioDispatcher = testDispatcher,
            )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ---------- Flow emissions ----------

    @Test
    fun `observe slots emits loading then rows`() =
        runTest(testDispatcher) {
            val slots = listOf(BaseSlotEntity(0, unlocked = true), BaseSlotEntity(1))
            every { slotDao.observeAll() } returns flowOf(slots)

            repository.observeSlots().test {
                assertEquals(Result.Loading, awaitItem())
                assertEquals(Result.Success(slots), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `observe owned buddies emits loading then rows`() =
        runTest(testDispatcher) {
            val buddies = listOf(OwnedBuddyEntity(uid = "u1", buddyId = "frogboot"))
            every { ownedBuddyDao.observeAll() } returns flowOf(buddies)

            repository.observeOwnedBuddies().test {
                assertEquals(Result.Loading, awaitItem())
                assertEquals(Result.Success(buddies), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `observe vault emits a default vault when no row is stored`() =
        runTest(testDispatcher) {
            every { vaultDao.observe() } returns flowOf(null)

            repository.observeVault().test {
                assertEquals(Result.Loading, awaitItem())
                assertEquals(Result.Success(VaultEntity()), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `observe tickets emits ticket rows`() =
        runTest(testDispatcher) {
            val tickets = listOf(TicketEntity("lucky", 2), TicketEntity("mega", 0))
            every { ticketDao.observeAll() } returns flowOf(tickets)

            repository.observeTickets().test {
                assertEquals(Result.Loading, awaitItem())
                assertEquals(Result.Success(tickets), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `observe shield emits default when no row is stored`() =
        runTest(testDispatcher) {
            every { shieldDao.observe() } returns flowOf(null)

            repository.observeShield().test {
                assertEquals(Result.Loading, awaitItem())
                assertEquals(Result.Success(ShieldEntity()), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `observe raid records emits rows`() =
        runTest(testDispatcher) {
            val records =
                listOf(
                    RaidRecordEntity(
                        raidId = "r1",
                        direction = "out",
                        otherGuestId = "g2",
                        otherBaseName = "Snack Shack",
                        buddyId = "gulp",
                        buddyLevel = 2,
                        createdAt = 1000L,
                    ),
                )
            every { raidRecordDao.observeAll() } returns flowOf(records)

            repository.observeRaidRecords().test {
                assertEquals(Result.Loading, awaitItem())
                assertEquals(Result.Success(records), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `observe friends emits friend rows`() =
        runTest(testDispatcher) {
            val friends =
                listOf(FriendEntity(guestId = "g2", baseName = "Snack Shack", code = "ABCDEF", pairedAt = 1000L))
            every { friendDao.observeAll() } returns flowOf(friends)

            repository.observeFriends().test {
                assertEquals(Result.Loading, awaitItem())
                assertEquals(Result.Success(friends), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `observe calendar emits calendar row`() =
        runTest(testDispatcher) {
            val calendar = CalendarEntity(currentDay = 7, lastClaimDate = "2026-10-15", streakDays = 7)
            every { calendarDao.observe() } returns flowOf(calendar)

            repository.observeCalendar().test {
                assertEquals(Result.Loading, awaitItem())
                assertEquals(Result.Success(calendar), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `observe guest id emits prefs value`() =
        runTest(testDispatcher) {
            every { gamePrefs.guestId } returns flowOf("guest-1")

            repository.observeGuestId().test {
                assertEquals(Result.Loading, awaitItem())
                assertEquals(Result.Success("guest-1"), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ---------- Error paths ----------

    @Test
    fun `observe slots maps a dao failure to error`() =
        runTest(testDispatcher) {
            every { slotDao.observeAll() } returns flow { throw IOException("boom") }

            repository.observeSlots().test {
                assertEquals(Result.Loading, awaitItem())
                val error = awaitItem()
                assertIs<Result.Error>(error)
                val storage = assertIs<DataError.Storage>(error.error)
                assertTrue(storage.message.contains("boom"))
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `upsert vault maps a dao failure to error`() =
        runTest(testDispatcher) {
            coEvery { vaultDao.upsert(any()) } throws RuntimeException("boom")

            val result = repository.upsertVault(VaultEntity())

            val error = assertIs<Result.Error>(result)
            assertTrue(assertIs<DataError.Storage>(error.error).message.contains("boom"))
        }

    @Test
    fun `remove friend maps a dao failure to error`() =
        runTest(testDispatcher) {
            coEvery { friendDao.deleteByGuestId(any()) } throws RuntimeException("boom")

            val result = repository.removeFriend("g2")

            assertIs<Result.Error>(result)
        }

    // ---------- Writes ----------

    @Test
    fun `unlock slot marks the slot unlocked`() =
        runTest(testDispatcher) {
            coEvery { slotDao.getByIndex(4) } returns BaseSlotEntity(4, unlocked = false)
            coEvery { slotDao.upsert(any()) } returns Unit

            val result = repository.unlockSlot(4)

            assertEquals(Result.Success(Unit), result)
            coVerify { slotDao.upsert(BaseSlotEntity(4, unlocked = true)) }
        }

    @Test
    fun `unlock slot creates the row when missing`() =
        runTest(testDispatcher) {
            coEvery { slotDao.getByIndex(9) } returns null
            coEvery { slotDao.upsert(any()) } returns Unit

            repository.unlockSlot(9)

            coVerify { slotDao.upsert(BaseSlotEntity(9, unlocked = true)) }
        }

    @Test
    fun `set slot buddy preserves the existing row`() =
        runTest(testDispatcher) {
            coEvery { slotDao.getByIndex(0) } returns BaseSlotEntity(0, unlocked = true, shieldUntil = 999L)
            coEvery { slotDao.upsert(any()) } returns Unit

            repository.setSlotBuddy(0, "uid-1")

            coVerify {
                slotDao.upsert(BaseSlotEntity(0, unlocked = true, ownedBuddyUid = "uid-1", shieldUntil = 999L))
            }
        }

    @Test
    fun `add tickets increments the existing count`() =
        runTest(testDispatcher) {
            coEvery { ticketDao.getByType("lucky") } returns TicketEntity("lucky", 2)
            coEvery { ticketDao.upsert(any()) } returns Unit

            val result = repository.addTickets(TicketType.LUCKY, 3)

            assertEquals(Result.Success(Unit), result)
            coVerify { ticketDao.upsert(TicketEntity("lucky", 5)) }
        }

    @Test
    fun `set ticket count never stores a negative count`() =
        runTest(testDispatcher) {
            coEvery { ticketDao.upsert(any()) } returns Unit

            repository.setTicketCount(TicketType.MEGA, -4)

            coVerify { ticketDao.upsert(TicketEntity("mega", 0)) }
        }

    @Test
    fun `set base shield until preserves the singleton row`() =
        runTest(testDispatcher) {
            coEvery { shieldDao.get() } returns ShieldEntity(baseShieldUntil = 100L)
            coEvery { shieldDao.upsert(any()) } returns Unit

            repository.setBaseShieldUntil(999L)

            coVerify { shieldDao.upsert(ShieldEntity(baseShieldUntil = 999L)) }
        }

    @Test
    fun `ensure guest id delegates to prefs`() =
        runTest(testDispatcher) {
            coEvery { gamePrefs.ensureGuestId() } returns "guest-9"

            val result = repository.ensureGuestId()

            assertEquals(Result.Success("guest-9"), result)
        }

    @Test
    fun `set base name delegates to prefs`() =
        runTest(testDispatcher) {
            coEvery { gamePrefs.setBaseName("Snack Shack") } returns Unit

            val result = repository.setBaseName("Snack Shack")

            assertEquals(Result.Success(Unit), result)
            coVerify { gamePrefs.setBaseName("Snack Shack") }
        }

    @Test
    fun `add used code delegates to prefs`() =
        runTest(testDispatcher) {
            coEvery { gamePrefs.addUsedCode("STEALFUN26") } returns Unit

            val result = repository.addUsedCode("STEALFUN26")

            assertEquals(Result.Success(Unit), result)
        }

    // ---------- Lifecycle ----------

    @Test
    fun `initialize new game creates twenty four slots with four unlocked`() =
        runTest(testDispatcher) {
            stubInitWrites()

            val result = repository.initializeNewGame("Wobble Tower")

            assertEquals(Result.Success(Unit), result)
            val captured = slot<List<BaseSlotEntity>>()
            coVerify { slotDao.upsertAll(capture(captured)) }
            val slots = captured.captured
            assertEquals(24, slots.size)
            assertTrue(slots.take(4).all { it.unlocked })
            assertTrue(slots.drop(4).none { it.unlocked })
        }

    @Test
    fun `initialize new game seeds vault tickets shield calendar and guest`() =
        runTest(testDispatcher) {
            stubInitWrites()

            repository.initializeNewGame("Wobble Tower")

            coVerify {
                vaultDao.upsert(VaultEntity())
                ticketDao.upsert(TicketEntity("lucky", 0))
                ticketDao.upsert(TicketEntity("mega", 0))
                shieldDao.upsert(ShieldEntity())
                calendarDao.upsert(CalendarEntity())
                gamePrefs.ensureGuestId()
                gamePrefs.setBaseName("Wobble Tower")
            }
        }

    @Test
    fun `clear all data wipes every table and prefs`() =
        runTest(testDispatcher) {
            coEvery { ownedBuddyDao.deleteAll() } returns Unit
            coEvery { slotDao.deleteAll() } returns Unit
            coEvery { vaultDao.deleteAll() } returns Unit
            coEvery { ticketDao.deleteAll() } returns Unit
            coEvery { shieldDao.deleteAll() } returns Unit
            coEvery { raidRecordDao.deleteAll() } returns Unit
            coEvery { friendDao.deleteAll() } returns Unit
            coEvery { calendarDao.deleteAll() } returns Unit
            coEvery { gamePrefs.clearAll() } returns Unit

            val result = repository.clearAllData()

            assertEquals(Result.Success(Unit), result)
            coVerify {
                ownedBuddyDao.deleteAll()
                slotDao.deleteAll()
                vaultDao.deleteAll()
                ticketDao.deleteAll()
                shieldDao.deleteAll()
                raidRecordDao.deleteAll()
                friendDao.deleteAll()
                calendarDao.deleteAll()
                gamePrefs.clearAll()
            }
        }

    private fun stubInitWrites() {
        coEvery { slotDao.upsertAll(any()) } returns Unit
        coEvery { vaultDao.upsert(any()) } returns Unit
        coEvery { ticketDao.upsert(any()) } returns Unit
        coEvery { shieldDao.upsert(any()) } returns Unit
        coEvery { calendarDao.upsert(any()) } returns Unit
        coEvery { gamePrefs.ensureGuestId() } returns "guest-1"
        coEvery { gamePrefs.setBaseName(any()) } returns Unit
    }
}
