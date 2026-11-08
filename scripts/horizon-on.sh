#!/bin/bash
# Installs Horizon and makes it the Home app. Never disables the stock launcher, so the TV
# always has a working home screen to fall back to. Undo with scripts/horizon-off.sh.
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
TV="$(cat "$ROOT/.tv-serial")"
tv() { "$ADB" -s "$TV" "$@"; }
APK="$ROOT/launcher/out/SteroidHome.apk"
PKG=tv.steroid.home

mkdir -p "$ROOT/backup"
# Remember the TV's own home screen the first time only, so later runs can't overwrite it with Horizon.
if [ ! -s "$ROOT/backup/original-home.txt" ]; then
  tv shell cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN \
    | tail -1 | tr -d '\r' > "$ROOT/backup/original-home.txt"
fi
echo "original home: $(cat "$ROOT/backup/original-home.txt")"

tv install -r "$APK" | tail -1
tv shell cmd package set-home-activity "$PKG/.Home" --user 0 | tail -1 || true
home=$(tv shell cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN | tail -1 | tr -d '\r')
echo "home is now: $home"
tv shell pm grant "$PKG" android.permission.RECORD_AUDIO 2>/dev/null || true
# Sky uses the TV's rough location when Android has one; otherwise it looks up the internet address's city.
tv shell pm grant "$PKG" android.permission.ACCESS_COARSE_LOCATION 2>/dev/null || true
# Google TV routes Home to its own launcher whatever the default is, so Horizon's accessibility
# service (HomeKey) catches the Home button instead. Nothing is disabled; Google TV stays as it was.
SVC="$PKG/$PKG.HomeKey"
# TCL TVs block background services unless the app may auto-start (APP_AUTO_START app-op).
tv shell appops set "$PKG" APP_AUTO_START allow 2>/dev/null || true
# Lets Horizon restart its own Home-button service after TCL's memory guard kills it at boot.
tv shell pm grant "$PKG" android.permission.WRITE_SECURE_SETTINGS 2>/dev/null || true
# Lets the home row put your most-watched apps first.
tv shell appops set "$PKG" GET_USAGE_STATS allow 2>/dev/null || true
cur=$(tv shell settings get secure enabled_accessibility_services | tr -d '\r')
case "$cur" in *"$SVC"*) ;; null|"") tv shell settings put secure enabled_accessibility_services "$SVC" ;;
  *) tv shell settings put secure enabled_accessibility_services "$cur:$SVC" ;; esac
tv shell settings put secure accessibility_enabled 1
# re-write the list so Android binds the (re)installed service now rather than after a reboot
cur=$(tv shell settings get secure enabled_accessibility_services | tr -d '\r')
tv shell settings put secure enabled_accessibility_services '""'; sleep 1
tv shell settings put secure enabled_accessibility_services "$cur"; sleep 2
echo "home button: $(tv shell settings get secure enabled_accessibility_services | tr -d '\r')"
tv shell input keyevent KEYCODE_HOME
