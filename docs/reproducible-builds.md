# Reproducible builds

PlugNap is built to be reproducible in the sense F-Droid/IzzyOnDroid care
about: building from source at a given tag should produce byte-identical
output (aside from the signature block itself) to the APK attached to the
matching GitHub release. This matters because it lets anyone — not just
the maintainer — verify that a released APK actually corresponds to the
public source, without having to trust the maintainer's build machine.

**Status: not yet independently verified by a third party.** The
properties below are true of the build configuration as of this writing;
`scripts/verify-reproducible-build.sh` lets you check them yourself, and a
clean confirmation from someone other than the maintainer would be a
useful thing to report in an issue.

## What makes this build deterministic

- **No dynamic dependency versions.** Every dependency in
  `app/build.gradle.kts` is pinned to an exact version (no `+`, no
  version ranges). Same for the Gradle wrapper (`gradle-wrapper.properties`)
  and the AGP/Kotlin plugin versions in the root `build.gradle.kts`.
- **`versionCode`/`versionName` are static integers/strings** in
  `app/build.gradle.kts`, not derived from the build timestamp, git describe
  output, or anything else that would vary between two builds of the same
  tag.
- **No build-time secrets or environment leak into the binary.** The
  release signing key is applied by Gradle at packaging time, entirely
  outside the APK's actual content (`classes.dex`, resources, manifest);
  an unsigned build from the same source is identical to a signed one
  minus the `META-INF/` signature files.
- **R8/D8 are deterministic** given identical inputs (same AGP/R8 version,
  same Kotlin version, same source) — the shrinker and dexer don't depend
  on wall-clock time, hostname, or file-system iteration order in a way
  that would affect the output bytes.
- **AGP normalizes ZIP entry timestamps** in the APK it produces, so two
  builds don't differ merely because they ran on different days.

## What could still break reproducibility (and hasn't been ruled out)

- **JDK vendor/version.** This project is developed and normally built
  with Eclipse Temurin JDK 21 (see `~/projects/CLAUDE.md` on the
  maintainer's machine), while CI (`.github/workflows/ci.yml`) uses
  Temurin JDK 17. Both target Java 17 bytecode
  (`compileOptions`/`kotlinOptions` in `app/build.gradle.kts`), and no
  discrepancy has been observed between the two — but this has not been
  exhaustively cross-checked against whatever JDK an independent builder
  (or F-Droid's build server) might use. If you find a mismatch, this is
  the first thing to suspect; pinning the exact JDK build used for a
  specific release would be the fix.
- **Host OS / locale / timezone** of the machine running the build, in
  the (unlikely, given the points above) case something in the toolchain
  is locale- or timezone-sensitive.

## Verifying it yourself

```bash
scripts/verify-reproducible-build.sh v1.7.0 /path/to/plugnap-v1.7.0.apk
```

This clones the given tag into a temporary directory, builds an unsigned
release APK from it, and compares every file inside the APK (except
`META-INF/`, which only ever contains the signature) against the
reference APK byte-for-byte. Requires a full JDK 17+ and the Android SDK
components that tag's `app/build.gradle.kts` asks for (see the main
[README](../README.md#building)).
