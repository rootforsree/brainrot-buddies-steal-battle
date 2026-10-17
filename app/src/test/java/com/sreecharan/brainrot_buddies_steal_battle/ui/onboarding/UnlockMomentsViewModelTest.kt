package com.sreecharan.brainrot_buddies_steal_battle.ui.onboarding

import app.cash.turbine.test
import com.sreecharan.brainrot_buddies_steal_battle.fakes.FakeGameRepository
import com.sreecharan.brainrot_buddies_steal_battle.ui.shop.SHIELD_DURATION_MS
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
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class UnlockMomentsViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeGameRepository

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeGameRepository()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = UnlockMomentsViewModel(repository)

    @Test
    fun `no moment before day 2`() =
        runTest(testDispatcher) {
            repository.onboardingDayState.value = 1
            val vm = viewModel()

            vm.pendingMoment.test {
                assertNull(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `day 2 surfaces the ticket moment`() =
        runTest(testDispatcher) {
            repository.onboardingDayState.value = 2
            val vm = viewModel()

            vm.pendingMoment.test {
                assertEquals(UnlockMoment.TICKET, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `opening the shield moment grants an 8 hour base shield`() =
        runTest(testDispatcher) {
            repository.onboardingDayState.value = 4
            val vm = viewModel()
            val before = System.currentTimeMillis()

            vm.pendingMoment.test {
                assertEquals(UnlockMoment.SHIELD, awaitItem())
                vm.onMomentOpened(UnlockMoment.SHIELD)
                advanceUntilIdle()
                // Shown: no more pending moments.
                assertNull(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertTrue("setBaseShieldUntil" in repository.calls)
            val until = repository.shieldState.value.baseShieldUntil
            assertTrue(until >= before + SHIELD_DURATION_MS - 5_000)
        }

    @Test
    fun `dismissed moments do not resurface`() =
        runTest(testDispatcher) {
            repository.onboardingDayState.value = 3
            val vm = viewModel()

            vm.pendingMoment.test {
                assertEquals(UnlockMoment.TICKET, awaitItem())
                vm.onMomentDismissed(UnlockMoment.TICKET)
                assertEquals(UnlockMoment.UPGRADES, awaitItem())
                vm.onMomentDismissed(UnlockMoment.UPGRADES)
                // Day 3: shield (day 4) not yet due.
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }
}
