#!/usr/bin/env bash
# APEX backend run (builds first if needed).
# Env: see .env.example / README.
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$HERE"

export APEX_HOME="${APEX_HOME:-$HERE}"

if [ ! -f out/com/apex/Main.class ]; then
  bash "$HERE/build.sh"
fi

java -Dapex.home="$HERE" -cp "out:lib/*" com.apex.Main
