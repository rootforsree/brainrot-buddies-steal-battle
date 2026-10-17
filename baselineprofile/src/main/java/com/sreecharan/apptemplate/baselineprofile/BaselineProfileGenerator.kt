package com.sreecharan.brainrot_buddies_steal_battle.baselineprofile

import androidx.benchmark.macro.BaselineProfileRule
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test

/**
 * Generates the baseline profile for :app. Drives a cold start of
 * MainActivity (the launcher activity) so the startup profile covers the
 * real first-frame code paths.
 *
 * Run on a rooted device or emulator, then the androidx.baselineprofile
 * plugin merges the result into :app at build time (see README.md).
 */
@LargeTest
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generateStartupProfile() {
        baselineProfileRule.collect(
            packageName = "com.sreecharan.brainrot_buddies_steal_battle",
            includeInStartupProfile = true,
        ) {
            pressHome()
            // Starts the default launcher activity (MainActivity) and waits
            // for the first frame.
            startActivityAndWait()
        }
    }
}
