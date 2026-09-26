@echo off
setlocal EnableExtensions
title APEX Renting - Java Build
set "NO_PAUSE="
if /I "%~1"=="--no-pause" set "NO_PAUSE=1"
cd /d "%~dp0" || goto :wrong_folder
where javac >nul 2>&1
if errorlevel 1 goto :no_javac
if not exist "src\com\apex\Main.java" goto :not_extracted
if not exist "lib\gson-2.10.1.jar" goto :not_extracted

if exist out rmdir /s /q out
mkdir out
if errorlevel 1 goto :build_failed
echo Compiling all APEX Java sources...
REM Put every source path in the argument file (including paths with spaces).
> sources.txt (
  for /r "%~dp0src" %%f in (*.java) do @echo "%%~ff"
)
javac --release 17 -encoding UTF-8 -cp "lib/*" -d out @sources.txt
if errorlevel 1 goto :build_failed
if exist sources.txt del sources.txt
echo Build OK. Run run.bat to start the Java backend.
if not defined NO_PAUSE pause
exit /b 0

:no_javac
echo.
echo ERROR: javac was not found. Install JDK 17, or run START_APEX.bat
echo from the extracted root folder using Docker Desktop instead.
goto :failure

:not_extracted
echo.
echo ERROR: Java source files or gson library missing.
echo Right-click the ZIP and select Extract All before running BAT files.
goto :failure

:wrong_folder
echo.
echo ERROR: Cannot open the backend folder.
goto :failure

:build_failed
echo.
echo ERROR: Build failed. See compiler messages above. JDK 17+ is required.
if exist sources.txt del sources.txt
goto :failure

:failure
if not defined NO_PAUSE pause
exit /b 1
