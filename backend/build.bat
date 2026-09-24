REM APEX backend build (Windows)
@echo off
setlocal
cd /d "%~dp0"
if exist out rmdir /s /q out
mkdir out
echo Compiling APEX backend...
for /r src %%f in (*.java) do echo %%~ff > sources.txt
javac --release 17 -encoding UTF-8 -cp "lib/*" -d out @sources.txt || exit /b 1
del sources.txt
echo Build OK
