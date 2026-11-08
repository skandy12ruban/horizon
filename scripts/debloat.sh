#!/bin/bash
# Usage: scripts/debloat.sh <keep-file>
#   <keep-file>: one package per line — the apps the user chose to keep (from the app picker).
# Switches off, per user and reversibly:
#   1. ad / tracking / telemetry packages from config/debloat.json that exist on this TV
#   2. every user-facing app the user did NOT keep (system apps: disabled; installed apps: removed
#      for this user, data kept, restorable with `cmd package install-existing`)
# Writes backup/restore.sh first. Never touches config/never-disable.txt.
. "$(dirname "$0")/env.sh"
KEEP="$1"; [ -f "$KEEP" ] || { echo "usage: $0 <keep-file>"; exit 1; }
B="$ROOT/backup"; mkdir -p "$B"
tv shell pm list packages -d | sed 's/package://' | tr -d '\r' | sort > "$B/disabled-before.txt"
tv shell pm list packages -3 | sed 's/package://' | tr -d '\r' | sort > "$B/user-installed.txt"
installed=$(tv shell pm list packages | sed 's/package://' | tr -d '\r')
never=$(grep -v '^#' "$ROOT/config/never-disable.txt" | grep -v '^$')
keep=$(grep -v '^#' "$KEEP" | grep -v '^$')
targets=$("$PY" -c "import json,sys;d=json.load(open('$ROOT/config/debloat.json'));print('\n'.join(p for g in d['ads_tracking'].values() for p in g))")
unused=$("$ROOT/scripts/inventory.sh" | cut -d'|' -f1)
R="$B/restore.sh"
[ -f "$R" ] || printf '#!/bin/bash\n# Undo Steroid TV debloat. Run from the repo root.\n. "$(dirname "$0")/../scripts/env.sh"\n' > "$R"
chmod +x "$R"
for p in $targets $unused; do
  echo "$installed" | grep -qx "$p" || continue
  echo "$never" | grep -qx "$p" && continue
  echo "$keep" | grep -qx "$p" && continue
  if grep -qx "$p" "$B/user-installed.txt"; then
    r=$(tv shell pm uninstall -k --user 0 "$p" | tr -d '\r')
    echo "removed   $p  ($r)"; echo "tv shell cmd package install-existing $p" >> "$R"
  else
    r=$(tv shell pm disable-user --user 0 "$p" | tr -d '\r' | tail -1)
    echo "disabled  $p  ($r)"; echo "tv shell pm enable $p" >> "$R"
  fi
done
# Snappier UI and no ad personalisation (both reversible).
tv shell settings put secure limit_ad_tracking 1 || true
for k in window_animation_scale transition_animation_scale animator_duration_scale; do
  tv shell settings put global $k 0.5; echo "tv shell settings put global $k 1" >> "$R"
done
sort -u "$R" -o "$R.tmp" && { head -3 "$R"; grep -v '^#\|^\.' "$R.tmp"; } > "$R.new" && mv "$R.new" "$R" && rm -f "$R.tmp"
chmod +x "$R"
echo "DONE — undo with backup/restore.sh"
