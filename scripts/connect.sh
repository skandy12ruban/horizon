#!/bin/bash
# Usage: scripts/connect.sh <tv-ip>[:port]
# Connects over Wi-Fi debugging and records the TV so later scripts target it.
. "$(dirname "$0")/env.sh"
[ -n "$1" ] || { echo "usage: $0 <tv-ip>[:port]"; exit 1; }
T="$1"; [[ "$T" == *:* ]] || T="$T:5555"
"$ADB" connect "$T" | tail -1
sleep 1
state=$("$ADB" -s "$T" get-state 2>/dev/null || true)
if [ "$state" != "device" ]; then
  echo "NOT CONNECTED (state: ${state:-none}). If the TV shows an 'Allow USB debugging?' prompt, accept it (tick 'Always allow') and run this again."
  exit 1
fi
echo "$T" > "$ROOT/.tv-serial"
"$ADB" -s "$T" shell 'echo "model: $(getprop ro.product.manufacturer) $(getprop ro.product.model)"; echo "android: $(getprop ro.build.version.release) (sdk $(getprop ro.build.version.sdk))"; echo "abi: $(getprop ro.product.cpu.abilist)"; echo "screen: $(wm size | tail -1)"'
echo CONNECTED
