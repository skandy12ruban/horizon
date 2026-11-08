# 9 · Verify

Do these on the TV, in order. Screenshot before pressing OK (see CLAUDE.md → "Look before you
press"). `. scripts/env.sh` gives you `tv`.

```bash
shot() { tv shell screencap -p /sdcard/s.png && tv pull /sdcard/s.png "$1" >/dev/null; }
```

## Home
- [ ] `tv shell input keyevent KEYCODE_HOME` → Steroid Home: clock + greeting, input/settings pills
  top-right, Watch row with the kept apps (their real banners), Retro TV + Radio row, "Look out" row
  (Windows + Little Office). Nothing overlaps or is clipped.
- [ ] D-pad down scrolls the page smoothly; the focused card's outline isn't cut off at the bottom.
- [ ] Home again returns to the top with the first app focused.

## Apps
- [ ] Each kept app launches:
  `tv shell monkey -p <pkg> -c android.intent.category.LEANBACK_LAUNCHER 1`, wait ~8 s, then
  `tv shell dumpsys activity activities | grep -m1 mResumedActivity` shows that app. Home returns.

## Radio
- [ ] Radio card → station grid (no column cut off) → OK on a station → Now Playing: "● LIVE",
  a song title within ~20 s, and "LIVE SPECTRUM" with moving bars.
  `tv logcat -d -s SteroidRadio | tail` shows `state=2` (playing).
- [ ] ◀ ▶ changes station; Home shows "● ON AIR"; ▼ stops.

## The World's Windows
- [ ] Card → browser with previews and mood filters → OK → full-screen view **with sound**, a place
  card with local time and a "RECORDED" badge that fades. OK shows the controls.

## Retro TV
- [ ] '80s tile → a programme within ~10–15 s, **no** YouTube title bar / pause icon / logo on screen,
  **no** loud static. ◀ ▶ changes channel, ▲ ▼ changes category (the card shows the right category),
  OK pauses (black screen + "Paused" card) and resumes, hold OK opens the guide, Back goes home.

## Little Office (if enabled)
- [ ] The card shows "Codex · … / Claude · …" (not "Looking for the computer…").
- [ ] Panel opens with Pinned + Other activity.
- [ ] `curl -s http://127.0.0.1:8765/test-event` on the Mac → a notice top-right within ~1 min
  (TV awake, not on the screensaver). Android logs it: `tv shell appops get tv.steroid.home
  SYSTEM_ALERT_WINDOW` shows a recent `duration=+9s`.

## Survives a reboot
- [ ] `tv reboot`, wait for `getprop sys.boot_completed` = 1 → Steroid Home, nothing crashed:
  `tv logcat -d -b crash | grep steroid` is empty.
- [ ] HDMI pill switches input; Bluetooth devices still paired; sound works.
