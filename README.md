# App Template

The clone-for-every-app Android starter. One repo per app, all hosted on the
rootforsree GitHub account: clone this template, rename, ship. It ships with a
full opinionated stack and zero manual build steps: push to main and a
phone-installable APK appears in the rolling `latest` release.

Stack: Kotlin 2.4.20, AGP 9.4.1, Compose BOM 2026.09.00, MVVM + unidirectional
data flow, Hilt dependency injection, Room with offline-first repository
pattern, Proto DataStore for typed preferences, Apple-HIG-on-Android dark-first
UI system, and Roborazzi screenshot tests guarding every screen.

## Stack

| Layer | Choice | Version |
|---|---|---|
| Language | Kotlin | 2.4.20 |
| Build | Android Gradle Plugin | 9.4.1 |
| UI | Jetpack Compose (BOM) | 2026.09.00 |
| Architecture | MVVM + unidirectional data flow | - |
| DI | Hilt | latest stable |
| Storage | Room (offline-first repos) | latest stable |
| Preferences | Proto DataStore | latest stable |
| Design | Apple-HIG-on-Android, dark-first | - |
| Screenshot tests | Roborazzi | latest stable |
| Java | Temurin | 17 |
| Runner | ubuntu-24.04 (pinned, not latest) | - |

## One-time setup (do this first)

Android requires the same signature on every install. Generate the release
keystore ONCE, before the first phone install, and back it up in a password
manager. Losing it means the app can never be updated under the same signature.

```bash
keytool -genkeypair -v -keystore release.jks -alias release -keyalg RSA -keysize 4096 -validity 10000
```

Then add four repository secrets at repo Settings > Secrets and variables > Actions:

| Secret | Value |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | Output of `base64 -w0 release.jks` (one line) |
| `ANDROID_KEYSTORE_PASSWORD` | The keystore password |
| `ANDROID_KEY_ALIAS` | `release` (or the alias you chose) |
| `ANDROID_KEY_PASSWORD` | The key password |

Never commit the `.jks` or passwords. Keep them in `.gitignore`.

## Clone for a new app in 5 steps

1. Clone this repo to a new repo: `git clone git@github.com:rootforsree/brainrot-buddies-steal-battle.git <new-app>`.
2. Rename the package: `com.sreecharan.apptemplate` to the new package name, in
   `settings.gradle.kts` (`rootProject.name`), `app/build.gradle.kts` (`namespace`
   and `applicationId`), `AndroidManifest.xml`, and every source file.
3. Update strings in `app/src/main/res/values/strings.xml`: `app_name`,
   `github_url`. Replace the launcher icon (see TEMPLATE_USAGE.md).
4. Delete the sample Notes feature if unwanted (see TEMPLATE_USAGE.md for the
   exact file list).
5. Push to main. CI builds. Install the APK from the `latest` release.

Bookmark the phone download link:
`https://github.com/rootforsree/<repo>/releases/download/latest/app-release.apk`

## CI behavior

| Workflow | Trigger | What it does |
|---|---|---|
| `ci.yml` | push to main, PRs | `./gradlew check` (unit tests, Spotless, detekt, Android Lint) + gitleaks secret scan |
| `build.yml` | push to main | Assembles debug + release APKs, publishes the rolling `latest` pre-release. Signs release with the keystore when secrets exist, warns loudly otherwise. |
| `release.yml` | tag push `v*` | Builds signed release APK + AAB (fails loudly if secrets are missing), verifies the signature, attaches both plus a SHA-256 checksum to the GitHub Release. |
| `screenshots.yml` | push to main, PRs | Records/verifies Roborazzi screenshot goldens. |

All third-party actions are pinned with `# PIN ME` markers: run the pin script
once at scaffold time, then let Dependabot keep pins fresh.

## Cutting a release

```bash
git tag v1.0.0 && git push origin v1.0.0
```

That fires `release.yml`, which sets versionName from the tag and versionCode
from the run number, then publishes the signed release.

## Screenshot goldens

The first `screenshots.yml` run records the PNGs as artifacts. Review them,
commit them to the repo, and every later run verifies against them.

## TODOs

- Crashlytics: NOT wired. Needs a Firebase account and `google-services.json`
  before it can be added. Do not ship a production app without it.
- Baseline profile generation: see `baselineprofile/README.md` (the baseline
  profile module is scaffolded separately).
