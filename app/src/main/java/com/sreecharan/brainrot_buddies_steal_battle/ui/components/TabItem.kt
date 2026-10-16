package com.sreecharan.brainrot_buddies_steal_battle.ui.components

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector

/** One tab in the floating frosted-glass tab bar. */
@Immutable
data class TabItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)
