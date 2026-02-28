@echo off
setlocal enabledelayedexpansion

REM ── Paths ──────────────────────────────────────────────────────────
set JAR_SOURCE=\beamlink\beamlink-agent\target\beamlink-agent-0.0.1.jar
set FRONTEND_DIST=beamlink\frontend\agent-ui\dist
set CONFIG_SOURCE=beamlink\beamlink-agent-dist\src\main\resources\application.yaml
set OUTPUT_DIR=beamlink
set STAGING_DIR=%OUTPUT_DIR%\update-staging
set ZIP_OUTPUT=%OUTPUT_DIR%\update.zip

REM ── Clean staging ───────────────────────────────────────────────────
echo Cleaning staging folder...
if exist "%STAGING_DIR%" rmdir /s /q "%STAGING_DIR%"
mkdir "%STAGING_DIR%"
mkdir "%STAGING_DIR%\static"
mkdir "%STAGING_DIR%\config"

REM ── Copy jar ────────────────────────────────────────────────────────
echo Copying agent jar...
if not exist "%JAR_SOURCE%" (
    echo ERROR: Jar not found at %JAR_SOURCE%
    echo Did you run mvn package?
    pause
    exit /b 1
)
copy "%JAR_SOURCE%" "%STAGING_DIR%\beamlink-agent-new.jar" >nul

REM ── Copy config ─────────────────────────────────────────────────────
echo Copying config...
if not exist "%CONFIG_SOURCE%" (
    echo ERROR: application.yaml not found at %CONFIG_SOURCE%
    pause
    exit /b 1
)
copy "%CONFIG_SOURCE%" "%STAGING_DIR%\config\application.yaml" >nul

REM ── Copy static files ───────────────────────────────────────────────
echo Copying frontend static files...
if not exist "%FRONTEND_DIST%" (
    echo ERROR: Frontend dist not found at %FRONTEND_DIST%
    echo Did you run npm run build?
    pause
    exit /b 1
)
xcopy "%FRONTEND_DIST%\*" "%STAGING_DIR%\static\" /e /i /q >nul

REM ── Create zip ──────────────────────────────────────────────────────
echo Creating update.zip...
if exist "%ZIP_OUTPUT%" del "%ZIP_OUTPUT%"
powershell -Command "Compress-Archive -Path '%STAGING_DIR%\*' -DestinationPath '%ZIP_OUTPUT%'"

REM ── Cleanup staging ─────────────────────────────────────────────────
rmdir /s /q "%STAGING_DIR%"

echo.
echo Done! update.zip created at:
echo %ZIP_OUTPUT%
echo.
echo Structure inside zip:
echo   beamlink-agent.jar
echo   config\application.yaml
echo   static\...
echo.
echo You can now upload this via the Nexus dashboard.
pause
```

