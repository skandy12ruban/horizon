# 2 · Choose the apps

## List what the user can actually open

```bash
scripts/inventory.sh
```

Output, one per line: `package|yes` (installed by the user) or `package|no` (came with the TV).
It already hides system plumbing and everything in `config/never-disable.txt` (Settings, TV inputs,
Play Store, Google services, keyboards…), so what's left is what a person would recognise.

## Ask the user

Turn packages into friendly names with `config/known-apps.json`; for anything not there, infer a
name from the package (`com.zee5.androidtv` → ZEE5). Then ask which to keep on the home screen.

- Show **only** real, user-facing apps: streaming, music, games, tools. Don't show launchers
  (`tvlauncher`, `tvhome`…), settings, live-TV/inputs, updaters, demos or factory apps.
- Put the most likely picks first (big streaming apps, YouTube, music) and suggest a sensible
  default set, but let them choose freely and order them.
- If your question tool limits options per question, split into two questions (e.g. "Streaming"
  and "Music & other") or list them in chat and let the user reply with names.
- Anything **not** kept will be switched off in the next phase (reversibly). Say so.

## Write the config

`config/apps.json` — in the user's order:

```json
{
  "apps": [
    {"package": "com.google.android.youtube.tv", "label": "YouTube", "accent": "#FF3B3B"},
    {"package": "com.netflix.ninja", "label": "Netflix", "accent": "#E50914"}
  ]
}
```

- `accent`: from `known-apps.json`, or the app's brand colour. It tints the ambient background when
  the card is focused.
- The home screen shows each app's own **banner** artwork (`getApplicationBanner`), so it looks
  right without any image work. 4–7 apps fit one row; more is fine but the row gets narrow.

`backup/keep.txt` — the same packages, one per line (used by `debloat.sh`).
