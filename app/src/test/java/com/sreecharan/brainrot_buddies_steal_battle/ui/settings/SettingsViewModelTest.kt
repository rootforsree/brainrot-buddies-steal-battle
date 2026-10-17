package com.sreecharan.brainrot_buddies_steal_battle.ui.settings

import android.app.Activity
import android.content.Context
import app.cash.turbine.test
import com.sreecharan.brainrot_buddies_steal_battle.billing.BillingEvent
import com.sreecharan.brainrot_buddies_steal_battle.billing.BillingManager
import com.sreecharan.brainrot_buddies_steal_battle.billing.BillingProducts
import com.sreecharan.brainrot_buddies_steal_battle.billing.Entitlements
import com.sreecharan.brainrot_buddies_steal_battle.data.datastore.AppSettings
import com.sreecharan.brainrot_buddies_steal_battle.data.datastore.SettingsRepository
import com.sreecharan.brainrot_buddies_steal_battle.data.model.ThemeMode
import com.sreecharan.brainrot_buddies_steal_battle.fakes.FakeGameRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeGameRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var billingManager: BillingManager
    private lateinit var billingEvents: MutableSharedFlow<BillingEvent>
    private lateinit var context: Context
    private val activity: Activity = mockk()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeGameRepository()
        settingsRepository =
            mockk {
                every { settings } returns
                    MutableStateFlow(AppSettings(themeMode = ThemeMode.SYSTEM, hapticsEnabled = true))
                coEvery { setThemeMode(any()) } returns Unit
                coEvery { setHapticsEnabled(any()) } returns Unit
            }
        billingEvents = MutableSharedFlow()
        billingManager =
            mockk {
                every { entitlements } returns MutableStateFlow(Entitlements())
                every { events } returns billingEvents
                every { startPurchase(any(), any()) } returns Unit
            }
        context =
            mockk {
                every { filesDir } returns File(System.getProperty("java.io.tmpdir")!!)
            }
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() =
        SettingsViewModel(
            settingsRepository,
            repository,
            billingManager,
            context,
        )

    @Test
    fun `emits audio volumes and benny hints from the repository`() =
        runTest(testDispatcher) {
            repository.musicVolumeState.value = 60
            repository.sfxVolumeState.value = 70
            repository.voiceVolumeState.value = 80
            repository.bennyHintsState.value = false
            val vm = viewModel()

            vm.uiState.test {
                awaitItem() // loading
                val loaded = awaitItem()
                assertEquals(60, loaded.musicVolume)
                assertEquals(70, loaded.sfxVolume)
                assertEquals(80, loaded.voiceVolume)
                assertFalse(loaded.bennyHints)
                assertFalse(loaded.adsRemoved)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `changing volumes writes through`() =
        runTest(testDispatcher) {
            val vm = viewModel()

            vm.onMusicVolumeChanged(42)
            vm.onSfxVolumeChanged(43)
            vm.onVoiceVolumeChanged(44)
            vm.onBennyHintsToggled(false)
            advanceUntilIdle()

            assertEquals(42, repository.musicVolumeState.value)
            assertEquals(43, repository.sfxVolumeState.value)
            assertEquals(44, repository.voiceVolumeState.value)
            assertFalse(repository.bennyHintsState.value)
        }

    @Test
    fun `theme and haptics write through to the template repository`() =
        runTest(testDispatcher) {
            val vm = viewModel()

            vm.onThemeModeSelected(ThemeMode.DARK)
            vm.onHapticsToggled(false)
            advanceUntilIdle()

            coVerify { settingsRepository.setThemeMode(ThemeMode.DARK) }
            coVerify { settingsRepository.setHapticsEnabled(false) }
        }

    @Test
    fun `remove ads purchase calls startPurchase and the event follows`() =
        runTest(testDispatcher) {
            val vm = viewModel()

            vm.events.test {
                advanceUntilIdle()
                vm.onRemoveAdsConfirmed(activity)
                verify { billingManager.startPurchase(activity, BillingProducts.REMOVE_ADS) }
                billingEvents.emit(BillingEvent.PurchaseSucceeded(BillingProducts.REMOVE_ADS))
                assertEquals(SettingsEvent.RemoveAdsPurchased, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `failed remove ads purchase emits BillingFailed`() =
        runTest(testDispatcher) {
            val vm = viewModel()

            vm.events.test {
                advanceUntilIdle()
                billingEvents.emit(BillingEvent.PurchaseFailed(BillingProducts.REMOVE_ADS))
                assertEquals(SettingsEvent.BillingFailed, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `delete data clears the repository and emits DataDeleted`() =
        runTest(testDispatcher) {
            repository.vaultState.value =
                com.sreecharan.brainrot_buddies_steal_battle.data.local.VaultEntity(
                    coins = 999L,
                )
            repository.baseNameState.value = "Test Base"
            val vm = viewModel()

            vm.events.test {
                advanceUntilIdle()
                vm.onDeleteDataConfirmed()
                advanceUntilIdle()
                assertEquals(SettingsEvent.DataDeleted, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertTrue("clearAllData" in repository.calls)
            assertEquals(0L, repository.vaultState.value.coins)
            assertEquals("", repository.baseNameState.value)
        }

    @Test
    fun `ads removed entitlement surfaces in state`() =
        runTest(testDispatcher) {
            billingManager =
                mockk {
                    every { entitlements } returns MutableStateFlow(Entitlements(adsRemoved = true))
                    every { events } returns billingEvents
                    every { startPurchase(any(), any()) } returns Unit
                }
            val vm = viewModel()

            vm.uiState.test {
                awaitItem() // loading
                assertTrue(awaitItem().adsRemoved)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
