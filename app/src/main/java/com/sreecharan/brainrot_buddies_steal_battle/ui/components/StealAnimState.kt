package com.sreecharan.brainrot_buddies_steal_battle.ui.components

import androidx.compose.runtime.Immutable

/** Immutable snapshot of one steal attempt's juice animation. */
@Immutable
data class StealAnimState(
    val buddyImageRes: Int,
    val buddyName: String,
    val seed: Long,
    val success: Boolean,
    val startUptimeMs: Long,
)
