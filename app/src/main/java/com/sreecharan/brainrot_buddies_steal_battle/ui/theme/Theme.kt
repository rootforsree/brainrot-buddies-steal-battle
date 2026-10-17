package com.sreecharan.brainrot_buddies_steal_battle.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.sreecharan.brainrot_buddies_steal_battle.data.model.ThemeMode

val LocalAppColors = staticCompositionLocalOf { LightAppColors }
val LocalHapticsEnabled = staticCompositionLocalOf { true }

/**
 * Frosted glass is an approximation, by design: a translucent fill plus a
 * 1dp hairline border reads as glass on a tab bar without paying for a real
 * blur pass every frame.
 */
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    hapticsEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkAppColors else LightAppColors
    // Map the fixed palette onto Material3 roles so every stock component
    // (Switch, RadioButton, Button, ripples) uses the one accent, never the
    // default purple. Roles are the contract; hexes stay in Tokens.kt.
    val colorScheme =
        if (darkTheme) {
            darkColorScheme(
                primary = colors.accent,
                onPrimary = Color.White,
                background = colors.groupedBackground,
                onBackground = colors.label,
                surface = colors.cardSurface,
                onSurface = colors.label,
                surfaceVariant = colors.groupedBackground,
                onSurfaceVariant = colors.secondaryLabel,
                error = colors.destructive,
                onError = Color.White,
                outline = colors.secondaryLabel.copy(alpha = 0.3f),
            )
        } else {
            lightColorScheme(
                primary = colors.accent,
                onPrimary = Color.White,
                background = colors.groupedBackground,
                onBackground = colors.label,
                surface = colors.cardSurface,
                onSurface = colors.label,
                surfaceVariant = colors.groupedBackground,
                onSurfaceVariant = colors.secondaryLabel,
                error = colors.destructive,
                onError = Color.White,
                outline = colors.secondaryLabel.copy(alpha = 0.3f),
            )
        }
    // Keep status and navigation bar icon tint in sync with the theme, since
    // edge-to-edge draws content under both bars.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    MaterialTheme(colorScheme = colorScheme) {
        CompositionLocalProvider(
            LocalAppColors provides colors,
            LocalHapticsEnabled provides hapticsEnabled,
        ) {
            content()
        }
    }
}

@Composable
fun ThemeMode.isDarkTheme(): Boolean =
    when (this) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

/**
 * Light tick for tabs, toggles and selections; medium for pull-to-refresh
 * and confirmations. Returns a no-op when the user disabled haptics.
 */
@Composable
fun rememberHapticTick(medium: Boolean = false): () -> Unit {
    val enabled = LocalHapticsEnabled.current
    val feedback = LocalHapticFeedback.current
    val type = if (medium) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove
    return remember(enabled, feedback, type) {
        {
            if (enabled) {
                feedback.performHapticFeedback(type)
            }
        }
    }
}
