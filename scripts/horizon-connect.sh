#!/bin/bash
# Connects to the TV over network debugging and remembers it for the other horizon-* scripts.
#   scripts/horizon-connect.sh 192.168.0.106          (port 5555 is assumed)
#   scripts/horizon-connect.sh 192.168.0.106:37123    (Android 11+ "Wireless debugging" port)
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
command -v adb >/dev/null 2>&1 && [ ! -x "$ADB" ] && ADB="$(command -v adb)"
[ -n "$1" ] || { echo "usage: $0 <tv-ip>[:port]"; exit 1; }
T="$1"; [[ "$T" == *:* ]] || T="$T:5555"
"$ADB" connect "$T" | tail -1
for i in 1 2 3 4 5 6 7 8 9 10 11 12; do
  [ "$("$ADB" -s "$T" get-state 2>/dev/null)" = device ] && break
  [ $i = 1 ] && echo "Accept 'Allow debugging?' on the TV (tick 'Always allow')…"
  sleep 5; "$ADB" connect "$T" >/dev/null 2>&1 || true
done
[ "$("$ADB" -s "$T" get-state 2>/dev/null)" = device ] || { echo "NOT CONNECTED — check Developer options › USB/Network debugging, then retry."; exit 1; }
echo "$T" > "$ROOT/.tv-serial"
"$ADB" -s "$T" shell 'echo "TV: $(getprop ro.product.manufacturer) $(getprop ro.product.model), Android $(getprop ro.build.version.release)"; echo "Home app: $(cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN | tail -1)"'
echo "CONNECTED — saved to .tv-serial"
