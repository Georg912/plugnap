#!/usr/bin/env bash
# Rebuilds PlugNap from a given tag and compares the result against a
# reference APK, byte-for-byte, ignoring only the signature block itself
# (META-INF/*). This is the same kind of check F-Droid/IzzyOnDroid run
# before trusting that a developer-signed APK actually corresponds to the
# public source at that tag.
#
# Usage: scripts/verify-reproducible-build.sh <tag> <reference-apk>
# Example: scripts/verify-reproducible-build.sh v1.7.0 ~/Downloads/plugnap-v1.7.0.apk
#
# Requires: git, a full JDK 17+ (JAVA_HOME), Android SDK with the platform/
# build-tools this tag's app/build.gradle.kts asks for, unzip, sha256sum.
set -euo pipefail

TAG="${1:?Usage: $0 <tag> <reference-apk>}"
REFERENCE="${2:?Usage: $0 <tag> <reference-apk>}"
REPO_ROOT="$(git -C "$(dirname "${BASH_SOURCE[0]}")" rev-parse --show-toplevel)"
WORKDIR="$(mktemp -d)"
trap 'rm -rf "$WORKDIR"' EXIT

echo "Cloning $TAG into a clean checkout ..."
git clone --quiet --depth 1 --branch "$TAG" "$REPO_ROOT" "$WORKDIR/src"

echo "Building unsigned release APK (no signing properties on purpose) ..."
(cd "$WORKDIR/src" && ./gradlew --quiet assembleRelease)

BUILT_APK=$(find "$WORKDIR/src/app/build/outputs/apk/release" -name "*.apk" | head -1)
if [ -z "$BUILT_APK" ]; then
    echo "Build produced no APK — aborting." >&2
    exit 1
fi

echo "Comparing $BUILT_APK against $REFERENCE ..."
entries_built=$(unzip -l "$BUILT_APK" | awk 'NF==4 {print $4}' | grep -v '^META-INF/' | sort)
entries_ref=$(unzip -l "$REFERENCE" | awk 'NF==4 {print $4}' | grep -v '^META-INF/' | sort)

if [ "$entries_built" != "$entries_ref" ]; then
    echo "✗ MISMATCH: file lists differ (excluding META-INF/)."
    diff <(echo "$entries_built") <(echo "$entries_ref") || true
    exit 1
fi

fail=0
while IFS= read -r entry; do
    [ -z "$entry" ] && continue
    a=$(unzip -p "$BUILT_APK" "$entry" | sha256sum | cut -d' ' -f1)
    b=$(unzip -p "$REFERENCE" "$entry" | sha256sum | cut -d' ' -f1)
    if [ "$a" != "$b" ]; then
        echo "✗ MISMATCH: $entry"
        fail=1
    fi
done <<< "$entries_built"

if [ "$fail" -eq 0 ]; then
    echo "✓ Reproducible: every non-signature entry matches byte-for-byte."
else
    echo "✗ Not reproducible — see mismatches above."
    exit 1
fi
