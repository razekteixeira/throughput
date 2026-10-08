#!/usr/bin/env bash
# Regenerates site/media from a real client run.
# Usage: caffeinate -dimsu ./gradlew runClientGameTest && branding/make_media.sh && python3 -P branding/make_banners.py
# (caffeinate keeps macOS from sleeping the display, which stalls the client window)
# Needs ffmpeg and ImageMagick. The chat panel text in site/index.html must be copied from the same run's
# log lines "[System] [CHAT] Factory smelter ..." and "[System] [CHAT] Alerts for smelter ...".
set -euo pipefail
cd "$(dirname "$0")/.."
shots=build/run/clientGameTest/screenshots
media=site/media
frames=$(mktemp -d)
trap 'rm -rf "$frames"' EXIT

cp "$shots"/*_hero.png "$media/hero.png"
cp "$shots"/*_stats.png "$media/stats.png"
cp "$shots"/*_alerts.png "$media/alerts.png"
for shot in angle night closeup; do
	cp "$shots"/*_gallery_${shot}.png "$media/gallery-${shot}.png"
done
i=0
find "$shots" -name '*_watch_*.png' | sort | while read -r f; do cp "$f" "$frames/$(printf '%03d' "$i").png"; i=$((i + 1)); done
ffmpeg -loglevel error -y -framerate 4 -i "$frames/%03d.png" \
	-vf "scale=800:-1:flags=lanczos,split[a][b];[a]palettegen=max_colors=128[p];[b][p]paletteuse=dither=bayer:bayer_scale=4" \
	-loop 0 "$media/watch.gif"
for f in "$media"/*.png; do magick "$f" -strip -define png:compression-level=9 "$f"; done
echo "media updated from $(find "$frames" -name '*.png' | wc -l | tr -d ' ') watch frames"
