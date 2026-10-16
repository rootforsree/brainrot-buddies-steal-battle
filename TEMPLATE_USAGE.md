# TEMPLATE_USAGE.md — file-by-file checklist for spinning a new app from this template

Check every box. The template repo lives at `github.com/rootforsree/app-template`;
each daily app gets its own repo under rootforsree.

## 1. Package rename

- [ ] `settings.gradle.kts`: change `rootProject.name = "app-template"` to the new app name.
- [ ] `app/build.gradle.kts`: change `namespace = "com.sreecharan.apptemplate"`.
- [ ] `app/build.gradle.kts`: change `applicationId = "com.sreecharan.apptemplate"`.
- [ ] `app/src/main/AndroidManifest.xml`: change the `package` attribute.
- [ ] All Kotlin/Java sources: replace every `com.sreecharan.apptemplate` import with
      the new package. Do this with a project-wide search for `com.sreecharan.apptemplate`.
- [ ] If Firebase/Crashlytics is added later: replace `google-services.json` with the
      new app's file (NOT in this template yet).

## 2. App name

- [ ] `app/src/main/res/values/strings.xml`: update the `app_name` string.
- [ ] `README.md`: update the title and the description paragraph.
- [ ] `app/src/main/res/values/strings.xml`: update the `github_url` string to the new repo.

## 3. Icon

- [ ] `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`: update or replace.
- [ ] `app/src/main/res/drawable/ic_launcher_foreground.xml`: update or replace.

## 4. Theme accent

- [ ] `app/src/main/java/com/<pkg>/ui/theme/Tokens.kt`: set the `accent` color
      (or its dark-first equivalent) to the new app's brand color. One place only.

## 5. Delete the sample feature (if unwanted)

- [ ] Delete `data/local/NoteEntity.kt` and `data/local/NoteDao.kt`.
- [ ] Delete `data/model/Note.kt`.
- [ ] Delete `data/repository/NotesRepository.kt`.
- [ ] In `ui/home/HomeViewModel.kt`: delete the sample Notes logic, keep the MVVM shell.
- [ ] In `AppDatabase.kt`: remove `NoteEntity` from the entity list.
- [ ] Remove any `NoteDao`/`NotesRepository` Hilt modules in the DI package.

## 6. Add a feature

- [ ] Create a new package under `ui/<featurename>/`: `FeatureScreen.kt`, `FeatureViewModel.kt`.
- [ ] Add a `@Serializable` route object and register it in `navigation/AppNavHost.kt`.
- [ ] If it is a top-level tab, add it to `GlassTabBar`.
- [ ] Follow the MVVM pattern: `UiState` data class, `UiEvent` sealed class, state flows down, events flow up.
- [ ] If it needs storage: add a Room entity + DAO in `data/local/`, a repository in
      `data/repository/`, and register the entity in `AppDatabase`.

## 7. Signing secrets

- [ ] Nothing per app. The four `ANDROID_KEYSTORE_*` secrets are repo-level and already
      set on the template's repo; copy them to the new repo (same keystore, all apps).

## 8. First release

- [ ] Push to main: `build.yml` publishes the rolling `latest` release for phone installs.
- [ ] Cut the first version: `git tag v0.1.0 && git push origin v0.1.0`.
