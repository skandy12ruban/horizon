# 0 · Requirements

## The TV

- Android TV / Google TV, **Android 8.0 or newer** (API 26+). Built and tested on a Xiaomi Mi TV
  (Android 11, MediaTek MT5867, 4× Cortex-A55, Mali-G31, 1.4 GB RAM, 32-bit userspace) — a modest
  TV, so anything newer will be comfortable.
- **Android System WebView 100+** (Retro TV and The World's Windows use it). Check:
  `tv shell dumpsys webviewupdate | grep "Current WebView"`. Update it from the Play Store if old.
- On the same Wi-Fi network as the computer.

## The computer

macOS is the reference (Little Office is macOS-only). Linux works for everything except Little Office.

| Tool | Why | Install (macOS, Homebrew) |
|---|---|---|
| adb (platform-tools) | talk to the TV | `brew install --cask android-platform-tools` |
| Android SDK build-tools + a platform | build the launcher (no Gradle, no Android Studio) | see below |
| JDK 17+ | compile | `brew install openjdk@17` (then follow its `PATH` note) |
| python3 | scripts | preinstalled, or `brew install python` |
| ffmpeg (optional) | crop window previews | `brew install ffmpeg` |

### SDK without Android Studio

```bash
brew install --cask android-commandlinetools
export ANDROID_HOME="$HOME/Library/Android/sdk"
yes | sdkmanager --sdk_root="$ANDROID_HOME" "platform-tools" "build-tools;34.0.0" "platforms;android-34"
```

`scripts/env.sh` looks for the SDK in `$ANDROID_HOME`, `$ANDROID_SDK_ROOT`,
`~/Library/Android/sdk` and `~/Android/Sdk`, and picks the newest build-tools and platform.

Run `scripts/check-env.sh` until it prints `READY`.

Note: if `adb` exists only inside the SDK (not on `PATH`), that's fine — the scripts use the SDK's
copy. Don't tell the user to run bare `adb`; use `scripts/` or the `tv` helper.
