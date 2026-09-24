@echo off
rem Build (if needed) and start the APEX full-stack server (default port 8080).
cd /d "%~dp0"
if not exist out\com\apex\Main.class call build.bat
java -Dapex.home="%cd%" -cp "out;lib/*" com.apex.Main
pause
