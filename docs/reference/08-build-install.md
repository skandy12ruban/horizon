# 8 · Build and install

```bash
scripts/build.sh      # → launcher/out/SteroidHome.apk
scripts/install.sh    # installs, makes it Home, grants permissions, disables stock launchers
```

## build.sh

- Copies `config/{apps,stations,windows,features}.json` into `launcher/assets/config/`.
- Fetches World's Windows previews (`fetch-previews.sh`) if missing.
- Compiles with plain `javac` → `d8` → `aapt2` → `zipalign` → `apksigner` (no Gradle).
  - `javac --release 8 -classpath android.jar` — **not** `-source 8 -target 8 -bootclasspath`,
    which fails on lambdas ("Unable to find method metafactory").
  - Source and class lists go through `@argfiles`, so paths with spaces work.
- Signs with `~/.android/debug.keystore` (created if missing). Keep the same keystore for updates;
  a different key means uninstalling first (`install -r` fails with a signature mismatch).

## install.sh

1. `adb install -r`.
2. `cmd package set-home-activity tv.steroid.home/.Home` and checks it took.
3. Grants `RECORD_AUDIO` (radio visualizer) and `SYSTEM_ALERT_WINDOW` (Little Office notice).
4. Only then disables the stock launchers in `config/debloat.json → launchers`. Order matters: never
   leave a TV without a working home screen.
5. Presses Home.

Re-run both after any config change. Reinstalling stops a playing radio station (the app restarts).

## The app

- Package `tv.steroid.home`, min SDK 26, target SDK 30, no third-party libraries.
- Activities: `Home` (HOME + LEANBACK_LAUNCHER), `Retro`, `Radio`, `Windows`, `OfficePanel`.
  Services: `RadioService`, `OfficeService`.
- Fonts (SIL OFL, licences in `launcher/assets/fonts/`): Instrument Serif (display), Hanken Grotesk
  (UI), DM Mono (labels).
