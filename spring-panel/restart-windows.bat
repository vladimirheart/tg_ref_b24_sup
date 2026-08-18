@echo off
setlocal EnableExtensions
chcp 65001 >nul

set "SCRIPT_DIR=%~dp0"
if "%SCRIPT_DIR:~-1%"=="\" set "SCRIPT_DIR=%SCRIPT_DIR:~0,-1%"

set "STOP_ARGS=--app-only"

if /I "%~1"=="--full" set "STOP_ARGS="
if /I "%~1"=="-full" set "STOP_ARGS="

if /I "%~1"=="--help" goto :Help
if /I "%~1"=="-h" goto :Help

if not "%~1"=="" if /I not "%~1"=="--full" if /I not "%~1"=="-full" (
    echo [ERROR] Unknown option: %~1
    echo.
    goto :HelpError
)

if defined STOP_ARGS (
    call "%SCRIPT_DIR%\stop-windows.bat" %STOP_ARGS%
) else (
    call "%SCRIPT_DIR%\stop-windows.bat"
)

if errorlevel 1 (
    echo [ERROR] Iguana stop failed. Restart cancelled.
    exit /b %ERRORLEVEL%
)

timeout /t 2 /nobreak >nul
call "%SCRIPT_DIR%\run-windows.bat"
exit /b %ERRORLEVEL%

:Help
echo Usage:
echo   restart-windows.bat         Restart Spring panel and bot runtimes; keep PostgreSQL/RabbitMQ running.
echo   restart-windows.bat --full  Stop the whole local stack, then start it again.
exit /b 0

:HelpError
echo Usage:
echo   restart-windows.bat         Restart Spring panel and bot runtimes; keep PostgreSQL/RabbitMQ running.
echo   restart-windows.bat --full  Stop the whole local stack, then start it again.
exit /b 2
