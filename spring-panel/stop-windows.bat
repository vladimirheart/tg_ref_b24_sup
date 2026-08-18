@echo off
setlocal EnableExtensions
chcp 65001 >nul

set "SCRIPT_DIR=%~dp0"
if "%SCRIPT_DIR:~-1%"=="\" set "SCRIPT_DIR=%SCRIPT_DIR:~0,-1%"
for %%I in ("%SCRIPT_DIR%\..") do set "WORKSPACE_ROOT=%%~fI"

set "APP_ONLY_ARG="

if /I "%~1"=="--app-only" set "APP_ONLY_ARG=-AppOnly"
if /I "%~1"=="-app-only" set "APP_ONLY_ARG=-AppOnly"

if /I "%~1"=="--help" goto :Help
if /I "%~1"=="-h" goto :Help

if not "%~1"=="" if not defined APP_ONLY_ARG (
    echo [ERROR] Unknown option: %~1
    echo.
    goto :HelpError
)

powershell.exe ^
    -NoLogo ^
    -NoProfile ^
    -ExecutionPolicy Bypass ^
    -File "%WORKSPACE_ROOT%\scripts\stop-windows-runtime.ps1" ^
    %APP_ONLY_ARG%

exit /b %ERRORLEVEL%

:Help
echo Usage:
echo   stop-windows.bat             Stop Spring panel, bot runtimes, PostgreSQL and RabbitMQ.
echo   stop-windows.bat --app-only  Stop only Spring panel and bot runtimes.
exit /b 0

:HelpError
echo Usage:
echo   stop-windows.bat             Stop Spring panel, bot runtimes, PostgreSQL and RabbitMQ.
echo   stop-windows.bat --app-only  Stop only Spring panel and bot runtimes.
exit /b 2
