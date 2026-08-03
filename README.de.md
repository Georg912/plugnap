# PlugNap

> *Ladenschluss für dein Handy.* — 🇬🇧 [English version](README.md)

**Bedtime-Modus beim Laden — für Android 15+ (gebaut für GrapheneOS auf dem Pixel).**

<p>
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1.png" width="270" alt="Hauptbildschirm">
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/2.png" width="270" alt="Einstellungen">
</p>

PlugNap schließt eine Lücke, die bisher nur das proprietäre Tasker abdeckte:
Es legt eine app-eigene `AutomaticZenRule` mit **`ZenDeviceEffects`** an und
aktiviert sie, sobald das Ladegerät angesteckt wird — inklusive der
Bildschirmeffekte, die sonst nur der System-Zeitplan schalten kann:

- **Graustufen** (`setShouldDisplayGrayscale`)
- **Always-on-Display aus** (`setShouldSuppressAmbientDisplay`)
- **Wallpaper dimmen** (`setShouldDimWallpaper`)
- **Dunkles Design** (`setShouldUseNightMode`)

Beim Abstecken (oder am Ende des Nachtfensters) wird der Modus wieder
deaktiviert.

## Funktionen

- Konfigurierbares **Nachtfenster** (Standard 21:00–07:00): einfach, eigene
  Wochenend-Zeiten oder **individuelle Zeiten pro Wochentag** (inklusive
  freier Tage) — oder Auslösen rund um die Uhr
- **Ladeart-Filter** (Netzteil / USB / kabellos), Anstecke-Verzögerung
  (0–10 min) und Abstecke-Karenz (0 s–2 min)
- **„Heute aussetzen"** in App und Benachrichtigung, **Ende beim nächsten
  Wecker** — auf Wunsch verlängert sich der Modus, wenn du länger schläfst
  (mit harter Obergrenze) — Quick-Settings-Kachel, 15-Sekunden-Vorschau
- Benachrichtigungsfilter (Priorität / Nur Wecker / Totale Stille),
  App-Design (System/Hell/Dunkel), ausblendbare Status-Benachrichtigung

## Funktionsweise

- Das Nachtfenster wird über **exakte Alarme** begrenzt. Nur innerhalb des
  Fensters läuft ein kleiner Foreground-Service, der auf
  `ACTION_POWER_CONNECTED`/`DISCONNECTED` lauscht (diese Broadcasts sind
  seit Android 8 nicht mehr im Manifest registrierbar).
- Ladegerät an → Zen-Regel aktiv (DND + Effekte). Ladegerät ab → Regel aus.
- Die Regel erscheint als regulärer **Modus** in den Systemeinstellungen
  („Bitte nicht stören"/Modi) und kann dort auch manuell geschaltet und
  feinjustiert werden.
- Alles ist ereignisgesteuert — kein Polling, kein messbarer Akkuverbrauch,
  und die App hat **keinerlei Internet-Berechtigung**.

## Benötigte Berechtigungen

| Berechtigung | Wozu |
|---|---|
| „Bitte nicht stören"-Zugriff | `AutomaticZenRule` + `ZenDeviceEffects` anlegen/schalten |
| Wecker & Erinnerungen (exakte Alarme) | Fensterstart pünktlich + erlaubter Service-Start aus dem Hintergrund |
| Benachrichtigungen | dezente Status-Notification des Service (IMPORTANCE_MIN) |

Kein Internet, keine Google-Dienste, keine Analytik. GPLv3.

## Bauen

```bash
./gradlew assembleDebug        # APK: app/build/outputs/apk/debug/
./gradlew assembleRelease      # signiert, wenn Signing-Properties gesetzt sind
```

Voraussetzungen: JDK 17+ (volles JDK, nicht nur JRE), Android SDK Platform 35.

### Tests

```bash
./gradlew test    # JVM-Unit-Tests: Fensterlogik (Schedule) + Dropdown-Mapping
```

Die Tests laufen ohne Emulator und sichern u. a. die Wochenend-/Mitternachts-
Fensterberechnung und den Dropdown-Regressionsfall aus v1.4.0 ab.

### Release-Signing

Keystore und Passwort liegen bewusst **außerhalb** des Repos, in
`~/.gradle/gradle.properties`:

```properties
ZENDOCK_KEYSTORE=/pfad/zu/release.jks    # Property-Namen sind historisch
ZENDOCK_KEYSTORE_PW=…
ZENDOCK_KEY_ALIAS=zendock
```

Ohne diese Properties baut `assembleRelease` unsigniert. **Keystore sichern!**
Updates lassen sich nur mit demselben Schlüssel installieren.

## Installieren

**Empfohlen: [Obtainium](https://github.com/ImranR98/Obtainium).** Dieses
Repository als App-Quelle hinzufügen:

```
https://github.com/Georg912/plugnap
```

Obtainium installiert die signierte APK direkt von den GitHub-Releases und
meldet Updates automatisch — kein Store-Konto, kein Zwischenhändler, und
die Entwickler-Signatur bleibt über Updates hinweg erhalten.

Alternativ die APK manuell von der
[Releases-Seite](https://github.com/Georg912/plugnap/releases) laden, oder
per adb:

```bash
adb install app/build/outputs/apk/release/app-release.apk
```

Danach in der App: beide Berechtigungen erteilen, Hauptschalter an,
mit „Effekte 15 Sekunden testen" prüfen.

## Grenzen

- Mindestens Android 15 (API 35) — vorher existiert die `ZenDeviceEffects`-API nicht.
- `TYPE_BEDTIME` ist der System-Wellbeing-App vorbehalten; die Regel nutzt
  daher `TYPE_OTHER` (funktional identisch, nur andere Kategorisierung).
- Vor dem Deinstallieren einmal den Hauptschalter ausschalten — das
  entfernt den Modus aus den Systemeinstellungen. Bei Deinstallation mit
  bestehender Regel kann ein verwaister Modus-Eintrag zurückbleiben, bis
  Android ihn aufräumt.

## Lizenz

Copyright © 2026 Georg FJ Hufnagl.

Dieses Programm ist freie Software: Weitergabe und Veränderung unter den
Bedingungen der **GNU General Public License, Version 3 oder später**
(GPL-3.0-or-later), siehe [LICENSE](LICENSE). Abhängigkeiten (AndroidX,
Material Components) stehen unter Apache-2.0 und sind mit der GPLv3
kompatibel.
