@ECHO OFF
SETLOCAL

SET MAVEN_VERSION=3.9.6
SET BASE_DIR=%~dp0
SET WRAPPER_DIR=%BASE_DIR%.mvn
SET MAVEN_DIR=%WRAPPER_DIR%\apache-maven-%MAVEN_VERSION%
SET MAVEN_CMD=%MAVEN_DIR%\bin\mvn.cmd
SET ARCHIVE=%WRAPPER_DIR%\apache-maven-%MAVEN_VERSION%-bin.zip
SET DOWNLOAD_URL=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/%MAVEN_VERSION%/apache-maven-%MAVEN_VERSION%-bin.zip

IF EXIST "%MAVEN_CMD%" GOTO run_maven

IF NOT EXIST "%WRAPPER_DIR%" (
  MKDIR "%WRAPPER_DIR%"
)

IF NOT EXIST "%ARCHIVE%" (
  POWERSHELL -NoProfile -ExecutionPolicy Bypass -Command "\
    Write-Host 'Downloading Maven %MAVEN_VERSION%...'; \
    Invoke-WebRequest -Uri '%DOWNLOAD_URL%' -OutFile '%ARCHIVE%'"
)

IF NOT EXIST "%ARCHIVE%" GOTO fallback_mvn

POWERSHELL -NoProfile -ExecutionPolicy Bypass -Command "\
  Write-Host 'Extracting Maven %MAVEN_VERSION%...'; \
  Expand-Archive -Path '%ARCHIVE%' -DestinationPath '%WRAPPER_DIR%' -Force"

:run_maven
IF EXIST "%MAVEN_CMD%" (
  "%MAVEN_CMD%" %*
) ELSE (
  GOTO fallback_mvn
)

:fallback_mvn
WHERE mvn >NUL 2>&1
IF %ERRORLEVEL% EQU 0 (
  ECHO Maven wrapper could not be prepared. Falling back to 'mvn' on PATH...>&2
  mvn %*
) ELSE (
  ECHO Maven wrapper could not be prepared and 'mvn' was not found on PATH.>&2
  EXIT /B 1
)
