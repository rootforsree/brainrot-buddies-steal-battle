import com.android.build.api.dsl.ApplicationExtension

plugins {
    id("apptemplate.android.application")
    id("apptemplate.library.compose")
    id("apptemplate.library.hilt")
    id("apptemplate.build.spotless")
    id("apptemplate.build.detekt")
    // KSP again for Room (idempotent; the hilt convention already applies it).
    alias(libs.plugins.ksp)
    alias(libs.plugins.protobuf)
    // Builder D: kotlinx.serialization compiler plugin (Nearby P2P payloads).
    alias(libs.plugins.serialization)
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.android.junit5)
    alias(libs.plugins.baselineprofile)
}

// Versioning: versionCode from the CI run number (strictly increasing, what
// Play requires), versionName from the release tag, local-dev defaults.
val versionCodeProp = providers.gradleProperty("versionCode").getOrElse("1").toInt()
val versionNameProp = providers.gradleProperty("versionName").getOrElse("1.0.0-dev")

extensions.configure<ApplicationExtension> {
    namespace = "com.sreecharan.brainrot_buddies_steal_battle"

    // signingConfigs lives on the Android extension: at the top level of a
    // Kotlin DSL script it is an unresolved reference.
    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("KEYSTORE_PATH")
            if (!keystorePath.isNullOrBlank()) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    defaultConfig {
        applicationId = "com.sreecharan.brainrot_buddies_steal_battle"
        targetSdk = 36
        versionCode = versionCodeProp
        versionName = versionNameProp
        // Builder D: AdMob App ID for the rewarded ad placement. CI/release
        // must supply the real ID via -PadmobAppId or the ADMOB_APP_ID env
        // var. The default is Google's official SAMPLE App ID, safe for test
        // builds only.
        val admobAppId =
            providers
                .gradleProperty("admobAppId")
                .orElse(providers.environmentVariable("ADMOB_APP_ID"))
                .getOrElse("ca-app-pub-3940256099942544~3347511713")
        manifestPlaceholders["ADMOB_APP_ID"] = admobAppId
    }

    buildTypes {
        getByName("release") {
            val hasKey = !System.getenv("KEYSTORE_PATH").isNullOrBlank()
            signingConfig =
                if (hasKey) {
                    signingConfigs.getByName("release")
                } else {
                    signingConfigs.getByName("debug")
                }
            if (!hasKey) logger.warn("No release keystore in env: signing release with debug key")
            isMinifyEnabled = true
            isShrinkResources = true
            // proguard-android-optimize.txt: the plain proguard-android.txt
            // implies -dontoptimize and AGP 9 rejects it.
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        buildConfig = true
    }
}

// Proto DataStore codegen: lite runtime, protoc version pinned in the catalog.
protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.get()}"
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                create("java") {
                    option("lite")
                }
            }
        }
    }
}

// Room schema export: every schema version is committed for migration tests.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    // hiltViewModel() lives here since androidx.hilt 1.4.0.
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.hilt.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.datastore)
    implementation(libs.datastore.preferences)
    implementation(libs.protobuf.javalite)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
    implementation(libs.collections.immutable)
    implementation(libs.serialization.json)
    // Steal Battle (Builder D): nearby P2P friend raids, Families
    // self-certified rewarded ads, Play Billing for remove-ads + hats.
    implementation(libs.play.services.nearby)
    implementation(libs.play.services.ads)
    implementation(libs.billing.ktx)
    // Sideloaded baseline profiles (bundled baseline-prof.txt) need this.
    implementation(libs.androidx.profileinstaller)
    // Consumer -> producer wiring for the :baselineprofile module. The
    // androidx.baselineprofile plugin adds this configuration to the app.
    baselineProfile(project(":baselineprofile"))
    coreLibraryDesugaring(libs.desugar)
    debugImplementation(libs.compose.ui.tooling)

    ksp(libs.hilt.compiler)
    ksp(libs.room.compiler)

    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.vintage.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.room.testing)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.robolectric)
    testImplementation(libs.compose.ui.test.junit4)
}
