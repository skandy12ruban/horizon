# 6 · Retro TV

Channel-surf the 80s, 90s, 2000s and 2010s by category — Cartoons, Music, TV shows, Movies, Comedy,
Science — with a D-pad remote. Fully automated; nothing to configure.

## Architecture (read this before changing anything)

The programming comes from **[MyRetroTVs](https://www.myretrotvs.com/)** (`80s.`, `90s.`, `00s.`,
`10s.myretrotvs.com`): thousands of curated YouTube clips per decade, organised into channels and
categories. Its own interface is a desktop/phone page (a skeuomorphic TV set, mouse + keyboard
shortcuts) — **unusable with a TV remote**, and rendering it on a TV is slow. So:

1. A **hidden** WebView (2 × 2 px) loads the decade site with a desktop user agent. It's never shown.
2. On load (**once per page load** — the site rewrites the URL hash on every channel change, which
   re-fires `onPageFinished`; booting again would toggle the set *off*), we inject a script that:
   - replaces the site player's `loadVideoById` with a bridge call: when the site picks a programme
     we get its video id, and the site's own player never plays (no double playback, no overlay);
   - mutes the site's sound effects via `Howler.mute(true)` — otherwise, because its player never
     reports "playing", it keeps looping its "no signal" static at full volume.
3. We power the set on (`keyCode 32`) and apply the category with the site's filter checkboxes
   (`#filter_none` + `#box_<id>`), then change channel (`keyCode 39`/`37`).
4. A second, visible WebView (`assets/retro.html`) plays the chosen video id with the YouTube IFrame
   Player, joining partway through like a live broadcast.
5. **Science** isn't on the decade sites, so it uses our own channels in `Lineup.java` — YouTube
   playlists (and channel upload feeds `UU…`); multi-playlist channels switch show after each
   programme so they behave like stations.

Category → site filters: Cartoons `c`, Music `m`, TV shows `d z g t k` (drama, soaps, game shows,
talk shows, kids), Movies `e f` (movies, trailers), Comedy `s`.

## Keeping YouTube's own UI off screen

YouTube shows its title bar, pause button, logo and "More videos" for a few seconds whenever a video
starts, seeks or is paused, and `controls=0` doesn't remove them. So:
- a black **cover** stays over the player until 2.8 s after the post-seek "playing" event;
- while **paused**, the cover stays up behind a "Paused" card;
- captions are unloaded (`cc_load_policy=0` + `unloadModule('captions')`);
- a short (0.4 s) analogue-static burst plays between channels.

## Remote

◀ ▶ channel · ▲ ▼ category · **OK** pause/resume · **hold OK** (or ☰) guide · Back home.
In the guide, every choice (decade, category, Science channel) tunes immediately.

## Limits to tell the user

- Clips come from YouTube via MyRetroTVs; some get removed — the player skips them automatically.
- YouTube may show its own ads.
- If MyRetroTVs changes its page structure (element ids `lcdbar`, `filter_none`, `box_*`, the
  `myytplayer` player or Howler), the injected script needs updating — see
  `docs/troubleshooting.md` → Retro TV.
