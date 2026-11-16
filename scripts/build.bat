@echo off
setlocal
cd /d "%~dp0.."
if not exist "build\classes" mkdir "build\classes"
if not exist "dist" mkdir "dist"
if exist "build\sources.txt" del "build\sources.txt"
for /R "src\main\java" %%f in (*.java) do echo "%%~ff">>"build\sources.txt"
rem javac from JDK 8 supports -source and -target 8, but NOT --release.
javac -source 8 -target 8 -encoding UTF-8 -d "build\classes" @build\sources.txt
if errorlevel 1 exit /b 1
jar cfe "dist\Jamb-Multiplayer.jar" com.jamb.Main -C "build\classes" .
if errorlevel 1 exit /b 1
echo Built dist\Jamb-Multiplayer.jar (Java 8 bytecode)
