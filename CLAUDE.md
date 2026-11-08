# Horizon — notes for Claude

Horizon is a custom Android TV launcher (package `tv.steroid.home`) built on the Steroid TV
codebase. Read `README.md` first; it covers setup, settings and troubleshooting.

## Rules that matter on a real TV

- **Never disable Google TV Home (`com.google.android.apps.tv.launcherx`) or Google's setup app
  (`com.google.android.tungsten.setupwraith`).** Disabling launcherx makes setupwraith's
  RecoveryActivity take Home and the TV shows a black screen. Horizon takes over Home with the
  `HomeKey` accessibility service instead (see README › How it takes over the Home button).
- The user's priority: the TV keeps working and can be reset to the stock launcher any time.
  `scripts/horizon-off.sh` must always work.
- Look before you press OK: screenshot first (`adb shell screencap -p /sdcard/s.png` + pull). Blind
  key presses can open apps and start playback on the user's accounts. Don't start radio playback
  without asking (it plays sound in the room).
- `adb shell screenrecord` writes broken MP4s on TCL; stream raw H.264 instead:
  `adb exec-out screenrecord --output-format=h264 - | ffmpeg -use_wallclock_as_timestamps 1 -f h264 -i - -c copy out.mkv`.

## Workflow

- Connect: `scripts/horizon-connect.sh <ip>` (writes `.tv-serial`).
- Build + install: `scripts/horizon-build.sh && scripts/horizon-on.sh`.
- After a change, verify on the TV with screenshots; after Home-button changes, also test a reboot.
- Keep `preview/horizon.html` in sync with visual changes (it mirrors `Sky.java` and `Home.java`).
- Old Steroid TV phase guides are in `docs/reference/` for background only; their install steps
  (disabling stock launchers) do not apply to Horizon.
