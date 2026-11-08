#!/bin/bash
# Final mix: seg/video.mp4 + music.wav (ducked) + audio.mp3 (voice-over from 0:00) -> horizon_linkedin.mp4
set -e; cd "$(dirname "$0")"
V=$(echo "$(ffprobe -v error -count_frames -select_streams v -show_entries stream=nb_read_frames -of csv=p=0 seg/video.mp4) / 30" | bc -l)
F=$(echo "$V - 2.5" | bc -l)
ffmpeg -loglevel error -y -i seg/video.mp4 -i music.wav -i audio.mp3 -filter_complex "
[2:a]aresample=48000,pan=stereo|c0=c0|c1=c0,highpass=f=80,lowpass=f=12000,acompressor=threshold=-20dB:ratio=2.5:attack=8:release=150,volume=1.4,apad=whole_dur=$V[vo];
[vo]asplit=2[vo1][vo2];
[1:a]aresample=48000,apad=whole_dur=$V,atrim=0:$V,afade=t=out:st=$F:d=2.5,volume=0.9[m];
[m][vo1]sidechaincompress=threshold=0.03:ratio=8:attack=30:release=400[md];
[md][vo2]amix=inputs=2:normalize=0,loudnorm=I=-14:TP=-1.5[a]" -map 0:v -map "[a]" -t $V \
  -c:v libx264 -crf 17 -preset slow -pix_fmt yuv420p -c:a aac -b:a 192k -movflags +faststart horizon_linkedin.mp4
echo "V=$V"
