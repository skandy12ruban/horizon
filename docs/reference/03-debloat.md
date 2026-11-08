# 3 · Clean up

```bash
scripts/debloat.sh backup/keep.txt
```

## What it does

1. Records what's already disabled and what's user-installed (`backup/`).
2. Switches off, **for user 0 only** (reversible):
   - ad / tracking / telemetry / demo packages listed in `config/debloat.json` that exist on this TV;
   - every user-facing app from the inventory that the user didn't keep.
   System apps are `pm disable-user`'d; user-installed apps are `pm uninstall -k --user 0`'d
   (data kept; restorable with `cmd package install-existing`).
3. Turns on *limit ad tracking* and sets animations to 0.5× (feels faster on slow TVs).
4. Appends the exact undo command for every change to `backup/restore.sh`.

It never touches `config/never-disable.txt`. The stock launchers are **not** disabled here —
`install.sh` does that only after Steroid Home is confirmed as the Home app, so the TV is never
left without a home screen.

## Other vendors (not Xiaomi)

`config/debloat.json` ships a verified Xiaomi list plus Google/AOSP extras. For another brand,
review its system packages before running the script:

```bash
. scripts/env.sh; tv shell pm list packages -s | sed 's/package://' | sort
```

Add a group (e.g. `"tcl": [...]`, `"samsung": [...]`, `"hisense": [...]`) with packages whose names
clearly mean **analytics, statistics, ads, recommendations/"channels" home rows, retail/store demo,
factory test, or a vendor's own content portal**. Leave alone anything that might handle video
playback, HDMI/tuner inputs, picture/sound settings, the remote, Bluetooth, Wi-Fi, updates, or
casting — when unsure, keep it and add it to `never-disable.txt`.

## Undo

```bash
backup/restore.sh
```

## Results on the reference TV

25 packages switched off (analytics, statistics, PatchWall rows, Mi TV+, retail demo, factory test,
print spooler, unused Netflix/Play Movies/Games, Xiaomi's media apps). Free memory went from ~47 MB
to ~60 MB on a 1.4 GB TV; boot to Steroid Home took 46 s.
