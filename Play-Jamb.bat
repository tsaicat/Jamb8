@echo off
cd /d "%~dp0"
if not exist "dist\Jamb-Multiplayer.jar" (
  call "scripts\build.bat"
  if errorlevel 1 pause & exit /b 1
)
start "Jamb" javaw -jar "dist\Jamb-Multiplayer.jar"
if errorlevel 1 (
  echo Cannot open the game. Please install Java 17 or newer.
  pause
)
