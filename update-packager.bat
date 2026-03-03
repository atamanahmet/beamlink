@echo off
setlocal enabledelayedexpansion

REM ── Paths ──────────────────────────────────────────────────────────
set JAR_SOURCE=C:\Users\Foxit\Desktop\beamlink\beamlink-agent-dist\target\beamlink-agent-dist-0.0.1.jar
set FRONTEND_DIST=C:\Users\Foxit\Desktop\beamlink\frontend\agent-ui\dist
set FRONTEND_DIR=C:\Users\Foxit\Desktop\beamlink\frontend\agent-ui
set CONFIG_SOURCE=C:\Users\Foxit\Desktop\beamlink\beamlink-agent-dist\src\main\resources\application.yaml
set OUTPUT_DIR=C:\Users\Foxit\Desktop\beamlink
set STAGING_DIR=%OUTPUT_DIR%\update-staging
set ZIP_OUTPUT=%OUTPUT_DIR%\update.zip

REM ── Build jar ───────────────────────────────────────────────────────
echo Building agent jar...
cd C:\Users\Foxit\Desktop\beamlink\beamlink-agent-dist
echo Current dir: %CD%
call mvn package -DskipTests
if errorlevel 1 (
    echo ERROR: Maven build failed
    pause
    exit /b 1
)
echo Jar build successful.

REM ── Build frontend ──────────────────────────────────────────────────
echo Building frontend...
cd C:\Users\Foxit\Desktop\beamlink\frontend\agent-ui
echo Current dir: %CD%
call npm run build
if errorlevel 1 (
    echo ERROR: Frontend build failed
    pause
    exit /b 1
)
echo Frontend build successful.

REM ── Clean staging ───────────────────────────────────────────────────
cd %OUTPUT_DIR%
echo Cleaning staging folder...
if exist "%STAGING_DIR%" rmdir /s /q "%STAGING_DIR%"
mkdir "%STAGING_DIR%"
mkdir "%STAGING_DIR%\static"
mkdir "%STAGING_DIR%\config"

REM ── Copy jar ────────────────────────────────────────────────────────
echo Copying agent jar...
if not exist "%JAR_SOURCE%" (
    echo ERROR: Jar not found at %JAR_SOURCE%
    pause
    exit /b 1
)
copy "%JAR_SOURCE%" "%STAGING_DIR%\beamlink-agent.jar" >nul

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
echo ========================================
echo Done! update.zip created at:
echo %ZIP_OUTPUT%
echo ========================================
echo Structure inside zip:
echo   beamlink-agent.jar
echo   config\application.yaml
echo   static\...
echo ========================================
echo You can now upload this via the Nexus dashboard.
pause