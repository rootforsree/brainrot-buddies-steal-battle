package com.sreecharan.brainrot_buddies_steal_battle.screenshots

import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.sreecharan.brainrot_buddies_steal_battle.ui.components.JuiceController
import com.sreecharan.brainrot_buddies_steal_battle.data.model.BuddyCatalog
import com.sreecharan.brainrot_buddies_steal_battle.data.model.BuddyId
import com.sreecharan.brainrot_buddies_steal_battle.data.model.Rarity
import com.sreecharan.brainrot_buddies_steal_battle.ui.home.CoachMarkUi
import com.sreecharan.brainrot_buddies_steal_battle.ui.home.CoachTarget
import com.sreecharan.brainrot_buddies_steal_battle.ui.home.ConveyorBuddyUi
import com.sreecharan.brainrot_buddies_steal_battle.ui.home.HomeScreen
import com.sreecharan.brainrot_buddies_steal_battle.ui.home.HomeUiState
import com.sreecharan.brainrot_buddies_steal_battle.ui.home.RaidBellUi
import com.sreecharan.brainrot_buddies_steal_battle.ui.home.SlotUi
import com.sreecharan.brainrot_buddies_steal_battle.ui.home.SlottedBuddyUi
import com.sreecharan.brainrot_buddies_steal_battle.ui.home.VaultUi
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.AppTheme
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * Roborazzi golden tests for the Base game board. Every state is a
 * deterministic fixture (fixed seeds, fixed frame time, procedural buddy
 * art): no repository, no audio, no network.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun base_loading() {
        capture(darkTheme = false, state = HomeUiState(isLoading = true), name = "base_loading")
    }

    @Test
    fun base_empty() {
        capture(darkTheme = false, state = emptyBase(), name = "base_empty")
    }

    @Test
    fun base_populated() {
        capture(darkTheme = false, state = populatedBase(), name = "base_populated")
    }

    @Test
    fun base_vault_full() {
        val state = populatedBase().copy(
            vault = VaultUi(coins = 14_400L, capCoins = 14_400L),
        )
        capture(darkTheme = false, state = state, name = "base_vault_full")
    }

    @Test
    fun base_raid_window() {
        val state = populatedBase().copy(
            raidBell = RaidBellUi(windowActive = true, raidsLeft = 3, nextWindowMs = 0L),
        )
        capture(darkTheme = false, state = state, name = "base_raid_window")
    }

    @Test
    fun base_dark() {
        capture(darkTheme = true, state = populatedBase(), name = "base_dark")
    }

    private fun capture(darkTheme: Boolean, state: HomeUiState, name: String) {
        val frameTime = mutableLongStateOf(1_000_000L)
        val juice = JuiceController()
        composeRule.setContent {
            AppTheme(darkTheme = darkTheme, hapticsEnabled = false) {
                HomeScreen(
                    state = state,
                    frameTime = frameTime,
                    juice = juice,
                    onEvent = {},
                )
            }
        }
        composeRule.captureRoboImage(name)
    }

    private fun emptyBase(): HomeUiState = HomeUiState(
        isLoading = false,
        baseName = "Wobble Tower",
        conveyor = persistentListOf(),
        slots = emptySlots(),
        vault = VaultUi(coins = 0L, capCoins = 14_400L),
        shieldActive = false,
        coachMark = CoachMarkUi(CoachTarget.CONVEYOR, "Tap him! Quick!"),
        nowMs = 1_700_000_000_000L,
        nextSpawnMs = 1_700_000_008_000L,
    )

    private fun populatedBase(): HomeUiState = HomeUiState(
        isLoading = false,
        baseName = "Wobble Tower",
        conveyor = persistentListOf(
            conveyorBuddy("ride-1", "frogboot", "Frogboot", Rarity.COMMON, lane = 0, seed = 11L),
            conveyorBuddy("ride-2", "lava_lulu", "Lava Lulu", Rarity.RARE, lane = 2, seed = 22L),
        ),
        slots = populatedSlots(),
        vault = VaultUi(coins = 7_200L, capCoins = 14_400L),
        raidBell = RaidBellUi(windowActive = false, raidsLeft = 0, nextWindowMs = 1_700_002_000_000L),
        shieldActive = true,
        shieldEndsAtMs = 1_700_003_600_000L,
        luckyTickets = 1,
        megaTickets = 0,
        nowMs = 1_700_000_000_000L,
        nextSpawnMs = 1_700_000_008_000L,
    )

    private fun emptySlots() = (0 until 24).map { index ->
        SlotUi(
            index = index,
            unlocked = index < 4,
            buddy = null,
            shielded = false,
            unlockCost = if (index < 4) 0L else 50L,
        )
    }.toImmutableList()

    private fun populatedSlots() = (0 until 24).map { index ->
        val buddy = when (index) {
            0 -> slottedBuddy("uid-1", "frogboot", "Frogboot", Rarity.COMMON, 1, 101L)
            1 -> slottedBuddy("uid-2", "lava_lulu", "Lava Lulu", Rarity.RARE, 2, 202L)
            2 -> slottedBuddy("uid-3", "extra_butter", "Extra Butter", Rarity.EPIC, 3, 303L)
            else -> null
        }
        SlotUi(
            index = index,
            unlocked = index < 4,
            buddy = buddy,
            shielded = index == 1,
            unlockCost = if (index < 4) 0L else 50L,
        )
    }.toImmutableList()

    private fun conveyorBuddy(
        id: String,
        buddyId: String,
        name: String,
        rarity: Rarity,
        lane: Int,
        seed: Long,
    ): ConveyorBuddyUi {
        val spawnedAt = 1_699_999_995_000L // 5 s into the 15 s ride
        return ConveyorBuddyUi(
            id = id,
            buddyId = buddyId,
            name = name,
            rarity = rarity,
            imageRes = BuddyCatalog.find(BuddyId(buddyId))?.imageRes ?: 0,
            catchphrase = "Test catchphrase!",
            voiceResName = "voice_$buddyId",
            coinsPerSec = 1,
            seed = seed,
            lane = lane,
            spawnedAtMs = spawnedAt,
            expiresAtMs = spawnedAt + 15_000L,
        )
    }

    private fun slottedBuddy(
        uid: String,
        buddyId: String,
        name: String,
        rarity: Rarity,
        level: Int,
        seed: Long,
    ): SlottedBuddyUi = SlottedBuddyUi(
        uid = uid,
        buddyId = buddyId,
        name = name,
        rarity = rarity,
        imageRes = BuddyCatalog.find(BuddyId(buddyId))?.imageRes ?: 0,
        voiceResName = "voice_$buddyId",
        catchphrase = "Test catchphrase!",
        level = level,
        seed = seed,
        hatId = null,
        coinsPerSec = 1,
        stealCooldownEndsAtMs = 0L,
    )
}
