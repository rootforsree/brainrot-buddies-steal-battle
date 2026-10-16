package com.sreecharan.brainrot_buddies_steal_battle.ui.gifts

import app.cash.turbine.test
import com.sreecharan.brainrot_buddies_steal_battle.data.local.CalendarEntity
import com.sreecharan.brainrot_buddies_steal_battle.data.local.VaultEntity
import com.sreecharan.brainrot_buddies_steal_battle.fakes.FakeGamePrefs
import com.sreecharan.brainrot_buddies_steal_battle.fakes.FakeGameRepository
import com.sreecharan.brainrot_buddies_steal_battle.game.engine.GameEngine
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GiftsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeGameRepository
    private lateinit var gamePrefs: FakeGamePrefs
    private val engine = GameEngine()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeGameRepository()
        gamePrefs = FakeGamePrefs()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = GiftsViewModel(repository, gamePrefs, engine)

    @Test
    fun `claiming today advances the calendar and grants the day reward`() =
        runTest(testDispatcher) {
            repository.calendarState.value = CalendarEntity(
                currentDay = 1,
                lastClaimDate = "",
                streakDays = 0,
            )
            repository.vaultState.value = VaultEntity(coins = 0L)
            val vm = viewModel()

            vm.events.test {
                advanceUntilIdle()
                vm.onClaimToday()
                advanceUntilIdle()
                val event = awaitItem()
                assertTrue(event is GiftsEvent.GiftClaimed)
                assertEquals(1, event.day)
                cancelAndIgnoreRemainingEvents()
            }
            // Day 1 reward is 200 coins (engine) + 1 Lucky Ticket (contract §1).
            assertEquals(200L, repository.vaultState.value.coins)
            assertEquals(1, repository.ticketsState.value.first { it.type == "lucky" }.count)
            assertEquals(engine.todayKey(), repository.calendarState.value.lastClaimDate)
            assertEquals(1, repository.calendarState.value.streakDays)
        }

    @Test
    fun `claiming twice in one day fails`() = runTest(testDispatcher) {
        repository.calendarState.value = CalendarEntity(
            currentDay = 2,
            lastClaimDate = engine.todayKey(),
            streakDays = 1,
        )
        val vm = viewModel()

        vm.events.test {
            advanceUntilIdle()
            vm.onClaimToday()
            advanceUntilIdle()
            assertEquals(GiftsEvent.ActionFailed, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `day 7 claim grants a mega ticket`() = runTest(testDispatcher) {
        // currentDay 6 + consecutive claim -> day 7 reward.
        repository.calendarState.value = CalendarEntity(
            currentDay = 6,
            lastClaimDate = engine.yesterdayKey(),
            streakDays = 6,
        )
        val vm = viewModel()

        vm.events.test {
            advanceUntilIdle()
            vm.onClaimToday()
            advanceUntilIdle()
            val event = awaitItem()
            assertTrue(event is GiftsEvent.GiftClaimed)
            assertEquals(7, event.day)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, repository.ticketsState.value.first { it.type == "mega" }.count)
        assertEquals(7, repository.calendarState.value.currentDay)
    }

    @Test
    fun `upcoming day advances only on consecutive claims`() = runTest(testDispatcher) {
        repository.calendarState.value = CalendarEntity(
            currentDay = 6,
            lastClaimDate = engine.yesterdayKey(),
            streakDays = 6,
        )
        val vm = viewModel()

        vm.uiState.test {
            awaitItem() // loading
            assertEquals(7, awaitItem().upcomingDay)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `upcoming day is a pure function of the calendar`() {
        val today = LocalDate.now()
        // First claim ever: day 1.
        assertEquals(
            1,
            GiftsViewModel.upcomingDay(CalendarEntity(currentDay = 1, lastClaimDate = ""), 1),
        )
        // Consecutive: advances.
        assertEquals(
            7,
            GiftsViewModel.upcomingDay(
                CalendarEntity(currentDay = 6, lastClaimDate = today.minusDays(1).toString()),
                6,
            ),
        )
        // Missed: stays put (pause, never reset).
        assertEquals(
            6,
            GiftsViewModel.upcomingDay(
                CalendarEntity(currentDay = 6, lastClaimDate = today.minusDays(3).toString()),
                6,
            ),
        )
        // Claimed today: stays put.
        assertEquals(
            6,
            GiftsViewModel.upcomingDay(
                CalendarEntity(currentDay = 6, lastClaimDate = today.toString()),
                6,
            ),
        )
    }

    @Test
    fun `catch-up grants the missed day and advances`() = runTest(testDispatcher) {
        val threeDaysAgo = LocalDate.now().minusDays(3).toString()
        repository.calendarState.value = CalendarEntity(
            currentDay = 5,
            lastClaimDate = threeDaysAgo,
            streakDays = 2,
        )
        repository.vaultState.value = VaultEntity(coins = 1_000L)
        val vm = viewModel()

        vm.uiState.test {
            awaitItem() // loading
            val loaded = awaitItem()
            assertTrue(loaded.catchUpAvailable)
            assertEquals(5, loaded.upcomingDay)
            cancelAndIgnoreRemainingEvents()
        }

        vm.events.test {
            advanceUntilIdle()
            vm.onCatchUpConfirmed()
            advanceUntilIdle()
            assertEquals(GiftsEvent.CatchUpDone, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(500L, repository.vaultState.value.coins)
        // Missed day 6 claimed: day advances, last claim becomes yesterday,
        // streak continues.
        val calendar = repository.calendarState.value
        assertEquals(6, calendar.currentDay)
        assertEquals(engine.yesterdayKey(), calendar.lastClaimDate)
        assertEquals(3, calendar.streakDays)
    }

    @Test
    fun `catch-up without coins fails`() = runTest(testDispatcher) {
        repository.calendarState.value = CalendarEntity(
            currentDay = 5,
            lastClaimDate = LocalDate.now().minusDays(3).toString(),
            streakDays = 2,
        )
        repository.vaultState.value = VaultEntity(coins = 100L)
        val vm = viewModel()

        vm.events.test {
            advanceUntilIdle()
            vm.onCatchUpConfirmed()
            advanceUntilIdle()
            assertEquals(GiftsEvent.ActionFailed, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(100L, repository.vaultState.value.coins)
    }

    @Test
    fun `weekly gift grants a buddy and stamps the date`() = runTest(testDispatcher) {
        gamePrefs.lastWeeklyGiftDateState.value = ""
        repository.slotsState.value = repository.freshSlots()
        val vm = viewModel()

        vm.uiState.test {
            awaitItem() // loading
            assertTrue(awaitItem().weeklyGiftAvailable)
            cancelAndIgnoreRemainingEvents()
        }

        vm.events.test {
            advanceUntilIdle()
            vm.onClaimWeekly()
            advanceUntilIdle()
            val event = awaitItem()
            assertTrue(event is GiftsEvent.WeeklyClaimed)
            assertTrue(event.buddyName.isNotBlank())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, repository.buddiesState.value.size)
        assertEquals(engine.todayKey(), gamePrefs.lastWeeklyGiftDateState.value)
    }

    @Test
    fun `weekly gift claimed this week is unavailable`() = runTest(testDispatcher) {
        gamePrefs.lastWeeklyGiftDateState.value = engine.todayKey()
        val vm = viewModel()

        vm.uiState.test {
            awaitItem() // loading
            assertFalse(awaitItem().weeklyGiftAvailable)
            cancelAndIgnoreRemainingEvents()
        }

        vm.events.test {
            advanceUntilIdle()
            vm.onClaimWeekly()
            advanceUntilIdle()
            assertEquals(GiftsEvent.ActionFailed, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `redeeming STEALFUN26 grants 500 coins and marks the code used`() =
        runTest(testDispatcher) {
            repository.vaultState.value = VaultEntity(coins = 0L)
            val vm = viewModel()

            vm.onRedeemCode("stealfun26")
            advanceUntilIdle()

            val result = vm.redeemUiState.value.result
            assertTrue(result is RedeemResultUi.Success)
            assertEquals(500L, result.coins)
            assertEquals(500L, repository.vaultState.value.coins)
            assertTrue("STEALFUN26" in repository.usedCodesState.value)
        }

    @Test
    fun `redeeming BUDDYUP26 grants a lucky ticket`() = runTest(testDispatcher) {
        val vm = viewModel()

        vm.onRedeemCode("BUDDYUP26")
        advanceUntilIdle()

        val result = vm.redeemUiState.value.result
        assertTrue(result is RedeemResultUi.Success)
        assertEquals(1, result.luckyTickets)
        assertEquals(1, repository.ticketsState.value.first { it.type == "lucky" }.count)
    }

    @Test
    fun `redeeming the same code twice reports already used`() = runTest(testDispatcher) {
        val vm = viewModel()

        vm.onRedeemCode("STEALFUN26")
        advanceUntilIdle()
        vm.onRedeemResultConsumed()
        vm.onRedeemCode("stealfun26")
        advanceUntilIdle()

        assertTrue(vm.redeemUiState.value.result is RedeemResultUi.AlreadyUsed)
        // Coins granted only once.
        assertEquals(500L, repository.vaultState.value.coins)
    }

    @Test
    fun `redeeming an unknown code reports invalid`() = runTest(testDispatcher) {
        val vm = viewModel()

        vm.onRedeemCode("NOPE123")
        advanceUntilIdle()

        assertTrue(vm.redeemUiState.value.result is RedeemResultUi.Invalid)
    }

    @Test
    fun `catch-up availability is a pure function of the last claim date`() {
        val today = LocalDate.now()
        assertFalse(GiftsViewModel.isCatchUpAvailable(""))
        assertFalse(GiftsViewModel.isCatchUpAvailable(today.toString()))
        assertFalse(GiftsViewModel.isCatchUpAvailable(today.minusDays(1).toString()))
        assertTrue(GiftsViewModel.isCatchUpAvailable(today.minusDays(2).toString()))
        assertTrue(GiftsViewModel.isCatchUpAvailable(today.minusDays(9).toString()))
    }

    @Test
    fun `weekly availability resets on monday`() {
        assertTrue(GiftsViewModel.isWeeklyAvailable(""))
        assertFalse(GiftsViewModel.isWeeklyAvailable(LocalDate.now().toString()))
        assertTrue(GiftsViewModel.isWeeklyAvailable(LocalDate.now().minusDays(8).toString()))
    }
}
