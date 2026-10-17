package com.sreecharan.brainrot_buddies_steal_battle.ui.shop

import android.app.Activity
import app.cash.turbine.test
import com.sreecharan.brainrot_buddies_steal_battle.ads.AdEvent
import com.sreecharan.brainrot_buddies_steal_battle.ads.RewardedAdManager
import com.sreecharan.brainrot_buddies_steal_battle.billing.BillingEvent
import com.sreecharan.brainrot_buddies_steal_battle.billing.BillingManager
import com.sreecharan.brainrot_buddies_steal_battle.billing.BillingProducts
import com.sreecharan.brainrot_buddies_steal_battle.billing.Entitlements
import com.sreecharan.brainrot_buddies_steal_battle.data.local.OwnedBuddyEntity
import com.sreecharan.brainrot_buddies_steal_battle.data.local.TicketEntity
import com.sreecharan.brainrot_buddies_steal_battle.data.local.VaultEntity
import com.sreecharan.brainrot_buddies_steal_battle.fakes.FakeGamePrefs
import com.sreecharan.brainrot_buddies_steal_battle.fakes.FakeGameRepository
import com.sreecharan.brainrot_buddies_steal_battle.game.engine.GameEngine
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.collections.immutable.persistentSetOf
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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ShopViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeGameRepository
    private lateinit var gamePrefs: FakeGamePrefs
    private lateinit var billingManager: BillingManager
    private lateinit var adsManager: RewardedAdManager
    private lateinit var billingEvents: MutableSharedFlow<BillingEvent>
    private lateinit var adEvents: MutableSharedFlow<AdEvent>
    private val activity: Activity = mockk()
    private val engine = GameEngine()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeGameRepository()
        gamePrefs = FakeGamePrefs()
        billingEvents = MutableSharedFlow()
        adEvents = MutableSharedFlow()
        billingManager =
            mockk {
                every { entitlements } returns MutableStateFlow(Entitlements())
                every { events } returns billingEvents
                every { startPurchase(any(), any()) } returns Unit
            }
        adsManager =
            mockk {
                every { adReady } returns MutableStateFlow(true)
                every { events } returns adEvents
                every { showAd(any(), any()) } answers {
                    secondArg<() -> Unit>().invoke()
                }
            }
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = ShopViewModel(repository, gamePrefs, engine, billingManager, adsManager)

    @Test
    fun `emits economy state from repository`() =
        runTest(testDispatcher) {
            repository.vaultState.value = VaultEntity(coins = 1_250L)
            gamePrefs.buddyBitesState.value = 12
            repository.slotsState.value = repository.freshSlots()
            repository.ticketsState.value =
                listOf(
                    TicketEntity("lucky", 2),
                    TicketEntity("mega", 1),
                )
            val vm = viewModel()

            vm.uiState.test {
                val state = awaitItem()
                assertTrue(state.isLoading)
                val loaded = awaitItem()
                assertFalse(loaded.isLoading)
                assertEquals(1_250L, loaded.coins)
                assertEquals(12, loaded.bites)
                assertEquals(4, loaded.unlockedSlots)
                assertEquals(50L, loaded.nextSlotCost)
                assertEquals(2, loaded.luckyTickets)
                assertEquals(1, loaded.megaTickets)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `next slot cost follows the engine ladder`() =
        runTest(testDispatcher) {
            repository.slotsState.value = repository.freshSlots()
            val vm = viewModel()

            vm.uiState.test {
                awaitItem() // loading
                val first = awaitItem()
                assertEquals(engine.slotUnlockCost(1), first.nextSlotCost)
                assertEquals(50L, first.nextSlotCost)

                repository.slotsState.value =
                    (0 until 24).map { index ->
                        com.sreecharan.brainrot_buddies_steal_battle.data.local.BaseSlotEntity(
                            slotIndex = index,
                            unlocked = index < 10,
                        )
                    }
                val second = awaitItem()
                assertEquals(engine.slotUnlockCost(7), second.nextSlotCost)

                repository.slotsState.value =
                    (0 until 24).map { index ->
                        com.sreecharan.brainrot_buddies_steal_battle.data.local.BaseSlotEntity(
                            slotIndex = index,
                            unlocked = true,
                        )
                    }
                val maxed = awaitItem()
                assertNull(maxed.nextSlotCost)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `buying a slot debits coins and unlocks`() =
        runTest(testDispatcher) {
            repository.vaultState.value = VaultEntity(coins = 1_000L)
            repository.slotsState.value = repository.freshSlots()
            val vm = viewModel()

            vm.events.test {
                vm.onBuySlotConfirmed()
                advanceUntilIdle()
                assertEquals(ShopEvent.SlotUnlocked, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(950L, repository.vaultState.value.coins)
            assertTrue(
                repository.slotsState.value
                    .first { it.slotIndex == 4 }
                    .unlocked,
            )
        }

    @Test
    fun `buying a slot without coins emits failure and changes nothing`() =
        runTest(testDispatcher) {
            repository.vaultState.value = VaultEntity(coins = 10L)
            repository.slotsState.value = repository.freshSlots()
            val vm = viewModel()

            vm.events.test {
                vm.onBuySlotConfirmed()
                advanceUntilIdle()
                assertEquals(
                    ShopEvent.PurchaseFailed(ShopViewModel.COINS_SHORT),
                    awaitItem(),
                )
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(10L, repository.vaultState.value.coins)
            assertFalse(
                repository.slotsState.value
                    .first { it.slotIndex == 4 }
                    .unlocked,
            )
        }

    @Test
    fun `upgrading debits coins and bites and levels up`() =
        runTest(testDispatcher) {
            repository.buddiesState.value =
                listOf(
                    OwnedBuddyEntity(uid = "u1", buddyId = "frogboot", level = 1),
                )
            repository.vaultState.value = VaultEntity(coins = 1_000L)
            gamePrefs.buddyBitesState.value = 10
            val vm = viewModel()

            vm.events.test {
                advanceUntilIdle()
                vm.onUpgradeConfirmed("u1")
                advanceUntilIdle()
                val event = awaitItem()
                assertTrue(event is ShopEvent.BuddyUpgraded)
                assertEquals("Frogboot", event.name)
                assertEquals(2, event.level)
                cancelAndIgnoreRemainingEvents()
            }
            // Common L1->L2: 100 coins + 5 bites (engine math).
            assertEquals(900L, repository.vaultState.value.coins)
            assertEquals(5, gamePrefs.buddyBitesState.value)
            assertEquals(
                2,
                repository.buddiesState.value
                    .first()
                    .level,
            )
        }

    @Test
    fun `upgrading without bites emits bites failure`() =
        runTest(testDispatcher) {
            repository.buddiesState.value =
                listOf(
                    OwnedBuddyEntity(uid = "u1", buddyId = "frogboot", level = 1),
                )
            repository.vaultState.value = VaultEntity(coins = 1_000L)
            gamePrefs.buddyBitesState.value = 0
            val vm = viewModel()

            vm.events.test {
                advanceUntilIdle()
                vm.onUpgradeConfirmed("u1")
                advanceUntilIdle()
                assertEquals(
                    ShopEvent.PurchaseFailed(ShopViewModel.BITES_SHORT),
                    awaitItem(),
                )
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(
                1,
                repository.buddiesState.value
                    .first()
                    .level,
            )
        }

    @Test
    fun `upgrading a maxed buddy emits maxed failure`() =
        runTest(testDispatcher) {
            repository.buddiesState.value =
                listOf(
                    OwnedBuddyEntity(uid = "u1", buddyId = "frogboot", level = 3),
                )
            val vm = viewModel()

            vm.events.test {
                advanceUntilIdle()
                vm.onUpgradeConfirmed("u1")
                advanceUntilIdle()
                assertEquals(
                    ShopEvent.PurchaseFailed(ShopViewModel.MAXED),
                    awaitItem(),
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `buying a mega ticket debits coins and adds a ticket`() =
        runTest(testDispatcher) {
            repository.vaultState.value = VaultEntity(coins = 3_000L)
            val vm = viewModel()

            vm.events.test {
                vm.onBuyMegaTicketConfirmed()
                advanceUntilIdle()
                assertEquals(ShopEvent.MegaTicketBought, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(500L, repository.vaultState.value.coins)
            assertEquals(
                1,
                repository.ticketsState.value
                    .first { it.type == "mega" }
                    .count,
            )
        }

    @Test
    fun `buying a base shield sets an 8 hour shield`() =
        runTest(testDispatcher) {
            repository.vaultState.value = VaultEntity(coins = 1_000L)
            val vm = viewModel()
            val before = System.currentTimeMillis()

            vm.events.test {
                vm.onBuyBaseShieldConfirmed()
                advanceUntilIdle()
                assertEquals(ShopEvent.BaseShieldBought, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(500L, repository.vaultState.value.coins)
            val until = repository.shieldState.value.baseShieldUntil
            assertTrue(until >= before + SHIELD_DURATION_MS - 5_000)
        }

    @Test
    fun `watching an ad grants coins`() =
        runTest(testDispatcher) {
            repository.vaultState.value = VaultEntity(coins = 0L)
            val vm = viewModel()

            vm.events.test {
                vm.onWatchAdConfirmed(AdRewardKind.COINS, activity)
                advanceUntilIdle()
                assertEquals(ShopEvent.AdRewardGranted, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            verify { adsManager.showAd(activity, any()) }
            assertEquals(AD_REWARD_COINS, repository.vaultState.value.coins)
        }

    @Test
    fun `watching an ad grants bites`() =
        runTest(testDispatcher) {
            gamePrefs.buddyBitesState.value = 0
            val vm = viewModel()

            vm.events.test {
                vm.onWatchAdConfirmed(AdRewardKind.BITES, activity)
                advanceUntilIdle()
                assertEquals(ShopEvent.AdRewardGranted, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(AD_REWARD_BITES, gamePrefs.buddyBitesState.value)
        }

    @Test
    fun `ad not ready emits AdNotReady`() =
        runTest(testDispatcher) {
            val vm = viewModel()

            vm.events.test {
                advanceUntilIdle()
                adEvents.emit(AdEvent.NotReady)
                assertEquals(ShopEvent.AdNotReady, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `hat purchase calls startPurchase and HatBought follows the billing event`() =
        runTest(testDispatcher) {
            val vm = viewModel()

            vm.events.test {
                advanceUntilIdle()
                vm.onHatBuyConfirmed(BillingProducts.HAT_TINY_CROWN, activity)
                verify { billingManager.startPurchase(activity, BillingProducts.HAT_TINY_CROWN) }
                billingEvents.emit(
                    BillingEvent.PurchaseSucceeded(BillingProducts.HAT_TINY_CROWN),
                )
                assertEquals(ShopEvent.HatBought, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `owned hats come from entitlements`() =
        runTest(testDispatcher) {
            repository.slotsState.value = repository.freshSlots()
            billingManager =
                mockk {
                    every { entitlements } returns
                        MutableStateFlow(
                            Entitlements(ownedHats = persistentSetOf(BillingProducts.HAT_PARTY_CONE)),
                        )
                    every { events } returns billingEvents
                    every { startPurchase(any(), any()) } returns Unit
                }
            val vm = viewModel()

            vm.uiState.test {
                awaitItem() // loading
                val loaded = awaitItem()
                val cone = loaded.hats.first { it.hatId == BillingProducts.HAT_PARTY_CONE }
                val crown = loaded.hats.first { it.hatId == BillingProducts.HAT_TINY_CROWN }
                assertTrue(cone.owned)
                assertFalse(crown.owned)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `equipping a hat writes hatId to the buddy`() =
        runTest(testDispatcher) {
            repository.buddiesState.value =
                listOf(
                    OwnedBuddyEntity(uid = "u1", buddyId = "frogboot", level = 1),
                )
            val vm = viewModel()

            vm.events.test {
                advanceUntilIdle()
                vm.onEquipHatConfirmed(BillingProducts.HAT_PARTY_CONE, "u1")
                advanceUntilIdle()
                val event = awaitItem()
                assertTrue(event is ShopEvent.HatEquipped)
                assertEquals("Frogboot", event.buddyName)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(
                BillingProducts.HAT_PARTY_CONE,
                repository.buddiesState.value
                    .first()
                    .hatId,
            )
        }

    @Test
    fun `removing a hat clears hatId`() =
        runTest(testDispatcher) {
            repository.buddiesState.value =
                listOf(
                    OwnedBuddyEntity(
                        uid = "u1",
                        buddyId = "frogboot",
                        level = 1,
                        hatId = BillingProducts.HAT_PARTY_CONE,
                    ),
                )
            val vm = viewModel()

            vm.events.test {
                advanceUntilIdle()
                vm.onRemoveHat(BillingProducts.HAT_PARTY_CONE)
                advanceUntilIdle()
                assertEquals(ShopEvent.HatRemoved, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertNull(
                repository.buddiesState.value
                    .first()
                    .hatId,
            )
        }
}
