@echo off
cd /d "%~dp0"
if not exist "dist\Jamb-Multiplayer.jar" (
  call "scripts\build.bat"
  if errorlevel 1 pause & exit /b 1
)
echo Jamb dedicated server listening on TCP port 5050.
echo Leave this window open while your friends are playing.
java -jar "dist\Jamb-Multiplayer.jar" --server 5050
pause
