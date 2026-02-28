@echo off
setlocal enabledelayedexpansion

set JAR_NAME=beamlink-agent.jar
set CONFIG_FILE=config\application.yaml
set SIGNAL_FILE=update.ready
set LOG_FILE=logs\launcher.log
set MAX_RESTARTS=5
set RESTART_COUNT=0

if not exist logs mkdir logs
if not exist data\uploads mkdir data\uploads

REM ─── Clean stale signal file ──────────────────────────────────────────────────
if exist %SIGNAL_FILE% del %SIGNAL_FILE%

echo [%date% %time%] Launcher started >> %LOG_FILE%

REM ─── JWT ─────────────────────────────────────────────────────────────────────
(
echo $raw = Get-Content '%CONFIG_FILE%' -Raw
echo if ^($raw -match 'jwt-secret:\s*auto'^) {
echo     $bytes = New-Object byte[] 64
echo     [Security.Cryptography.RNGCryptoServiceProvider]::Create^(^).GetBytes^($bytes^)
echo     $secret = [Convert]::ToBase64String^($bytes^)
echo     $raw = $raw -replace 'jwt-secret:\s*auto', ^"jwt-secret: $secret^"
echo     [System.IO.File]::WriteAllText^('%CONFIG_FILE%', $raw^)
echo     Write-Host 'JWT generated.'
echo } else {
echo     Write-Host 'JWT already set.'
echo }
) > %temp%\bl_jwt.ps1
powershell -nologo -executionpolicy bypass -file %temp%\bl_jwt.ps1
del %temp%\bl_jwt.ps1

REM ─── Read IP from config ──────────────────────────────────────────────────────
(
echo $raw = Get-Content '%CONFIG_FILE%' -Raw
echo $m = [regex]::Match^($raw, 'ip-address:\s*^(\S+^)'^)
echo if ^($m.Success^) { $m.Groups[1].Value } else { 'auto' }
) > %temp%\bl_readip.ps1
set CONFIG_IP=
for /f %%a in ('powershell -nologo -executionpolicy bypass -file %temp%\bl_readip.ps1') do set CONFIG_IP=%%a
del %temp%\bl_readip.ps1

echo [%date% %time%] Config ip-address: !CONFIG_IP! >> %LOG_FILE%
echo Config ip-address: !CONFIG_IP!

REM ─── Main restart loop ────────────────────────────────────────────────────────
:loop
set /a RESTART_COUNT+=1
if !RESTART_COUNT! gtr %MAX_RESTARTS% (
    echo [%date% %time%] Too many restarts. Giving up. >> %LOG_FILE%
    echo Too many restarts. Check %LOG_FILE%
    pause
    exit /b 1
)

REM ─── Detect IP if auto ───────────────────────────────────────────────────────
set IP=
set IP_ARG=

if /i "!CONFIG_IP!"=="auto" (
    (
    echo Get-NetIPAddress -AddressFamily IPv4 ^|
    echo Where-Object {
    echo     $_.IPAddress -notmatch '^169\.254\.' -and
    echo     $_.IPAddress -notmatch '^127\.' -and
    echo     $_.InterfaceAlias -notmatch 'Loopback^|Hamachi^|Radmin^|TAP^|VMware^|VirtualBox^|Hyper-V^|vEthernet^|WSL^|Bluetooth'
    echo } ^| Select-Object -ExpandProperty IPAddress -First 1
    ) > %temp%\bl_ip.ps1
    for /f %%a in ('powershell -nologo -executionpolicy bypass -file %temp%\bl_ip.ps1') do set IP=%%a
    del %temp%\bl_ip.ps1

    if "!IP!"=="" (
        echo [%date% %time%] Could not detect IP. Retrying in 10s... >> %LOG_FILE%
        echo Could not detect IP. Retrying in 10s...
        timeout /t 10 /nobreak >nul
        goto loop
    )

    echo [%date% %time%] Detected IP: !IP! >> %LOG_FILE%
    echo Detected IP: !IP!
    set IP_ARG=--agent.ip-address=!IP!
) else (
    echo [%date% %time%] Using manual IP: !CONFIG_IP! >> %LOG_FILE%
    echo Using manual IP: !CONFIG_IP!
)

REM ─── Start agent ─────────────────────────────────────────────────────────────
echo [%date% %time%] Starting agent... >> %LOG_FILE%
start /b "" java -jar %JAR_NAME% --spring.config.location=./config/ !IP_ARG!
timeout /t 4 /nobreak >nul

REM ─── Get PID ─────────────────────────────────────────────────────────────────
(
echo $p = Get-Process java -ErrorAction SilentlyContinue ^| Sort-Object StartTime -Descending ^| Select-Object -First 1
echo if ^($p^) { $p.Id } else { '' }
) > %temp%\bl_pid.ps1
set AGENT_PID=
for /f %%a in ('powershell -nologo -executionpolicy bypass -file %temp%\bl_pid.ps1') do set AGENT_PID=%%a
del %temp%\bl_pid.ps1

if "!AGENT_PID!"=="" (
    echo [%date% %time%] Could not get PID. Agent may have crashed. >> %LOG_FILE%
    echo Agent may have crashed. Retrying...
    timeout /t 5 /nobreak >nul
    goto loop
)

echo [%date% %time%] Agent running. PID: !AGENT_PID! >> %LOG_FILE%
echo Agent running. PID: !AGENT_PID!
set RESTART_COUNT=0

REM ─── Watch loop ──────────────────────────────────────────────────────────────
:watch
timeout /t 3 /nobreak >nul

if exist %SIGNAL_FILE% (
    echo [%date% %time%] Update signal. Stopping !AGENT_PID!... >> %LOG_FILE%
    echo Update signal received. Restarting...
    taskkill /f /pid !AGENT_PID! >nul 2>&1
    timeout /t 3 /nobreak >nul
    del %SIGNAL_FILE%
    goto loop
)

tasklist /fi "pid eq !AGENT_PID!" 2>nul | find /i "java.exe" >nul
if errorlevel 1 (
    echo [%date% %time%] Agent stopped. Restarting... >> %LOG_FILE%
    echo Agent stopped. Restarting...
    goto loop
)

goto watch
