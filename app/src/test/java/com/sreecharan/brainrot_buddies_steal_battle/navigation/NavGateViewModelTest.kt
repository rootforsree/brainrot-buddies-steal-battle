package com.sreecharan.brainrot_buddies_steal_battle.navigation

import app.cash.turbine.test
import com.sreecharan.brainrot_buddies_steal_battle.fakes.FakeGameRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NavGateViewModelTest {
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

    @Test
    fun `no base name routes to onboarding`() =
        runTest(testDispatcher) {
            repository.baseNameState.value = ""
            val vm = NavGateViewModel(repository)

            vm.uiState.test {
                awaitItem() // loading
                val gate = awaitItem()
                assertFalse(gate.loading)
                assertFalse(gate.hasBaseName)
                assertEquals(1, gate.onboardingDay)
                assertFalse(gate.tutorialDone)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `base name set routes to the main tabs with the album gated by day`() =
        runTest(testDispatcher) {
            repository.baseNameState.value = "Sunny Meadows"
            repository.onboardingDayState.value = 3
            val vm = NavGateViewModel(repository)

            vm.uiState.test {
                awaitItem() // loading
                val gate = awaitItem()
                assertTrue(gate.hasBaseName)
                assertEquals(3, gate.onboardingDay)
                cancelAndIgnoreRemainingEvents()
            }

            repository.onboardingDayState.value = 5
            vm.uiState.test {
                // Already loaded; day change flows through.
                val gate = awaitItem()
                assertEquals(5, gate.onboardingDay)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `tutorial done surfaces`() =
        runTest(testDispatcher) {
            repository.baseNameState.value = "Sunny Meadows"
            repository.tutorialDoneState.value = true
            val vm = NavGateViewModel(repository)

            vm.uiState.test {
                awaitItem() // loading
                assertTrue(awaitItem().tutorialDone)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
