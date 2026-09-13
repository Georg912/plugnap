# CLAUDE.md — PlugNap

> Project context for AI-assisted development. Machine-specific environment
> notes live outside the repo (`~/projects/CLAUDE.md` on the maintainer's
> machine); this file is committed and contributor-friendly.

## What this app is

PlugNap ("Ladenschluss") activates a bedtime mode — an `AutomaticZenRule`
with **`ZenDeviceEffects`** (grayscale, AOD off, dim wallpaper, dark theme)
plus Do Not Disturb — when the charger is connected during a configurable
night window, and deactivates it on unplug / window end / alarm clock.
Android 15+ (API 35), built for GrapheneOS on the Pixel. GPL-3.0-or-later.
No internet permission, no trackers, no Google services — keep it that way.

## Architecture (one paragraph)

Exact alarms (`AlarmScheduler`) bound the night window. Inside it, a
foreground service (`BedtimeService`, type specialUse) listens for
`ACTION_POWER_CONNECTED/DISCONNECTED` (not manifest-registrable since
Android 8). State changes go through `ZenRuleManager`, which owns exactly
one zen rule (condition URI contains the package name) and self-heals
orphaned rules. All window math lives in `Schedule` (pure JVM, tested),
parameterized via the `ScheduleParams` interface that `Prefs` implements;
the next alarm-clock time is runtime state and is passed as an optional
parameter, never stored in params. Short timers (unplug grace, plug-in
delay) are doze-safe wakeup alarms (`ACTION_REEVALUATE`), never
`Handler.postDelayed`.

## File map (app/src/main/java/io/github/georg912/plugnap/)

| File | Role |
|---|---|
| `Schedule.kt` | Window math: `ScheduleMode` (SIMPLE/WEEKEND/PER_DAY), `AlarmEndMode` (OFF/SHORTEN/EXTEND/FOLLOW, capped), midnight rollover, day off = start==end |
| `Prefs.kt` | Settings + migrations; device-bound state (ruleId) in separate, backup-excluded prefs file |
| `ZenRuleManager.kt` | Create/update/toggle the zen rule; orphan cleanup scoped to own CONDITION_URI |
| `BedtimeService.kt` | FGS watcher; charger-type filter; notification (normal + hidden channel) |
| `AlarmScheduler.kt` | Window start/end + re-evaluate alarms (`ELAPSED_REALTIME_WAKEUP`), `nextAlarmClockTime()` |
| `AlarmReceiver.kt` | WINDOW_START/END, REEVALUATE, SKIP_TONIGHT |
| `BootReceiver.kt` | Boot/timezone/update recovery (action allow-list — it is exported) |
| `ZenTileService.kt` | Quick-settings tile |
| `MainActivity.kt` | Single-screen settings UI; per-day rows built programmatically |
| `NoFilterAdapter.kt` | Dropdown adapter — see regression rules below |

Tests: `app/src/test/…/ScheduleTest.kt` (window/alarm math, 34 cases),
`DropdownMappingTest.kt`, `PrefsJsonTest.kt` (export/import round-trip) —
pure JVM, no emulator, run with `./gradlew test`.

`app/src/androidTest/…/MainActivityStateTest.kt` — real Activity-lifecycle
regression tests (recreate() reverting switch/toggle state; see rule 8
below) that no unit test can catch. Needs a device/emulator:
`./gradlew connectedDebugAndroidTest`.

`app/src/androidTest/…/PrefsExportImportRoundTripTest.kt` — "exact-restore"
test: sets every exported field away from its default, exports, wipes real
SharedPreferences, imports, asserts everything is back — and that
device-bound state (ruleId/ruleActive) and skipUntil stay excluded. Catches
what `PrefsJsonTest.kt` structurally can't: that test only exercises the
pure JSON layer, never a real Context/SharedPreferences round trip.

## Commands

```bash
./gradlew test                       # JVM unit tests (must stay green)
./gradlew lintRelease                # only cosmetic findings are acceptable
./gradlew assembleDebug               # debug APK
./gradlew assembleRelease             # signed iff ZENDOCK_* properties exist in ~/.gradle/gradle.properties
./gradlew connectedDebugAndroidTest   # instrumented tests; needs a booted emulator/device
```

Requires a full JDK 17+ (not a JRE) and Android SDK Platform 35.

## Hard-won rules (violating these reintroduces shipped bugs)

1. **Never `Handler.postDelayed` for real-world delays** — uptimeMillis
   stops in CPU suspend ("cable pulled, screen off" IS the suspend case).
   Use the existing `AlarmScheduler.scheduleReevaluate()` pattern.
2. **Dropdowns:** always `NoFilterAdapter` + `labelToValue()` (label-based,
   never popup-position-based). The default adapter filters the list after
   activity re-creation (theme switch) and positions stop lining up.
3. **Zen-rule cleanup only via own `CONDITION_URI`** — never trust
   `getAutomaticZenRules()` to be caller-scoped.
4. **Runtime state stays out of `ScheduleParams`** (that interface is what
   keeps `Schedule` JVM-testable). Pass the alarm time as a parameter.
5. Modes as **enums, not boolean combinations** (`ScheduleMode`,
   `AlarmEndMode`) — with migration getters reading the legacy keys.
6. The FGS notification's hidden variant works via a **user-blocked
   channel** (Android re-bumps IMPORTANCE_NONE on FGS channels on its own).
7. `zen_mode` in `settings get global` lags the actual rule state by ~1 s —
   generous waits in emulator assertions.
8. **Every stateful widget needs `android:saveEnabled="false"`.** Android
   restores each switch/checkbox/toggle-group's OWN instance state after
   `recreate()` — AFTER `onCreate()` already set it from `Prefs` and
   attached listeners — which re-fires the listener with the PRE-recreate
   value and silently reverts it. Triggered not just by explicit
   `recreate()` calls (settings import) but also by
   `AppCompatDelegate.setDefaultNightMode()` on a real theme change. Setting
   `saveEnabled=false` on a parent container is NOT sufficient — it must be
   on each individual widget. `MainActivityStateTest` guards this
   regression class; a new switch/toggle added without the attribute will
   fail CI instead of shipping the bug again.

## Language & release conventions

- English is the primary language: code, comments, commits, README.md,
  log tag `PlugNap`. German is the maintained second track: README.de.md,
  `values-de`, fastlane `de-DE`, release notes bilingual (EN first).
- Release checklist: bump `versionCode`/`versionName` → add
  `fastlane/metadata/android/{en-US,de-DE}/changelogs/<versionCode>.txt` →
  `test` + `lintRelease` + `assembleDebug` + `connectedDebugAndroidTest`
  locally → commit → `git tag vX.Y.Z && git push origin vX.Y.Z`.
  Pushing the tag triggers `.github/workflows/release.yml`, which builds
  the signed release APK **in CI** (keystore decoded from
  `ANDROID_KEYSTORE_BASE64`/`ANDROID_KEYSTORE_PASSWORD` secrets — the
  keystore itself never leaves the runner's filesystem) and attaches it
  to the GitHub Release for that tag. **Building/signing locally and
  running `gh release create ... <apk>` is no longer the normal path** —
  only write/edit the bilingual release notes afterward with
  `gh release edit vX.Y.Z --notes-file -` once the workflow has attached
  the APK (check with `gh run list` / the repo's Actions tab).
- Fastlane metadata must stay symmetric across en-US/de-DE (title, both
  descriptions, icon.png, screenshots, changelogs) — IzzyOnDroid/F-Droid
  render from it. F-Droid recipe: `docs/fdroid-metadata.yml`.
- Never put personal email addresses into commits or files; author identity
  is the GitHub noreply address.

## Rejected alternatives (so this doesn't get re-litigated)

- **Exact alarms, not `WorkManager`.** IzzyOnDroid's review often pushes
  back on `SCHEDULE_EXACT_ALARM` (Android 14+ denies it to new apps by
  default). Considered switching the window-boundary/grace-period timers to
  `WorkManager` periodic work to avoid that friction — rejected: the window
  start/end are user-set clock times the app must hit to the minute, not a
  "run sometime in this interval" reminder. `WorkManager`'s minimum
  granularity and deferral under Doze/battery-saver would make the bedtime
  mode activate/deactivate at the wrong time, which defeats the app's one
  job. State this rationale up front in the IzzyOnDroid inclusion issue
  rather than waiting to be asked (see `docs/fdroid-metadata.yml`).

## Known limitations (documented, not bugs)

- `TYPE_BEDTIME` is reserved for the system wellbeing app → rule uses
  `TYPE_OTHER`.
- Uninstalling with the rule present can leave an orphaned mode entry
  (README tells users to flip the main switch off first; reinstalls
  self-heal via the orphan cleanup).
- Emulator can't render display saturation — grayscale is invisible there
  (and in screenshots on any device); verify via `dumpsys`/rule state.
