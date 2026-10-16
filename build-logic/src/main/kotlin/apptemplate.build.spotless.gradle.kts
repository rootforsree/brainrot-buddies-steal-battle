import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    // Versionless: the plugin marker is on build-logic's classpath.
    // alias(libs.plugins.*) does NOT work here: the version catalog is not
    // in scope inside a precompiled script plugin's plugins {} block.
    id("com.diffplug.spotless")
}

// The type-safe `libs` accessor is not generated for precompiled script
// plugins, so look the catalog up explicitly (resolves against the target
// project at apply time).
val libs = the<VersionCatalogsExtension>().named("libs")

spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**")
        // Line length (120) and indent (4) come from the root .editorconfig;
        // ktlint reads it automatically.
        ktlint(libs.findVersion("ktlint").get().requiredVersion)
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**")
        ktlint(libs.findVersion("ktlint").get().requiredVersion)
    }
}
// spotlessCheck is wired into `check` by the Spotless plugin itself.
