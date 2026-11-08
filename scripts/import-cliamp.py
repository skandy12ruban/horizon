#!/usr/bin/env python3
"""Import radio stations from CLI AMP (https://github.com/bjarneo/cliamp) into config/stations.json.

Reads the user's CLI AMP playlists (~/.config/cliamp/playlists/*.toml), keeps live-stream entries
(those with `realtime = true` or an http(s) path that isn't a single track), preserves their order
and names, and gives each a mood colour from its genre. Leaves config/stations.json untouched if no
playlist is found (the shipped default list stays).

Usage: scripts/import-cliamp.py [playlist.toml ...]
"""
import glob
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "config", "stations.json")

# genre keyword -> colour (warm lo-fi, misty ambient, deep space, neon electronic, saffron Indian)
MOODS = [
    (("lofi", "lo-fi", "chill", "jazz", "study"), "#E8915A"),
    (("meditat", "sleep", "drone"), "#8FB8A8"),
    (("ambient", "space"), "#5F7FB8"),
    (("synth", "retro", "80s"), "#FF4FA3"),
    (("edm", "electronic", "dance", "techno", "house"), "#39D0FF"),
    (("chip", "8bit", "amiga", "game"), "#9BE15D"),
    (("bollywood", "hindi", "india", "desi"), "#FF7A2F"),
    (("rock", "metal", "punk"), "#E84A4A"),
    (("classical", "piano", "orchestra"), "#C9A46A"),
    (("news", "talk", "podcast"), "#8F9BB3"),
]
FALLBACK = ["#E8915A", "#8FB8A8", "#5F7FB8", "#8A6CE0", "#FF4FA3", "#39D0FF", "#9BE15D", "#FFB347"]


def parse_toml_tracks(text):
    """Minimal parser for CLI AMP playlists: [[track]] tables with key = value lines."""
    tracks, cur = [], None
    for line in text.splitlines():
        s = line.strip()
        if s == "[[track]]":
            cur = {}
            tracks.append(cur)
            continue
        if cur is None or not s or s.startswith("#") or "=" not in s:
            continue
        k, v = s.split("=", 1)
        v = v.strip()
        if v.startswith('"') and v.endswith('"'):
            v = bytes(v[1:-1], "utf-8").decode("unicode_escape").encode("latin-1").decode("utf-8")
        elif v in ("true", "false"):
            v = v == "true"
        cur[k.strip()] = v
    return tracks


def colour(title, genre, i):
    hay = (title + " " + genre).lower()
    for keys, c in MOODS:
        if any(k in hay for k in keys):
            return c
    return FALLBACK[i % len(FALLBACK)]


def main():
    files = sys.argv[1:] or sorted(glob.glob(os.path.expanduser("~/.config/cliamp/playlists/*.toml")))
    stations = []
    for f in files:
        for t in parse_toml_tracks(open(f, encoding="utf-8").read()):
            url = t.get("path", "")
            if not re.match(r"https?://", url):
                continue
            if not t.get("realtime") and "/tracks/" in url:
                continue  # a single song, not a station
            title = t.get("title") or url
            genre = t.get("genre", "").replace("|", " · ")
            stations.append({"title": title, "url": url, "genre": genre or "radio",
                             "color": colour(title, genre, len(stations))})
    if not stations:
        print("No CLI AMP playlist with stations found — keeping the default config/stations.json")
        return
    json.dump({"_comment": "Imported from CLI AMP: " + ", ".join(os.path.basename(f) for f in files),
               "stations": stations}, open(OUT, "w"), indent=2, ensure_ascii=False)
    print(f"Imported {len(stations)} stations into config/stations.json")


if __name__ == "__main__":
    main()
