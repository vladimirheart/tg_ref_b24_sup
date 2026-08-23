@echo off
setlocal EnableExtensions DisableDelayedExpansion
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

set "PREFLIGHT_EXIT=%ERRORLEVEL%"

if "%PREFLIGHT_EXIT%"=="3010" (
    echo.
    echo [WARN] Windows restart is required before Iguana can start.
    echo [WARN] Restart Windows and run Iguana again.
    set "EXIT_CODE=3010"
    goto :Exit
)

if "%PREFLIGHT_EXIT%"=="20" (
    echo.
    echo [INFO] Environment setup was cancelled by the user.
    set "EXIT_CODE=20"
    goto :Exit
)

if not "%PREFLIGHT_EXIT%"=="0" (
    echo.
    echo [ERROR] Iguana environment preflight failed with exit code %PREFLIGHT_EXIT%.
    set "EXIT_CODE=%PREFLIGHT_EXIT%"
    goto :Exit
)

echo [INFO] Environment preflight completed successfully.
echo.

rem ---------------------------------------------------------------------------
rem Load repository .env into THIS CMD process.
rem
rem Spring Boot does not load the repository-root .env file automatically.
rem preflight-windows.ps1 can read it for validation, but a child PowerShell
rem process cannot modify the environment of this parent CMD process.
rem Therefore .env must be imported here before Maven / Java is started.
rem
rem Existing process environment variables take precedence over .env values.
rem Delayed expansion is intentionally disabled while loading so that values
rem containing exclamation marks are not corrupted.
rem ---------------------------------------------------------------------------

if exist "%WORKSPACE_ROOT%\.env" (
    call :LoadEnvFile "%WORKSPACE_ROOT%\.env"

    if errorlevel 1 (
        echo [ERROR] Failed to load repository .env file.
        set "EXIT_CODE=1"
        goto :Exit
    )

    echo [INFO] Loaded environment from %WORKSPACE_ROOT%\.env
) else (
    echo [WARN] Repository .env file was not found.
)

rem ---------------------------------------------------------------------------
rem Local PostgreSQL bootstrap compatibility defaults.
rem
rem The first-run bootstrap currently provisions PostgreSQL and RabbitMQ for a
rem local/dev contour, but production-only Redis/S3 readiness must stay opt-in.
rem Keep the launcher session usable even for older bootstrap-generated .env
rem files that do not yet declare these compatibility flags explicitly.
rem ---------------------------------------------------------------------------

if /I "%IGUANA_BOOTSTRAP_DB_MODE%"=="postgresql" if /I "%APP_DB_MODE%"=="postgresql" (
    if not defined APP_COORDINATION_MODE (
        set "APP_COORDINATION_MODE=direct"
        echo [INFO] APP_COORDINATION_MODE was not set. Using local direct coordination for bootstrap PostgreSQL mode.
    )
    if not defined APP_COORDINATION_REQUIRED_FOR_POSTGRESQL (
        set "APP_COORDINATION_REQUIRED_FOR_POSTGRESQL=false"
        echo [INFO] APP_COORDINATION_REQUIRED_FOR_POSTGRESQL was not set. Skipping Redis-only readiness for local bootstrap mode.
    )
    if not defined APP_STORAGE_OBJECT_REQUIRED_FOR_POSTGRESQL (
        set "APP_STORAGE_OBJECT_REQUIRED_FOR_POSTGRESQL=false"
        echo [INFO] APP_STORAGE_OBJECT_REQUIRED_FOR_POSTGRESQL was not set. Keeping local filesystem object storage for bootstrap PostgreSQL mode.
    )
)

rem Fail early with a useful message instead of letting Spring Boot fail deep
rem inside Flyway/DataSource bean initialization.
if /I "%APP_DB_MODE%"=="postgresql" (
    if not defined SPRING_DATASOURCE_URL if not defined DATABASE_URL (
        echo.
        echo [ERROR] PostgreSQL mode is active, but no database URL is configured.
        echo [ERROR] Expected SPRING_DATASOURCE_URL or DATABASE_URL.
        echo [ERROR] Check: %WORKSPACE_ROOT%\.env
        echo.
        set "EXIT_CODE=1"
        goto :Exit
    )
)

if defined SPRING_DATASOURCE_URL (
    echo [INFO] PostgreSQL datasource configuration is loaded.
) else if defined DATABASE_URL (
    echo [INFO] DATABASE_URL configuration is loaded.
)

rem The rest of the launcher uses delayed expansion inside command blocks.
setlocal EnableDelayedExpansion

rem ---------------------------------------------------------------------------
rem Resolve Java again in this CMD process.
rem
rem preflight-windows.ps1 may have installed JDK 17. A child PowerShell process
rem cannot update the environment of this parent CMD process, so we search the
rem known Microsoft OpenJDK location before falling back to PATH.
rem ---------------------------------------------------------------------------

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

rem ---------------------------------------------------------------------------
rem Choose a free HTTP port when APP_HTTP_PORT was not explicitly specified.
rem ---------------------------------------------------------------------------

set "DEFAULT_PORT=%APP_HTTP_PORT%"
if not defined DEFAULT_PORT set "DEFAULT_PORT=8080"

set "PORT_RESOLVER=%SCRIPT_DIR%\scripts\resolve-panel-port.ps1"
set "PORT_RESULT_FILE=%TEMP%\iguana-panel-port-!RANDOM!-!RANDOM!.txt"

if not exist "!PORT_RESOLVER!" (
    echo [ERROR] Missing port resolver:
    echo [ERROR] !PORT_RESOLVER!
    set "EXIT_CODE=1"
    goto :Exit
)

if not defined APP_HTTP_PORT (
    powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass ^
        -File "!PORT_RESOLVER!" ^
        -StartPort !DEFAULT_PORT! ^
        -OutputPath "!PORT_RESULT_FILE!"

    set "PORT_RESOLVE_EXIT=!ERRORLEVEL!"

    if not "!PORT_RESOLVE_EXIT!"=="0" (
        echo [ERROR] Unable to resolve a free panel HTTP port. Exit code !PORT_RESOLVE_EXIT!.
        del "!PORT_RESULT_FILE!" >nul 2>&1
        set "EXIT_CODE=1"
        goto :Exit
    )

    set "APP_HTTP_PORT="
    if exist "!PORT_RESULT_FILE!" set /p "APP_HTTP_PORT="<"!PORT_RESULT_FILE!"
    del "!PORT_RESULT_FILE!" >nul 2>&1

    if not defined APP_HTTP_PORT (
        echo [ERROR] Port resolver returned no HTTP port.
        set "EXIT_CODE=1"
        goto :Exit
    )

    if not "!APP_HTTP_PORT!"=="!DEFAULT_PORT!" (
        echo [INFO] Port !DEFAULT_PORT! is already listening. Falling back to APP_HTTP_PORT=!APP_HTTP_PORT!.
    )
) else (
    powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass ^
        -File "!PORT_RESOLVER!" ^
        -StartPort !APP_HTTP_PORT! ^
        -Exact

    set "PORT_CHECK_EXIT=!ERRORLEVEL!"

    if "!PORT_CHECK_EXIT!"=="10" (
        echo [WARN] APP_HTTP_PORT=!APP_HTTP_PORT! already has a TCP listener. The application may fail to start.
    ) else if not "!PORT_CHECK_EXIT!"=="0" (
        echo [ERROR] Unable to check APP_HTTP_PORT=!APP_HTTP_PORT!. Exit code !PORT_CHECK_EXIT!.
        set "EXIT_CODE=1"
        goto :Exit
    )
)

echo [INFO] Panel URL: http://localhost:!APP_HTTP_PORT!/

rem ---------------------------------------------------------------------------
rem Validate Java.
rem ---------------------------------------------------------------------------

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

rem ---------------------------------------------------------------------------
rem Maven.
rem ---------------------------------------------------------------------------

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

rem ---------------------------------------------------------------------------
rem Force UTF-8 for Java/Maven runtime.
rem ---------------------------------------------------------------------------

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

if not "!EXIT_CODE!"=="0" (
    echo [INFO] Maven spring-boot:run exited with code !EXIT_CODE!.
    echo [INFO] Runtime launch is not retried automatically; Ctrl+C must stop the panel exactly once.
)

goto :Exit

rem ---------------------------------------------------------------------------
rem Load KEY=VALUE pairs from .env.
rem
rem Rules:
rem - empty lines are ignored;
rem - lines beginning with # are comments;
rem - existing environment variables are NOT overwritten;
rem - values may contain additional '=' characters;
rem - expected repository-generated .env files are UTF-8 without BOM.
rem ---------------------------------------------------------------------------

:LoadEnvFile

set "ENV_FILE=%~1"

if not exist "%ENV_FILE%" (
    exit /b 1
)

for /f "usebackq eol=# tokens=1,* delims==" %%A in ("%ENV_FILE%") do (
    if not "%%A"=="" (
        if not defined %%A (
            set "%%A=%%B"
        )
    )
)

exit /b 0

:RunMaven

if "!MVN_CMD!"=="mvn" (
    call mvn !MVN_REPO_ARG! !TEST_SKIP_ARGS! !EXTRA_APP_ARG! %*
) else (
    call "!MVN_CMD!" !MVN_REPO_ARG! !TEST_SKIP_ARGS! !EXTRA_APP_ARG! %*
)

exit /b !ERRORLEVEL!

:RunMavenForceUpdate

if "!MVN_CMD!"=="mvn" (
    call mvn -U !MVN_REPO_ARG! !TEST_SKIP_ARGS! !EXTRA_APP_ARG! %*
) else (
    call "!MVN_CMD!" -U !MVN_REPO_ARG! !TEST_SKIP_ARGS! !EXTRA_APP_ARG! %*
)

exit /b !ERRORLEVEL!

:ClearMavenLastUpdatedMarkers

if not exist "%MVN_REPO_DIR%" goto :eof

set "LAST_UPDATED_COUNT=0"

for /r "%MVN_REPO_DIR%" %%F in (*.lastUpdated) do (
    del /q "%%~fF" >nul 2>&1
    if not exist "%%~fF" set /a LAST_UPDATED_COUNT+=1
)

if !LAST_UPDATED_COUNT! GTR 0 (
    echo [INFO] Cleared !LAST_UPDATED_COUNT! stale Maven *.lastUpdated marker^(s^).
)

goto :eof


:Exit

if defined APP_HTTP_PORT (
    if exist "%SCRIPT_DIR%\scripts\cleanup-panel-port.ps1" (
        powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass ^
            -File "%SCRIPT_DIR%\scripts\cleanup-panel-port.ps1" ^
            -Port !APP_HTTP_PORT! ^
            -RepoRoot "%WORKSPACE_ROOT%"
    )
)

popd >nul
exit /b %EXIT_CODE%
