@echo off
setlocal EnableExtensions
title APEX Renting - Java Server
cd /d "%~dp0" || goto :wrong_folder

if not exist "..\frontend\index.html" goto :not_extracted
if not exist "migrations\001_init.sql" goto :not_extracted
if not exist "lib\gson-2.10.1.jar" goto :not_extracted

REM This is the manual Java + PostgreSQL mode. Without a configured DB, the
REM one-click Docker launcher is the easiest way to run the complete stack.
if not defined DATABASE_URL if not defined PGHOST goto :use_docker
where java >nul 2>&1
if errorlevel 1 goto :no_java
if not exist "out\com\apex\Main.class" (
  where javac >nul 2>&1
  if errorlevel 1 goto :no_javac
  call "%~dp0build.bat" --no-pause
  if errorlevel 1 goto :build_failed
)
if not defined APEX_HOME set "APEX_HOME=%~dp0"
if not defined APEX_STATIC set "APEX_STATIC=%~dp0..\frontend"
echo Starting APEX at http://localhost:3030/
echo PostgreSQL must be running. Errors will remain visible below.
echo.
java -Dapex.home="%~dp0" -cp "out;lib/*" com.apex.Main
set "EXIT_CODE=%ERRORLEVEL%"
echo.
if not "%EXIT_CODE%"=="0" echo ERROR: APEX stopped with code %EXIT_CODE%. Check Java/PostgreSQL settings above.
if "%EXIT_CODE%"=="0" echo APEX server stopped.
echo Press any key to close this window.
pause >nul
exit /b %EXIT_CODE%

:use_docker
echo.
echo No DATABASE_URL or PGHOST was set for the Java backend.
if exist "..\START_APEX.bat" (
  echo Launching the complete app + database with Docker instead...
  call "..\START_APEX.bat"
  if errorlevel 1 exit /b 1
  exit /b 0
)
echo Extract the full ZIP and run START_APEX.bat from its root folder.
echo Or set DATABASE_URL in PowerShell and run this file there:
echo   $env:DATABASE_URL = 'postgres://USER:PASS@127.0.0.1:5432/apex'
echo   .\run.bat
goto :failure

:no_java
echo.
echo ERROR: Java was not found. Install JDK 17 or use START_APEX.bat with Docker.
goto :failure

:no_javac
echo.
echo ERROR: javac was not found. Install the full JDK 17, not just a JRE.
echo Or use START_APEX.bat with Docker Desktop.
goto :failure

:build_failed
echo.
echo ERROR: Java compilation failed. See the compiler error above.
goto :failure

:not_extracted
echo.
echo ERROR: Frontend/backend files are missing.
echo Right-click the ZIP and choose Extract All; do not run a single BAT inside ZIP.
goto :failure

:wrong_folder
echo.
echo ERROR: Cannot open the backend folder.
goto :failure

:failure
echo.
echo Press any key to close this window (the error is shown above).
pause
exit /b 1
