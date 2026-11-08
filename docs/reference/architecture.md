# Architecture

One small Android app (`tv.steroid.home`), no third-party libraries, built without Gradle.

```
launcher/
  AndroidManifest.xml
  assets/
    fonts/            Instrument Serif, Hanken Grotesk, DM Mono (+ OFL licences)
    retro.html        Retro TV player page (YouTube IFrame API)
    window.html       World's Windows player page (YouTube IFrame API)
    config/           ← copied from /config by build.sh
    windows/          ← previews fetched by build.sh
  src/tv/steroid/home/
    Home.java         the launcher (HOME activity): header, Watch row, worlds, Look out deck
    Ui.java           fonts, sizes, small view helpers (calls Config/Stations/Places.load)
    Ambient.java      slow aurora background + film grain (~12 fps, paused when hidden)
    Config.java       reads assets/config/*.json
    Retro.java        Retro TV (hidden MyRetroTVs engine + visible player + guide)
    Lineup.java       Retro TV Science channels
    Radio.java        station browser + Now Playing
    RadioService.java streaming foreground service (MediaPlayer, ICY titles, reconnects)
    Spectrum.java     visualizer (audiofx.Visualizer FFT)
    Stations.java     stations from stations.json
    Windows.java      The World's Windows browser + viewer
    Places.java       windows from windows.json
    Office.java       Little Office client (mDNS discovery, polling, wording)
    OfficePanel.java  Little Office glance panel
    OfficeService.java Little Office poller + overlay notice
```

## Design language

- Dark, warm, ambient. Background: a slowly drifting aurora tinted by whatever is focused, with
  grain and a bottom vignette. No hard edges.
- Type: serif display (Instrument Serif) for names and the clock, a neutral grotesk for body, a
  mono for small uppercase labels with wide tracking.
- Cards: rounded 14–20dp, a 1dp hairline that becomes a 2.5dp white ring on focus, plus a gentle
  scale (1.04–1.12) and elevation. Never clip a focused card.
- Overlays fade after a few seconds; nothing persistent covers video.
- Honesty: "LIVE" only when live, "LIVE SPECTRUM" only when real audio data is flowing, "Last seen"
  instead of a stale "Working".

## Adding a feature

Add a card to a new row in `Home.onCreate` below "Look out" (the page scrolls), gate it with a flag in
`config/features.json`, and give its screen an Activity. Keep D-pad focus order natural (rows left
to right), handle Back explicitly, and test with a basic remote.
