package com.sreecharan.brainrot_buddies_steal_battle.ui.settings

import androidx.compose.runtime.Immutable
import com.sreecharan.brainrot_buddies_steal_battle.data.model.ThemeMode

@Immutable
data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val hapticsEnabled: Boolean = true,
    val musicVolume: Int = 80,
    val sfxVolume: Int = 80,
    val voiceVolume: Int = 80,
    val bennyHints: Boolean = true,
    val adsRemoved: Boolean = false,
    val isDeleting: Boolean = false,
)
