#!/bin/bash
# Shared paths for every script. Source it: . "$(dirname "$0")/env.sh"
set -e
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
[ -d "$SDK" ] || SDK="$HOME/Android/Sdk"
ADB="$SDK/platform-tools/adb"
command -v adb >/dev/null 2>&1 && [ ! -x "$ADB" ] && ADB="$(command -v adb)"
BT="$(ls -d "$SDK"/build-tools/* 2>/dev/null | sort -V | tail -1)"
PLATFORM="$(ls -d "$SDK"/platforms/android-* 2>/dev/null | sort -V | tail -1)"
PY="$(command -v python3)"
[ -x /opt/homebrew/bin/python3 ] && PY=/opt/homebrew/bin/python3
# The TV to talk to (set by connect.sh); every adb call goes through tv().
TV_SERIAL="$(cat "$ROOT/.tv-serial" 2>/dev/null || true)"
tv() { if [ -n "$TV_SERIAL" ]; then "$ADB" -s "$TV_SERIAL" "$@"; else "$ADB" "$@"; fi; }
