package com.sreecharan.brainrot_buddies_steal_battle.ui.home

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import com.sreecharan.brainrot_buddies_steal_battle.core.audio.SoundManager
import com.sreecharan.brainrot_buddies_steal_battle.data.model.Rarity
import com.sreecharan.brainrot_buddies_steal_battle.data.model.TicketType
import com.sreecharan.brainrot_buddies_steal_battle.game.engine.GameEngine
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Base ViewModel state transitions (not internals): snapshot application,
 * the conveyor spawner, sub-100 ms claim ack, slot wiggle, vault collect,
 * tour mode, ticket ceremony phases, staged-replay lifecycle, epic trauma.
 *
 * The spawner runs inside the ViewModel on the test dispatcher; the first
 * 500 ms tick spawns a buddy because the initial `nextSpawnMs` is 0.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var gameData: FakeBaseGameData
    private lateinit var soundManager: SoundManager
    private lateinit var appContext: Context
    private lateinit var viewModel: HomeViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // SystemClock is an Android stub in unit tests (throws RuntimeException
        // on every call). The handlers stamp animations with uptimeMillis, so
        // pin it to a fixed value instead of touching main code.
        mockkStatic(SystemClock::class)
        every { SystemClock.uptimeMillis() } returns 0L
        gameData = FakeBaseGameData()
        soundManager = mockk(relaxed = true)
        appContext = mockk()
        every { appContext.getString(any(), *anyVararg()) } answers { "fmt" }
        // Note: no scheduler run here on purpose. Pumping it would apply the
        // fake's initial snapshot before the tests subscribe, so the
        // `snapshot clears loading` test could never observe isLoading = true.
        viewModel = HomeViewModel(gameData, GameEngine(), soundManager, appContext, testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(SystemClock::class)
        Dispatchers.resetMain()
    }

    /**
     * Runs [body] on the shared test dispatcher and always cancels the
     * ViewModel's scope afterwards. Cancelling stops the ViewModel's infinite
     * tick loop: runTest drains the shared scheduler when the body finishes,
     * and a live infinite loop would never drain, hanging the test. The
     * try/finally keeps a failing assertion from turning into that hang.
     */
    private fun runVmTest(body: suspend TestScope.() -> Unit): TestResult =
        runTest(testDispatcher) {
            try {
                body()
            } finally {
                viewModel.viewModelScope.cancel()
            }
        }

    @Test
    fun `snapshot clears loading and fills the board`() =
        runVmTest {
            // The board starts loading before any snapshot arrives. Read it
            // synchronously: once turbine suspends, the collector may process
            // the fake's initial empty snapshot first.
            assertTrue(viewModel.uiState.value.isLoading)

            viewModel.uiState.test {
                awaitItem() // stale: initial or empty-snapshot state
                gameData.snapshotFlow.value =
                    baseSnapshot(
                        baseName = "Wobble Tower",
                        coins = 1_234L,
                    )
                runCurrent()

                val loaded = awaitItem()
                assertFalse(loaded.isLoading)
                assertEquals("Wobble Tower", loaded.baseName)
                assertEquals(1_234L, loaded.vault.coins)
                assertEquals(24, loaded.slots.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `the spawner puts a buddy on the conveyor`() =
        runVmTest {
            advanceTimeBy(600L)
            runCurrent()

            val conveyor = viewModel.uiState.value.conveyor
            assertTrue(conveyor.isNotEmpty(), "one spawn expected after the first tick")
            val ride = conveyor.first()
            assertTrue(ride.expiresAtMs > ride.spawnedAtMs)
            assertTrue(viewModel.uiState.value.nextSpawnMs > System.currentTimeMillis())
        }

    @Test
    fun `claim tap acks instantly without waiting on the data layer`() =
        runVmTest {
            advanceTimeBy(600L)
            runCurrent()
            val ride =
                viewModel.uiState.value.conveyor
                    .first()

            val gate = CompletableDeferred<ClaimPlacement>()
            gameData.placeHandler = { gate.await() }

            viewModel.onConveyorTap(ride.id)
            runCurrent()

            // The squash ack is in state before the data layer answers, and the
            // ride leaves the conveyor optimistically.
            assertTrue(
                viewModel.uiState.value.claimAnims
                    .containsKey(ride.id),
            )
            assertFalse(
                viewModel.uiState.value.conveyor
                    .any { it.id == ride.id },
            )
            assertFalse(gate.isCompleted)

            gate.complete(ClaimPlacement.Placed(4, slottedBuddy("uid-1", Rarity.COMMON)))
            runCurrent()
            assertTrue(
                viewModel.uiState.value.waddleAnims
                    .containsKey("uid-1"),
            )
        }

    @Test
    fun `full base converts the claim to buddy bites`() =
        runVmTest {
            advanceTimeBy(600L)
            runCurrent()
            val ride =
                viewModel.uiState.value.conveyor
                    .first()
            gameData.placeHandler = { ClaimPlacement.BaseFull }

            viewModel.onConveyorTap(ride.id)
            runCurrent()

            assertEquals(5, viewModel.uiState.value.bites)
            assertTrue(
                viewModel.uiState.value.floatUps
                    .isNotEmpty(),
            )
        }

    @Test
    fun `mega ticket reveal is always epic and adds a trauma impulse`() =
        runVmTest {
            gameData.snapshotFlow.value = baseSnapshot(megaTickets = 1)
            runCurrent()

            viewModel.onTicketTap(TicketType.MEGA)
            runCurrent()

            val drumroll = viewModel.uiState.value.ceremony
            assertNotNull(drumroll)
            assertEquals(CeremonyPhase.DRUMROLL, drumroll.phase)
            verify { soundManager.playVoice("voice_drumroll_please") }

            advanceTimeBy(1_300L)
            runCurrent()

            val reveal = viewModel.uiState.value.ceremony
            assertNotNull(reveal)
            assertEquals(CeremonyPhase.REVEAL, reveal.phase)
            val buddy = assertNotNull(reveal.buddy)
            assertEquals(Rarity.EPIC, buddy.rarity)
            val trauma = viewModel.uiState.value.trauma
            assertTrue(trauma.token > 0L)
            assertTrue(trauma.amount > 0f)

            viewModel.onCeremonySkip()
            runCurrent()
            assertNull(viewModel.uiState.value.ceremony)
        }

    @Test
    fun `locked slot tap wiggles and never stays silent`() =
        runVmTest {
            gameData.snapshotFlow.value = baseSnapshot()
            runCurrent()

            viewModel.onSlotTap(7) // locked: only 0..3 start unlocked
            runCurrent()

            assertEquals(
                7,
                viewModel.uiState.value.wiggleSlot
                    ?.slotIndex,
            )
            assertTrue(
                viewModel.uiState.value.floatUps
                    .isNotEmpty(),
                "a price float-up must appear",
            )
            verify { soundManager.playButton() }
        }

    @Test
    fun `tapping your own buddy plays its catchphrase`() =
        runVmTest {
            gameData.snapshotFlow.value = baseSnapshot()
            runCurrent()

            viewModel.onSlotTap(0) // occupied in the fixture
            runCurrent()

            verify { soundManager.playVoice("voice_frogboot") }
            assertTrue(
                viewModel.uiState.value.boingAnims
                    .containsKey("uid-frogboot"),
            )
        }

    @Test
    fun `collect vault collects and floats the amount`() =
        runVmTest {
            gameData.collectedCoins = 500L

            viewModel.onCollectVault()
            runCurrent()

            verify { soundManager.playVaultCollect() }
            assertTrue(
                viewModel.uiState.value.floatUps
                    .isNotEmpty(),
            )
        }

    @Test
    fun `tour mode toggles and spotlights the first buddy`() =
        runVmTest {
            gameData.snapshotFlow.value = baseSnapshot()
            runCurrent()

            viewModel.onTourToggle()
            runCurrent()
            val tour = viewModel.uiState.value.tour
            assertNotNull(tour)
            assertEquals(0, tour.spotlightSlot)

            viewModel.onTourToggle()
            runCurrent()
            assertNull(viewModel.uiState.value.tour)
        }

    @Test
    fun `pending raid record plays the staged replay then acknowledges`() =
        runVmTest {
            val replay =
                StagedReplayUi(
                    id = "replay-1",
                    kind = ReplayKind.STEAL,
                    buddyId = "frogboot",
                    buddyImageRes = 0,
                    buddyName = "Frogboot",
                    buddySeed = 42L,
                    otherBaseName = "Snack Shack",
                    caption = "Sneaky Snout took Frogboot!",
                    startUptimeMs = 0L,
                )
            gameData.snapshotFlow.value = baseSnapshot(pendingReplay = replay)
            runCurrent()

            assertNotNull(viewModel.uiState.value.replay)
            verify { soundManager.playStealSuccess() }

            advanceTimeBy(5_100L)
            runCurrent()

            assertNull(viewModel.uiState.value.replay)
            assertTrue(gameData.acknowledgedReplays.contains("replay-1"))
        }

    @Test
    fun `first interaction unlocks audio and starts music`() =
        runVmTest {
            every { soundManager.isUnlocked() } returns false

            viewModel.onFirstInteraction()

            verify { soundManager.unlockAudio() }
            verify { soundManager.startMusic() }
        }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private fun baseSnapshot(
        baseName: String = "Wobble Tower",
        coins: Long = 0L,
        luckyTickets: Int = 0,
        megaTickets: Int = 0,
        pendingReplay: StagedReplayUi? = null,
    ): BaseSnapshot {
        val slots =
            (0 until 24).map { index ->
                SlotUi(
                    index = index,
                    unlocked = index < 4,
                    buddy = if (index == 0) slottedBuddy("uid-frogboot", Rarity.COMMON) else null,
                    shielded = false,
                    unlockCost = if (index < 4) 0L else 50L,
                )
            }
        return BaseSnapshot(
            baseName = baseName,
            slots = slots,
            vaultCoins = coins,
            vaultCap = 14_400L,
            shieldActive = true,
            shieldEndsAtMs = System.currentTimeMillis() + 3_600_000L,
            raidWindowActive = false,
            raidsLeft = 0,
            nextRaidWindowMs = System.currentTimeMillis() + 7_200_000L,
            luckyTickets = luckyTickets,
            megaTickets = megaTickets,
            coachMark = null,
            pendingReplay = pendingReplay,
        )
    }

    private fun slottedBuddy(
        uid: String,
        rarity: Rarity,
    ): SlottedBuddyUi =
        SlottedBuddyUi(
            uid = uid,
            buddyId = "frogboot",
            name = "Frogboot",
            rarity = rarity,
            imageRes = 0,
            voiceResName = "voice_frogboot",
            catchphrase = "Plip plop! RAIN'S HERE!",
            level = 1,
            seed = 123L,
            hatId = null,
            coinsPerSec = 1,
            stealCooldownEndsAtMs = 0L,
        )

    private class FakeBaseGameData : BaseGameData {
        val snapshotFlow =
            MutableStateFlow(
                BaseSnapshot(
                    baseName = "",
                    slots = emptyList(),
                    vaultCoins = 0L,
                    vaultCap = 1L,
                    shieldActive = false,
                    shieldEndsAtMs = 0L,
                    raidWindowActive = false,
                    raidsLeft = 0,
                    nextRaidWindowMs = 0L,
                    luckyTickets = 0,
                    megaTickets = 0,
                    coachMark = null,
                    pendingReplay = null,
                ),
            )
        override val snapshot: Flow<BaseSnapshot> = snapshotFlow

        var placeHandler: suspend (String) -> ClaimPlacement = {
            ClaimPlacement.Placed(0, slottedBuddyFixture(it))
        }
        var collectedCoins = 0L
        var accrueCalls = 0
        val acknowledgedReplays = mutableListOf<String>()
        val consumedTickets = mutableListOf<TicketType>()

        override suspend fun placeClaimedBuddy(buddyId: String): ClaimPlacement = placeHandler(buddyId)

        override suspend fun collectVault(): Long = collectedCoins

        override suspend fun accrueVault() {
            accrueCalls++
        }

        override suspend fun buySlot(index: Int): BuySlotOutcome = BuySlotOutcome.Success

        override suspend fun consumeTicket(type: TicketType): Int {
            consumedTickets.add(type)
            return 0
        }

        override suspend fun acknowledgeReplay(id: String) {
            acknowledgedReplays.add(id)
        }

        companion object {
            fun slottedBuddyFixture(uid: String): SlottedBuddyUi =
                SlottedBuddyUi(
                    uid = uid,
                    buddyId = "frogboot",
                    name = "Frogboot",
                    rarity = Rarity.COMMON,
                    imageRes = 0,
                    voiceResName = "voice_frogboot",
                    catchphrase = "Plip plop! RAIN'S HERE!",
                    level = 1,
                    seed = 1L,
                    hatId = null,
                    coinsPerSec = 1,
                    stealCooldownEndsAtMs = 0L,
                )
        }
    }
}
