#!/bin/bash
# Builds horizon_linkedin.mp4: TV recordings (raw/), preview renders (frames/), overlays/, voice-over (vo/), music.wav.
set -e
cd "$(dirname "$0")"
E=(-c:v libx264 -crf 17 -preset slow -pix_fmt yuv420p -r 30)
dur() { ffprobe -v error -show_entries format=duration -of csv=p=0 "$1"; }

# --- scene sources, trimmed to fit the narration
ffmpeg -loglevel error -y -ss 0.8 -t 7.2 -i raw/a_home.mkv   -vf "crop=1920:1080:0:0,fps=30" "${E[@]}" seg/a.mp4
ffmpeg -loglevel error -y -ss 1.4 -t 3.6 -i raw/b_allapps.mkv -vf "crop=1920:1080:0:0,fps=30" "${E[@]}" seg/b.mp4
ffmpeg -loglevel error -y -ss 1.8 -t 4.6 -i raw/c_radio.mkv   -vf "crop=1920:1080:0:0,fps=30" "${E[@]}" seg/c.mp4
ffmpeg -loglevel error -y -framerate 24 -i frames/tl_%04d.png -vf "scale=1920:1080:flags=lanczos,fps=30" "${E[@]}" seg/tl.mp4
ffmpeg -loglevel error -y -framerate 30 -i frames/wx_%04d.png -vf "scale=1920:1080:flags=lanczos" "${E[@]}" seg/wx.mp4

cap() { # seg caption out
  D=$(dur seg/$1.mp4); OUT=$(echo "$D - 0.95" | bc -l)
  ffmpeg -loglevel error -y -i seg/$1.mp4 -loop 1 -t $D -i overlays/$2.png -filter_complex \
   "[1]format=rgba,fade=in:st=0.55:d=0.4:alpha=1,fade=out:st=$OUT:d=0.4:alpha=1[c];[0][c]overlay=0:0,format=yuv420p" "${E[@]}" -t $D seg/$3.mp4
}
cap a cap_a s2; cap tl cap_tl s3; cap wx cap_wx s4; cap c cap_c s5; cap b cap_b s6
card() { # bgframe overlay dur out
  ffmpeg -loglevel error -y -framerate 30 -loop 1 -t $3 -i frames/$1 -framerate 30 -loop 1 -t $3 -i overlays/$2.png -filter_complex \
   "[0]scale=1920:1080,boxblur=18:2,eq=brightness=-0.16:saturation=1.1,zoompan=z='1.0+0.0007*on':d=1:s=1920x1080:fps=30[bg];[1]format=rgba,fade=in:st=0.2:d=0.6:alpha=1[t];[bg][t]overlay=0:0,format=yuv420p" "${E[@]}" -t $3 seg/$4.mp4
}
card tl_0118.png title 5.4 s1; card tl_0158.png end 5.6 s7

# --- a different transition for each change of scene
T=(smoothup hblur radial smoothleft circleopen fadeblack); X=0.6
o=0; f=""; prev="[0]"; starts=(0)
for i in 1 2 3 4 5 6; do
  o=$(echo "$o + $(dur seg/s$i.mp4) - $X" | bc -l); starts+=($o)
  f="$f${prev}[$i]xfade=transition=${T[$((i-1))]}:duration=$X:offset=$o[v$i];"; prev="[v$i]"
done
ffmpeg -loglevel error -y -i seg/s1.mp4 -i seg/s2.mp4 -i seg/s3.mp4 -i seg/s4.mp4 -i seg/s5.mp4 -i seg/s6.mp4 -i seg/s7.mp4 \
  -filter_complex "${f%;}" -map "[v6]" "${E[@]}" seg/video.mp4
V=$(echo "$(ffprobe -v error -count_frames -select_streams v -show_entries stream=nb_read_frames -of csv=p=0 seg/video.mp4) / 30" | bc -l)  # container duration is unreliable after xfade

# --- narration: one line per scene, starting just after each transition; music ducks under the voice
a=""; mix=""
for i in 0 1 2 3 4 5 6; do
  d=$(printf "%.0f" "$(echo "(${starts[$i]} + 0.45) * 1000" | bc -l)")
  a="$a[$((i+1)):a]aresample=44100,pan=stereo|c0=c0|c1=c0,adelay=${d}|${d}[l$i];"; mix="$mix[l$i]"
done
ffmpeg -loglevel error -y -i music.wav -i vo/l1.aiff -i vo/l2.aiff -i vo/l3.aiff -i vo/l4.aiff -i vo/l5.aiff -i vo/l6.aiff -i vo/l7.aiff -filter_complex "
${a}${mix}amix=inputs=7:normalize=0,volume=1.6,highpass=f=80,acompressor=threshold=-18dB:ratio=3:attack=5:release=120,apad=whole_dur=$V[vo];
[vo]asplit=2[vo1][vo2];
[0:a]apad=whole_dur=$V,atrim=0:$V,afade=t=out:st=$(echo "$V - 2.5" | bc -l):d=2.5,volume=0.9[m];
[m][vo1]sidechaincompress=threshold=0.03:ratio=8:attack=30:release=400[md];
[md][vo2]amix=inputs=2:normalize=0,loudnorm=I=-14:TP=-1.5[out]" -map "[out]" -t $V -c:a pcm_s16le seg/audio.wav
ffmpeg -loglevel error -y -i seg/video.mp4 -i seg/audio.wav -c:v libx264 -crf 17 -preset slow -pix_fmt yuv420p -r 30 -c:a aac -b:a 192k -movflags +faststart horizon_linkedin.mp4
echo "$(dur horizon_linkedin.mp4)s $(du -h horizon_linkedin.mp4 | cut -f1)  scene starts: ${starts[*]}"
