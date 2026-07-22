# Contributing

Danke für dein Interesse! / Thanks for your interest!

- **Bugs & Ideen:** Bitte als GitHub-Issue melden — mit Gerät, Android-
  Version und (bei Abstürzen) einem Logcat-Auszug (`adb logcat -s ZenDock:*`).
- **Pull Requests:** Gerne! Bitte vorher ein Issue aufmachen, damit wir
  Doppelarbeit vermeiden. Ein PR pro logischer Änderung.
- **Code-Stil:** Kotlin, wie im Bestand — kommentiert wird das Warum, nicht
  das Was. Keine neuen Abhängigkeiten ohne guten Grund; die App bleibt ohne
  Internet-Berechtigung, ohne Tracker und ohne Google-Dienste.
- **Bauen:** `./gradlew assembleDebug` — braucht JDK 17+ und Android SDK
  Platform 35. Details in der README.
- **Lizenz:** Mit einem Beitrag stimmst du zu, dass er unter GPL-3.0-or-later
  veröffentlicht wird.
