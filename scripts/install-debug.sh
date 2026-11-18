#!/usr/bin/env bash
# Builds Mori, installs it on a connected phone or emulator, and turns on its accessibility service.
# Usage: scripts/install-debug.sh [serial]   (serials are listed by `adb devices`)
set -euo pipefail
cd "$(dirname "$0")/.."

export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ADB=("$ANDROID_HOME/platform-tools/adb")
if [ $# -gt 0 ]; then
  ADB+=(-s "$1")
  export ANDROID_SERIAL="$1"
fi
SERVICE=io.github.balazstasi.mori.debug/app.mori.GuardService

# Install for the current user only. A plain install also lands in work and dual app profiles.
./gradlew -q assembleDebug
"${ADB[@]}" install -r --user current app/build/outputs/apk/debug/app-debug.apk

for attempt in 1 2 3; do
  # Keep any accessibility services that are already on, such as a password manager.
  current=$("${ADB[@]}" shell settings get secure enabled_accessibility_services | tr -d '\r')
  case "$current" in
    null | "") wanted="$SERVICE" ;;
    *"$SERVICE"*) wanted="$current" ;;
    *) wanted="$current:$SERVICE" ;;
  esac
  "${ADB[@]}" shell settings put secure enabled_accessibility_services "$wanted"
  "${ADB[@]}" shell settings put secure accessibility_enabled 1
  sleep 2
  if "${ADB[@]}" shell dumpsys accessibility | grep "Enabled services" | grep -q "$SERVICE"; then
    echo "Mori is installed and running."
    exit 0
  fi
done

echo "Installed, but the accessibility service did not turn on. Turn it on in Settings, Accessibility." >&2
exit 1
