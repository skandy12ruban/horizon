#!/bin/bash
# Puts the TV's original home screen back.
#   scripts/horizon-off.sh            switch Home back, keep Horizon installed (re-enable with horizon-on.sh)
#   scripts/horizon-off.sh --remove   also uninstall Horizon
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
TV="$(cat "$ROOT/.tv-serial")"
tv() { "$ADB" -s "$TV" "$@"; }
PKG=tv.steroid.home
ORIG="$(cat "$ROOT/backup/original-home.txt" 2>/dev/null || true)"

# Stop catching the Home button (remove only Horizon's entry; keep any other accessibility services).
SVC="$PKG/$PKG.HomeKey"
cur=$(tv shell settings get secure enabled_accessibility_services | tr -d '\r')
new=$(printf '%s' "$cur" | tr ':' '\n' | grep -v -x "$SVC" | grep -v -x null | paste -sd: -)
if [ -n "$new" ]; then tv shell settings put secure enabled_accessibility_services "$new"
else tv shell settings delete secure enabled_accessibility_services >/dev/null; fi

# Safety net: if anything ever disabled a stock launcher, turn it back on.
for p in com.google.android.tvlauncher com.google.android.apps.tv.launcherx com.google.android.leanbacklauncher \
         com.google.android.tvrecommendations com.mitv.tvhome.atv "${ORIG%%/*}"; do
  [ -n "$p" ] && tv shell pm enable "$p" >/dev/null 2>&1 || true
done

if [ -n "$ORIG" ] && [ "${ORIG%%/*}" != "$PKG" ]; then
  tv shell cmd package set-home-activity "$ORIG" --user 0 | tail -1 || true
fi
if [ "$1" = "--remove" ]; then tv uninstall "$PKG" | tail -1; fi
tv shell input keyevent KEYCODE_HOME
echo "home is now: $(tv shell cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN | tail -1 | tr -d '\r')"
