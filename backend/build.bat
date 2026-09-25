REM APEX backend build (Windows)
@echo off
setlocal
cd /d "%~dp0"
if exist out rmdir /s /q out
mkdir out || exit /b 1
echo Compiling APEX backend...
REM Collect every Java source; a redirection inside the loop would overwrite
REM sources.txt on each iteration and only compile the last file.
> sources.txt (
  for /r "%~dp0src" %%f in (*.java) do @echo "%%~ff"
)
javac --release 17 -encoding UTF-8 -cp "lib/*" -d out @sources.txt
if errorlevel 1 (
  del sources.txt
  exit /b 1
)
del sources.txt
echo Build OK
