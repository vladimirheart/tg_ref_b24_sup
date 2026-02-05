@echo off
setlocal enabledelayedexpansion

set "SCRIPT_DIR=%~dp0"
if "%SCRIPT_DIR:~-1%"=="\" set "SCRIPT_DIR=%SCRIPT_DIR:~0,-1%"
set "ORIGINAL_DIR=%CD%"
pushd "%SCRIPT_DIR%" >nul

set "JAVA_EXE="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
if not defined JAVA_EXE (
    for %%J in (java.exe) do if not "%%~$PATH:J"=="" set "JAVA_EXE=%%~$PATH:J"
)

rem Choose a port if the default one is busy and the user has not explicitly set APP_HTTP_PORT.
set "DEFAULT_PORT=%APP_HTTP_PORT%"
if not defined DEFAULT_PORT set "DEFAULT_PORT=8080"
if not defined APP_HTTP_PORT (
    call :CheckPort !DEFAULT_PORT!
    if "!PORT_BUSY!"=="1" (
        set "APP_HTTP_PORT=8081"
        echo [INFO] Port !DEFAULT_PORT! is already in use. Falling back to APP_HTTP_PORT=!APP_HTTP_PORT!.
    )
) else (
    call :CheckPort %APP_HTTP_PORT%
    if "!PORT_BUSY!"=="1" echo [WARN] APP_HTTP_PORT=%APP_HTTP_PORT% appears to be in use. The application may fail to start.
)

if not defined JAVA_EXE (
    echo [ERROR] Java executable not found. Install JDK 17 and expose it via JAVA_HOME or PATH.
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
if not "%JAVA_MAJOR%"=="17" (
    echo [ERROR] JDK 17 is required, but %JAVA_VERSION% was detected.
    exit /b 1
)

set "MVN_CMD=%SCRIPT_DIR%\mvnw.cmd"
if exist "%MVN_CMD%" (
    rem use wrapper
) else (
    set "MVN_CMD=mvn"
)

set "EXTRA_JVM_ARG="
if defined JAVA_OPTS (
    set "EXTRA_JVM_ARG=-Dspring-boot.run.jvmArguments=\"%JAVA_OPTS%\""
)

set "EXTRA_APP_ARG="
if defined SPRING_OPTS (
    set "EXTRA_APP_ARG=-Dspring-boot.run.arguments=\"%SPRING_OPTS%\""
)

echo Starting Spring panel with %MVN_CMD%
echo [INFO] Running Maven clean phase before startup to remove stale compiled classes.
if "%MVN_CMD%"=="mvn" (
    call mvn %EXTRA_JVM_ARG% %EXTRA_APP_ARG% clean spring-boot:run %*
) else (
    call "%MVN_CMD%" %EXTRA_JVM_ARG% %EXTRA_APP_ARG% clean spring-boot:run %*
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
