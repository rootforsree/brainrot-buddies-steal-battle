package com.sreecharan.brainrot_buddies_steal_battle.ui.onboarding

import app.cash.turbine.test
import com.sreecharan.brainrot_buddies_steal_battle.data.local.CalendarEntity
import com.sreecharan.brainrot_buddies_steal_battle.data.local.OwnedBuddyEntity
import com.sreecharan.brainrot_buddies_steal_battle.data.local.VaultEntity
import com.sreecharan.brainrot_buddies_steal_battle.fakes.FakeGameRepository
import com.sreecharan.brainrot_buddies_steal_battle.game.engine.GameEngine
import kotlin.test.assertEquals
import kotlin.test.assertNull
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
class GuidedClaimsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeGameRepository
    private val engine = GameEngine()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeGameRepository()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = GuidedClaimsViewModel(repository, engine)

    @Test
    fun `fresh player starts at claim frogboot`() = runTest(testDispatcher) {
        repository.slotsState.value = repository.freshSlots()
        val vm = viewModel()

        vm.step.test {
            assertEquals(GuidedStep.CLAIM_FROGBOOT, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `step advances as buddies are claimed`() = runTest(testDispatcher) {
        repository.slotsState.value = repository.freshSlots()
        val vm = viewModel()

        vm.step.test {
            assertEquals(GuidedStep.CLAIM_FROGBOOT, awaitItem())
            repository.buddiesState.value = listOf(
                OwnedBuddyEntity(uid = "u1", buddyId = "frogboot", level = 1),
            )
            assertEquals(GuidedStep.CLAIM_PIP, awaitItem())
            repository.buddiesState.value = listOf(
                OwnedBuddyEntity(uid = "u1", buddyId = "frogboot", level = 1),
                OwnedBuddyEntity(uid = "u2", buddyId = "pip_mcsnacks", level = 1),
            )
            assertEquals(GuidedStep.BUY_SLOT, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `completed ladder yields no step`() = runTest(testDispatcher) {
        repository.slotsState.value = repository.freshSlots().map {
            if (it.slotIndex < 5) it.copy(unlocked = true) else it
        }
        repository.buddiesState.value = listOf(
            OwnedBuddyEntity(uid = "u1", buddyId = "frogboot", level = 1),
            OwnedBuddyEntity(uid = "u2", buddyId = "pip_mcsnacks", level = 1),
            OwnedBuddyEntity(uid = "u3", buddyId = "splorch", level = 1),
        )
        repository.vaultState.value = VaultEntity(coins = 0L)
        repository.calendarState.value = CalendarEntity(
            currentDay = 1,
            lastClaimDate = engine.todayKey(),
            streakDays = 1,
        )
        val vm = viewModel()

        vm.step.test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `tutorial done hides the overlay`() = runTest(testDispatcher) {
        repository.tutorialDoneState.value = true
        val vm = viewModel()

        vm.step.test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `dismissed step stays hidden this session`() = runTest(testDispatcher) {
        repository.slotsState.value = repository.freshSlots()
        val vm = viewModel()

        vm.step.test {
            assertEquals(GuidedStep.CLAIM_FROGBOOT, awaitItem())
            vm.onStepDismissed(GuidedStep.CLAIM_FROGBOOT)
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ladder complete writes tutorial done`() = runTest(testDispatcher) {
        val vm = viewModel()
        vm.onLadderComplete()
        advanceUntilIdle()
        assertEquals(true, repository.tutorialDoneState.value)
    }

    @Test
    fun `deriveStep is a pure function of game state`() {
        assertEquals(
            GuidedStep.CLAIM_FROGBOOT,
            GuidedClaimsViewModel.deriveStep(emptySet(), 4, 0L, true),
        )
        assertEquals(
            GuidedStep.CLAIM_PIP,
            GuidedClaimsViewModel.deriveStep(setOf("frogboot"), 4, 0L, true),
        )
        assertEquals(
            GuidedStep.BUY_SLOT,
            GuidedClaimsViewModel.deriveStep(setOf("frogboot", "pip_mcsnacks"), 4, 0L, true),
        )
        assertEquals(
            GuidedStep.CLAIM_SPLORCH,
            GuidedClaimsViewModel.deriveStep(setOf("frogboot", "pip_mcsnacks"), 5, 0L, true),
        )
        assertEquals(
            GuidedStep.COLLECT_VAULT,
            GuidedClaimsViewModel.deriveStep(
                setOf("frogboot", "pip_mcsnacks", "splorch"), 5, 120L, true,
            ),
        )
        assertEquals(
            GuidedStep.OPEN_GIFT,
            GuidedClaimsViewModel.deriveStep(
                setOf("frogboot", "pip_mcsnacks", "splorch"), 5, 0L, true,
            ),
        )
        assertNull(
            GuidedClaimsViewModel.deriveStep(
                setOf("frogboot", "pip_mcsnacks", "splorch"), 5, 0L, false,
            ),
        )
    }
}
