#!/bin/bash
# Stops Little Office and removes its login item (keeps ~/.little-office/config.json).
launchctl bootout "gui/$(id -u)/com.steroid.littleoffice" 2>/dev/null || true
rm -f "$HOME/Library/LaunchAgents/com.steroid.littleoffice.plist" "$HOME/.little-office/office.py"
echo "Little Office removed"
