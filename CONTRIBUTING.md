# Contributing

Thanks for your interest! / Danke für dein Interesse!

- **Bugs & ideas:** please file a GitHub issue — including device, Android
  version and (for crashes) a logcat excerpt (`adb logcat -s PlugNap:*`).
- **Pull requests:** welcome! Please open an issue first so we avoid
  duplicate work. One PR per logical change.
- **Code style:** Kotlin, as in the existing code — comments explain the
  *why*, not the *what*. No new dependencies without a good reason; the app
  stays free of internet permission, trackers and Google services.
- **Building:** `./gradlew assembleDebug` — needs JDK 17+ and Android SDK
  Platform 35. `./gradlew test` runs the JVM unit tests. Details in the
  README.
- **Language:** code, commits and issues in English; the app UI and store
  metadata are also maintained in German.
- **License:** by contributing you agree that your contribution is released
  under GPL-3.0-or-later.

## Translations

New languages are very welcome — the app is structured for it:

1. App UI strings: `app/src/main/res/values/strings.xml` — copy it to a new
   `values-<language-code>/strings.xml` (e.g. `values-fr` for French) and
   translate the `<string>`/`<string-array>` values. Don't translate the
   `name="…"` attributes or `%s`/`%d` placeholders.
2. Store listing: `fastlane/metadata/android/en-US/` — copy the folder to
   `fastlane/metadata/android/<language-code>/` (F-Droid/IzzyOnDroid locale
   codes, e.g. `fr-FR`) and translate `title.txt`, `short_description.txt`,
   `full_description.txt`. Screenshots and `changelogs/` are optional to
   translate — falling back to `en-US` is fine.
3. Open a pull request with both. If you only have time for one, the app
   UI (1) matters more than the store listing (2).

CI (`.github/workflows/ci.yml`) builds and lints every PR, so a missing
string resource or malformed XML is caught automatically. A Weblate
instance may be set up later if there's enough translator interest — for
now, PRs are the way in.
