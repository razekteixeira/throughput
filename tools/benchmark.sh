#!/usr/bin/env bash
# Measures sampling cost on the dev server: one factory of 4,096 filled chests (the default limit),
# 40 s warm-up, then 15 readings of "last sample" from /flow factory list. Prints min/median/max ms.
# Usage: tools/benchmark.sh   (needs JDK 25 as JAVA_HOME; uses ./run, which is gitignored)
set -euo pipefail
cd "$(dirname "$0")/.."
java_bin="${JAVA_HOME:+$JAVA_HOME/bin/}java"
if ! "$java_bin" -version 2>&1 | grep -q 'version "25'; then
	echo "JDK 25 not found: set JAVA_HOME to a JDK 25 installation" >&2
	exit 1
fi
mkdir -p run
export RCON_PASSWORD
RCON_PASSWORD=$(python3 -c 'import secrets; print(secrets.token_hex(16))')
echo "eula=true" > run/eula.txt
cat > run/server.properties <<PROPS
server-ip=127.0.0.1
enable-rcon=true
rcon.port=25575
rcon.password=${RCON_PASSWORD}
level-type=minecraft\:flat
online-mode=false
spawn-monsters=false
PROPS
log=$(mktemp)
./gradlew runServer --console=plain > "$log" 2>&1 &
server=$!
trap 'python3 tools/rcon.py stop > /dev/null 2>&1 || true' EXIT
until grep -q "RCON running" "$log"; do
	if ! kill -0 "$server" 2>/dev/null || grep -q "BUILD FAILED" "$log"; then
		echo "Dev server failed to start, see $log" >&2
		exit 1
	fi
	sleep 2
done
rcon() { python3 tools/rcon.py "$@"; }
if ! rcon "flow factory list" | grep -q "bench ("; then
	rcon "forceload add 96 96 167 167" \
		"fill 100 0 100 163 0 163 chest{Items:[{Slot:0b,id:\"minecraft:stone\",count:64},{Slot:5b,id:\"minecraft:iron_ingot\",count:32},{Slot:9b,id:\"minecraft:coal\",count:16}]}" \
		"flow factory create bench" "flow addarea bench 100 0 100 163 0 163" > /dev/null
fi
sleep 40
readings=()
for _ in $(seq 15); do readings+=("flow factory list" "sleep 1.1"); done
rcon "${readings[@]}" | grep -oE "bench \([^)]*last sample [0-9.]+" | grep -oE "[0-9.]+$" | sort -n \
	| awk '{a[NR]=$1} END {printf "4096 chests: n=%d min=%.2f median=%.2f max=%.2f ms\n", NR, a[1], a[int((NR+1)/2)], a[NR]}'
