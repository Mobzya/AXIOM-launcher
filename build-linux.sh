#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
if command -v gradle >/dev/null 2>&1; then
  exec gradle --no-daemon :app:assembleDebug "$@"
fi
VERSION="8.13"
ARCHIVE=".gradle-local/gradle-$VERSION-bin.zip"
GRADLE_BIN=".gradle-local/gradle-$VERSION/bin/gradle"
if [ ! -x "$GRADLE_BIN" ]; then
  mkdir -p .gradle-local
  echo "Downloading Gradle $VERSION from the official Gradle distribution server..."
  curl --fail --location --retry 2 --proto '=https' --tlsv1.2 \
    "https://services.gradle.org/distributions/gradle-$VERSION-bin.zip" -o "$ARCHIVE"
  unzip -q "$ARCHIVE" -d .gradle-local
fi
exec "$GRADLE_BIN" --no-daemon :app:assembleDebug "$@"
