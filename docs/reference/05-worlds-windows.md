# 5 · The World's Windows

Long, high-quality views to leave on: train windows, rooms with a view, still nature, the sea, the
sky. 22 videos in 5 moods, with sound, chosen to feel like looking out — not like a CCTV feed.

## Automated

```bash
scripts/check-youtube.py     # every video still exists and allows embedding?
```

Previews are downloaded during the build (`scripts/fetch-previews.sh`). Nothing to configure.

## If a video disappears — curation rules

Replace it in `config/windows.json` with one that meets **all** of these (the user insisted on
each, after earlier attempts were rejected):

1. **Feels like being there.** Train side windows and cab rides, a fixed camera on a beautiful
   place, a real window view, a deck, a plane window. **Not** CCTV/webcam live feeds (low quality,
   timestamps, news graphics) — those were tried first and rejected.
2. **Natural sound, no music.** Prefer titles saying "no music" / "natural ambience". Silent videos
   feel like a screensaver.
3. **Real motion.** Even subtle (water, trees, a train). No stills, no slideshow "ambience" images.
4. **Long.** An hour or more (the shipped ones are 1–12 h).
5. **High quality.** 1080p minimum, 4K/8K preferred.
6. **Embeddable.** `check-youtube.py` must say `ok`.
7. **Honest labels.** Composited "window frame" videos are labelled "Real footage framed by a
   window"; AI renders are avoided; nothing recorded is ever called live.
8. **Variety.** Several flavours, not all trains.

Fields: `youtube` (video id), `name`, `where`, `mood` (one of `moods`), `timezone` (IANA, for the
local-time line), `source` (channel, shown as credit), `note` (shown under the title), `start`
(seconds to skip on first visit — past intros, presenters, taxiing; default 60).

Good sources found: Abao Ambience (8K Japan rides, no music), lorirocks777 (4K Swiss cab rides),
Oliver Hinz (Swiss side windows), Naturescapes, Nomadic Ambience, Framic window views, Window
Scenes, Skylite Productions (full flights), NRK's Hurtigruten slow TV.

## How it works (`Windows`, `assets/window.html`)

- One WebView loads a local page (`loadDataWithBaseURL("https://steroid.home/#<id>@<start>@<muted>")`)
  with the standard **YouTube IFrame Player**, `controls=0`, full screen. The launcher draws its own
  overlay (place, local time, "Recorded" badge, credit) and fades it after a few seconds.
- **Sound on by default.** The mute state is passed in the URL hash so it's applied when the
  player is created — calling `setMuted()` before the page loads silently left everything muted.
- **Resume:** the page reports the position every 15 s; each window resumes where you left it.
- **Honest badge:** "● LIVE" only if the player reports `isLive`; recordings say "RECORDED · NOT LIVE".
- Offline/removed video → a calm "This window is closed" card offering the next one.
- Favourites, Wander (auto-rotate every 10/30/60 min), sound toggle, next window, all from OK.
