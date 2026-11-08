# Troubleshooting — everything that went wrong while building this

Every item below happened on a real TV. **The fixes are already in this repo's code and scripts.**
This list is here so you recognise the symptom if something regresses, and don't "fix" a working
design back into a broken one.

## Connecting and tooling

| Symptom | Cause | Fix (in repo) |
|---|---|---|
| `adb: command not found` | platform-tools not on `PATH` | `scripts/env.sh` finds the SDK's adb |
| `adbd cannot run as root in production builds` | retail TV | Expected. Nothing needs root |
| `connect … Operation timed out` | TV asleep / deep standby | Wake it; enable network standby |
| `javac: … Unable to find method metafactory` | `-bootclasspath android.jar` hides the JDK's lambda support | `javac --release 8 -classpath android.jar` |
| Build fails only on some machines | paths with spaces split by `$(find …)` | `@argfile`s in `build.sh` |
| Screenshot is black | some surfaces (other apps' video/GL) aren't captured by `screencap` | `screenrecord --time-limit 3` + ffmpeg frame |
| Key press does nothing, logcat says `Unhandled key … keyCode=0` | wrong key name (`KEYCODE_DOWN` isn't a key) | use `KEYCODE_DPAD_DOWN/UP/LEFT/RIGHT/CENTER`, `KEYCODE_HOME`, `KEYCODE_BACK` |
| Need to see inside the Retro TV / Windows pages | WebViews aren't inspectable by default | set `"debugWebViews": true` in `config/features.json`, rebuild, then `adb forward tcp:9333 localabstract:webview_devtools_remote_<pid>` and open `http://localhost:9333/json` (or chrome://inspect). Set it back to false afterwards |
| `input keyevent` does nothing in some apps | apps that read raw input devices ignore injected keys | Expected; test Steroid's own screens |
| Key presses land somewhere unexpected and open an app | focus wasn't where assumed | Screenshot before OK; press Home first — Home resets to the top/first app (`onNewIntent`) |

## Home screen

| Symptom | Fix |
|---|---|
| Hard horizontal band across the background | vignette gradient starts at its own rect, not at y=0 |
| Focused card scaled up gets clipped flat at an edge | `setClipChildren(false)` + `setClipToPadding(false)` on every parent up to the root; bottom padding (96dp) so the last row's focus outline is visible after scrolling |
| App banners cropped | banners use `FIT_XY` in a 16:9-ish card, not `CENTER_CROP` |
| Input pills squeezed to "HDMI 2 / HD…" when the On-air chip appears | the pill row gets explicit `WRAP_CONTENT` params; the chip lives in the left column |
| Retro TV mini-set overlaps the panel label | label shortened to "Retro TV" |
| No room for more features | the page is a `ScrollView`; new features go in rows below ("Look out") |
| Watch label shows a non-app name | only app cards set the "Watch · <app>" label |

## Radio

| Symptom | Cause | Fix |
|---|---|---|
| One station stuck on "Tuning…" while its song title loads | stream URL redirects to another host; MediaPlayer won't follow | resolve the final URL with `HttpURLConnection` first |
| Visualizer bars clump into steps, always high | too few FFT bins at low frequencies; loose normalisation | 48 bands from 90 Hz, `(dB-18)/30` + smoothstep, a slight high-band tilt |
| Waveform collides with the title | drawn mid-screen | drawn on the bars' baseline |
| Third column of station tiles cut off | tile width 186dp | 166dp |
| Visualizer silent | `RECORD_AUDIO` not granted | `install.sh` grants it; the UI says "VISUALIZER OFF · NO AUDIO PERMISSION" rather than faking motion |

## The World's Windows

| Symptom | Cause | Fix |
|---|---|---|
| Everything muted although sound is "on" | `setMuted(false)` sent before the page/player existed | mute state passed in the URL hash, applied on create and on every "playing" |
| Hurtigruten opens on a presenter, flights at the gate | intros | per-window `start` in `windows.json` (first visit only, then resume) |
| Grey placeholder previews | video has no `maxresdefault` | fall back to `hqdefault`, crop 4:3 → 16:9 |
| User rejected the first selection | live CCTV/webcam feeds felt like surveillance, low quality | curated long recorded views with natural sound — see the curation rules in `docs/05` |
| "Live" claims | — | badge only says LIVE when the player reports `isLive` |

## Retro TV — the big one

The first version simply showed the MyRetroTVs page full-screen and mapped keys to its shortcuts.
**Don't go back to that.** Problems, in order:

| Symptom | Cause | Fix |
|---|---|---|
| "Impossible to control" with the remote | site is a mouse/keyboard desktop UI | native remote-first UI; the site is hidden and only picks programmes |
| ▶ turned the set **off** | the site rewrites the URL hash on each channel change → `onPageFinished` fires again → our boot pressed power again | boot once per load (`booted` flag) |
| Hyper-laggy, "always looking for signal" | the whole desktop page (TV-set art, glow, static) rendered on a weak TV + our own static on top | hidden 2×2 px WebView; our own lightweight player shows the video |
| YouTube title bar / pause icon / logo / "More videos" always on screen | YouTube's own chrome on start, seek and pause; `controls=0` doesn't remove it | black cover until 2.8 s after the post-seek "playing" event; cover stays while paused; captions unloaded |
| Picking "Cartoons" in the guide kept playing Music | closing the guide restored the old category *before* tuning | every guide choice commits immediately |
| Channel label wrong/missing on some decades | label format differs per site ("CH 6161 MUSIC:…" vs plain titles) | title comes from the player (`getVideoData().title`) |
| Constant loud static | site never sees its player start, so it keeps playing its "no signal" sound | `Howler.mute(true)` |
| Pause pressed once, video stays paused / resume impossible | a "resume on any key" check ran on OK's key-down, then OK's key-up paused again | removed; OK toggles on key-up, hold OK (600 ms) opens the guide |
| Holding OK opened the guide *and* tuned a channel | the long press's key-up clicked the focused guide chip | swallow that first key-up |
| Some clips won't play | removed / not embeddable | player error → next channel automatically |
| YouTube's red ▶ button on a black screen after opening | first load of a decade site takes 15–30 s (it downloads a ~0.5 MB channel lineup); a timed fallback revealed the idle player | the cover is only lifted once a programme reports "playing"; the card says "Tuning in…" meanwhile |

If the site changes and Retro TV stops tuning: open the decade site in a desktop browser, check the
ids `lcdbar`, `filter_none`, `box_*`, the YouTube player id `myytplayer`, the global `Howler`, and that
keyboard `keyCode` 32/37/39 still mean power/prev/next. Update `WATCH` in `Retro.java`.

## Little Office

| Symptom | Cause | Fix |
|---|---|---|
| Card stuck on "Looking for the computer…" | Android blocks plain HTTP by default (target SDK 30) | `android:usesCleartextTraffic="true"` |
| Panel crashed on open with nothing loaded | focused a row that didn't exist | focus the first focusable row, if any |
| Notices never appear | first poll with zero events left the "seen" marker unset, so nothing counted as new | first contact records the newest id (or 0) as seen |
| Notice not visible though Android logged it | the TV's screensaver (Ambient mode) draws above overlays | expected; it shows when the TV is in use |
| Capitalised names lower-cased ("android TV") | first letter lowered for style | text shown as written |
| macOS asks whether Python may accept connections | firewall | allow — the TV needs port 8765 |
| Codex projects shown as folder slugs | Codex runs in dated scratch folders | thread titles from `~/.codex/session_index.jsonl` |

## Things that were tried and removed (don't rebuild them)

- **Retro Games (RetroArch).** It worked at 60 fps for NES/SNES/Genesis/GB/GBA — cores were shipped
  inside the launcher APK as native libs because RetroArch ignores injected input and Android won't
  load cores from shared storage — but the user's Bluetooth controller wouldn't pair, so it was
  removed. DS emulation ran at ~16 fps on this hardware.
- **Live webcams** for The World's Windows — replaced by recorded journeys (see above).
- **Showing MyRetroTVs directly** — replaced by the hidden-engine design.

## Not bugs

- A crossed-out speaker icon in screenshots = the TV is muted on the remote.
- Ads inside YouTube, Prime Video, Hotstar etc. — a launcher can't remove them.
- Xiaomi's PatchWall update service logs noise in `logcat` — harmless.
