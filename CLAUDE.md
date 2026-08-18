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

Tests: `app/src/test/…/ScheduleTest.kt` (window/alarm math, 34 cases) and
`DropdownMappingTest.kt`. Run with `./gradlew test` — pure JVM, no emulator.

## Commands

```bash
./gradlew test                 # JVM unit tests (must stay green)
./gradlew lintRelease          # only cosmetic findings are acceptable
./gradlew assembleDebug        # debug APK
./gradlew assembleRelease      # signed iff ZENDOCK_* properties exist in ~/.gradle/gradle.properties
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

## Language & release conventions

- English is the primary language: code, comments, commits, README.md,
  log tag `PlugNap`. German is the maintained second track: README.de.md,
  `values-de`, fastlane `de-DE`, release notes bilingual (EN first).
- Release checklist: bump `versionCode`/`versionName` → add
  `fastlane/metadata/android/{en-US,de-DE}/changelogs/<versionCode>.txt` →
  `test` + `lintRelease` + `assembleRelease` → emulator smoke test →
  commit → tag `vX.Y.Z` → `gh release create` with bilingual notes + APK.
- Fastlane metadata must stay symmetric across en-US/de-DE (title, both
  descriptions, icon.png, screenshots, changelogs) — IzzyOnDroid/F-Droid
  render from it. F-Droid recipe: `docs/fdroid-metadata.yml`.
- Never put personal email addresses into commits or files; author identity
  is the GitHub noreply address.

## Known limitations (documented, not bugs)

- `TYPE_BEDTIME` is reserved for the system wellbeing app → rule uses
  `TYPE_OTHER`.
- Uninstalling with the rule present can leave an orphaned mode entry
  (README tells users to flip the main switch off first; reinstalls
  self-heal via the orphan cleanup).
- Emulator can't render display saturation — grayscale is invisible there
  (and in screenshots on any device); verify via `dumpsys`/rule state.
