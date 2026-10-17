import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension

plugins {
    // Versionless: the plugin marker is on build-logic's classpath.
    // alias(libs.plugins.*) does NOT work here: the version catalog is not
    // in scope inside a precompiled script plugin's plugins {} block.
    id("io.gitlab.arturbosch.detekt")
}

// detekt.yml ships as a build-logic resource so every module shares one config.
// The resource can live inside a jar on the plugin classpath (a jar: URI),
// and File(URI) throws "URI is not hierarchical" for those, so copy it to a
// real temp file first. This works whether the resource is a file or jar entry.
val detektConfigFile: java.io.File = run {
    val stream = javaClass.classLoader.getResourceAsStream("detekt.yml")
        ?: error("detekt.yml not found on the build-logic classpath")
    val out = java.io.File(System.getProperty("java.io.tmpdir"), "apptemplate-detekt.yml")
    stream.use { input -> out.outputStream().use { output -> input.copyTo(output) } }
    out
}

extensions.configure<DetektExtension> {
    config.setFrom(files(detektConfigFile))
    buildUponDefaultConfig = true
    allRules = false
}

tasks.withType<Detekt>().configureEach {
    jvmTarget = "17"
}

// Wire detekt into `./gradlew check` alongside Spotless and Android Lint.
tasks.named("check") {
    dependsOn(tasks.withType<Detekt>())
}
