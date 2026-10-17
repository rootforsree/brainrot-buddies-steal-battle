package com.sreecharan.brainrot_buddies_steal_battle.p2p

import com.sreecharan.brainrot_buddies_steal_battle.data.local.VaultEntity
import com.sreecharan.brainrot_buddies_steal_battle.data.model.BuddyCatalog
import com.sreecharan.brainrot_buddies_steal_battle.data.model.BuddyId
import com.sreecharan.brainrot_buddies_steal_battle.data.model.Result
import com.sreecharan.brainrot_buddies_steal_battle.data.repository.GameRepository
import com.sreecharan.brainrot_buddies_steal_battle.game.engine.GameEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Small helpers over Builder A's GameRepository, used by the P2P raid flows.
 * The repository wraps everything in [Result]; these unwrap for the raid
 * logic, which treats a missing value as "not available" rather than an
 * error surface.
 */

/** Unwraps [Result.Success] or null. */
fun <T> Result<T>.getOrNull(): T? = (this as? Result.Success<T>)?.data

/** Unwraps [Result.Success] or [default]. */
fun <T> Result<T>.getOrDefault(default: T): T = getOrNull() ?: default

/**
 * First non-Loading emission's data, or null on error. Use for one-shot
 * reads: every observe flow starts with [Result.Loading].
 */
suspend fun <T> Flow<Result<T>>.firstData(): T? =
    first { it !is Result.Loading }.getOrNull()

/** Adds coins to the vault (pairing gifts, consolations, ceremony gifts). */
suspend fun GameRepository.grantCoins(amount: Long) {
    val vault: VaultEntity = observeVault().firstData() ?: return
    upsertVault(
        vault.copy(
            coins = vault.coins + amount,
            lifetimeCoins = vault.lifetimeCoins + amount,
        ),
    )
}

/**
 * Builds this device's live base snapshot for P2P exchange (§8). Returns
 * null when identity is not ready yet.
 */
suspend fun GameRepository.exportSnapshot(
    gameEngine: GameEngine,
    guestId: String,
    baseName: String,
    nowMs: Long,
): BaseSnapshot? {
    if (guestId.isEmpty()) return null
    val slots = observeSlots().firstData().orEmpty()
    val buddies = observeOwnedBuddies().firstData().orEmpty().associateBy { it.uid }
    val snapSlots = slots.mapNotNull { slot ->
        val buddy = slot.ownedBuddyUid?.let { buddies[it] } ?: return@mapNotNull null
        SnapshotSlot(slot = slot.slotIndex, buddyId = buddy.buddyId, level = buddy.level)
    }
    val coinsPerSec = slots.sumOf { slot ->
        val buddy = slot.ownedBuddyUid?.let { buddies[it] } ?: return@sumOf 0.0
        val def = BuddyCatalog.find(BuddyId(buddy.buddyId)) ?: return@sumOf 0.0
        gameEngine.buddyCoinsPerSec(def.baseCoinsPerSec, buddy.level)
    }
    val shieldUntil = observeShield().firstData()?.baseShieldUntil ?: 0L
    return SnapshotCodec.export(
        SnapshotCodec.SnapshotExport(
            guestId = guestId,
            baseName = baseName,
            shieldUntil = shieldUntil,
            coinsPerSec = coinsPerSec.toInt(),
            slots = snapSlots,
            exportedAt = nowMs,
        ),
    )
}
