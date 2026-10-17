package com.sreecharan.brainrot_buddies_steal_battle

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sreecharan.brainrot_buddies_steal_battle.data.model.ThemeMode
import com.sreecharan.brainrot_buddies_steal_battle.navigation.AppNavHost
import com.sreecharan.brainrot_buddies_steal_battle.ui.settings.SettingsViewModel
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.AppTheme
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.LocalAppColors
import dagger.hilt.android.AndroidEntryPoint
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Guarded for minSdk 26: the setter only exists on API 29+.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
            val darkTheme = when (settingsState.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            AppTheme(darkTheme = darkTheme, hapticsEnabled = settingsState.hapticsEnabled) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = LocalAppColors.current.groupedBackground,
                ) {
                    AppNavHost()
                }
            }
        }
    }
}
