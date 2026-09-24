#!/usr/bin/env bash
# Build (if needed) and start the APEX full-stack server on PORT (default 3030).
set -e
cd "$(dirname "$0")"
if [ ! -f out/com/apex/Main.class ]; then bash build.sh; fi
exec java -Dapex.home="$(pwd)" -cp "out:lib/*" com.apex.Main
