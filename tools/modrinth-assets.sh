#!/usr/bin/env bash
# Uploads the project icon and gallery to Modrinth. Safe to re-run: images whose title is already
# in the gallery are skipped. Needs MODRINTH_TOKEN (a personal access token with PROJECT_WRITE).
# Usage: MODRINTH_TOKEN=mrp_... tools/modrinth-assets.sh [project-slug]
set -euo pipefail
cd "$(dirname "$0")/.."
: "${MODRINTH_TOKEN:?set MODRINTH_TOKEN to a Modrinth personal access token with PROJECT_WRITE}"
project="${1:-throughput}"
api="https://api.modrinth.com/v2/project/$project"
agent="razekteixeira/throughput (github.com/razekteixeira/throughput)"

existing=$(curl -sf -H "User-Agent: $agent" "$api" | python3 -c 'import json, sys; print("\n".join(g.get("title") or "" for g in json.load(sys.stdin)["gallery"]))')

upload() { # file featured ordering title description
	local file=$1 featured=$2 ordering=$3 title=$4 description=$5
	if grep -qxF "$title" <<< "$existing"; then
		echo "skip   $title (already in the gallery)"
		return
	fi
	local size; size=$(wc -c < "$file")
	if [ "$size" -gt $((5 * 1024 * 1024)) ]; then
		echo "error  $file is over Modrinth's 5 MiB gallery limit" >&2
		return 1
	fi
	local query
	query=$(python3 -c 'import sys, urllib.parse as u; print(u.urlencode(dict(zip(["ext", "featured", "ordering", "title", "description"], sys.argv[1:]))))' \
		"${file##*.}" "$featured" "$ordering" "$title" "$description")
	curl -sf -X POST -H "Authorization: $MODRINTH_TOKEN" -H "User-Agent: $agent" -H "Content-Type: image/${file##*.}" \
		"$api/gallery?$query" --data-binary "@$file" -o /dev/null
	echo "added  $title"
}

curl -sf -X PATCH -H "Authorization: $MODRINTH_TOKEN" -H "User-Agent: $agent" -H "Content-Type: image/png" \
	"$api/icon?ext=png" --data-binary @branding/icon-512.png -o /dev/null && echo "icon   updated"

upload site/media/banner.png         true  0 "Throughput" "Factorio-style production stats for any Minecraft factory."
upload site/media/gallery-night.png  false 1 "Night shift" "A six-furnace smelting floor tracked as one factory."
upload site/media/stats.png          false 2 "Rates and sparklines" "/flow stats: produced and consumed per minute, with history."
upload site/media/alerts.png         false 3 "Grouped alerts" "/flow alerts: blocked outputs, emptied buffers and time-to-empty."
upload site/media/watch.gif          false 4 "Live ticker" "/flow watch: the factory's top output and input on your action bar."
upload site/media/gallery-angle.png  false 5 "Any container" "Chests, hoppers, furnaces and modded storage, read without client mods."
upload site/media/gallery-closeup.png false 6 "One line up close" "Input chest, hopper, furnace, hopper, output chest."
