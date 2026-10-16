package com.sreecharan.brainrot_buddies_steal_battle.ui.album

import app.cash.turbine.test
import com.sreecharan.brainrot_buddies_steal_battle.core.audio.SoundManager
import com.sreecharan.brainrot_buddies_steal_battle.data.local.OwnedBuddyEntity
import com.sreecharan.brainrot_buddies_steal_battle.data.local.VaultEntity
import com.sreecharan.brainrot_buddies_steal_battle.data.model.BuddyCatalog
import com.sreecharan.brainrot_buddies_steal_battle.data.model.Rarity
import com.sreecharan.brainrot_buddies_steal_battle.fakes.FakeGamePrefs
import com.sreecharan.brainrot_buddies_steal_battle.fakes.FakeGameRepository
import com.sreecharan.brainrot_buddies_steal_battle.game.engine.GameEngine
import io.mockk.mockk
import io.mockk.verify
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
class AlbumViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeGameRepository
    private lateinit var gamePrefs: FakeGamePrefs
    private lateinit var soundManager: SoundManager
    private val engine = GameEngine()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeGameRepository()
        gamePrefs = FakeGamePrefs()
        soundManager = mockk(relaxed = true)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = AlbumViewModel(repository, gamePrefs, engine, soundManager)

    private fun ownAll(rarity: Rarity) {
        repository.buddiesState.value = BuddyCatalog.all
            .filter { it.rarity == rarity }
            .mapIndexed { index, def ->
                OwnedBuddyEntity(uid = "u$index", buddyId = def.id.value, level = 1)
            }
    }

    @Test
    fun `stickers split by rarity with caught flags`() = runTest(testDispatcher) {
        repository.buddiesState.value = listOf(
            OwnedBuddyEntity(uid = "u1", buddyId = "frogboot", level = 1),
        )
        val vm = viewModel()

        vm.uiState.test {
            awaitItem() // loading
            val loaded = awaitItem()
            assertFalse(loaded.isLoading)
            val frogboot = loaded.commons.first { it.buddyId == "frogboot" }
            assertTrue(frogboot.caught)
            assertFalse(loaded.commons.first { it.buddyId != "frogboot" }.caught)
            assertTrue(loaded.rares.all { !it.caught })
            assertTrue(loaded.epics.all { !it.caught })
            assertEquals(10, loaded.comingSoon.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `complete tier is claimable and claiming grants the engine reward`() =
        runTest(testDispatcher) {
            ownAll(Rarity.COMMON)
            repository.vaultState.value = VaultEntity(coins = 0L)
            val vm = viewModel()

            vm.uiState.test {
                awaitItem() // loading
                val loaded = awaitItem()
                val bonus = loaded.bonuses.first { it.rarity == Rarity.COMMON }
                assertTrue(bonus.claimable)
                assertFalse(bonus.claimed)
                assertEquals(engine.albumTierReward(Rarity.COMMON), bonus.amount)
                cancelAndIgnoreRemainingEvents()
            }

            vm.events.test {
                advanceUntilIdle()
                vm.onClaimBonus(Rarity.COMMON)
                advanceUntilIdle()
                val event = awaitItem()
                assertTrue(event is AlbumEvent.BonusClaimed)
                assertEquals(engine.albumTierReward(Rarity.COMMON), event.amount)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(engine.albumTierReward(Rarity.COMMON), repository.vaultState.value.coins)
            assertTrue(Rarity.COMMON.name in gamePrefs.claimedAlbumBonusesState.value)
        }

    @Test
    fun `incomplete tier is not claimable`() = runTest(testDispatcher) {
        repository.buddiesState.value = listOf(
            OwnedBuddyEntity(uid = "u1", buddyId = "frogboot", level = 1),
        )
        val vm = viewModel()

        vm.uiState.test {
            awaitItem() // loading
            val loaded = awaitItem()
            val bonus = loaded.bonuses.first { it.rarity == Rarity.COMMON }
            assertFalse(bonus.claimable)
            cancelAndIgnoreRemainingEvents()
        }

        vm.events.test {
            advanceUntilIdle()
            vm.onClaimBonus(Rarity.COMMON)
            advanceUntilIdle()
            assertEquals(AlbumEvent.ClaimFailed, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(0L, repository.vaultState.value.coins)
    }

    @Test
    fun `already claimed tier cannot be claimed twice`() = runTest(testDispatcher) {
        ownAll(Rarity.RARE)
        gamePrefs.claimedAlbumBonusesState.value = setOf(Rarity.RARE.name)
        val vm = viewModel()

        vm.events.test {
            advanceUntilIdle()
            vm.onClaimBonus(Rarity.RARE)
            advanceUntilIdle()
            assertEquals(AlbumEvent.ClaimFailed, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(0L, repository.vaultState.value.coins)
    }

    @Test
    fun `tapping a caught sticker plays its voice file`() = runTest(testDispatcher) {
        val def = BuddyCatalog.all.first { it.id.value == "frogboot" }
        repository.buddiesState.value = listOf(
            OwnedBuddyEntity(uid = "u1", buddyId = "frogboot", level = 1),
        )
        val vm = viewModel()

        vm.uiState.test {
            awaitItem() // loading
            val loaded = awaitItem()
            val sticker = loaded.commons.first { it.buddyId == "frogboot" }
            vm.onStickerTapped(sticker)
            cancelAndIgnoreRemainingEvents()
        }
        verify { soundManager.playVoice(def.voiceFile) }
    }

    @Test
    fun `tapping an uncaught sticker plays nothing`() {
        val vm = viewModel()
        val sticker = Sticker(
            buddyId = "frogboot",
            name = "Frogboot",
            caught = false,
            blurb = "",
            voiceFile = "voice_frogboot",
        )
        vm.onStickerTapped(sticker)
        verify(exactly = 0) { soundManager.playVoice(any()) }
    }
}
