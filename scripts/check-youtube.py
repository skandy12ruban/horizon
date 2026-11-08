#!/usr/bin/env python3
"""Checks that every YouTube video (World's Windows) and playlist (Retro TV Science) still exists and
can be embedded. Run before building; replace anything reported as GONE or NO-EMBED.

Usage: scripts/check-youtube.py
"""
import json, os, re, urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
UA = {"User-Agent": "Mozilla/5.0 (Macintosh) AppleWebKit/537.36 Chrome/150 Safari/537.36",
      "Accept-Language": "en-US,en;q=0.9", "Cookie": "CONSENT=YES+1"}


def page(url):
    return urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=20).read().decode("utf-8", "ignore")


def player(html):
    m = re.search(r"ytInitialPlayerResponse\s*=\s*(\{.+?\});\s*(?:var |</script>)", html, re.S)
    return json.loads(m.group(1)) if m else {}


bad = 0
for w in json.load(open(os.path.join(ROOT, "config", "windows.json")))["windows"]:
    try:
        p = player(page("https://www.youtube.com/watch?v=" + w["youtube"]))
        st = p.get("playabilityStatus", {})
        ok, emb = st.get("status") == "OK", st.get("playableInEmbed")
        verdict = "ok" if ok and emb else ("NO-EMBED" if ok else "GONE")
    except Exception as e:
        verdict = "ERROR " + str(e)[:40]
    bad += verdict != "ok"
    print(f"{verdict:9} window   {w['youtube']}  {w['name']}")

src = open(os.path.join(ROOT, "launcher", "src", "tv", "steroid", "home", "Lineup.java")).read()
for pid in sorted(set(re.findall(r'"((?:PL|UU)[A-Za-z0-9_-]{10,})"', src))):
    try:
        html = page("https://www.youtube.com/playlist?list=" + pid)
        verdict = "ok" if '"playlistId"' in html or "videoCount" in html else "GONE"
    except Exception as e:
        verdict = "ERROR " + str(e)[:40]
    bad += verdict != "ok"
    print(f"{verdict:9} playlist {pid}")
print("ALL GOOD" if bad == 0 else f"{bad} problem(s) — see above")
