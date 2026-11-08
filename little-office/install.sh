#!/bin/bash
# Installs Little Office on this Mac: copies the service to ~/.little-office, writes its config,
# and registers a LaunchAgent so it starts at login and restarts if it stops.
# Usage: little-office/install.sh ["Pinned Project" ...]
set -e
HERE="$(cd "$(dirname "$0")" && pwd)"
D="$HOME/.little-office"; mkdir -p "$D"
cp "$HERE/office.py" "$D/office.py"
PY=/opt/homebrew/bin/python3; [ -x "$PY" ] || PY="$(command -v python3)"
if [ $# -gt 0 ] || [ ! -f "$D/config.json" ]; then
  "$PY" - "$D/config.json" "$@" <<'P'
import json, sys
path, pins = sys.argv[1], sys.argv[2:]
try: cfg = json.load(open(path))
except Exception: cfg = {"pinned": [], "names": {}}
if pins: cfg["pinned"] = pins
json.dump(cfg, open(path, "w"), indent=2)
P
fi
PL="$HOME/Library/LaunchAgents/com.steroid.littleoffice.plist"
cat > "$PL" <<P
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
  <key>Label</key><string>com.steroid.littleoffice</string>
  <key>ProgramArguments</key><array><string>$PY</string><string>$D/office.py</string></array>
  <key>RunAtLoad</key><true/>
  <key>KeepAlive</key><true/>
  <key>StandardErrorPath</key><string>$D/office.log</string>
</dict></plist>
P
launchctl bootout "gui/$(id -u)/com.steroid.littleoffice" 2>/dev/null || true
launchctl bootstrap "gui/$(id -u)" "$PL"
sleep 2
curl -sf -m 5 http://127.0.0.1:8765/status >/dev/null && echo "LITTLE OFFICE RUNNING on port 8765" || { echo "Service didn't answer — see $D/office.log"; exit 1; }
