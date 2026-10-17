package com.sreecharan.brainrot_buddies_steal_battle.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Spring constants (mirroring androidx.compose.animation.core values). */
private const val DampingRatioNoBouncy = 1f
private const val DampingRatioMediumBouncy = 0.5f
private const val StiffnessMedium = 1500f

/** Bouncy spring for delightful moments: tab switches, sheet entrances. */
val DelightfulSpring: AnimationSpec<Float> =
    spring(dampingRatio = DampingRatioMediumBouncy, stiffness = StiffnessMedium)

/** No-bounce spring for utilitarian state changes. */
val UtilitarianSpring: AnimationSpec<Float> =
    spring(dampingRatio = DampingRatioNoBouncy, stiffness = StiffnessMedium)

/**
 * Honors the system's Reduce Motion setting. When the user turned
 * animations off, every component falls back to the utilitarian no-bounce
 * spring instead of the delightful one.
 */
fun animationsEnabled(context: Context): Boolean {
    val scale = Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    )
    return scale != 0f
}

@Composable
fun rememberComponentSpring(delightful: Boolean = true): AnimationSpec<Float> {
    val reduceMotion = !animationsEnabled(LocalContext.current)
    return remember(delightful, reduceMotion) {
        if (reduceMotion || !delightful) UtilitarianSpring else DelightfulSpring
    }
}
