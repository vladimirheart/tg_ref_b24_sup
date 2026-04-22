@echo off
setlocal enabledelayedexpansion
chcp 65001 >nul

set "SCRIPT_DIR=%~dp0"
if "%SCRIPT_DIR:~-1%"=="\" set "SCRIPT_DIR=%SCRIPT_DIR:~0,-1%"
set "ORIGINAL_DIR=%CD%"
pushd "%SCRIPT_DIR%" >nul

set "JAVA_EXE="
if defined JAVA_HOME_17 if exist "%JAVA_HOME_17%\bin\java.exe" (
    set "JAVA_HOME=%JAVA_HOME_17%"
    set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
)
if not defined JAVA_EXE if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
if not defined JAVA_EXE (
    for %%J in (java.exe) do if not "%%~$PATH:J"=="" set "JAVA_EXE=%%~$PATH:J"
)

rem Choose a free port if the default one is busy and the user has not explicitly set APP_HTTP_PORT.
set "DEFAULT_PORT=%APP_HTTP_PORT%"
if not defined DEFAULT_PORT set "DEFAULT_PORT=8080"
if not defined APP_HTTP_PORT (
    call :FindAvailablePort !DEFAULT_PORT!
    if not "!APP_HTTP_PORT!"=="!DEFAULT_PORT!" (
        echo [INFO] Port !DEFAULT_PORT! is already in use. Falling back to APP_HTTP_PORT=!APP_HTTP_PORT!.
    )
) else (
    call :CheckPort %APP_HTTP_PORT%
    if "!PORT_BUSY!"=="1" echo [WARN] APP_HTTP_PORT=%APP_HTTP_PORT% appears to be in use. The application may fail to start.
)

if not defined JAVA_EXE (
    echo [ERROR] Java executable not found. Install JDK 17+ and expose it via JAVA_HOME or PATH.
    exit /b 1
)

set "JAVA_VERSION_FILE=%TEMP%\spring-panel-java-version.txt"
"%JAVA_EXE%" -version 1>nul 2>"%JAVA_VERSION_FILE%"
if errorlevel 1 (
    echo [ERROR] Unable to determine Java version.
    del "%JAVA_VERSION_FILE%" >nul 2>&1
    exit /b 1
)
set /p "JAVA_VERSION_LINE=" <"%JAVA_VERSION_FILE%"
del "%JAVA_VERSION_FILE%" >nul 2>&1
if not defined JAVA_VERSION_LINE (
    echo [ERROR] Unable to determine Java version.
    exit /b 1
)
for /f "tokens=3 delims= " %%A in ("%JAVA_VERSION_LINE%") do set "JAVA_VERSION=%%~A"
set "JAVA_VERSION=%JAVA_VERSION:\"=%"
for /f "delims=." %%M in ("%JAVA_VERSION%") do set "JAVA_MAJOR=%%M"
if not defined JAVA_MAJOR (
    echo [ERROR] Unable to determine Java version.
    exit /b 1
)
set "JAVA_EFFECTIVE_MAJOR=%JAVA_MAJOR%"
if "%JAVA_MAJOR%"=="1" (
    for /f "tokens=2 delims=." %%N in ("%JAVA_VERSION%") do set "JAVA_EFFECTIVE_MAJOR=%%N"
)
if not defined JAVA_EFFECTIVE_MAJOR (
    echo [ERROR] Unable to determine Java version.
    exit /b 1
)
if !JAVA_EFFECTIVE_MAJOR! LSS 17 (
    echo [ERROR] JDK 17+ is required, but %JAVA_VERSION% was detected.
    exit /b 1
)
echo [INFO] Java runtime: %JAVA_VERSION% (major !JAVA_EFFECTIVE_MAJOR!)
if not "!JAVA_EFFECTIVE_MAJOR!"=="17" (
    echo [WARN] This project is primarily tested on JDK 17. If build errors occur, set JAVA_HOME_17 to a JDK 17 path.
)

set "MVN_CMD=%SCRIPT_DIR%\mvnw.cmd"
if exist "%MVN_CMD%" (
    rem use wrapper
) else (
    set "MVN_CMD=mvn"
)
set "MVN_REPO_DIR=%SCRIPT_DIR%\.m2\repository"
if not exist "%SCRIPT_DIR%\.m2" mkdir "%SCRIPT_DIR%\.m2" >nul 2>&1
set "MVN_REPO_ARG=-Dmaven.repo.local=%MVN_REPO_DIR%"

set "SPRING_BOOT_JVM_ARGS=-Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8"
if defined JAVA_OPTS (
    set "SPRING_BOOT_JVM_ARGS=%SPRING_BOOT_JVM_ARGS% %JAVA_OPTS%"
)
set "EXTRA_JVM_ARG=-Dspring-boot.run.jvmArguments=\"%SPRING_BOOT_JVM_ARGS%\""

set "EXTRA_APP_ARG="
if defined SPRING_OPTS (
    set "EXTRA_APP_ARG=-Dspring-boot.run.arguments=\"%SPRING_OPTS%\""
)

set "TEST_SKIP_ARGS=-Dmaven.test.skip=true"
if defined RUN_WITH_TESTS set "TEST_SKIP_ARGS="

echo Starting Spring panel with %MVN_CMD%
echo [INFO] Running Maven clean phase before startup to remove stale compiled classes.
if "%MVN_CMD%"=="mvn" (
    call mvn !MVN_REPO_ARG! !TEST_SKIP_ARGS! !EXTRA_JVM_ARG! !EXTRA_APP_ARG! clean spring-boot:run %*
) else (
    call "%MVN_CMD%" !MVN_REPO_ARG! !TEST_SKIP_ARGS! !EXTRA_JVM_ARG! !EXTRA_APP_ARG! clean spring-boot:run %*
)

set "EXIT_CODE=%ERRORLEVEL%"
popd >nul
endlocal & exit /b %EXIT_CODE%

:CheckPort
set "PORT_BUSY=0"
set "PORT_TO_CHECK=%~1"
if "%PORT_TO_CHECK%"=="" goto :eof
for /f "tokens=1" %%P in ('netstat -ano -p tcp ^| findstr /R ":%PORT_TO_CHECK% " 2^>nul') do (
    set "PORT_BUSY=1"
    goto :eof
)
goto :eof

:FindAvailablePort
set "PORT_CANDIDATE=%~1"
if "%PORT_CANDIDATE%"=="" set "PORT_CANDIDATE=8080"
:FindAvailablePortLoop
call :CheckPort %PORT_CANDIDATE%
if "!PORT_BUSY!"=="0" (
    set "APP_HTTP_PORT=%PORT_CANDIDATE%"
    goto :eof
)
set /a PORT_CANDIDATE+=1
goto :FindAvailablePortLoop
