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
