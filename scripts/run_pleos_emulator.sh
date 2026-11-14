#!/usr/bin/env bash
set -euo pipefail

SDK_PATH="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
AVD_NAME="${1:-Gyeot_Pleos_2_1}"
if [[ $# -gt 0 ]]; then shift; fi
if [[ ! -x "$SDK_PATH/emulator/emulator" ]]; then
  echo "Android Emulator not found. Set ANDROID_HOME to your Android SDK directory." >&2
  exit 1
fi

# Pleos requires cold boot for VHAL. Keep the virtual device's user data.
exec "$SDK_PATH/emulator/emulator" -avd "$AVD_NAME" -no-snapshot -memory 4096 -gpu host "$@"
