import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

// The type-safe `libs` accessor is not generated for precompiled script
// plugins, so look the catalog up explicitly. At apply time this resolves
// against the target project, whose `libs` catalog is the root TOML.
val libs = the<VersionCatalogsExtension>().named("libs")

plugins {
    // Versionless: the plugin marker is on build-logic's classpath
    // (see build-logic/build.gradle.kts), so the id resolves from there.
    // alias(libs.plugins.*) does NOT work here: the version catalog is not
    // in scope inside a precompiled script plugin's plugins {} block.
    id("com.android.application")
}

// AGP 9 rule: org.jetbrains.kotlin.android is NEVER applied. AGP 9 builds
// Kotlin itself and registers the Kotlin extension below on its own.
extensions.configure<ApplicationExtension> {
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
    }

    testOptions {
        unitTests {
            // Roborazzi needs Android resources on the unit-test classpath.
            isIncludeAndroidResources = true
        }
    }
}

// JUnit Platform for all unit-test tasks (Roborazzi runs on it).
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

extensions.configure<JavaPluginExtension> {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

extensions.configure<KotlinAndroidProjectExtension> {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    add("coreLibraryDesugaring", libs.findLibrary("desugar").get())
}
