package com.sreecharan.brainrot_buddies_steal_battle.ui.onboarding

import android.content.Context
import android.content.res.Resources
import app.cash.turbine.test
import com.sreecharan.brainrot_buddies_steal_battle.fakes.FakeGameRepository
import io.mockk.every
import io.mockk.mockk
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeGameRepository
    private lateinit var context: Context

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeGameRepository()
        val resources =
            mockk<Resources> {
                every { getStringArray(any()) } returns arrayOf("Sunny Meadows", "Cozy Cove")
            }
        context =
            mockk {
                every { getResources() } returns resources
            }
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = OnboardingViewModel(repository, context)

    @Test
    fun `play creates the guest id and moves to naming`() =
        runTest(testDispatcher) {
            val vm = viewModel()

            vm.uiState.test {
                val start = awaitItem()
                assertEquals(OnboardingStep.PLAY, start.step)
                assertEquals(2, start.baseNames.size)

                vm.onPlayClicked()
                advanceUntilIdle()

                val naming = awaitItem()
                assertEquals(OnboardingStep.NAMING, naming.step)
                assertFalse(naming.isWorking)
                cancelAndIgnoreRemainingEvents()
            }
            assertTrue("ensureGuestId" in repository.calls)
            assertTrue(repository.guestIdState.value?.isNotBlank() == true)
        }

    @Test
    fun `starting with a name initializes the game`() =
        runTest(testDispatcher) {
            val vm = viewModel()
            vm.onPlayClicked()
            advanceUntilIdle()

            vm.onBaseNameSelected("Cozy Cove")
            vm.onStartClicked()
            advanceUntilIdle()

            assertTrue("initializeNewGame" in repository.calls)
            assertEquals("Cozy Cove", repository.baseNameState.value)
            // Fresh game seeded: 24 slots, first 4 unlocked.
            assertEquals(24, repository.slotsState.value.size)
            assertEquals(4, repository.slotsState.value.count { it.unlocked })
        }

    @Test
    fun `start without a name does nothing`() =
        runTest(testDispatcher) {
            val vm = viewModel()
            vm.onStartClicked()
            advanceUntilIdle()

            assertFalse("initializeNewGame" in repository.calls)
            assertNull(repository.guestIdState.value)
        }
}
