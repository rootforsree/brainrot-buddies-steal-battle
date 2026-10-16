package com.sreecharan.brainrot_buddies_steal_battle.p2p

import com.sreecharan.brainrot_buddies_steal_battle.data.local.RaidRecordEntity
import com.sreecharan.brainrot_buddies_steal_battle.data.model.BuddyCatalog
import com.sreecharan.brainrot_buddies_steal_battle.data.model.BuddyId
import com.sreecharan.brainrot_buddies_steal_battle.data.repository.GameRepository
import com.sreecharan.brainrot_buddies_steal_battle.di.IoDispatcher
import com.sreecharan.brainrot_buddies_steal_battle.game.engine.GameEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Victim-side raid handling, per docs/contract.md §8 "live raid" semantics.
 *
 * On a [RaidRequest] this device validates (friend? shield? cooldown?
 * already raided this window? rookie shield?), removes the buddy from its
 * own base on success, replies [RaidResult], records the raid, and grants
 * the consolation gift (50% of the stolen buddy's hourly earnings, via
 * Builder A's [GameEngine]).
 *
 * On a [ShadowNotice] (the attacker's pending shadow raid, replayed on the
 * next P2P connection) this device records the raid and grants consolation.
 * The victim never silently loses a buddy: shadow heists copy the buddy to
 * the attacker, the victim keeps theirs.
 */
@Singleton
class LiveRaidResponder @Inject constructor(
    private val gameRepository: GameRepository,
    private val gameEngine: GameEngine,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    suspend fun onRaidRequest(request: RaidRequest): RaidResult = withContext(ioDispatcher) {
        val now = System.currentTimeMillis()
        val friends = gameRepository.observeFriends().firstData().orEmpty()
        val slots = gameRepository.observeSlots().firstData().orEmpty()
        val buddies = gameRepository.observeOwnedBuddies().firstData().orEmpty()
        val slotEntity = slots.firstOrNull { it.slotIndex == request.slot }
        val buddy = slotEntity?.ownedBuddyUid?.let { uid -> buddies.firstOrNull { it.uid == uid } }
        val baseShieldUntil = gameRepository.observeShield().firstData()?.baseShieldUntil ?: 0L
        val window = gameEngine.currentRaidWindow()
        val windowStartMs = RaidRules.windowStartMs(window.date, window.startHour)
        val records = gameRepository.observeRaidRecords().firstData().orEmpty()
        // Only inbound steals count: the original theft leaves an "out" record
        // here, and that must not block the victim's revenge (§8, §1).
        val alreadyRaided = records.any {
            it.direction == "in" &&
                it.otherGuestId == request.attackerGuestId &&
                it.createdAt >= windowStartMs
        }
        val onboardingDay = gameRepository.observeOnboardingDay().firstData() ?: 1

        val verdict: StealVerdict = when {
            friends.none { it.guestId == request.attackerGuestId } ->
                StealVerdict.Rejected(StealRejectReason.EMPTY)
            else -> RaidRules.validateLiveSteal(
                slotOccupied = buddy != null,
                alreadyRaidedThisWindow = alreadyRaided,
                baseShieldUntilMs = baseShieldUntil,
                slotShieldUntilMs = slotEntity?.shieldUntil ?: 0L,
                buddyCooldownUntilMs = buddy?.stealCooldownUntil ?: 0L,
                victimRookieShielded = RaidRules.isRookieShielded(onboardingDay),
                nowMs = now,
            )
        }
        val stolen = (verdict as? StealVerdict.Accepted)?.let { buddy }
        if (stolen != null) {
            acceptRaid(request, stolen.buddyId, stolen.level, stolen.uid, slotEntity?.slotIndex, now)
        } else {
            val reason = (verdict as? StealVerdict.Rejected)?.reason ?: StealRejectReason.EMPTY
            reject(request.raidId, reason)
        }
    }

    suspend fun onShadowNotice(notice: ShadowNotice) = withContext(ioDispatcher) {
        gameRepository.upsertRaidRecord(
            RaidRecordEntity(
                raidId = notice.raidId,
                direction = "in",
                otherGuestId = notice.attackerGuestId,
                otherBaseName = notice.attackerBaseName,
                buddyId = notice.buddyId,
                buddyLevel = notice.buddyLevel,
                createdAt = notice.createdAt,
                revengeUsed = false,
                revengeDeadline = notice.createdAt + RaidRules.REVENGE_WINDOW_MS,
                isShadow = true,
            ),
        )
        gameRepository.grantCoins(consolationFor(notice.buddyId, notice.buddyLevel))
    }

    private suspend fun acceptRaid(
        request: RaidRequest,
        buddyId: String,
        buddyLevel: Int,
        buddyUid: String,
        slotIndex: Int?,
        now: Long,
    ): RaidResult {
        gameRepository.removeOwnedBuddy(buddyUid)
        if (slotIndex != null) {
            gameRepository.setSlotBuddy(slotIndex, null)
        }
        gameRepository.grantCoins(consolationFor(buddyId, buddyLevel))
        gameRepository.upsertRaidRecord(
            RaidRecordEntity(
                raidId = request.raidId,
                direction = "in",
                otherGuestId = request.attackerGuestId,
                otherBaseName = request.attackerBaseName,
                buddyId = buddyId,
                buddyLevel = buddyLevel,
                createdAt = now,
                revengeUsed = false,
                revengeDeadline = now + RaidRules.REVENGE_WINDOW_MS,
                isShadow = false,
            ),
        )
        return RaidResult(
            v = 1,
            raidId = request.raidId,
            accepted = true,
            buddyId = buddyId,
            buddyLevel = buddyLevel,
        )
    }

    private fun reject(raidId: String, reason: StealRejectReason): RaidResult = RaidResult(
        v = 1,
        raidId = raidId,
        accepted = false,
        reason = reason.wire,
    )

    private fun consolationFor(buddyId: String, buddyLevel: Int): Long {
        val def = BuddyCatalog.find(BuddyId(buddyId)) ?: return 0L
        val coinsPerSec = gameEngine.buddyCoinsPerSec(def.baseCoinsPerSec, buddyLevel)
        return gameEngine.consolationCoins(coinsPerSec)
    }
}
