package com.sreecharan.brainrot_buddies_steal_battle.ui.home

import androidx.compose.runtime.Immutable
import com.sreecharan.brainrot_buddies_steal_battle.data.model.Rarity
import com.sreecharan.brainrot_buddies_steal_battle.data.model.TicketType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

/**
 * Base screen (contract §7 route `Base`) UI state. One atomic snapshot,
 * updated only via `update { it.copy(...) }`.
 *
 * Rarity is Builder A's `data.model.Rarity` (§5) throughout; drawables are
 * real `R.drawable` ids from `BuddyDef.imageRes` (§6).
 */
@Immutable
data class HomeUiState(
    val isLoading: Boolean = true,
    val baseName: String = "",
    val conveyor: ImmutableList<ConveyorBuddyUi> = persistentListOf(),
    /** Always 24 entries, index = slotIndex. */
    val slots: ImmutableList<SlotUi> = persistentListOf(),
    val vault: VaultUi = VaultUi(),
    val raidBell: RaidBellUi = RaidBellUi(),
    val shieldActive: Boolean = false,
    val shieldEndsAtMs: Long = 0L,
    val luckyTickets: Int = 0,
    val megaTickets: Int = 0,
    val bites: Int = 0,
    val claimAnims: ImmutableMap<String, ClaimAnimUi> = persistentMapOf(),
    val waddleAnims: ImmutableMap<String, Long> = persistentMapOf(),
    val boingAnims: ImmutableMap<String, Long> = persistentMapOf(),
    val floatUps: ImmutableList<FloatUpUi> = persistentListOf(),
    val wiggleSlot: WiggleUi? = null,
    val trauma: TraumaUi = TraumaUi(),
    val tour: TourModeUi? = null,
    val ceremony: MysteryCeremonyUi? = null,
    val replay: StagedReplayUi? = null,
    val coachMark: CoachMarkUi? = null,
    /** Wall clock, refreshed on a 500 ms cadence: countdowns only. */
    val nowMs: Long = 0L,
    /** Wall-clock ms of the next conveyor spawn: "show the next fun thing" (§11). */
    val nextSpawnMs: Long = 0L,
)

/** One buddy riding the conveyor. [id] is the ride instance, not the buddy. */
@Immutable
data class ConveyorBuddyUi(
    val id: String,
    val buddyId: String,
    val name: String,
    val rarity: Rarity,
    val imageRes: Int,
    val catchphrase: String,
    val voiceResName: String,
    val coinsPerSec: Int,
    val seed: Long,
    val lane: Int,
    val spawnedAtMs: Long,
    val expiresAtMs: Long,
    val ticketPull: Boolean = false,
)

@Immutable
data class SlotUi(
    val index: Int,
    val unlocked: Boolean,
    val buddy: SlottedBuddyUi?,
    val shielded: Boolean,
    val unlockCost: Long,
)

@Immutable
data class SlottedBuddyUi(
    val uid: String,
    val buddyId: String,
    val name: String,
    val rarity: Rarity,
    val imageRes: Int,
    val voiceResName: String,
    val catchphrase: String,
    val level: Int,
    val seed: Long,
    val hatId: String?,
    val coinsPerSec: Int,
    val stealCooldownEndsAtMs: Long,
)

@Immutable
data class VaultUi(
    val coins: Long = 0L,
    val capCoins: Long = 1L,
) {
    val fillFraction: Float get() = (coins.toFloat() / capCoins.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
}

@Immutable
data class RaidBellUi(
    val windowActive: Boolean = false,
    val raidsLeft: Int = 0,
    /** Wall-clock ms of the next window start (or current window end). */
    val nextWindowMs: Long = 0L,
)

/**
 * In-flight claim animation, contract §10: 0-60 ms squash ack, 60-250 ms
 * spring arc, 250-600 ms landing. [targetSlotIndex] is the lowest free slot
 * at tap time; the data layer confirms the final slot on [ClaimPlacement].
 */
@Immutable
data class ClaimAnimUi(
    val instanceId: String,
    val startUptimeMs: Long,
    val rarity: Rarity,
    val imageRes: Int,
    val seed: Long,
    val fromLane: Int,
    val fromProgress: Float,
    val targetSlotIndex: Int?,
)

@Immutable
data class FloatUpUi(
    val id: Long,
    val text: String,
    val anchor: FloatUpAnchor,
    val startUptimeMs: Long,
    val durationMs: Long,
)

@Immutable
sealed interface FloatUpAnchor {
    @Immutable data class Slot(val index: Int) : FloatUpAnchor
    @Immutable data class Conveyor(val instanceId: String) : FloatUpAnchor
    @Immutable data object Vault : FloatUpAnchor
    @Immutable data object Center : FloatUpAnchor
}

@Immutable
data class WiggleUi(val slotIndex: Int, val startUptimeMs: Long)

/** Trauma impulse for the shake controller; the route applies it once per token. */
@Immutable
data class TraumaUi(val amount: Float = 0f, val token: Long = 0L)

/** Base tour mode, contract §2 V1.8: auto-camera pan, idle acts, catchphrases. */
@Immutable
data class TourModeUi(
    val spotlightSlot: Int,
    val buddyName: String,
    val caption: String,
)

/** Mystery-pull ceremony, contract §2 V1.6: drumroll, darken, slow reveal. */
@Immutable
data class MysteryCeremonyUi(
    val ticket: TicketType,
    val phase: CeremonyPhase,
    val phaseStartUptimeMs: Long,
    val buddy: ConveyorBuddyUi?,
)

@Immutable
enum class CeremonyPhase { DRUMROLL, REVEAL }

/**
 * Staged 5 s steal/revenge auto-replay, contract §2 V1.1. Triggered by
 * Builder D's raid flow via raid records (surfaced through [BaseGameData]);
 * rendered here.
 */
@Immutable
data class StagedReplayUi(
    val id: String,
    val kind: ReplayKind,
    val buddyId: String,
    val buddyImageRes: Int,
    val buddyName: String,
    val buddySeed: Long,
    val otherBaseName: String,
    val caption: String,
    val startUptimeMs: Long,
)

@Immutable
enum class ReplayKind { STEAL, REVENGE }

/** Benny coach mark: <= 8 words, ends in an immediate action (contract §11). */
@Immutable
data class CoachMarkUi(
    val target: CoachTarget,
    val text: String,
)

@Immutable
enum class CoachTarget { CONVEYOR, GRID, VAULT }
