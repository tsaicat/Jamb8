#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/classes dist
find src/main/java -name '*.java' -print > build/sources.txt
# Java 8 source and bytecode. Works with a JDK 8 compiler, too.
javac -source 8 -target 8 -encoding UTF-8 -d build/classes @build/sources.txt
jar cfe dist/Jamb-Multiplayer.jar com.jamb.Main -C build/classes .
echo "Built dist/Jamb-Multiplayer.jar (Java 8 bytecode)"
