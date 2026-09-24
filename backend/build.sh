#!/usr/bin/env bash
# APEX backend build: compiles all Java sources into out/.
# Usage: APEX_HOME=... bash build.sh   (defaults to the directory of this script)
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$HERE"

rm -rf out
mkdir -p out

echo "Compiling APEX backend..."
find src -name '*.java' -print0 | xargs -0 javac --release 17 -encoding UTF-8 -cp "lib/*" -d out
echo "Build OK -> $HERE/out"
