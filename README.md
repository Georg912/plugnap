# ZenDock

**Bedtime-Modus beim Laden — für Android 15+ (gebaut für GrapheneOS auf dem Pixel).**

ZenDock schließt eine Lücke, die bisher nur das proprietäre Tasker abdeckte:
Es legt eine app-eigene `AutomaticZenRule` mit **`ZenDeviceEffects`** an und
aktiviert sie, sobald das Ladegerät angesteckt wird — inklusive der
Bildschirmeffekte, die sonst nur der System-Zeitplan schalten kann:

- **Graustufen** (`setShouldDisplayGrayscale`)
- **Always-on-Display aus** (`setShouldSuppressAmbientDisplay`)
- **Wallpaper dimmen** (`setShouldDimWallpaper`)
- **Dunkles Design** (`setShouldUseNightMode`)

Beim Abstecken (oder am Ende des Nachtfensters) wird der Modus wieder
deaktiviert.

## Funktionsweise

- Ein **Nachtfenster** (Standard 21:00–07:00) wird über exakte Alarme
  begrenzt. Nur innerhalb des Fensters läuft ein kleiner Foreground-Service,
  der auf `ACTION_POWER_CONNECTED`/`DISCONNECTED` lauscht (diese Broadcasts
  sind seit Android 8 nicht mehr im Manifest registrierbar).
- Ladegerät an → Zen-Regel aktiv (DND + Effekte). Ladegerät ab → Regel aus.
- Die Regel erscheint als eigener **Modus** in den Systemeinstellungen
  („Bitte nicht stören"/Modi) und kann dort auch manuell geschaltet und
  feinjustiert werden.
- Optional: „Zu jeder Tageszeit auslösen" (kein Zeitfenster).

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

### Release-Signing

Keystore und Passwort liegen bewusst **außerhalb** des Repos, in
`~/.gradle/gradle.properties`:

```properties
ZENDOCK_KEYSTORE=/pfad/zu/zendock-release.jks
ZENDOCK_KEYSTORE_PW=…
ZENDOCK_KEY_ALIAS=zendock
```

Ohne diese Properties baut `assembleRelease` unsigniert. **Keystore sichern!**
Updates lassen sich nur mit demselben Schlüssel installieren.

## Installieren (Pixel, GrapheneOS)

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

Danach in der App: beide Berechtigungen erteilen, Hauptschalter an,
mit „Effekte 15 Sekunden testen" prüfen.

## Grenzen

- Mindestens Android 15 (API 35) — vorher existiert die `ZenDeviceEffects`-API nicht.
- `TYPE_BEDTIME` ist der System-Wellbeing-App vorbehalten; die Regel nutzt
  daher `TYPE_OTHER` (funktional identisch, nur andere Kategorisierung).
