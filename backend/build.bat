@echo off
rem Compile the APEX Java backend (requires JDK 11+).
cd /d "%~dp0"
if not exist out mkdir out
dir /s /b src\*.java > sources.txt
javac -encoding UTF-8 -cp "lib/*" -d out @sources.txt
del sources.txt
echo Build OK -^> backend\out
