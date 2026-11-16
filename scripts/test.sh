#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
bash scripts/build.sh
mkdir -p build/test-classes
find src/test/java -name '*.java' -print > build/test-sources.txt
javac -source 8 -target 8 -encoding UTF-8 -cp build/classes -d build/test-classes @build/test-sources.txt
java -ea -cp "build/classes:build/test-classes" com.jamb.GameRulesTest
# Full integration test uses Python 3.8+ only as a test driver; the game itself is all Java 8.
python3 scripts/test_multiplayer.py
