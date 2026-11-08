#!/bin/bash
# Checks the computer has what the build needs. Prints a fix for anything missing.
. "$(dirname "$0")/env.sh"; set +e
ok=1
say() { printf "%-16s %s\n" "$1" "$2"; }
[ -x "$ADB" ] && say adb "ok ($ADB)" || { say adb "MISSING — install Android platform-tools (see docs/00-requirements.md)"; ok=0; }
java -version >/dev/null 2>&1 && say java "ok ($(java -version 2>&1 | head -1))" || { say java "MISSING — install a JDK 17+ (e.g. brew install openjdk@17)"; ok=0; }
[ -n "$BT" ] && say build-tools "ok ($(basename "$BT"))" || { say build-tools "MISSING — sdkmanager \"build-tools;34.0.0\""; ok=0; }
[ -n "$PLATFORM" ] && say platform "ok ($(basename "$PLATFORM"))" || { say platform "MISSING — sdkmanager \"platforms;android-34\""; ok=0; }
[ -n "$PY" ] && say python3 "ok ($($PY --version 2>&1))" || { say python3 "MISSING"; ok=0; }
command -v ffmpeg >/dev/null && say ffmpeg ok || say ffmpeg "optional (used to crop window previews) — brew install ffmpeg"
command -v curl >/dev/null && say curl ok || { say curl MISSING; ok=0; }
[ $ok = 1 ] && echo "READY" || { echo "NOT READY"; exit 1; }
