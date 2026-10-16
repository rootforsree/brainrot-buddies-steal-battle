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
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
class GameRepositoryAdapter @Inject constructor(
    private val repository: GameRepository,
    private val engine: GameEngine,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : BaseGameData {

    private val mutex = Mutex()
    private val seenReplayIds = mutableSetOf<String>()
    private val sessionStartMs = System.currentTimeMillis()

    override val snapshot: Flow<BaseSnapshot> = combine(
        repository.observeOwnedBuddies(),
        repository.observeSlots(),
        repository.observeVault(),
        repository.observeTickets(),
        repository.observeShield(),
        repository.observeBaseName(),
        repository.observeOnboardingDay(),
        repository.observeTutorialDone(),
        repository.observeRaidRecords(),
        repository.observeLastRaidWindow(),
        repository.observeRaidsUsed(),
    ) { buddiesR, slotsR, vaultR, ticketsR, shieldR, nameR, dayR, tutorialR, raidsR, lastWindowR, raidsUsedR ->
        buildSnapshot(
            buddies = buddiesR.getOrNull().orEmpty(),
            slots = slotsR.getOrNull().orEmpty(),
            vault = vaultR.getOrNull() ?: VaultEntity(),
            tickets = ticketsR.getOrNull().orEmpty(),
            shield = shieldR.getOrNull(),
            baseName = nameR.getOrNull().orEmpty(),
            onboardingDay = dayR.getOrNull() ?: 1,
            tutorialDone = tutorialR.getOrNull() == true,
            raids = raidsR.getOrNull().orEmpty(),
            lastRaidWindow = lastWindowR.getOrNull().orEmpty(),
            raidsUsed = raidsUsedR.getOrNull() ?: 0,
        )
    }.flowOn(ioDispatcher)

    override suspend fun placeClaimedBuddy(buddyId: String): ClaimPlacement = mutex.withLock {
        val now = System.currentTimeMillis()
        val slots = repository.observeSlots().firstSuccess().orEmpty()
        val index = (0 until SLOT_COUNT).firstOrNull { i ->
            val entity = slots.find { it.slotIndex == i }
            val unlocked = entity?.unlocked ?: (i < START_SLOTS)
            unlocked && entity?.ownedBuddyUid == null
        } ?: return ClaimPlacement.BaseFull
        val uid = UUID.randomUUID().toString()
        repository.upsertOwnedBuddy(
            OwnedBuddyEntity(uid = uid, buddyId = buddyId, level = 1, acquiredAt = now),
        )
        repository.setSlotBuddy(index, uid)
        val buddy = toSlotted(uid, buddyId, level = 1, hatId = null, stealCooldownUntil = 0L)
        ClaimPlacement.Placed(index, buddy)
    }

    override suspend fun collectVault(): Long = mutex.withLock {
        val now = System.currentTimeMillis()
        val vault = repository.observeVault().firstSuccess() ?: return 0L
        val collected = vault.coins
        if (collected > 0) {
            repository.upsertVault(vault.copy(coins = 0L, lastTickAt = now))
        }
        collected
    }

    override suspend fun accrueVault(): Unit = mutex.withLock {
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

    override suspend fun buySlot(index: Int): BuySlotOutcome = mutex.withLock {
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

    override suspend fun consumeTicket(type: TicketType): Int = mutex.withLock {
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

    private fun buildSnapshot(
        buddies: List<OwnedBuddyEntity>,
        slots: List<BaseSlotEntity>,
        vault: VaultEntity,
        tickets: List<TicketEntity>,
        shield: ShieldEntity?,
        baseName: String,
        onboardingDay: Int,
        tutorialDone: Boolean,
        raids: List<RaidRecordEntity>,
        lastRaidWindow: String,
        raidsUsed: Int,
    ): BaseSnapshot {
        val now = System.currentTimeMillis()
        val byUid = buddies.associateBy { it.uid }
        val slotUis = (0 until SLOT_COUNT).map { index ->
            val entity = slots.find { it.slotIndex == index }
            val unlocked = entity?.unlocked ?: (index < START_SLOTS)
            SlotUi(
                index = index,
                unlocked = unlocked,
                buddy = entity?.ownedBuddyUid?.let { byUid[it] }?.toSlotted(),
                shielded = (entity?.shieldUntil ?: 0L) > now,
                unlockCost = if (unlocked) 0L else engine.slotUnlockCost(index - START_SLOTS + 1),
            )
        }
        val totalCps = buddies.sumOf { totalCpsOf(it) }
        val windowBucket = engine.raidWindowBucket(engine.currentRaidWindow())
        val usedThisWindow = if (lastRaidWindow == windowBucket) raidsUsed else 0
        return BaseSnapshot(
            baseName = baseName,
            slots = slotUis,
            vaultCoins = vault.coins,
            vaultCap = engine.vaultCapCoins(totalCps),
            shieldActive = (shield?.baseShieldUntil ?: 0L) > now,
            shieldEndsAtMs = shield?.baseShieldUntil ?: 0L,
            raidWindowActive = true,
            raidsLeft = (RAIDS_PER_WINDOW - usedThisWindow).coerceAtLeast(0),
            nextRaidWindowMs = now + engine.millisUntilNextRaidWindow(),
            luckyTickets = tickets.firstOrNull { it.type == TicketType.LUCKY.key }?.count ?: 0,
            megaTickets = tickets.firstOrNull { it.type == TicketType.MEGA.key }?.count ?: 0,
            coachMark = if (onboardingDay <= 1 && !tutorialDone) {
                CoachMarkUi(CoachTarget.CONVEYOR, COACH_TEXT)
            } else {
                null
            },
            pendingReplay = newestUnseenReplay(raids),
        )
    }

    private fun newestUnseenReplay(
        raids: List<RaidRecordEntity>,
    ): StagedReplayUi? {
        val record = raids
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

    private fun OwnedBuddyEntity.toSlotted(): SlottedBuddyUi =
        toSlotted(uid, buddyId, level, hatId, stealCooldownUntil)

    private fun toSlotted(
        uid: String,
        buddyId: String,
        level: Int,
        hatId: String?,
        stealCooldownUntil: Long,
    ): SlottedBuddyUi {
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

    private suspend fun <T> Flow<Result<T>>.firstSuccess(): T? {
        val result = first { it is Result.Success }
        @Suppress("UNCHECKED_CAST")
        return (result as Result.Success<T>).data
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
