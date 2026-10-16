@echo off
setlocal
cd /d "%~dp0"
set "SCOUTER_JAVA=java"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "SCOUTER_JAVA=%JAVA_HOME%\bin\java.exe"
if exist "%LOCALAPPDATA%\RuneLite\jre\bin\java.exe" set "SCOUTER_JAVA=%LOCALAPPDATA%\RuneLite\jre\bin\java.exe"
"%SCOUTER_JAVA%" -version >nul 2>&1
if errorlevel 1 (
  echo Java was not found. Install Java 11 or newer, then run START.cmd again.
  echo Official download: https://adoptium.net/temurin/releases/
  pause
  exit /b 1
)
"%SCOUTER_JAVA%" -ea -jar wintertodt-scouter-test-client.jar --developer-mode
if errorlevel 1 pause
