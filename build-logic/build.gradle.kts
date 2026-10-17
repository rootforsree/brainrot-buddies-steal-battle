plugins {
    `kotlin-dsl`
}

// No repositories block here: settings.gradle.kts centralizes all repositories
// (RepositoriesMode.PREFER_SETTINGS), so a project-level block fails the build.

// Every plugin a convention plugin may apply by id must be on this classpath.
// Versions come from the version catalog, so pins live in exactly one place.
//
// NOTE: these must be plugin *marker* coordinates
// ("<plugin-id>:<plugin-id>.gradle.plugin:<version>"), NOT libs.plugins.*
// aliases. A plugins-block alias is a PluginDependency (id + version), which
// is not valid dependency notation and fails configuration with
// "Cannot convert the provided notation to an object of type Dependency".
dependencies {
    implementation("com.android.application:com.android.application.gradle.plugin:${libs.versions.agp.get()}")
    implementation("com.android.library:com.android.library.gradle.plugin:${libs.versions.agp.get()}")
    implementation("com.android.test:com.android.test.gradle.plugin:${libs.versions.agp.get()}")
    implementation("org.jetbrains.kotlin.plugin.compose:org.jetbrains.kotlin.plugin.compose.gradle.plugin:${libs.versions.kotlin.get()}")
    implementation("com.google.dagger.hilt.android:com.google.dagger.hilt.android.gradle.plugin:${libs.versions.hilt.get()}")
    implementation("com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:${libs.versions.ksp.get()}")
    implementation("com.google.protobuf:com.google.protobuf.gradle.plugin:${libs.versions.protobufPlugin.get()}")
    implementation("io.github.takahirom.roborazzi:io.github.takahirom.roborazzi.gradle.plugin:${libs.versions.roborazzi.get()}")
    implementation("com.diffplug.spotless:com.diffplug.spotless.gradle.plugin:${libs.versions.spotless.get()}")
    implementation("io.gitlab.arturbosch.detekt:io.gitlab.arturbosch.detekt.gradle.plugin:${libs.versions.detekt.get()}")
    implementation("androidx.baselineprofile:androidx.baselineprofile.gradle.plugin:${libs.versions.baselineprofile.get()}")
    implementation("de.mannodermaus.android-junit:de.mannodermaus.android-junit.gradle.plugin:${libs.versions.androidJunit5.get()}")
}
