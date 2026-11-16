@echo off
setlocal
cd /d "%~dp0.."
call scripts\build.bat
if errorlevel 1 exit /b 1
if not exist "build\test-classes" mkdir "build\test-classes"
if exist "build\test-sources.txt" del "build\test-sources.txt"
for /R "src\test\java" %%f in (*.java) do echo "%%~ff">>"build\test-sources.txt"
javac -source 8 -target 8 -encoding UTF-8 -cp "build\classes" -d "build\test-classes" @build\test-sources.txt
if errorlevel 1 exit /b 1
java -ea -cp "build\classes;build\test-classes" com.jamb.GameRulesTest
if errorlevel 1 exit /b 1
echo Java scoring tests passed. For network tests install Python 3.8+ and run: py -3 scripts\test_multiplayer.py
