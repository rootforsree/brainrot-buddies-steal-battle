package com.sreecharan.brainrot_buddies_steal_battle.ui.home

import com.sreecharan.brainrot_buddies_steal_battle.data.local.BaseSlotEntity
import com.sreecharan.brainrot_buddies_steal_battle.data.local.OwnedBuddyEntity
import com.sreecharan.brainrot_buddies_steal_battle.data.local.RaidRecordEntity
import com.sreecharan.brainrot_buddies_steal_battle.data.local.ShieldEntity
import com.sreecharan.brainrot_buddies_steal_battle.data.local.TicketEntity
import com.sreecharan.brainrot_buddies_steal_battle.data.local.VaultEntity
import com.sreecharan.brainrot_buddies_steal_battle.data.model.BuddyCatalog
import com.sreecharan.brainrot_buddies_steal_battle.data.model.BuddyId
import com.sreecharan.brainrot_buddies_steal_battle.data.model.Rarity
import com.sreecharan.brainrot_buddies_steal_battle.data.model.Result
import com.sreecharan.brainrot_buddies_steal_battle.data.model.TicketType
import com.sreecharan.brainrot_buddies_steal_battle.data.repository.GameRepository
import com.sreecharan.brainrot_buddies_steal_battle.di.IoDispatcher
import com.sreecharan.brainrot_buddies_steal_battle.game.engine.GameEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * The production [BaseGameData]: maps Builder A's [GameRepository] flows and
 * [GameEngine] pure logic onto the base board's data seam. This is the only
 * file in `ui/home` that knows the repository's shape; the ViewModel and its
 * tests stay behind the seam.
 *
 * Read-modify-write operations (claim, collect, buy, accrue) run under one
 * mutex so concurrent ticks and taps cannot interleave vault updates.
 */
@Singleton
class GameRepositoryAdapter
    @Inject
    constructor(
        private val repository: GameRepository,
        private val engine: GameEngine,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : BaseGameData {
        private val mutex = Mutex()
        private val seenReplayIds = mutableSetOf<String>()
        private val sessionStartMs = System.currentTimeMillis()

        private data class SnapshotPartA(
            val buddies: Result<List<OwnedBuddyEntity>>,
            val slots: Result<List<BaseSlotEntity>>,
            val vault: Result<VaultEntity>,
            val tickets: Result<List<TicketEntity>>,
            val shield: Result<ShieldEntity>,
            val baseName: Result<String>,
        )

        private data class SnapshotPartB(
            val onboardingDay: Result<Int>,
            val tutorialDone: Result<Boolean>,
            val raids: Result<List<RaidRecordEntity>>,
            val lastRaidWindow: Result<String>,
            val raidsUsed: Result<Int>,
        )

        private val snapshotPartA: Flow<SnapshotPartA> =
            combine(
                combine(
                    repository.observeOwnedBuddies(),
                    repository.observeSlots(),
                ) { buddiesR, slotsR -> buddiesR to slotsR },
                combine(
                    repository.observeVault(),
                    repository.observeTickets(),
                ) { vaultR, ticketsR -> vaultR to ticketsR },
                combine(
                    repository.observeShield(),
                    repository.observeBaseName(),
                ) { shieldR, nameR -> shieldR to nameR },
            ) { first, second, third ->
                SnapshotPartA(
                    buddies = first.first,
                    slots = first.second,
                    vault = second.first,
                    tickets = second.second,
                    shield = third.first,
                    baseName = third.second,
                )
            }

        private val snapshotPartB: Flow<SnapshotPartB> =
            combine(
                combine(
                    repository.observeOnboardingDay(),
                    repository.observeTutorialDone(),
                ) { dayR, tutorialR -> dayR to tutorialR },
                combine(
                    repository.observeRaidRecords(),
                    repository.observeLastRaidWindow(),
                ) { raidsR, lastWindowR -> raidsR to lastWindowR },
                repository.observeRaidsUsed(),
            ) { first, second, raidsUsedR ->
                SnapshotPartB(
                    onboardingDay = first.first,
                    tutorialDone = first.second,
                    raids = second.first,
                    lastRaidWindow = second.second,
                    raidsUsed = raidsUsedR,
                )
            }

        override val snapshot: Flow<BaseSnapshot> =
            combine(snapshotPartA, snapshotPartB) { a, b ->
                buildSnapshot(
                    SnapshotInputs(
                        buddies = a.buddies.getOrNull().orEmpty(),
                        slots = a.slots.getOrNull().orEmpty(),
                        vault = a.vault.getOrNull() ?: VaultEntity(),
                        tickets = a.tickets.getOrNull().orEmpty(),
                        shield = a.shield.getOrNull(),
                        baseName = a.baseName.getOrNull().orEmpty(),
                        onboardingDay = b.onboardingDay.getOrNull() ?: 1,
                        tutorialDone = b.tutorialDone.getOrNull() == true,
                        raids = b.raids.getOrNull().orEmpty(),
                        lastRaidWindow = b.lastRaidWindow.getOrNull().orEmpty(),
                        raidsUsed = b.raidsUsed.getOrNull() ?: 0,
                    ),
                )
            }.flowOn(ioDispatcher)

        override suspend fun placeClaimedBuddy(buddyId: String): ClaimPlacement =
            mutex.withLock {
                val now = System.currentTimeMillis()
                val slots = repository.observeSlots().firstSuccess().orEmpty()
                val index =
                    (0 until SLOT_COUNT).firstOrNull { i ->
                        val entity = slots.find { it.slotIndex == i }
                        val unlocked = entity?.unlocked ?: (i < START_SLOTS)
                        unlocked && entity?.ownedBuddyUid == null
                    } ?: return ClaimPlacement.BaseFull
                val uid = UUID.randomUUID().toString()
                val entity = OwnedBuddyEntity(uid = uid, buddyId = buddyId, level = 1, acquiredAt = now)
                repository.upsertOwnedBuddy(entity)
                repository.setSlotBuddy(index, uid)
                val buddy = entity.toSlotted()
                ClaimPlacement.Placed(index, buddy)
            }

        override suspend fun collectVault(): Long =
            mutex.withLock {
                val now = System.currentTimeMillis()
                val vault = repository.observeVault().firstSuccess() ?: return 0L
                val collected = vault.coins
                if (collected > 0) {
                    repository.upsertVault(vault.copy(coins = 0L, lastTickAt = now))
                }
                collected
            }

        override suspend fun accrueVault(): Unit =
            mutex.withLock {
                val now = System.currentTimeMillis()
                val vault = repository.observeVault().firstSuccess() ?: return
                val buddies = repository.observeOwnedBuddies().firstSuccess().orEmpty()
                val totalCps = buddies.sumOf { totalCpsOf(it) }
                val accrued = engine.accrueVaultCoins(vault.coins, totalCps, vault.lastTickAt, now)
                if (accrued != vault.coins) {
                    repository.upsertVault(
                        vault.copy(
                            coins = accrued,
                            lifetimeCoins = vault.lifetimeCoins + (accrued - vault.coins),
                            lastTickAt = now,
                        ),
                    )
                }
            }

        override suspend fun buySlot(index: Int): BuySlotOutcome =
            mutex.withLock {
                if (index < START_SLOTS || index >= SLOT_COUNT) return BuySlotOutcome.AlreadyUnlocked
                val slots = repository.observeSlots().firstSuccess().orEmpty()
                if (slots.find { it.slotIndex == index }?.unlocked == true) {
                    return BuySlotOutcome.AlreadyUnlocked
                }
                val cost = engine.slotUnlockCost(index - START_SLOTS + 1)
                val vault = repository.observeVault().firstSuccess() ?: return BuySlotOutcome.NotEnoughCoins
                if (vault.coins < cost) return BuySlotOutcome.NotEnoughCoins
                repository.upsertVault(vault.copy(coins = vault.coins - cost))
                repository.unlockSlot(index)
                BuySlotOutcome.Success
            }

        override suspend fun consumeTicket(type: TicketType): Int =
            mutex.withLock {
                val tickets = repository.observeTickets().firstSuccess().orEmpty()
                val current = tickets.firstOrNull { it.type == type.key }?.count ?: 0
                val remaining = (current - 1).coerceAtLeast(0)
                repository.setTicketCount(type, remaining)
                remaining
            }

        override suspend fun acknowledgeReplay(id: String) {
            seenReplayIds.add(id)
        }

        // ------------------------------------------------------------------
        // Snapshot assembly
        // ------------------------------------------------------------------

        /** The eleven snapshot inputs, bundled so [buildSnapshot] takes one param. */
        private data class SnapshotInputs(
            val buddies: List<OwnedBuddyEntity>,
            val slots: List<BaseSlotEntity>,
            val vault: VaultEntity,
            val tickets: List<TicketEntity>,
            val shield: ShieldEntity?,
            val baseName: String,
            val onboardingDay: Int,
            val tutorialDone: Boolean,
            val raids: List<RaidRecordEntity>,
            val lastRaidWindow: String,
            val raidsUsed: Int,
        )

        private fun buildSnapshot(input: SnapshotInputs): BaseSnapshot {
            val now = System.currentTimeMillis()
            val byUid = input.buddies.associateBy { it.uid }
            val slotUis =
                (0 until SLOT_COUNT).map { index ->
                    val entity = input.slots.find { it.slotIndex == index }
                    val unlocked = entity?.unlocked ?: (index < START_SLOTS)
                    SlotUi(
                        index = index,
                        unlocked = unlocked,
                        buddy = entity?.ownedBuddyUid?.let { byUid[it] }?.toSlotted(),
                        shielded = (entity?.shieldUntil ?: 0L) > now,
                        unlockCost = if (unlocked) 0L else engine.slotUnlockCost(index - START_SLOTS + 1),
                    )
                }
            val totalCps = input.buddies.sumOf { totalCpsOf(it) }
            val windowBucket = engine.raidWindowBucket(engine.currentRaidWindow())
            val usedThisWindow = if (input.lastRaidWindow == windowBucket) input.raidsUsed else 0
            return BaseSnapshot(
                baseName = input.baseName,
                slots = slotUis,
                vaultCoins = input.vault.coins,
                vaultCap = engine.vaultCapCoins(totalCps),
                shieldActive = (input.shield?.baseShieldUntil ?: 0L) > now,
                shieldEndsAtMs = input.shield?.baseShieldUntil ?: 0L,
                raidWindowActive = true,
                raidsLeft = (RAIDS_PER_WINDOW - usedThisWindow).coerceAtLeast(0),
                nextRaidWindowMs = now + engine.millisUntilNextRaidWindow(),
                luckyTickets = input.tickets.firstOrNull { it.type == TicketType.LUCKY.key }?.count ?: 0,
                megaTickets = input.tickets.firstOrNull { it.type == TicketType.MEGA.key }?.count ?: 0,
                coachMark =
                    if (input.onboardingDay <= 1 && !input.tutorialDone) {
                        CoachMarkUi(CoachTarget.CONVEYOR, COACH_TEXT)
                    } else {
                        null
                    },
                pendingReplay = newestUnseenReplay(input.raids),
            )
        }

        private fun newestUnseenReplay(raids: List<RaidRecordEntity>): StagedReplayUi? {
            val record =
                raids
                    .filter { it.direction == RAID_DIRECTION_OUT && it.createdAt >= sessionStartMs }
                    .filter { it.raidId !in seenReplayIds }
                    .maxByOrNull { it.createdAt }
                    ?: return null
            val def = BuddyCatalog.find(BuddyId(record.buddyId))
            val buddyName = def?.name ?: "Buddy"
            return StagedReplayUi(
                id = record.raidId,
                kind = ReplayKind.STEAL,
                buddyId = record.buddyId,
                buddyImageRes = def?.imageRes ?: 0,
                buddyName = buddyName,
                buddySeed = record.buddyId.hashCode().toLong(),
                otherBaseName = record.otherBaseName,
                caption = "Sneaky Snout took $buddyName from ${record.otherBaseName}!",
                startUptimeMs = 0L,
            )
        }

        private fun OwnedBuddyEntity.toSlotted(): SlottedBuddyUi {
            val def = BuddyCatalog.find(BuddyId(buddyId))
            return SlottedBuddyUi(
                uid = uid,
                buddyId = buddyId,
                name = def?.name ?: "Buddy",
                rarity = def?.rarity ?: Rarity.COMMON,
                imageRes = def?.imageRes ?: 0,
                voiceResName = def?.voiceFile ?: "",
                catchphrase = def?.catchphrase ?: "",
                level = level,
                seed = uid.hashCode().toLong(),
                hatId = hatId,
                coinsPerSec = engine.buddyCoinsPerSec(def?.baseCoinsPerSec ?: 0, level).roundToInt(),
                stealCooldownEndsAtMs = stealCooldownUntil,
            )
        }

        private fun totalCpsOf(buddy: OwnedBuddyEntity): Double {
            val def = BuddyCatalog.find(BuddyId(buddy.buddyId))
            return engine.buddyCoinsPerSec(def?.baseCoinsPerSec ?: 0, buddy.level)
        }

        companion object {
            const val SLOT_COUNT = 24
            const val START_SLOTS = 4

            /** Contract §2 V1.4: three raids per 6-hour window. */
            const val RAIDS_PER_WINDOW = 3
            private const val RAID_DIRECTION_OUT = "out"

            /** Contract §11: <= 8 words, ends in an immediate action. */
            private const val COACH_TEXT = "Tap him! Quick!"
        }
    }

/** Nullable unwrap for the data layer's sealed [Result]. */
private fun <T> Result<T>.getOrNull(): T? = (this as? Result.Success<T>)?.data

/** First successful emission of a [Result] flow, or null when the flow is empty. */
private suspend fun <T> Flow<Result<T>>.firstSuccess(): T? {
    val result = first { it is Result.Success }
    @Suppress("UNCHECKED_CAST")
    return (result as Result.Success<T>).data
}
