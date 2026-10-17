package com.sreecharan.brainrot_buddies_steal_battle.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.sreecharan.brainrot_buddies_steal_battle.ui.about.AboutScreenContent // ASSUMPTION: UI worker places this in ui.about
import com.sreecharan.brainrot_buddies_steal_battle.ui.theme.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * Roborazzi golden tests for the about screen.
 * AboutScreenContent is owned by the UI worker; this test drives it with a fixed
 * version string so the goldens stay deterministic.
 * Verified against Roborazzi 1.70.0 API (pinned in libs.versions.toml) (io.github.takahirom.roborazzi).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AboutScreenShotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun about_light() {
        capture(darkTheme = false, name = "about_light")
    }

    @Test
    fun about_dark() {
        capture(darkTheme = true, name = "about_dark")
    }

    private fun capture(
        darkTheme: Boolean,
        name: String,
    ) {
        composeRule.setContent {
            AppTheme(darkTheme = darkTheme, hapticsEnabled = false) {
                AboutScreenContent(
                    appVersion = "1.0.0-dev",
                    onOpenGitHub = {},
                    onShareApp = {},
                    onRateApp = {},
                )
            }
        }
        composeRule.captureRoboImage(name)
    }
}
