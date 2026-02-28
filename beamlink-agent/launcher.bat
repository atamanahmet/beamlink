@echo off
setlocal enabledelayedexpansion

set JAR_NAME=beamlink-agent.jar
set SIGNAL_FILE=update.ready
set LOG_FILE=logs\launcher.log

echo [%date% %time%] Launcher started >> %LOG_FILE%

:loop
REM Detect IP
set IP=
for /f "tokens=2 delims=:" %%a in ('ipconfig ^| findstr /c:"IPv4 Address"') do (
    set RAWIP=%%a
    set RAWIP=!RAWIP: =!
    echo !RAWIP! | findstr /b "192.168." >nul
    if !errorlevel! == 0 (
        if "!IP!"=="" set IP=!RAWIP!
    )
)
:start
set IP=%IP: =%
echo [%date% %time%] Detected IP: %IP% >> %LOG_FILE%

REM Start agent
start /b "" java -jar %JAR_NAME% --spring.config.location=./config/ --agent.ip-address=%IP%

timeout /t 3 /nobreak >nul

REM Capture PID
set AGENT_PID=
for /f "tokens=2" %%a in ('tasklist /fi "imagename eq java.exe" /fo list ^| findstr "PID"') do (
    if "!AGENT_PID!"=="" set AGENT_PID=%%a
)
echo [%date% %time%] Agent PID: %AGENT_PID% >> %LOG_FILE%

timeout /t 5 /nobreak >nul

:watch
if exist %SIGNAL_FILE% (
    echo [%date% %time%] Update signal. Stopping PID %AGENT_PID%... >> %LOG_FILE%
    taskkill /f /pid %AGENT_PID% >nul 2>&1
    timeout /t 3 /nobreak >nul
    del %SIGNAL_FILE%
    goto loop
)

tasklist /fi "pid eq %AGENT_PID%" 2>nul | find /i "java.exe" >nul
if errorlevel 1 (
    echo [%date% %time%] Agent crashed. Restarting... >> %LOG_FILE%
    goto loop
)

timeout /t 3 /nobreak >nul
goto watch