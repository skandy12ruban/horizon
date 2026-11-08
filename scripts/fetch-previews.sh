#!/bin/bash
# Downloads a preview image for every World's Windows entry (YouTube's own thumbnail) into
# launcher/assets/windows/<id>.jpg. Falls back to the 4:3 thumbnail, cropped to 16:9, when a video
# has no high-resolution one (YouTube then serves a grey placeholder).
. "$(dirname "$0")/env.sh"
D="$ROOT/launcher/assets/windows"; mkdir -p "$D"
ids=$("$PY" -c "import json;print(' '.join(w['youtube'] for w in json.load(open('$ROOT/config/windows.json'))['windows']))")
for id in $ids; do
  [ -s "$D/$id.jpg" ] && continue
  tmp="$D/.tmp.jpg"
  curl -sL -m 20 -o "$tmp" "https://i.ytimg.com/vi/$id/maxresdefault.jpg"
  if [ ! -s "$tmp" ] || [ "$(wc -c < "$tmp")" -lt 8000 ]; then   # missing or the grey placeholder
    curl -sL -m 20 -o "$tmp" "https://i.ytimg.com/vi/$id/hqdefault.jpg"
    crop="crop=iw:ih*0.75,"
  else crop=""; fi
  if command -v ffmpeg >/dev/null; then
    ffmpeg -loglevel error -y -i "$tmp" -vf "${crop}scale=480:-2" -q:v 4 "$D/$id.jpg"
  else cp "$tmp" "$D/$id.jpg"; fi
  rm -f "$tmp"; echo "preview $id"
done
echo "previews ready ($(ls "$D" | wc -l | tr -d ' ') files)"
