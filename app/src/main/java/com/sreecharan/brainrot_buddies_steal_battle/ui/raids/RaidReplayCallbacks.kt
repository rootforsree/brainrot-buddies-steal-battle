package com.sreecharan.brainrot_buddies_steal_battle.ui.raids

/**
 * Staged 5s auto-replay callbacks (§2 V1.1): Builder B's juice seams where
 * the cinematic player plugs in. They default to no-ops so this screen
 * works before Builder B lands.
 */
data class RaidReplayCallbacks(
    val onPlayStealReplay: (buddyName: String, attackerName: String) -> Unit = {},
    val onPlayRevengeReplay: (buddyName: String, attackerName: String) -> Unit = {},
    val onPlayStealFail: () -> Unit = {},
)
