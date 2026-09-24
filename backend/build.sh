#!/usr/bin/env bash
# Compile the APEX Java backend (requires JDK 11+).
set -e
cd "$(dirname "$0")"
mkdir -p out
javac -encoding UTF-8 -cp "lib/*" -d out $(find src -name "*.java")
echo "Build OK -> backend/out"
