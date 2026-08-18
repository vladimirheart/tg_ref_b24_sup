@echo off
setlocal enabledelayedexpansion
chcp 65001 >nul

set "SCRIPT_DIR=%~dp0"
if "%SCRIPT_DIR:~-1%"=="\" set "SCRIPT_DIR=%SCRIPT_DIR:~0,-1%"
set "ORIGINAL_DIR=%CD%"
for %%I in ("%SCRIPT_DIR%\..") do set "WORKSPACE_ROOT=%%~fI"
pushd "%SCRIPT_DIR%" >nul

set "EXIT_CODE=0"

echo.
echo ============================================================
echo  Iguana - environment preflight
echo ============================================================
echo.

if not exist "%WORKSPACE_ROOT%\scripts\preflight-windows.ps1" (
    echo [ERROR] Missing preflight script:
    echo [ERROR] %WORKSPACE_ROOT%\scripts\preflight-windows.ps1
    set "EXIT_CODE=1"
    goto :Exit
)

powershell.exe ^
    -NoLogo ^
    -NoProfile ^
    -ExecutionPolicy Bypass ^
    -File "%WORKSPACE_ROOT%\scripts\preflight-windows.ps1"

set "PREFLIGHT_EXIT=!ERRORLEVEL!"

if "!PREFLIGHT_EXIT!"=="3010" (
    echo.
    echo [WARN] Windows restart is required before Iguana can start.
    echo [WARN] Restart Windows and run Iguana again.
    set "EXIT_CODE=3010"
    goto :Exit
)

if "!PREFLIGHT_EXIT!"=="20" (
    echo.
    echo [INFO] Environment setup was cancelled by the user.
    set "EXIT_CODE=20"
    goto :Exit
)

if not "!PREFLIGHT_EXIT!"=="0" (
    echo.
    echo [ERROR] Iguana environment preflight failed with exit code !PREFLIGHT_EXIT!.
    set "EXIT_CODE=!PREFLIGHT_EXIT!"
    goto :Exit
)

echo [INFO] Environment preflight completed successfully.
echo.

rem Resolve Java again in this CMD process because preflight may have installed it.
set "JAVA_EXE="

if defined JAVA_HOME_17 if exist "%JAVA_HOME_17%\bin\java.exe" (
    set "JAVA_HOME=%JAVA_HOME_17%"
    set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
)

if not defined JAVA_EXE if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" (
    set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
)

if not defined JAVA_EXE (
    for /d %%J in ("%ProgramFiles%\Microsoft\jdk-17*") do (
        if exist "%%~fJ\bin\java.exe" (
            set "JAVA_HOME=%%~fJ"
            set "JAVA_HOME_17=%%~fJ"
            set "JAVA_EXE=%%~fJ\bin\java.exe"
        )
    )
)

if not defined JAVA_EXE (
    for %%J in (java.exe) do (
        if not "%%~$PATH:J"=="" set "JAVA_EXE=%%~$PATH:J"
    )
)

rem Choose a free port if the default one is busy and the user has not explicitly set APP_HTTP_PORT.
set "DEFAULT_PORT=%APP_HTTP_PORT%"
if not defined DEFAULT_PORT set "DEFAULT_PORT=8080"

if not defined APP_HTTP_PORT (
    call :FindAvailablePort !DEFAULT_PORT!

    if not "!APP_HTTP_PORT!"=="!DEFAULT_PORT!" (
        call :DescribePortOwner !DEFAULT_PORT!
        echo [INFO] Port !DEFAULT_PORT! is already in use!PORT_OWNER_MESSAGE! Falling back to APP_HTTP_PORT=!APP_HTTP_PORT!.
    )
) else (
    call :CheckPort %APP_HTTP_PORT%

    if "!PORT_BUSY!"=="1" (
        call :DescribePortOwner %APP_HTTP_PORT%
        echo [WARN] APP_HTTP_PORT=%APP_HTTP_PORT% appears to be in use!PORT_OWNER_MESSAGE! The application may fail to start.
    )
)

echo [INFO] Panel URL: http://localhost:!APP_HTTP_PORT!/

if not defined JAVA_EXE (
    echo [ERROR] Java executable was not found after successful preflight.
    echo [ERROR] Expected JDK 17+ to be installed or available through JAVA_HOME/PATH.
    set "EXIT_CODE=1"
    goto :Exit
)

set "JAVA_VERSION_FILE=%TEMP%\spring-panel-java-version.txt"

"%JAVA_EXE%" -version 1>nul 2>"%JAVA_VERSION_FILE%"

if errorlevel 1 (
    echo [ERROR] Unable to determine Java version from:
    echo [ERROR] %JAVA_EXE%
    del "%JAVA_VERSION_FILE%" >nul 2>&1
    set "EXIT_CODE=1"
    goto :Exit
)

set "JAVA_VERSION_LINE="
set /p "JAVA_VERSION_LINE=" <"%JAVA_VERSION_FILE%"
del "%JAVA_VERSION_FILE%" >nul 2>&1

if not defined JAVA_VERSION_LINE (
    echo [ERROR] Unable to determine Java version.
    set "EXIT_CODE=1"
    goto :Exit
)

for /f "tokens=3 delims= " %%A in ("!JAVA_VERSION_LINE!") do set "JAVA_VERSION=%%~A"
set "JAVA_VERSION=!JAVA_VERSION:"=!"

for /f "delims=." %%M in ("!JAVA_VERSION!") do set "JAVA_MAJOR=%%M"

if not defined JAVA_MAJOR (
    echo [ERROR] Unable to determine Java major version.
    set "EXIT_CODE=1"
    goto :Exit
)

set "JAVA_EFFECTIVE_MAJOR=!JAVA_MAJOR!"

if "!JAVA_MAJOR!"=="1" (
    for /f "tokens=2 delims=." %%N in ("!JAVA_VERSION!") do set "JAVA_EFFECTIVE_MAJOR=%%N"
)

if not defined JAVA_EFFECTIVE_MAJOR (
    echo [ERROR] Unable to determine effective Java major version.
    set "EXIT_CODE=1"
    goto :Exit
)

if !JAVA_EFFECTIVE_MAJOR! LSS 17 (
    echo [ERROR] JDK 17+ is required, but Java !JAVA_VERSION! was detected.
    set "EXIT_CODE=1"
    goto :Exit
)

echo [INFO] Java runtime: !JAVA_VERSION! ^(major !JAVA_EFFECTIVE_MAJOR!^)
echo [INFO] Java executable: !JAVA_EXE!

if not "!JAVA_EFFECTIVE_MAJOR!"=="17" (
    echo [WARN] This project is primarily tested on JDK 17.
    echo [WARN] If build errors occur, set JAVA_HOME_17 to a JDK 17 installation.
)

set "MVN_CMD=%SCRIPT_DIR%\mvnw.cmd"

if exist "%MVN_CMD%" (
    rem Use the repository Maven Wrapper.
) else (
    set "MVN_CMD=mvn"
)

set "MVN_REPO_DIR=%SCRIPT_DIR%\.m2\repository"

if not exist "%SCRIPT_DIR%\.m2" (
    mkdir "%SCRIPT_DIR%\.m2" >nul 2>&1
)

set "MVN_REPO_ARG=-Dmaven.repo.local=%MVN_REPO_DIR%"

set "UTF8_JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8"

if defined JAVA_TOOL_OPTIONS (
    set "JAVA_TOOL_OPTIONS=!UTF8_JAVA_TOOL_OPTIONS! !JAVA_TOOL_OPTIONS!"
) else (
    set "JAVA_TOOL_OPTIONS=!UTF8_JAVA_TOOL_OPTIONS!"
)

if defined JAVA_OPTS (
    set "JAVA_TOOL_OPTIONS=!JAVA_TOOL_OPTIONS! !JAVA_OPTS!"
)

set "EXTRA_APP_ARG="

if defined SPRING_OPTS (
    set "EXTRA_APP_ARG=-Dspring-boot.run.arguments=!SPRING_OPTS!"
)

set "TEST_SKIP_ARGS=-Dmaven.test.skip=true"

if defined RUN_WITH_TESTS (
    set "TEST_SKIP_ARGS="
)

echo Starting Spring panel with !MVN_CMD!

set "SKIP_MAVEN_CLEAN=0"

if /I "%SPRING_PANEL_SKIP_CLEAN%"=="1" set "SKIP_MAVEN_CLEAN=1"
if /I "%SPRING_PANEL_SKIP_CLEAN%"=="true" set "SKIP_MAVEN_CLEAN=1"

if "!SKIP_MAVEN_CLEAN!"=="1" (
    echo [INFO] Skipping Maven clean phase because SPRING_PANEL_SKIP_CLEAN=%SPRING_PANEL_SKIP_CLEAN%.
) else (
    echo [INFO] Running Maven clean phase before startup to remove stale compiled classes.

    call :RunMaven clean %*

    if errorlevel 1 (
        echo [WARN] Maven clean failed. Files under target may be locked by another process.
        echo [WARN] Continuing with spring-boot:run without clean. Close IDE Java processes if startup still fails.
    )
)

call :RunMaven spring-boot:run %*
set "EXIT_CODE=!ERRORLEVEL!"

goto :Exit

:RunMaven

if "!MVN_CMD!"=="mvn" (
    call mvn !MVN_REPO_ARG! !TEST_SKIP_ARGS! !EXTRA_APP_ARG! %*
) else (
    call "!MVN_CMD!" !MVN_REPO_ARG! !TEST_SKIP_ARGS! !EXTRA_APP_ARG! %*
)

exit /b !ERRORLEVEL!

:CheckPort

set "PORT_BUSY=0"
set "PORT_TO_CHECK=%~1"

if "%PORT_TO_CHECK%"=="" goto :eof

for /f "tokens=1" %%P in ('netstat -ano -p tcp ^| findstr /R ":%PORT_TO_CHECK% " 2^>nul') do (
    set "PORT_BUSY=1"
    goto :eof
)

goto :eof

:DescribePortOwner

set "PORT_OWNER_MESSAGE="
set "PORT_OWNER_PID="
set "PORT_OWNER_NAME="
set "PORT_OWNER_PORT=%~1"

if "%PORT_OWNER_PORT%"=="" goto :eof

for /f "tokens=5" %%P in ('netstat -ano -p tcp ^| findstr /R ":%PORT_OWNER_PORT% .*LISTENING" 2^>nul') do (
    set "PORT_OWNER_PID=%%P"
    goto :DescribePortOwnerFound
)

goto :eof

:DescribePortOwnerFound

for /f "usebackq tokens=1 delims=," %%N in (`tasklist /FI "PID eq !PORT_OWNER_PID!" /FO CSV /NH 2^>nul`) do (
    set "PORT_OWNER_NAME=%%~N"
    goto :DescribePortOwnerReady
)

:DescribePortOwnerReady

if defined PORT_OWNER_PID (
    if defined PORT_OWNER_NAME (
        set "PORT_OWNER_MESSAGE= (PID !PORT_OWNER_PID!, process !PORT_OWNER_NAME!)"
    ) else (
        set "PORT_OWNER_MESSAGE= (PID !PORT_OWNER_PID!)"
    )
)

goto :eof

:FindAvailablePort

set "PORT_CANDIDATE=%~1"

if "%PORT_CANDIDATE%"=="" set "PORT_CANDIDATE=8080"

:FindAvailablePortLoop

call :CheckPort !PORT_CANDIDATE!

if "!PORT_BUSY!"=="0" (
    set "APP_HTTP_PORT=!PORT_CANDIDATE!"
    goto :eof
)

set /a PORT_CANDIDATE+=1
goto :FindAvailablePortLoop

:Exit

popd >nul
endlocal & exit /b %EXIT_CODE%
