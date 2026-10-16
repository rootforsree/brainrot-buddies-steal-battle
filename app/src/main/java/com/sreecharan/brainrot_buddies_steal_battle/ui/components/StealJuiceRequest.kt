package com.sreecharan.brainrot_buddies_steal_battle.ui.components

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State

/** Everything the steal-juice overlay needs, in one value. */
@Immutable
data class StealJuiceRequest(
    val anim: StealAnimState,
    val frameTime: State<Long>,
    val juice: JuiceController,
    val reduceMotion: Boolean,
    val onDone: () -> Unit,
)
