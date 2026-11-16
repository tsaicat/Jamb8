#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
if [ ! -f dist/Jamb-Multiplayer.jar ]; then bash scripts/build.sh; fi
java -jar dist/Jamb-Multiplayer.jar
