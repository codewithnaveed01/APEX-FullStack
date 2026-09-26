@echo off
setlocal EnableExtensions
title APEX Renting - Local Start
cd /d "%~dp0" || goto :wrong_folder

if not exist "docker-compose.yml" goto :not_extracted
if not exist "Dockerfile" goto :not_extracted
if not exist "frontend\index.html" goto :not_extracted
if not exist "backend\seed.json" goto :not_extracted

if not defined APEX_LOCAL_PORT set "APEX_LOCAL_PORT=3030"
echo ========================================
echo APEX Renting - Local Windows Launcher
echo ========================================
echo.
echo Checking Docker Desktop...
where docker >nul 2>&1
if errorlevel 1 goto :no_docker
docker compose version >nul 2>&1
if errorlevel 1 goto :no_compose
docker info >nul 2>&1
if errorlevel 1 goto :docker_stopped

echo Building and starting website + PostgreSQL...
echo First run needs internet to download Docker images.
echo Your bookings are kept in Docker volumes between restarts.
echo.
docker compose up --build -d
if errorlevel 1 goto :compose_failed

echo.
echo Waiting for the website to become ready...
for /L %%N in (1,1,60) do (
  powershell -NoProfile -NonInteractive -Command "try { $null = Invoke-WebRequest -Uri 'http://127.0.0.1:%APEX_LOCAL_PORT%/health' -UseBasicParsing -TimeoutSec 3; exit 0 } catch { exit 1 }" >nul 2>&1
  if not errorlevel 1 goto :ready
  timeout /t 2 /nobreak >nul
)
echo.
echo ERROR: Website did not become ready on port %APEX_LOCAL_PORT%.
docker compose ps
docker compose logs --tail=50 app
goto :failure

:ready
echo.
echo SUCCESS: Website is running at http://localhost:%APEX_LOCAL_PORT%/
echo Admin panel: http://localhost:%APEX_LOCAL_PORT%/admin.html
echo Local demo login: admin / admin1234
echo To stop later: docker compose down   (keeps your saved data)
echo.
start "" "http://localhost:%APEX_LOCAL_PORT%/"
echo Press any key to close this window. The website will keep running.
pause >nul
exit /b 0

:no_docker
echo.
echo ERROR: Docker command was not found.
echo Install and start Docker Desktop for Windows, then run START_APEX.bat again.
echo Without Docker: install JDK 17 + PostgreSQL, set DATABASE_URL,
echo and run backend\run.bat from the SAME PowerShell window.
goto :failure

:no_compose
echo.
echo ERROR: Docker Compose V2 is missing. Update Docker Desktop.
goto :failure

:docker_stopped
echo.
echo ERROR: Docker Desktop is installed but the engine is not running.
echo Open Docker Desktop and wait until it says Engine running, then retry.
goto :failure

:compose_failed
echo.
echo ERROR: Docker could not build or start the app. Read the error above.
echo If port %APEX_LOCAL_PORT% is busy, open PowerShell in this folder and run:
echo   $env:APEX_LOCAL_PORT = '3031'
echo   .\START_APEX.bat
echo Then open http://localhost:3031/
echo.
docker compose ps
goto :failure

:not_extracted
echo.
echo ERROR: This script must be run from the FULL extracted ZIP folder.
echo Right-click the ZIP, select Extract All, then double-click
echo START_APEX.bat inside the extracted APEX-Renting-Local folder.
goto :failure

:wrong_folder
echo.
echo ERROR: Cannot open the extracted project folder.
goto :failure

:failure
echo.
echo The error is shown above. This window will stay open for you to read it.
pause
exit /b 1
