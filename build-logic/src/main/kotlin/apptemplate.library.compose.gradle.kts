import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension

plugins {
    // The Compose compiler plugin, versioned with Kotlin (the catalog pins
    // kotlin = 2.4.20 for both; the marker version comes from
    // build-logic/build.gradle.kts). Strong skipping is automatic on Kotlin
    // 2.4+; do NOT add an enableStrongSkippingMode flag, it is a build error.
    // Versionless id(): the marker is on build-logic's classpath.
    // alias(libs.plugins.*) does NOT work here: the version catalog is not
    // in scope inside a precompiled script plugin's plugins {} block.
    id("org.jetbrains.kotlin.plugin.compose")
}

// Order-independent: works whether the Android convention ran before or after.
pluginManager.withPlugin("com.android.application") {
    extensions.configure<ApplicationExtension> {
        buildFeatures {
            compose = true
        }
    }
}

pluginManager.withPlugin("com.android.library") {
    extensions.configure<LibraryExtension> {
        buildFeatures {
            compose = true
        }
    }
}
