# Baseline profiles

This module generates the baseline profile that :app merges at build time
(the `baselineProfile { variants { release { from(...) } } }` wiring in
`app/build.gradle.kts`).

## Generate

1. Connect a rooted device or an emulator (API 28+). Non-rooted physical
   devices cannot reset the app state between iterations, so their numbers
   are fiction for benchmarking; generation itself works on any device the
   test APK installs on.
2. Run the generator:
   `./gradlew :baselineprofile:generateBaselineProfile`
3. The androidx.baselineprofile plugin (1.5.0) runs the generator against the
   release build type of :app and merges the profile automatically. A copy
   also lands in `app/src/main/baseline-prof.txt`; commit it.

## When to regenerate

- After every major Compose/BOM bump.
- After changes to the startup path (Application.onCreate, DI graph,
  MainActivity, first-screen composition).

## Notes

- The generator drives a cold start of MainActivity via
  `BaselineProfileRule.collect { pressHome(); startActivityAndWait() }`.
  MainActivity must be the launcher activity.
- Keep the generated `baseline-prof.txt` committed; it is the fallback for
  builds that do not run the producer (e.g. CI without a device).
