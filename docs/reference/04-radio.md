# 4 · Radio

Internet radio with a station browser, a full-screen Now Playing view, live song titles and a
visualizer that reacts to the real audio.

## Stations

- **User has CLI AMP** ([cliamp](https://github.com/bjarneo/cliamp), a terminal music player):
  detect with `command -v cliamp` or `ls ~/.config/cliamp/playlists/*.toml`. Ask whether to bring
  those stations to the TV. If yes:

  ```bash
  scripts/import-cliamp.py            # all playlists, or pass specific .toml files
  ```

  It keeps live-stream entries in order with their names and genres, skips single tracks, and
  colours each station by mood. It only reads the CLI AMP files.
- **No CLI AMP:** keep the shipped `config/stations.json` — 17 stations (lo-fi, ambient, SomaFM,
  synthwave, EDM, chiptune, Bollywood), all tested on a TV.
- To edit by hand: `{"title", "url", "genre", "color"}` per station. MP3/AAC Icecast/Shoutcast
  streams work. `color` is optional.

CLI AMP itself can't run on the TV (it's a desktop terminal app); the TV plays the same streams
with Android's MediaPlayer.

## How it works (`RadioService`, `Radio`, `Spectrum`)

- A foreground service streams one station with `MediaPlayer`, keeps a Wi-Fi lock, and keeps
  playing while the user browses the rest of the TV. An "On air" chip appears on the home screen.
- **Redirects:** MediaPlayer won't follow a redirect to a *different host*. The service resolves
  the final URL with `HttpURLConnection` first (needed for e.g. `streams.dez.ovh`).
- **Song titles:** every 20 s a side connection asks for ICY metadata (`Icy-MetaData: 1`), reads one
  metadata block (`StreamTitle='…'`) and closes. A few KB each time.
- **Reconnects** up to 3 times with back-off; then shows "Station unreachable".
- **Visualizer:** `android.media.audiofx.Visualizer` on the player's audio session — 48 log-spaced
  bands from 90 Hz–16 kHz with smoothstep contrast, peak caps, a mirrored reflection, a bass-driven
  glow and a waveform line on the baseline. It needs `RECORD_AUDIO` (granted by `install.sh`; it
  reads only the radio's own output, not the microphone). It only says **"LIVE SPECTRUM"** while real
  FFT data is arriving; otherwise it says it's waiting and draws nothing that moves — never fake it.

## Remote

Browser: D-pad + OK to play. Now Playing: ◀ ▶ station, OK pause, ▼ stop, Back to the browser.
