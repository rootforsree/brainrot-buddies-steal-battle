plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.sreecharan.brainrot_buddies_steal_battle.baselineprofile"
    compileSdk = 36

    defaultConfig {
        // Macrobenchmark needs API 28+.
        minSdk = 28
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"
    // No testBuildType here: AGP 9 removed it from the test module DSL, and the
    // baselineprofile producer plugin targets the release build type by default.
}

dependencies {
    implementation(libs.benchmark.macro.junit4)
    implementation(libs.uiautomator)
}
