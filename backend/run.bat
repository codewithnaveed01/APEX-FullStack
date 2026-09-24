REM APEX backend run (Windows, builds first if needed)
@echo off
setlocal
cd /d "%~dp0"
if not defined APEX_HOME set APEX_HOME=%~dp0
if not exist out\com\apex\Main.class call "%~dp0build.bat"
java -Dapex.home="%~dp0" -cp "out;lib\*" com.apex.Main
